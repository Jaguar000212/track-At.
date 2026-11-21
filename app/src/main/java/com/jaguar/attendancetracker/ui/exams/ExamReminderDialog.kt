package com.jaguar.attendancetracker.ui.exams

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.reminders.ReminderChoice
import com.jaguar.attendancetracker.backend.reminders.ReminderTimeIssue
import com.jaguar.attendancetracker.ui.components.fromPickerDateMillis
import com.jaguar.attendancetracker.ui.components.toPickerDateMillis
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ReminderPicker { DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExamReminderDialog(
    editor: ReminderEditorState,
    permissionWaiting: Boolean,
    changeDate: (LocalDate) -> Unit,
    changeClock: (LocalTime) -> Unit,
    close: () -> Unit,
    disable: () -> Unit,
    enable: () -> Unit,
    settings: () -> Unit,
) {
    var picking by remember(editor.id) { mutableStateOf<ReminderPicker?>(null) }
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val editable = !editor.working && !permissionWaiting
    val action = editor.confirmedTime()

    when (picking) {
        ReminderPicker.DATE -> {
            val years = 1900..2100
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = editor.date?.takeIf { it.year in years }?.toPickerDateMillis(),
                initialDisplayedMonthMillis = editor.exam.date?.takeIf { it.year in years }?.toPickerDateMillis(),
                yearRange = years,
            )
            DatePickerDialog(onDismissRequest = { picking = null }, confirmButton = {
                TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                    picker.selectedDateMillis?.fromPickerDateMillis()?.let(changeDate)
                    picking = null
                }) { Text(stringResource(R.string.exam_reminder_use_date)) }
            }, dismissButton = {
                TextButton(onClick = { picking = null }) { Text(stringResource(android.R.string.cancel)) }
            }) { DatePicker(picker) }
        }
        ReminderPicker.TIME -> {
            val context = LocalContext.current
            val picker = rememberTimePickerState(
                initialHour = editor.clock?.hour ?: 18,
                initialMinute = editor.clock?.minute ?: 0,
                is24Hour = android.text.format.DateFormat.is24HourFormat(context),
            )
            AlertDialog(onDismissRequest = { picking = null },
                title = { Text(stringResource(R.string.exam_reminder_choose_time)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = picker) } },
                confirmButton = {
                    TextButton(onClick = {
                        changeClock(LocalTime.of(picker.hour, picker.minute))
                        picking = null
                    }) { Text(stringResource(R.string.exam_reminder_use_time)) }
                },
                dismissButton = {
                    TextButton(onClick = { picking = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        null -> AlertDialog(
            onDismissRequest = { if (!editor.working) close() },
            title = { Text(stringResource(R.string.exam_reminder_action)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.exam_reminder_approximate))
                    Text(stringResource(R.string.exam_reminder_zone, editor.zone.id))
                    if (action != null) {
                        val offset = action.offset.id.let { if (it == "Z") "+00:00" else it }
                        Text(stringResource(R.string.exam_reminder_review_time,
                            action.local.toLocalDate().format(dateFormat), action.local.toLocalTime().format(timeFormat), offset))
                    }
                    TextButton(enabled = editable, onClick = { picking = ReminderPicker.DATE }) {
                        Text(editor.date?.let { stringResource(R.string.exam_reminder_date, it.format(dateFormat)) }
                            ?: stringResource(R.string.exam_reminder_choose_date))
                    }
                    TextButton(enabled = editable, onClick = { picking = ReminderPicker.TIME }) {
                        Text(editor.clock?.let { stringResource(R.string.exam_reminder_time, it.format(timeFormat)) }
                            ?: stringResource(R.string.exam_reminder_choose_time))
                    }
                    (editor.choice as? ReminderChoice.NeedsChoice)?.let { Text(stringResource(it.issue.label())) }
                    editor.notice?.let {
                        Text(stringResource(it.label()))
                        if (it == ReminderFeedback.NOTIFICATIONS_OFF || it == ReminderFeedback.PERMISSION_DENIED) {
                            TextButton(enabled = editable, onClick = settings) {
                                Text(stringResource(R.string.exam_reminder_settings))
                            }
                        }
                    }
                    if (editor.replacing) {
                        TextButton(enabled = editable, onClick = disable) {
                            Text(stringResource(R.string.exam_reminder_turn_off))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = editable && action != null, onClick = enable) {
                    Text(stringResource(if (editor.replacing) R.string.exam_reminder_update else R.string.exam_reminder_enable))
                }
            },
            dismissButton = {
                TextButton(enabled = !editor.working, onClick = close) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@StringRes
internal fun ReminderFeedback.label(): Int = when (this) {
    ReminderFeedback.NOTIFICATIONS_OFF -> R.string.exam_reminder_notifications_off
    ReminderFeedback.PERMISSION_DENIED -> R.string.exam_reminder_permission_denied
    ReminderFeedback.STALE -> R.string.exam_reminder_stale
    ReminderFeedback.FAILED -> R.string.exam_reminder_failed
    ReminderFeedback.DATE_CHANGED -> R.string.exam_reminder_date_changed
    ReminderFeedback.DISABLED -> R.string.exam_reminder_disabled
}

@StringRes
private fun ReminderTimeIssue.label(): Int = when (this) {
    ReminderTimeIssue.NO_EXAM_DATE -> R.string.exam_reminder_exam_date_needed
    ReminderTimeIssue.PAST -> R.string.exam_reminder_choose_future
    ReminderTimeIssue.AFTER_EXAM_DATE -> R.string.exam_reminder_after_exam
    ReminderTimeIssue.GAP -> R.string.exam_reminder_gap
    ReminderTimeIssue.INVALID_DATE -> R.string.exam_reminder_invalid_date
}
