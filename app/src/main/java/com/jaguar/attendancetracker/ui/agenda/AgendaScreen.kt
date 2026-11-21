package com.jaguar.attendancetracker.ui.agenda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import com.jaguar.attendancetracker.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun AgendaScreen(onBack: () -> Unit, viewModel: AgendaViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AgendaContent(state, LocalDate.now(), onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AgendaContent(state: AgendaState, today: LocalDate, onBack: () -> Unit) {
    var selectedEpoch by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val dayNumber = DateTimeFormatter.ofPattern("d", locale).withDecimalStyle(java.time.format.DecimalStyle.of(locale))
    val days = agendaWeek(selected, WeekFields.of(locale).firstDayOfWeek)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("agenda-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.agenda_back)) }
                AgendaHeading(stringResource(R.string.agenda_title))
                Text(stringResource(R.string.agenda_description))
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { selectedEpoch = selected.minusWeeks(1).toEpochDay() }) {
                        Text(stringResource(R.string.agenda_previous_week))
                    }
                    OutlinedButton(onClick = { selectedEpoch = selected.plusWeeks(1).toEpochDay() }) {
                        Text(stringResource(R.string.agenda_next_week))
                    }
                    OutlinedButton(onClick = { selectedEpoch = today.toEpochDay() }, enabled = selected != today) {
                        Text(stringResource(R.string.agenda_jump_today))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        val label = if (day == today) stringResource(R.string.agenda_today_date, full.format(day))
                            else full.format(day)
                        FilterChip(
                            selected = day == selected,
                            onClick = { selectedEpoch = day.toEpochDay() },
                            modifier = Modifier.testTag("agenda-day-$day").semantics { contentDescription = label },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale))
                                    Text(dayNumber.format(day))
                                    if (day == today) Text(stringResource(R.string.agenda_today_short), style = MaterialTheme.typography.labelSmall)
                                }
                            },
                        )
                    }
                }
            }
            when (state) {
                AgendaState.Loading -> item { CircularProgressIndicator() }
                AgendaState.Error -> item { Text(stringResource(R.string.agenda_unavailable), color = MaterialTheme.colorScheme.error) }
                is AgendaState.Ready -> {
                    item { AgendaHeading(full.format(selected)) }
                    val dated = state.entries.dated[selected].orEmpty()
                    if (dated.isEmpty()) item { Text(stringResource(R.string.agenda_day_empty)) }
                    items(dated, key = { it.key }) { AgendaRow(it) }
                    if (state.entries.undated.isNotEmpty()) {
                        item {
                            AgendaHeading(stringResource(R.string.agenda_undated))
                            Text(stringResource(R.string.agenda_undated_description))
                        }
                        items(state.entries.undated, key = { it.key }) { AgendaRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaHeading(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun AgendaRow(item: AgendaItem) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(stringResource(if (item.kind == AgendaKind.TASK) R.string.agenda_task else R.string.agenda_exam),
                style = MaterialTheme.typography.labelMedium)
            Text(item.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
