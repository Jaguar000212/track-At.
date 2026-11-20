package com.jaguar.attendancetracker.backend.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val EXAM = "exam"
private const val REVISION = "revision"

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ExamReminderWorkerEntryPoint {
    fun reminders(): ExamReminderCoordinator
}

internal class WorkManagerReminderJobs(private val context: Context) : ReminderJobs {
    private fun name(id: UUID) = "exam-reminder:$id"

    override suspend fun replace(ticket: ReminderTicket, scheduled: Instant) {
        val delay = requireNotNull(reminderDelayMillis(Instant.now(), scheduled)) { "Reminder delay is not representable" }
        val request = OneTimeWorkRequestBuilder<ExamReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(EXAM to ticket.examId.toString(), REVISION to ticket.revision.toString()))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(ticket.examId), ExistingWorkPolicy.REPLACE, request).await()
    }

    override suspend fun cancel(examId: UUID) {
        WorkManager.getInstance(context).cancelUniqueWork(name(examId)).await()
    }
}

class ExamReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = canonicalUuid(inputData.getString(EXAM)) ?: return Result.success()
        val revision = canonicalUuid(inputData.getString(REVISION)) ?: return Result.success()
        try {
            EntryPointAccessors.fromApplication(applicationContext, ExamReminderWorkerEntryPoint::class.java)
                .reminders().deliver(ReminderTicket(id, revision))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Do not log private identities or spin after local IO or permission failure.
            return Result.failure()
        }
        return Result.success()
    }

    private fun canonicalUuid(value: String?): UUID? =
        if (value?.length == 36) runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString() == value }
        else null
}


// Round up so sub-millisecond precision can never admit an early initial delay.
internal fun reminderDelayMillis(now: Instant, scheduled: Instant): Long? {
    if (scheduled <= now) return null
    return try {
        val delay = Duration.between(now, scheduled).plusNanos(999_999).toMillis()
        Math.addExact(now.toEpochMilli(), delay)
        delay
    } catch (_: ArithmeticException) {
        null
    }
}
