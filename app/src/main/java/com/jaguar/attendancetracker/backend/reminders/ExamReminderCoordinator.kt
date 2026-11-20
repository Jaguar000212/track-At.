package com.jaguar.attendancetracker.backend.reminders

import android.content.Context
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal interface ReminderJobs {
    suspend fun replace(ticket: ReminderTicket, scheduled: Instant)
    suspend fun pending(ticket: ReminderTicket): Boolean
    suspend fun cancel(examId: UUID)
}

internal interface ReminderNotice {
    fun allowed(createChannel: Boolean): Boolean
    fun post(examId: UUID)
    fun cancel(examId: UUID)
}

internal enum class ReminderEnable { SCHEDULED, STALE_OR_INVALID, NOTIFICATIONS_OFF }

internal data class ReminderStatus(
    val examDate: LocalDate, val time: ReminderTime, val queued: Boolean, val notificationsAllowed: Boolean,
)

@Singleton
internal class ExamReminderCoordinator(
    private val files: ReminderConsentFiles,
    private val readExam: suspend (UUID) -> ReminderExam?,
    private val jobs: ReminderJobs,
    private val notice: ReminderNotice,
    private val clock: Clock,
) {
    @Inject constructor(@ApplicationContext context: Context, db: AttendanceDatabase) : this(
        ReminderConsentFiles(File(context.noBackupFilesDir, "exam-reminders")),
        { id -> db.examDao().reminderExam(id)?.let { ReminderExam(it.id, it.date) } },
        WorkManagerReminderJobs(context),
        AndroidReminderNotice(context),
        Clock.systemUTC(),
    )

    private val lock = Mutex()
    private val changes = MutableStateFlow(0L)
    val revisions = changes.asStateFlow()

    suspend fun statuses(ids: List<UUID>): Map<UUID, ReminderStatus> = withContext(Dispatchers.IO) {
        lock.withLock {
            buildMap {
                for (id in ids) {
                    val consent = files.current(id) ?: continue
                    val validity = ExamReminderPolicy.delivery(consent.ticket, readExam(id), consent, clock.instant())
                    if (validity == ReminderDelivery.TOO_EARLY || validity == ReminderDelivery.ELIGIBLE) {
                        put(id, ReminderStatus(consent.examDate, consent.time, jobs.pending(consent.ticket),
                            notice.allowed(createChannel = false)))
                    }
                }
            }
        }
    }

    /** Future UI can call this only from an explicit confirmed, already-previewed decision. */
    suspend fun enable(reviewedExam: ReminderExam, reviewedTime: ReminderTime): ReminderEnable = serialized {
        val current = readExam(reviewedExam.id)
        if (current != reviewedExam) return@serialized ReminderEnable.STALE_OR_INVALID
        val consent = ExamReminderPolicy.consent(reviewedExam, reviewedTime.local, reviewedTime.zone, UUID.randomUUID(), clock.instant())
            ?: return@serialized ReminderEnable.STALE_OR_INVALID
        if (consent.time != reviewedTime || reminderDelayMillis(clock.instant(), consent.time.scheduled) == null) {
            return@serialized ReminderEnable.STALE_OR_INVALID
        }
        if (!notice.allowed(createChannel = true)) return@serialized ReminderEnable.NOTIFICATIONS_OFF
        // Retire old consent before replacing work. The new worker cannot race ahead of
        // the local consent write because it takes this same lock. Partial failure is off.
        revokeLocked(reviewedExam.id)
        jobs.replace(consent.ticket, consent.time.scheduled)
        files.enable(consent)
        ReminderEnable.SCHEDULED
    }

    suspend fun disable(id: UUID) = serialized { revokeLocked(id) }

    /**
     * Future Exam edit/delete paths must change dates inside this lock. A failed invalidation
     * stops the edit. A failed DB write can leave the old reminder safely disabled, never revived.
     * Passing an unchanged date (e.g. a title edit) preserves existing reminder consent.
     */
    suspend fun <T> changeExamDate(id: UUID, proposedDate: LocalDate?, change: suspend () -> T): T = serialized {
        val current = readExam(id)
        if (current == null || current.date != proposedDate) revokeLocked(id)
        change()
    }

    suspend fun deliver(ticket: ReminderTicket) = withContext(Dispatchers.IO) {
        lock.withLock {
            val consent = files.current(ticket.examId) ?: return@withLock
            val exam = readExam(ticket.examId)
            val result = ExamReminderPolicy.delivery(ticket, exam, consent, clock.instant())
            if (result == ReminderDelivery.STALE || result == ReminderDelivery.NO_CONSENT) return@withLock
            // This one-time request has run, even if the device clock or permission now
            // disallows posting. Consume it so later UI cannot show this spent work as active.
            if (files.claim(ticket)) {
                changes.update { it + 1 }
                if (result != ReminderDelivery.ELIGIBLE) return@withLock
                // A clock change during the file commit must not post early or too late.
                val finalTime = clock.instant()
                if (finalTime >= consent.time.scheduled && finalTime < consent.time.expiresAt &&
                    notice.allowed(createChannel = false)
                ) notice.post(ticket.examId)
            }
        }
    }

    private suspend fun revokeLocked(id: UUID) {
        files.revoke(id)
        // Cancelling work is cleanup only. Durable revocation, not WorkManager cancellation,
        // is what makes concurrent, failed-cancel or previously restored work harmless.
        notice.cancel(id)
        try {
            jobs.cancel(id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A revocation remains in force even if scheduler cleanup fails.
        }
    }

    private suspend fun <T> serialized(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { withContext(NonCancellable) {
            try { action() } finally { changes.update { it + 1 } }
        } }
    }
}
