package com.jaguar.attendancetracker.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

@Composable
internal fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state)
}

@Composable
internal fun InsightsContent(state: InsightsState) {
    var selectedId by remember { mutableStateOf<UUID?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val ready = state as? InsightsState.Ready
    val selection = selectedId
    val selected = ready?.subjects?.firstOrNull { it.id == selection }
    LaunchedEffect(ready?.subjects, selection) {
        if (ready != null && selection != null && selected == null && selectedId == selection) {
            selectedId = null
        }
    }
    val weeks = remember(ready, selected?.id) {
        ready?.let { attendanceTrend(it.rows, it.today, subjectId = selected?.id) }.orEmpty()
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("insights-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.insights_intro))
                    }
                }
            }
            when (state) {
                InsightsState.Loading -> item { CircularProgressIndicator() }
                InsightsState.Unavailable -> item { Text(stringResource(R.string.insights_load_error)) }
                is InsightsState.Ready -> {
                    item {
                        OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth(),
                            enabled = state.subjects.isNotEmpty()) {
                            Text(stringResource(R.string.insights_scope, selected?.let { subjectLabel(it) }
                                ?: stringResource(R.string.insights_all_subjects)))
                        }
                    }
                    val anyRows = weeks.any {
                        it.rate != null || it.unmarked > 0 || it.cancelled > 0 || it.excludedRows > 0
                    }
                    if (!anyRows) {
                        item { Text(stringResource(R.string.insights_no_records_scope)) }
                    } else {
                        if (weeks.none { it.rate != null }) {
                            item { Text(stringResource(R.string.insights_no_marked_scope)) }
                        }
                        item { Text(stringResource(R.string.insights_denominator)) }
                        items(weeks.asReversed(), key = { it.firstDate.toString() }) { week ->
                            TrendCard(week, runningWeek = week == weeks.last())
                        }
                        item { Text(stringResource(R.string.insights_excluded_help)) }
                    }
                }
            }
        }
    }

    if (choosing && ready != null) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.insights_choose_subject)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { selectedId = null; choosing = false }) {
                        Text(stringResource(R.string.insights_all_subjects))
                    }
                    ready.subjects.forEach {
                        TextButton(onClick = { selectedId = it.id; choosing = false }) {
                            Text(subjectLabel(it))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.insights_close)) }
            },
        )
    }
}

@Composable
private fun TrendCard(week: AttendanceTrendWeek, runningWeek: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(R.string.insights_week, week.firstDate.display(), week.throughDate.display()),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (runningWeek) Text(stringResource(R.string.insights_current_week))
            val rate = week.rate
            if (rate == null) {
                Text(stringResource(R.string.insights_no_marked_week))
            } else {
                val percent = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }
                    .format(rate.attended.toDouble() / rate.marked)
                Text(stringResource(R.string.insights_rate, percent, rate.attended, rate.marked))
                // The adjacent text is the non-colour equivalent. No bar is rendered without marks.
                Row(
                    Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                        .clearAndSetSemantics {},
                ) {
                    if (week.present > 0L) Box(Modifier.weight(week.present.toFloat()).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary))
                    if (week.absent > 0L) Box(Modifier.weight(week.absent.toFloat()).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.error))
                }
            }
            Text(stringResource(R.string.insights_marked_counts, week.present, week.absent))
            Text(stringResource(R.string.insights_other_counts, week.unmarked, week.cancelled))
            if (week.excludedRows > 0L) {
                Text(stringResource(R.string.insights_excluded_count, week.excludedRows))
            }
        }
    }
}

@Composable
private fun subjectLabel(subject: InsightsSubject) = stringResource(
    if (subject.archived) R.string.insights_archived_subject else R.string.insights_subject,
    subject.name, subject.termName,
)

private fun LocalDate.display() = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
