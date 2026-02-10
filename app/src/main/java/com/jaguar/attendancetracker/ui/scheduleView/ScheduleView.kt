package com.jaguar.attendancetracker.ui.scheduleView

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.ui.components.AddSessionBottomSheet
import com.jaguar.attendancetracker.ui.components.ScheduleCard
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.DayOfWeek

@Composable
fun DayHeader(
    day: Int, expanded: Boolean, onToggle: () -> Unit
) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DayOfWeek.of(day).name,
            fontStyle = AppTypography.titleSmall.fontStyle,
            fontSize = AppTypography.titleSmall.fontSize,
            fontWeight = AppTypography.titleSmall.fontWeight,
            fontFamily = AppTypography.titleSmall.fontFamily,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
            else Icons.Default.KeyboardArrowDown, contentDescription = null
        )
    }
}

@Composable
fun ScheduleView(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    val subjectsSchedulable by viewModel.getAllSubjects().collectAsStateWithLifecycle(emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            ScheduleState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is ScheduleState.Error -> {
                Text(
                    "Something went wrong :(",
                    fontStyle = AppTypography.bodyMedium.fontStyle,
                    fontSize = AppTypography.bodyMedium.fontSize,
                    fontWeight = AppTypography.bodyMedium.fontWeight,
                    fontFamily = AppTypography.bodyMedium.fontFamily,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    (uiState as ScheduleState.Error).message,
                    fontStyle = AppTypography.bodyMedium.fontStyle,
                    fontSize = AppTypography.bodyMedium.fontSize,
                    fontWeight = AppTypography.bodyMedium.fontWeight,
                    fontFamily = AppTypography.bodyMedium.fontFamily,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is ScheduleState.Success -> {
                var showAddScheduleDialog by remember { mutableStateOf(false) }

                val subjects: List<Subject> =
                    (uiState as ScheduleState.Success).sessionRecords.map { it.subject }

                var sessions: List<SessionRecord> by remember { mutableStateOf(emptyList()) }
                sessions = (uiState as ScheduleState.Success).sessionRecords.map { it.session }

                val scheduledSubjects = remember(subjects, sessions) {
                    subjects.filter { it.id in sessions.map { session -> session.subjectId } }
                }
                val sessionsByDay = remember(sessions) {
                    sessions.groupBy { it.dayOfWeek }
                }
                val expandedDays = remember(sessionsByDay) {
                    mutableStateMapOf<Int, Boolean>().apply {
                        sessionsByDay.keys.forEach { put(it, true) }
                    }
                }

                if (sessions.isEmpty()) {
                    Text(
                        "No sessions yet.",
                        fontStyle = AppTypography.bodyMedium.fontStyle,
                        fontSize = AppTypography.bodyMedium.fontSize,
                        fontWeight = AppTypography.bodyMedium.fontWeight,
                        fontFamily = AppTypography.bodyMedium.fontFamily,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sessionsByDay.toSortedMap().forEach { (day, daySessions) ->
                            item(key = "header_$day") {
                                DayHeader(
                                    day = day, expanded = expandedDays[day] == true, onToggle = {
                                        expandedDays[day] = !(expandedDays[day] ?: true)
                                    })
                            }

                            if (expandedDays[day] == true) {
                                items(
                                    items = daySessions, key = { it.id }) { session ->
                                    ScheduleCard(
                                        sessionRecord = session,
                                        subject = scheduledSubjects.first { it.id == session.subjectId },
                                        onDelete = {
                                            viewModel.deleteSession(it)
                                        })
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = isAtTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    enter = slideInHorizontally { it } + scaleIn(),
                    exit = slideOutHorizontally { it } + scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = { showAddScheduleDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Add Session",
                                fontStyle = AppTypography.labelMedium.fontStyle,
                                fontSize = AppTypography.labelMedium.fontSize,
                                fontWeight = AppTypography.labelMedium.fontWeight,
                                fontFamily = AppTypography.labelMedium.fontFamily,
                            )
                        }
                    }
                }
                if (showAddScheduleDialog) {
                    AddSessionBottomSheet(
                        subjectsSchedulable,
                        { showAddScheduleDialog = false },
                        { viewModel.addSession(it) })
                }
            }
        }
    }
}
