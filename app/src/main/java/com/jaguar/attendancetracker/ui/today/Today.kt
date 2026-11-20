package com.jaguar.attendancetracker.ui.today

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun Today(
    openDayView: () -> Unit,
    openDashboard: () -> Unit,
    openExams: () -> Unit,
    openLeave: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TodayContent(state, openDayView, openDashboard, openExams, openLeave)
}

@Composable
internal fun TodayContent(
    state: TodayState,
    openDayView: () -> Unit,
    openDashboard: () -> Unit,
    openExams: () -> Unit,
    openLeave: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.nav_today),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (state is TodayState.Ready) {
                            if (state.markedCount > 0) Text(
                                pluralStringResource(
                                    R.plurals.today_marked, state.markedCount, state.markedCount,
                                ),
                            )
                            if (state.pendingCount > 0) Text(
                                pluralStringResource(
                                    R.plurals.today_pending, state.pendingCount, state.pendingCount,
                                ),
                            )
                        }
                    }
                }
            }
            item {
                Button(onClick = openDayView, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.today_open_day_view))
                }
            }
            item {
                OutlinedButton(onClick = openExams, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.exams_title))
                }
            }
            item {
                OutlinedButton(onClick = openLeave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.leave_title))
                }
            }
            when (state) {
                is TodayState.Loading -> item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is TodayState.Error -> item {
                    Text(stringResource(R.string.today_load_error), color = MaterialTheme.colorScheme.error)
                }
                is TodayState.Ready -> {
                    if (state.records.isEmpty() && state.scheduled.isEmpty()) {
                        item {
                            Text(stringResource(R.string.today_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                        item {
                            OutlinedButton(onClick = openDashboard, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.today_open_dashboard))
                            }
                        }
                    } else {
                        item {
                            Text(
                                stringResource(R.string.today_classes),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        items(state.records, key = { "record-${it.record.id}" }) {
                            TodayClass(
                                it.subject.name,
                                when (it.record.status) {
                                    AttendanceStatus.PRESENT -> R.string.today_present
                                    AttendanceStatus.ABSENT -> R.string.today_absent
                                    AttendanceStatus.CANCELLED -> R.string.today_cancelled
                                    null -> R.string.today_not_marked
                                },
                            )
                        }
                        items(state.scheduled, key = { "session-${it.session.id}" }) {
                            TodayClass(it.subject.name, R.string.today_scheduled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayClass(name: String, status: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(status), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
