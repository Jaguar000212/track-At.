package com.jaguar.attendancetracker.ui.exams

import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.reminders.ExamReminderPolicy
import com.jaguar.attendancetracker.backend.reminders.ReminderChoice
import com.jaguar.attendancetracker.backend.reminders.ReminderExam
import com.jaguar.attendancetracker.backend.reminders.ReminderStatus
import com.jaguar.attendancetracker.backend.reminders.ReminderTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal enum class ReminderFeedback { NOTIFICATIONS_OFF, PERMISSION_DENIED, STALE, FAILED, DATE_CHANGED, DISABLED }

internal data class ReminderEditorState(
    val id: UUID,
    val exam: ReminderExam,
    val zone: ZoneId,
    val date: LocalDate?,
    val clock: LocalTime?,
    val choice: ReminderChoice,
    val replacing: Boolean,
    val working: Boolean = false,
    val permissionDenied: Boolean = false,
    val notice: ReminderFeedback? = null,
) {
    fun change(date: LocalDate?, clock: LocalTime?, now: Instant): ReminderEditorState {
        val reviewed = if (date != null && clock != null) {
            ExamReminderPolicy.choose(exam, LocalDateTime.of(date, clock), zone, now)
        } else choice
        return copy(date = date, clock = clock, choice = reviewed, notice = null)
    }

    fun confirmedTime(): ReminderTime? = (choice as? ReminderChoice.Available)?.time
}

internal data class ReminderUiState(
    val statuses: Map<UUID, ReminderStatus> = emptyMap(),
    val statusUnavailable: Boolean = false,
    val opening: Boolean = false,
    val permissionWaiting: Boolean = false,
    val editor: ReminderEditorState? = null,
    val notice: ReminderFeedback? = null,
) {
    fun statusFor(exam: Exam): ReminderStatus? = statuses[exam.id]?.takeIf { it.examDate == exam.date }
}

internal data class ReminderPermissionAttempt(
    val editorId: UUID,
    val exam: ReminderExam,
    val time: ReminderTime,
) {
    fun matches(editor: ReminderEditorState?): Boolean =
        editor != null && editor.id == editorId && editor.exam == exam && editor.confirmedTime() == time
}
