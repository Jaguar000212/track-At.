package com.jaguar.attendancetracker.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.Semester
import java.util.UUID
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The two scopes have separate composition lifetimes, so an active editor cannot follow into history. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SemesterBrowser(content: @Composable (showArchived: Boolean) -> Unit) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val scopeState = rememberSaveableStateHolder()
    BackHandler(enabled = showArchived) { showArchived = false }
    Column(Modifier.fillMaxSize()) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(!showArchived, { showArchived = false }, { Text(stringResource(R.string.terms_active)) })
            FilterChip(showArchived, { showArchived = true }, { Text(stringResource(R.string.terms_archived)) })
        }
        Box(Modifier.weight(1f)) {
            scopeState.SaveableStateProvider(if (showArchived) "archived-terms" else "active-terms") {
                content(showArchived)
            }
        }
    }
}

@Composable
internal fun ArchivedTerms(
    state: DashboardState,
    onSetArchived: ((UUID, Boolean) -> Unit)? = null,
) {
    when (state) {
        DashboardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is DashboardState.Error -> Text(
            stringResource(R.string.terms_archive_load_error),
            Modifier.padding(16.dp),
        )
        is DashboardState.Success -> {
            val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(stringResource(R.string.terms_archive_intro), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.semesters.isEmpty()) {
                    item { Text(stringResource(R.string.terms_archive_empty)) }
                }
                state.semesters.forEach { (semester, subjects) ->
                    item(key = "term_${semester.id}") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(semester.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    semester.endDate?.let {
                                        stringResource(R.string.terms_archive_dates, semester.startDate.format(dateFormat), it.format(dateFormat))
                                    } ?: stringResource(R.string.terms_archive_start, semester.startDate.format(dateFormat)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (onSetArchived != null) {
                                    SemesterArchiveAction(semester, onSetArchived)
                                }
                                if (subjects.isEmpty()) {
                                    Text(stringResource(R.string.terms_archive_subjects_empty), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    subjects.sortedWith(compareBy({ it.name }, { it.id })).forEach { subject ->
                        item(key = "subject_${subject.id}") {
                            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(subject.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.terms_archive_saved_totals, subject.attendedClasses, subject.totalClasses),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SemesterArchiveAction(
    semester: Semester,
    onSetArchived: (UUID, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Never restore a pending confirmation unattended across a scope change or process restart.
    var confirm by remember(semester.id, semester.isArchived) { mutableStateOf(false) }
    val action = stringResource(if (semester.isArchived) R.string.terms_unarchive_action else R.string.terms_archive_action)
    TextButton(onClick = { confirm = true }, modifier = modifier) { Text(action) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = {
                Text(stringResource(
                    if (semester.isArchived) R.string.terms_unarchive_title else R.string.terms_archive_title,
                    semester.name,
                ))
            },
            text = { Text(stringResource(if (semester.isArchived) R.string.terms_unarchive_detail else R.string.terms_archive_detail)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSetArchived(semester.id, !semester.isArchived)
                }) { Text(stringResource(if (semester.isArchived) R.string.terms_confirm_unarchive else R.string.terms_confirm_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
