package com.jaguar.attendancetracker.backend.reminders

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

class ExamReminderRuntimeTest {
    @Test fun consentClaimsOnceAndDateRoundTripCannotReviveOldWork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = Files.createTempDirectory(context.noBackupFilesDir.toPath(), "reminder-test-").toFile()
        val exam = ReminderExam(UUID.randomUUID(), LocalDate.of(2026, 7, 3))
        var current = exam
        val clock = MutableClock(Instant.parse("2026-07-01T12:00:00Z"))
        val jobs = FakeJobs()
        val notice = FakeNotice()
        fun coordinator() = ExamReminderCoordinator(ReminderConsentFiles(directory), { current }, jobs, notice, clock)
        try {
            val first = coordinator()
            val zone = ZoneId.of("Asia/Kolkata")
            val time = (ExamReminderPolicy.suggest(exam, zone, clock.instant()) as ReminderChoice.Available).time
            assertEquals(ReminderEnable.SCHEDULED, first.enable(exam, time))
            val old = checkNotNull(jobs.ticket)
            // Even a failed platform cancellation cannot leave old consent valid through A→B→A.
            jobs.failCancel = true
            val moved = exam.date?.plusDays(1)
            first.changeExamDate(exam.id, moved) { current = exam.copy(date = moved) }
            first.changeExamDate(exam.id, exam.date) { current = exam }
            clock.now = time.scheduled
            coordinator().deliver(old)
            assertEquals(0, notice.posts)
            assertNull(ReminderConsentFiles(directory).current(exam.id))

            clock.now = time.scheduled.minusSeconds(60)
            assertEquals(ReminderEnable.SCHEDULED, first.enable(exam, time))
            val newer = checkNotNull(jobs.ticket)
            assertNotEquals(old, newer)
            first.changeExamDate(exam.id, exam.date) { /* title-only edit */ }
            assertEquals(newer, ReminderConsentFiles(directory).current(exam.id)?.ticket)
            coordinator().deliver(newer)
            assertEquals(0, notice.posts) // cannot notify early; the one-off attempt is spent
            assertNull(ReminderConsentFiles(directory).current(exam.id))
            assertEquals(ReminderEnable.SCHEDULED, first.enable(exam, time))
            val latest = checkNotNull(jobs.ticket)
            clock.now = time.scheduled
            coordinator().deliver(old)
            coordinator().deliver(newer)
            coordinator().deliver(latest)
            coordinator().deliver(latest)
            assertEquals(1, notice.posts)
            assertNull(ReminderConsentFiles(directory).current(exam.id))
            assertTrue(directory.canonicalPath.startsWith(context.noBackupFilesDir.canonicalPath + File.separator))
        } finally { directory.deleteRecursively() }
    }

    @Test fun defaultWorkManagerKeepsOneDelayedRequestPerExam() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val jobs = WorkManagerReminderJobs(context)
        val id = UUID.randomUUID()
        val now = Instant.now()
        val future = now.plusSeconds(3600)
        assertEquals(1L, reminderDelayMillis(now, now.plusNanos(1)))
        assertNull(reminderDelayMillis(now, now))
        assertNull(reminderDelayMillis(now, Instant.MAX))
        try {
            jobs.replace(ReminderTicket(id, UUID.randomUUID()), future)
            jobs.replace(ReminderTicket(id, UUID.randomUUID()), future)
            val results = manager.getWorkInfosForUniqueWork("exam-reminder:$id").get(10, TimeUnit.SECONDS)
            assertEquals(1, results.count { it.state == WorkInfo.State.ENQUEUED })
            assertEquals(0, results.count { it.state == WorkInfo.State.RUNNING })
        } finally { jobs.cancel(id) }
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
    }

    private class FakeJobs : ReminderJobs {
        var ticket: ReminderTicket? = null
        var failCancel = false
        override suspend fun replace(ticket: ReminderTicket, scheduled: Instant) { this.ticket = ticket }
        override suspend fun cancel(examId: UUID) { if (failCancel) error("Simulated scheduler cancellation failure") }
    }

    private class FakeNotice : ReminderNotice {
        var posts = 0
        override fun allowed(createChannel: Boolean) = true
        override fun post(examId: UUID) { posts++ }
        override fun cancel(examId: UUID) = Unit
    }
}
