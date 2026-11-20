package com.jaguar.attendancetracker.ui.exams

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ExamsScreen(viewModel: ExamsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    ExamsContent(state, draft, viewModel::edit, viewModel::change, viewModel::cancel, viewModel::save)
}

@Composable
internal fun ExamsContent(
    state: ExamsState,
    draft: ExamDraft?,
    edit: (Exam?) -> Unit,
    change: (ExamDraft) -> Unit,
    cancel: () -> Unit,
    save: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.exams_title), style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.exams_intro))
                    }
                }
            }
            when (state) {
                ExamsState.Loading -> item { CircularProgressIndicator() }
                ExamsState.Unavailable -> item {
                    Text(stringResource(R.string.exams_load_failed), color = MaterialTheme.colorScheme.error)
                }
                is ExamsState.Ready -> {
                    item {
                        Button(onClick = { edit(null) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.exams_add))
                        }
                    }
                    val scheduled = state.exams.filter { it.date != null }
                        .sortedWith(compareBy<Exam> { it.date }.thenBy { it.title }.thenBy { it.id })
                    val undated = state.exams.filter { it.date == null }
                        .sortedWith(compareBy<Exam> { it.title }.thenBy { it.id })
                    val terms = state.semesters.associateBy { it.id }
                    if (state.exams.isEmpty()) {
                        item { Text(stringResource(R.string.exams_empty)) }
                    }
                    if (scheduled.isNotEmpty()) {
                        item { SectionTitle(R.string.exams_scheduled) }
                        items(scheduled, key = { it.id.toString() }) { exam ->
                            ExamCard(exam, terms[exam.semesterId]) { edit(exam) }
                        }
                    }
                    if (undated.isNotEmpty()) {
                        item { SectionTitle(R.string.exams_undated) }
                        items(undated, key = { it.id.toString() }) { exam ->
                            ExamCard(exam, terms[exam.semesterId]) { edit(exam) }
                        }
                    }
                }
            }
        }
    }

    if (draft != null && state is ExamsState.Ready) {
        ExamEditor(draft, state.semesters, change, cancel, save)
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
}

@Composable
private fun ExamCard(exam: Exam, semester: Semester?, edit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exam.title, style = MaterialTheme.typography.titleLarge)
            Text(exam.date?.displayDate() ?: stringResource(R.string.exams_date_tbd))
            Text(semesterName(semester))
            val editLabel = stringResource(R.string.exams_edit_named, exam.title)
            TextButton(onClick = edit, modifier = Modifier.semantics { contentDescription = editLabel }) {
                Text(stringResource(R.string.exams_edit))
            }
        }
    }
}

private enum class EditorPicker { DATE, SEMESTER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamEditor(
    draft: ExamDraft, semesters: List<Semester>, change: (ExamDraft) -> Unit, cancel: () -> Unit, save: () -> Unit,
) {
    // This controls only which modal is visible. Draft fields themselves stay in the ViewModel.
    var picker by remember { mutableStateOf<EditorPicker?>(null) }
    val semester = semesters.firstOrNull { it.id == draft.semesterId }
    val missingSemester = draft.semesterId != null && semester == null
    when (picker) {
        EditorPicker.DATE -> {
            val years = 1900..2100
            val date = rememberDatePickerState(
                initialSelectedDateMillis = draft.date?.takeIf { it.year in years }
                    ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                yearRange = years,
            )
            DatePickerDialog(onDismissRequest = { picker = null }, confirmButton = {
                TextButton(enabled = date.selectedDateMillis != null, onClick = {
                    date.selectedDateMillis?.let {
                        change(draft.copy(date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    picker = null
                }) { Text(stringResource(R.string.exams_set_date)) }
            }, dismissButton = {
                TextButton(onClick = { picker = null }) { Text(stringResource(R.string.exams_cancel)) }
            }) { DatePicker(state = date) }
        }
        EditorPicker.SEMESTER -> {
            AlertDialog(onDismissRequest = { picker = null },
                title = { Text(stringResource(R.string.exams_choose_semester)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TextButton(onClick = { change(draft.copy(semesterId = null)); picker = null }) {
                            Text(stringResource(R.string.exams_no_semester))
                        }
                        semesters.sortedWith(compareBy<Semester> { it.isArchived }.thenBy { it.name }.thenBy { it.id })
                            .forEach { item ->
                                TextButton(onClick = { change(draft.copy(semesterId = item.id)); picker = null }) {
                                    Text(semesterName(item))
                                }
                            }
                    }
                }, confirmButton = {
                    TextButton(onClick = { picker = null }) { Text(stringResource(R.string.exams_cancel)) }
                })
        }
        null -> AlertDialog(
            onDismissRequest = { if (!draft.saving) cancel() },
            title = { Text(stringResource(if (draft.id == null) R.string.exams_add else R.string.exams_edit)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = draft.title, onValueChange = { change(draft.copy(title = it)) },
                        enabled = !draft.saving, singleLine = true,
                        label = { Text(stringResource(R.string.exams_exam_title)) },
                    )
                    Text(draft.date?.displayDate() ?: stringResource(R.string.exams_date_tbd))
                    TextButton(enabled = !draft.saving, onClick = { picker = EditorPicker.DATE }) {
                        Text(stringResource(R.string.exams_choose_date))
                    }
                    if (draft.date != null) {
                        TextButton(enabled = !draft.saving, onClick = { change(draft.copy(date = null)) }) {
                            Text(stringResource(R.string.exams_clear_date))
                        }
                    }
                    Text(if (missingSemester) stringResource(R.string.exams_missing_semester) else semesterName(semester))
                    TextButton(enabled = !draft.saving, onClick = { picker = EditorPicker.SEMESTER }) {
                        Text(stringResource(R.string.exams_choose_semester))
                    }
                    if (draft.failed) Text(
                        stringResource(R.string.exams_save_failed), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (draft.saving) CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(onClick = save, enabled = draft.title.isNotBlank() && !draft.saving && !missingSemester) {
                    Text(stringResource(R.string.exams_save))
                }
            },
            dismissButton = {
                TextButton(onClick = cancel, enabled = !draft.saving) { Text(stringResource(R.string.exams_cancel)) }
            },
        )
    }
}

@Composable
private fun semesterName(semester: Semester?): String = when {
    semester == null -> stringResource(R.string.exams_no_semester)
    semester.isArchived -> stringResource(R.string.exams_archived_semester, semester.name)
    else -> semester.name
}

private fun LocalDate.displayDate(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
