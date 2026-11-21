package com.jaguar.attendancetracker.ui.rollover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.components.fromPickerDateMillis
import com.jaguar.attendancetracker.ui.components.toPickerDateMillis
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun RolloverScreen(onDone: () -> Unit, viewModel: RolloverViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RolloverContent(state, viewModel::change, viewModel::review, viewModel::editAgain, viewModel::confirm, onDone)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RolloverContent(
    state: RolloverState,
    onChange: (RolloverDraft) -> Unit,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(enabled = state.review != null) { if (!state.working) onEdit() }
    val review = state.review
    val draft = state.draft
    val details = draft.details
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val source = state.source
    val isDark = isSystemInDarkTheme()
    var pickingEnd by remember { mutableStateOf<Boolean?>(null) }
    pickingEnd?.let { end ->
        val initial = if (end) details.endDate ?: details.startDate else details.startDate
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerDateMillis())
        DatePickerDialog(
            onDismissRequest = { pickingEnd = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.fromPickerDateMillis()?.let { picked ->
                        onChange(draft.copy(details = if (end) details.copy(endDate = picked) else details.copy(startDate = picked)))
                    }
                    pickingEnd = null
                }, enabled = picker.selectedDateMillis != null) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickingEnd = null }) { Text(stringResource(android.R.string.cancel)) } },
        ) { DatePicker(picker) }
    }
    // A distinct list for each step starts the review at the destination details.
    key(review != null, state.created) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("rollover-list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(if (review == null) R.string.rollover_title else R.string.rollover_review_title),
                    style = AppTypography.titleLarge, modifier = Modifier.semantics { heading() },
                )
            }
            state.notice?.let { notice ->
                item { Text(stringResource(notice.label()), color = MaterialTheme.colorScheme.error) }
            }
            when {
                state.created -> {
                    item { Text(stringResource(R.string.rollover_created, details.name)) }
                    item { Button(onClick = onDone) { Text(stringResource(R.string.rollover_done)) } }
                }
                state.loading -> item { CircularProgressIndicator() }
                review != null -> {
                    item { Text(stringResource(R.string.rollover_from, review.sourceName)) }
                    item { Text(review.newTerm.name, style = AppTypography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    item { Text(stringResource(R.string.rollover_start, review.newTerm.startDate.format(formatter))) }
                    item { Text(stringResource(R.string.rollover_end, review.newTerm.endDate?.format(formatter)
                        ?: stringResource(R.string.rollover_no_end))) }
                    item { Text(stringResource(R.string.rollover_target, review.newTerm.targetAttendance)) }
                    item { Text(stringResource(R.string.rollover_copy_scope)) }
                    items(review.courses) { course ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).background(Color(course.color.color(isDark)), CircleShape))
                            Text(stringResource(R.string.rollover_course_color, course.name, stringResource(course.color.label())))
                        }
                    }
                    if (review.slots.isEmpty()) {
                        item { Text(stringResource(R.string.rollover_no_timetable_copy)) }
                    } else {
                        item { Text(stringResource(R.string.rollover_timetable_review), Modifier.semantics { heading() }) }
                        items(review.slots) { slot ->
                            Text(stringResource(R.string.rollover_timetable_first,
                                stringResource(R.string.rollover_slot_label, slot.courseName, weekdayName(slot.weekday), slot.ordinal),
                                slot.firstDate.format(formatter)))
                        }
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onEdit, enabled = !state.working) { Text(stringResource(R.string.rollover_edit)) }
                            Button(onClick = onConfirm, enabled = !state.working) { Text(stringResource(R.string.rollover_create)) }
                        }
                    }
                    if (state.working) item { CircularProgressIndicator() }
                }
                source != null -> {
                    item { Text(stringResource(R.string.rollover_from, source.name)) }
                    item {
                        OutlinedTextField(
                            value = details.name, onValueChange = { onChange(draft.copy(details = details.copy(name = it))) },
                            enabled = !state.working, singleLine = true,
                            label = { Text(stringResource(R.string.rollover_name)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { pickingEnd = false }, enabled = !state.working) {
                                Text(stringResource(R.string.rollover_start, details.startDate.format(formatter)))
                            }
                            TextButton(onClick = { pickingEnd = true }, enabled = !state.working) {
                                Text(stringResource(R.string.rollover_end, details.endDate?.format(formatter)
                                    ?: stringResource(R.string.rollover_no_end)))
                            }
                            if (details.endDate != null) {
                                TextButton(onClick = { onChange(draft.copy(details = details.copy(endDate = null))) }, enabled = !state.working) {
                                    Text(stringResource(R.string.rollover_clear_end))
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.rollover_target, details.targetAttendance)) }
                    item {
                        Slider(
                            value = details.targetAttendance.toFloat(),
                            onValueChange = { onChange(draft.copy(details = details.copy(targetAttendance = (it / 5).roundToInt() * 5))) },
                            valueRange = 0f..100f, steps = 19, enabled = !state.working,
                        )
                    }
                    item { Text(stringResource(R.string.rollover_pick_courses), modifier = Modifier.semantics { heading() }) }
                    if (source.courses.isEmpty()) item { Text(stringResource(R.string.rollover_no_courses)) }
                    items(source.courses, key = { it.id }) { course ->
                        val checked = course.id in draft.selected
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = checked, enabled = !state.working, role = Role.Checkbox,
                                onValueChange = { checkedNow ->
                                    onChange(draft.copy(selected = if (checkedNow) draft.selected + course.id else draft.selected - course.id))
                                },
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, enabled = !state.working)
                            Text(course.name)
                        }
                    }
                    val offered = source.slots.filter { it.row.subjectId in draft.selected }
                    val courseNames = source.courses.associate { it.id to it.name }
                    if (offered.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.rollover_timetable_optional), Modifier.semantics { heading() })
                            Text(stringResource(R.string.rollover_timetable_explanation))
                        }
                        items(offered, key = { "slot:${it.row.id}" }) { slot ->
                            val checked = slot.row.id in draft.selectedSlots
                            Row(
                                Modifier.fillMaxWidth().toggleable(
                                    value = checked, enabled = !state.working, role = Role.Checkbox,
                                    onValueChange = { selected ->
                                        val id = slot.row.id
                                        onChange(draft.copy(selectedSlots = if (selected) draft.selectedSlots + id else draft.selectedSlots - id))
                                    },
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, onCheckedChange = null, enabled = !state.working)
                                Text(stringResource(R.string.rollover_slot_label, courseNames.getValue(slot.row.subjectId),
                                    weekdayName(slot.row.dayOfWeek), slot.ordinal))
                            }
                        }
                    }
                    item {
                        Button(onClick = onReview, enabled = !state.working && details.name.isNotBlank() && draft.selected.isNotEmpty()) {
                            Text(stringResource(R.string.rollover_review_action))
                        }
                    }
                    if (state.working) item { CircularProgressIndicator() }
                }
            }
        }
    }
}

private fun RolloverNotice.label(): Int = when (this) {
    RolloverNotice.UNAVAILABLE -> R.string.rollover_source_unavailable
    RolloverNotice.INVALID_TERM -> R.string.rollover_invalid_term
    RolloverNotice.INVALID_SELECTION -> R.string.rollover_invalid_selection
    RolloverNotice.INVALID_SLOT_DATES -> R.string.rollover_invalid_slot_dates
    RolloverNotice.TIMETABLE_FULL -> R.string.rollover_timetable_full
    RolloverNotice.STALE -> R.string.rollover_stale
    RolloverNotice.READ_FAILED -> R.string.rollover_read_failed
    RolloverNotice.WRITE_FAILED -> R.string.rollover_write_failed
}

private fun SubjectColor.label(): Int = when (this) {
    SubjectColor.RED -> R.string.rollover_red
    SubjectColor.BLUE -> R.string.rollover_blue
    SubjectColor.GREEN -> R.string.rollover_green
    SubjectColor.YELLOW -> R.string.rollover_yellow
    SubjectColor.PURPLE -> R.string.rollover_purple
    SubjectColor.TEAL -> R.string.rollover_teal
    SubjectColor.PINK -> R.string.rollover_pink
    SubjectColor.ORANGE -> R.string.rollover_orange
    SubjectColor.GRAY -> R.string.rollover_gray
}

private fun weekdayName(day: Int): String = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, Locale.getDefault())
