package com.jaguar.attendancetracker.backend.reminders

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** Data used by the reminder policy; exam titles and semester details never enter it. */
internal data class ReminderExam(val id: UUID, val date: LocalDate?)
internal data class ReminderTime(
    val local: LocalDateTime,
    val zone: ZoneId,
    val offset: ZoneOffset,
    val scheduled: Instant,
    val expiresAt: Instant,
)

internal enum class ReminderTimeIssue { NO_EXAM_DATE, PAST, AFTER_EXAM_DATE, GAP, INVALID_DATE }
internal sealed interface ReminderChoice {
    data class Available(val time: ReminderTime) : ReminderChoice
    data class NeedsChoice(val issue: ReminderTimeIssue) : ReminderChoice
}

/** Requested work carries only this versioned identity, never a title or user data payload. */
internal data class ReminderTicket(val examId: UUID, val revision: UUID)
internal data class ReminderConsent(
    val ticket: ReminderTicket,
    val examDate: LocalDate,
    val time: ReminderTime,
)

internal enum class ReminderDelivery { NO_CONSENT, STALE, TOO_EARLY, EXPIRED, ELIGIBLE }

internal object ExamReminderPolicy {
    private val defaultTime = LocalTime.of(18, 0)
    private val maxLateness = Duration.ofHours(2)

    fun suggest(exam: ReminderExam, zone: ZoneId, now: Instant): ReminderChoice {
        val date = exam.date ?: return ReminderChoice.NeedsChoice(ReminderTimeIssue.NO_EXAM_DATE)
        return try {
            choose(exam, date.minusDays(1).atTime(defaultTime), zone, now)
        } catch (_: DateTimeException) {
            ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE)
        }
    }

    fun choose(exam: ReminderExam, local: LocalDateTime, zone: ZoneId, now: Instant): ReminderChoice {
        val examDate = exam.date ?: return ReminderChoice.NeedsChoice(ReminderTimeIssue.NO_EXAM_DATE)
        val choice = resolve(examDate, local, zone)
        return if (choice is ReminderChoice.Available && choice.time.scheduled <= now) {
            ReminderChoice.NeedsChoice(ReminderTimeIssue.PAST)
        } else choice
    }

    /**
     * This models explicit consent only. Callers must not create consent when loading/importing
     * an exam. A fresh caller-generated revision is required for every enable or edit.
     */
    fun consent(
        exam: ReminderExam, local: LocalDateTime, zone: ZoneId, revision: UUID, now: Instant,
    ): ReminderConsent? {
        val date = exam.date ?: return null
        val time = (choose(exam, local, zone, now) as? ReminderChoice.Available)?.time ?: return null
        return ReminderConsent(ReminderTicket(exam.id, revision), date, time)
    }

    /**
     * A worker must read current local consent and the current exam, then own/consume the still
     * current revision before posting. ELIGIBLE alone is not a notification or a one-shot claim.
     */
    fun delivery(ticket: ReminderTicket, current: ReminderExam?, consent: ReminderConsent?, now: Instant): ReminderDelivery {
        if (consent == null) return ReminderDelivery.NO_CONSENT
        if (consent.ticket != ticket || current == null || current.id != ticket.examId || current.date != consent.examDate) {
            return ReminderDelivery.STALE
        }
        val time = consent.time
        // Fail closed for malformed storage or timezone rule changes that alter the reviewed time.
        val resolved = resolve(consent.examDate, time.local, time.zone) as? ReminderChoice.Available
        if (resolved?.time != time) return ReminderDelivery.STALE
        return when {
            now < time.scheduled -> ReminderDelivery.TOO_EARLY
            now >= time.expiresAt -> ReminderDelivery.EXPIRED
            else -> ReminderDelivery.ELIGIBLE
        }
    }

    private fun resolve(examDate: LocalDate, local: LocalDateTime, zone: ZoneId): ReminderChoice {
        if (local.toLocalDate() > examDate) {
            return ReminderChoice.NeedsChoice(ReminderTimeIssue.AFTER_EXAM_DATE)
        }
        return try {
            if (examDate.atStartOfDay(zone).toLocalDate() != examDate) {
                return ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE)
            }
            val offset = zone.rules.getValidOffsets(local).firstOrNull()
                ?: return ReminderChoice.NeedsChoice(ReminderTimeIssue.GAP)
            // The earlier instant in an overlap is deterministic and returned for the UI to show.
            val scheduled = local.toInstant(offset)
            val examEnd = examDate.plusDays(1).atStartOfDay(zone).toInstant()
            val expires = minOf(scheduled.plus(maxLateness), examEnd)
            if (expires <= scheduled) {
                ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE)
            } else ReminderChoice.Available(ReminderTime(local, zone, offset, scheduled, expires))
        } catch (_: DateTimeException) {
            ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE)
        }
    }
}
