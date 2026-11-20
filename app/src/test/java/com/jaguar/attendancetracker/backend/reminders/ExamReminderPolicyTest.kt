package com.jaguar.attendancetracker.backend.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ExamReminderPolicyTest {
    private val now = Instant.parse("2026-07-01T12:00:00Z")
    private val exam = ReminderExam(UUID(0, 1), LocalDate.of(2026, 7, 3))
    private val zone = ZoneId.of("Asia/Kolkata")
    private val otherZone = ZoneId.of("America/New_York")
    private val revision = UUID(0, 2)
    private val local = LocalDateTime.parse("2026-07-02T18:00")

    @Test fun suggestionUsesPreviousCalendarDayAndOriginZone() {
        val proposed = ExamReminderPolicy.suggest(exam, zone, now) as ReminderChoice.Available
        assertEquals(local, proposed.time.local)
        assertEquals(zone, proposed.time.zone)
        assertEquals(Instant.parse("2026-07-02T12:30:00Z"), proposed.time.scheduled)
        assertEquals(Instant.parse("2026-07-02T14:30:00Z"), proposed.time.expiresAt)
    }

    @Test fun noDatePastDefaultAndOverflowNeverProduceAnImmediateReminder() {
        val undated = exam.copy(date = null)
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.NO_EXAM_DATE),
            ExamReminderPolicy.suggest(undated, zone, now))
        assertNull(ExamReminderPolicy.consent(undated, local, zone, revision, now))
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.PAST),
            ExamReminderPolicy.suggest(exam, zone, Instant.parse("2026-07-02T12:30:00Z")))
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE),
            ExamReminderPolicy.suggest(exam.copy(date = LocalDate.MIN), zone, now))
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE),
            ExamReminderPolicy.suggest(exam.copy(date = LocalDate.MAX), zone, now))
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.INVALID_DATE), ExamReminderPolicy.suggest(
            exam.copy(date = LocalDate.of(2011, 12, 30)), ZoneId.of("Pacific/Apia"), Instant.EPOCH))
    }

    @Test fun springGapRejectedAndFallOverlapUsesEarlierInstant() {
        val spring = LocalDateTime.parse("2026-03-08T02:30")
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.GAP), ExamReminderPolicy.choose(
            exam.copy(date = spring.toLocalDate()), spring, otherZone, Instant.EPOCH))
        val fall = LocalDateTime.parse("2026-11-01T01:30")
        val choice = ExamReminderPolicy.choose(exam.copy(date = fall.toLocalDate()),
            fall, otherZone, now) as ReminderChoice.Available
        assertEquals(ZoneOffset.ofHours(-4), choice.time.offset)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), choice.time.scheduled)
    }

    @Test fun explicitReplacementCannotCrossExamDateAndCutoffIsExclusive() {
        val consent = checkNotNull(ExamReminderPolicy.consent(
            exam, LocalDateTime.parse("2026-07-03T23:30"), zone, revision, now))
        val end = Instant.parse("2026-07-03T18:30:00Z")
        assertEquals(end, consent.time.expiresAt)
        assertEquals(ReminderDelivery.TOO_EARLY,
            ExamReminderPolicy.delivery(consent.ticket, exam, consent, consent.time.scheduled.minusNanos(1)))
        assertEquals(ReminderDelivery.ELIGIBLE,
            ExamReminderPolicy.delivery(consent.ticket, exam, consent, consent.time.scheduled))
        assertEquals(ReminderDelivery.ELIGIBLE,
            ExamReminderPolicy.delivery(consent.ticket, exam, consent, end.minusNanos(1)))
        assertEquals(ReminderDelivery.EXPIRED, ExamReminderPolicy.delivery(consent.ticket, exam, consent, end))
        assertEquals(ReminderChoice.NeedsChoice(ReminderTimeIssue.AFTER_EXAM_DATE), ExamReminderPolicy.choose(
            exam, LocalDateTime.parse("2026-07-04T00:00"), zone, now))
    }

    @Test fun noConsentOldRevisionChangedDateRemovedExamOrCorruptTimeCannotDeliver() {
        val consent = checkNotNull(ExamReminderPolicy.consent(exam, local, zone, revision, now))
        val at = consent.time.scheduled
        assertEquals(ReminderDelivery.NO_CONSENT, ExamReminderPolicy.delivery(consent.ticket, exam, null, at))
        assertEquals(ReminderDelivery.STALE,
            ExamReminderPolicy.delivery(consent.ticket.copy(revision = UUID(0, 3)), exam, consent, at))
        assertEquals(ReminderDelivery.STALE,
            ExamReminderPolicy.delivery(consent.ticket.copy(examId = UUID(0, 4)), exam, consent, at))
        assertEquals(ReminderDelivery.STALE,
            ExamReminderPolicy.delivery(consent.ticket, exam.copy(date = exam.date?.plusDays(1)), consent, at))
        assertEquals(ReminderDelivery.STALE,
            ExamReminderPolicy.delivery(consent.ticket, exam.copy(date = null), consent, at))
        assertEquals(ReminderDelivery.STALE, ExamReminderPolicy.delivery(consent.ticket, null, consent, at))
        assertEquals(ReminderDelivery.STALE, ExamReminderPolicy.delivery(consent.ticket, exam,
            consent.copy(time = consent.time.copy(zone = otherZone)), at))
    }
}
