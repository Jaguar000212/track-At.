package com.jaguar.attendancetracker.ui.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun LeaveScreen(viewModel: LeaveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    LeaveContent(state, today)
}

private enum class LeaveDateField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LeaveContent(state: LeaveScreenState, today: LocalDate) {
    var range by remember(today) { mutableStateOf(LeaveDateRange(today, today)) }
    var choosing by remember { mutableStateOf<LeaveDateField?>(null) }
    // Ephemeral per date range; never store private scenarios in SavedState or Room.
    val manual = remember(range) { mutableStateMapOf<java.util.UUID, String>() }
    val facts = remember(state, range, today) {
        (state as? LeaveScreenState.Ready)?.let { leaveFacts(it.snapshot, range, today) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("leave-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.leave_title), style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.leave_intro))
                    }
                }
            }
            item {
                OutlinedButton(onClick = { choosing = LeaveDateField.START }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.leave_start_date, range.first.displayDate()))
                }
            }
            item {
                OutlinedButton(onClick = { choosing = LeaveDateField.END }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.leave_end_date, range.last.displayDate()))
                }
            }
            when (state) {
                LeaveScreenState.Loading -> item { CircularProgressIndicator() }
                LeaveScreenState.Unavailable -> item { Text(stringResource(R.string.leave_load_error)) }
                is LeaveScreenState.Ready -> if (facts != null) {
                    item { LeaveSection(R.string.leave_attendance) }
                    item { Text(stringResource(R.string.leave_coverage)) }
                    if (facts.subjects.isEmpty()) item { Text(stringResource(R.string.leave_no_subjects)) }
                    items(facts.subjects, key = { "subject-${it.subject.id}" }) { subject ->
                        LeaveSubjectCard(subject, range, today, manual[subject.subject.id].orEmpty()) {
                            manual[subject.subject.id] = it
                        }
                    }
                    item { LeaveSection(R.string.leave_exams) }
                    if (facts.overlappingExams.isEmpty()) {
                        item { Text(stringResource(R.string.leave_no_dated_exams)) }
                    }
                    items(facts.overlappingExams, key = { "exam-${it.id}" }) {
                        Text(stringResource(R.string.leave_exam_row, it.title, requireNotNull(it.date).displayDate()))
                    }
                    if (facts.undatedExams.isNotEmpty()) {
                        item {
                            LeaveSection(R.string.leave_undated_exams)
                            Text(stringResource(R.string.leave_undated_caveat))
                        }
                        items(facts.undatedExams, key = { "undated-${it.id}" }) { Text(it.title) }
                    }
                }
            }
        }
    }

    choosing?.let { field ->
        val minDate = if (field == LeaveDateField.START) today else range.first
        val maxDate = if (field == LeaveDateField.START) null else range.first.plusDays(MAX_LEAVE_RANGE_DAYS - 1)
        val selection = if (field == LeaveDateField.START) range.first else range.last
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = selection.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = today.year..maxOf(today.year + 10, selection.year + 1),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val value = utcTimeMillis.localDay()
                    return value >= minDate && (maxDate == null || value <= maxDate)
                }
            },
        )
        DatePickerDialog(onDismissRequest = { choosing = null }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.localDay()?.let { selected ->
                    if (field == LeaveDateField.START) {
                        val candidate = LeaveDateRange(selected, range.last)
                        range = if (validLeaveRange(candidate, today)) candidate else LeaveDateRange(selected, selected)
                    } else if (validLeaveRange(LeaveDateRange(range.first, selected), today)) {
                        range = LeaveDateRange(range.first, selected)
                    }
                }
                choosing = null
            }) { Text(stringResource(R.string.leave_use_date)) }
        }, dismissButton = {
            TextButton(onClick = { choosing = null }) { Text(stringResource(R.string.leave_cancel)) }
        }) { DatePicker(state = picker) }
    }
}

@Composable
private fun LeaveSection(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun LeaveSubjectCard(
    facts: LeaveSubjectFacts, range: LeaveDateRange, today: LocalDate, input: String, changeInput: (String) -> Unit,
) {
    val manual = input.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
    val invalid = input.isNotEmpty() && manual == null
    val estimate = facts.estimate(range, today, manual)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(facts.subject.name, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            val counts = facts.verifiedCounts
            Text(if (counts == null) stringResource(R.string.leave_counts_unverified)
                else stringResource(R.string.leave_counts, counts.attended, counts.total))
            if (facts.subject.minAttendance in 0..100) {
                Text(stringResource(R.string.leave_target, facts.subject.minAttendance))
            }
            Text(stringResource(R.string.leave_known_entries, facts.knownTimetableMisses, facts.knownUnmarkedExtras))
            OutlinedTextField(
                value = input, onValueChange = { if (it.length <= 16) changeInput(it) },
                singleLine = true, isError = invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.leave_manual_for, facts.subject.name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val result = when {
                invalid -> stringResource(R.string.leave_invalid_manual)
                estimate is LeaveProjection.Estimate -> {
                    val relation = stringResource(if (estimate.relation == LeaveTargetRelation.AT_OR_ABOVE)
                        R.string.leave_meets_target else R.string.leave_below_target)
                    stringResource(R.string.leave_projected, estimate.after.attended, estimate.after.total, relation)
                }
                estimate is LeaveProjection.Unknown && estimate.reason == LeaveUnknownReason.NO_ATTENDANCE_HISTORY ->
                    stringResource(R.string.leave_no_history)
                estimate is LeaveProjection.Unknown && estimate.reason == LeaveUnknownReason.INVALID_TARGET ->
                    stringResource(R.string.leave_invalid_target)
                counts == null -> stringResource(R.string.leave_no_projection)
                else -> stringResource(R.string.leave_enter_manual)
            }
            Text(result, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

private fun Long.localDay(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun LocalDate.displayDate(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
