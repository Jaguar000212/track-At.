package com.jaguar.attendancetracker.ui.exams

import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.reminders.ExamReminderPolicy
import com.jaguar.attendancetracker.backend.reminders.ReminderChoice
import com.jaguar.attendancetracker.backend.reminders.ReminderExam
import com.jaguar.attendancetracker.backend.reminders.ReminderStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class ExamReminderUiStateTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val exam = ReminderExam(UUID(0, 1), LocalDate.of(2026, 11, 21))

    @Test fun pastSuggestionNeedsBothDateAndTime() {
        val now = Instant.parse("2026-11-21T03:00:00Z")
        val editor = ReminderEditorState(UUID(0, 2), exam, zone, null, null,
            ExamReminderPolicy.suggest(exam, zone, now), replacing = false)
        assertNull(editor.confirmedTime())
        val dated = editor.change(exam.date, null, now)
        assertNull(dated.confirmedTime())
        val reviewed = dated.change(exam.date, LocalTime.of(18, 0), now)
        assertEquals(Instant.parse("2026-11-21T12:30:00Z"), reviewed.confirmedTime()?.scheduled)
        assertEquals(zone, reviewed.confirmedTime()?.zone)
    }

    @Test fun permissionResultOnlyMatchesSameLiveReview() {
        val now = Instant.parse("2026-11-19T00:00:00Z")
        val choice = ExamReminderPolicy.suggest(exam, zone, now) as ReminderChoice.Available
        val time = choice.time
        val editor = ReminderEditorState(UUID(0, 2), exam, zone, time.local.toLocalDate(),
            time.local.toLocalTime(), choice, replacing = false)
        val attempt = ReminderPermissionAttempt(editor.id, exam, time)
        assertTrue(attempt.matches(editor))
        assertFalse(attempt.matches(null))
        assertFalse(attempt.matches(editor.copy(id = UUID(0, 3))))
        assertFalse(attempt.matches(editor.change(editor.date, LocalTime.of(19, 0), now)))
        assertFalse(attempt.matches(editor.copy(exam = exam.copy(date = exam.date?.plusDays(1)))))
    }

    @Test fun staleScheduledBadgeCannotAttachToChangedExamDate() {
        val date = requireNotNull(exam.date)
        val displayed = Exam(id = exam.id, title = "Synthetic fixture", date = date)
        val time = (ExamReminderPolicy.suggest(exam, zone, Instant.parse("2026-11-19T00:00:00Z"))
            as ReminderChoice.Available).time
        val status = ReminderStatus(date, time, queued = true, notificationsAllowed = true)
        val ui = ReminderUiState(statuses = mapOf(exam.id to status))
        assertEquals(status, ui.statusFor(displayed))
        assertNull(ui.statusFor(displayed.copy(date = date.plusDays(1))))
        assertNull(ui.statusFor(displayed.copy(date = null)))
    }
}
