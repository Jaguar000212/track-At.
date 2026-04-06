package com.jaguar.attendancetracker.ui.scheduleView

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.ui.components.AddSessionBottomSheet
import com.jaguar.attendancetracker.ui.components.ScheduleCard
import com.jaguar.attendancetracker.ui.theme.AppTypography
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.DayOfWeek

@Composable
fun DayHeader(
    day: Int, expanded: Boolean, onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "ArrowRotation"
    )

    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DayOfWeek.of(day).name,
            fontStyle = AppTypography.titleSmall.fontStyle,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ScheduleView(
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    val subjectsSchedulable by viewModel.getAllSubjects().collectAsStateWithLifecycle(emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState, transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            }, label = "ScheduleStateAnimation", modifier = Modifier.fillMaxSize()
        ) { targetState ->
            when (targetState) {
                ScheduleState.Loading -> Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ScheduleState.Error -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Something went wrong :(",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                            )
                            Text(
                                targetState.message,
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                            )
                        }
                    }
                }

                is ScheduleState.Success -> {
                    var showAddScheduleDialog by remember { mutableStateOf(false) }
                    var isEditMode by remember { mutableStateOf(false) }

                    val shakeRotation = if (isEditMode) {
                        val infiniteTransition =
                            rememberInfiniteTransition(label = "shake_transition")
                        infiniteTransition.animateFloat(
                            initialValue = -2f,
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(150, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "shake_animation"
                        ).value
                    } else {
                        0f
                    }

                    val subjects: List<Subject> = targetState.sessionRecords.map { it.subject }
                    val subjectMap = remember(subjects) {
                        subjects.associateBy { it.id }
                    }

                    var sessions: List<SessionRecord> by remember { mutableStateOf(emptyList()) }
                    LaunchedEffect(targetState) {
                        sessions = targetState.sessionRecords.map { it.session }
                    }
                    val reorderableLazyColumnState =
                        rememberReorderableLazyListState(listState) { from, to ->
                            val fromIndex = sessions.indexOfFirst { it.id == from.key }
                            val toIndex = sessions.indexOfFirst { it.id == to.key }

                            if (fromIndex != -1 && toIndex != -1) {
                                sessions = sessions.toMutableList().apply {
                                    add(toIndex, removeAt(fromIndex))
                                }
                            }

                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        }

                    val sessionsByDay = remember(sessions) {
                        sessions.groupBy { it.dayOfWeek }
                    }
                    val expandedDays = remember(sessionsByDay) {
                        mutableStateMapOf<Int, Boolean>().apply {
                            sessionsByDay.keys.forEach { put(it, true) }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (sessions.isEmpty()) {
                            Text(
                                "No sessions yet.",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                sessionsByDay.toSortedMap().forEach { (day, daySessions) ->
                                    item(key = "header_$day") {
                                        DayHeader(
                                            day = day,
                                            expanded = expandedDays[day] == true,
                                            onToggle = {
                                                expandedDays[day] = !(expandedDays[day] ?: true)
                                            })
                                    }

                                    if (expandedDays[day] == true) {
                                        itemsIndexed(
                                            items = daySessions,
                                            key = { _, session -> session.id }) { _, session ->
                                            ReorderableItem(
                                                state = reorderableLazyColumnState,
                                                key = session.id,
                                                modifier = Modifier.animateItem()
                                            ) {
                                                val interactionSource =
                                                    remember { MutableInteractionSource() }

                                                Box(
                                                    modifier = Modifier.then(
                                                        if (isEditMode) {
                                                        Modifier
                                                            .rotate(shakeRotation)
                                                            .draggableHandle(
                                                                onDragStarted = {
                                                                    haptic.performHapticFeedback(
                                                                        HapticFeedbackType.LongPress
                                                                    )
                                                                },
                                                                onDragStopped = {
                                                                    haptic.performHapticFeedback(
                                                                        HapticFeedbackType.GestureEnd
                                                                    )
                                                                },
                                                                interactionSource = interactionSource,
                                                            )
                                                    } else {
                                                        Modifier.pointerInput(Unit) {
                                                            detectTapGestures(onLongPress = {
                                                                isEditMode = true
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            }, onTap = {})
                                                        }
                                                    })) {
                                                    ScheduleCard(
                                                        sessionRecord = session,
                                                        subject = subjectMap[session.subjectId]!!,
                                                        onDelete = {
                                                            viewModel.deleteSession(it)
                                                        },
                                                        modifier = Modifier
                                                            .semantics {
                                                                customActions = listOf(
                                                                    CustomAccessibilityAction(
                                                                        label = "Move Up",
                                                                        action = {
                                                                            val globalIndex =
                                                                                sessions.indexOfFirst { it.id == session.id }
                                                                            if (globalIndex > 0) {
                                                                                sessions =
                                                                                    sessions.toMutableList()
                                                                                        .apply {
                                                                                            add(
                                                                                                globalIndex - 1,
                                                                                                removeAt(
                                                                                                    globalIndex
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                true
                                                                            } else {
                                                                                false
                                                                            }
                                                                        }),
                                                                    CustomAccessibilityAction(
                                                                        label = "Move Down",
                                                                        action = {
                                                                            val globalIndex =
                                                                                sessions.indexOfFirst { it.id == session.id }
                                                                            if (globalIndex != -1 && globalIndex < sessions.size - 1) {
                                                                                sessions =
                                                                                    sessions.toMutableList()
                                                                                        .apply {
                                                                                            add(
                                                                                                globalIndex + 1,
                                                                                                removeAt(
                                                                                                    globalIndex
                                                                                                )
                                                                                            )
                                                                                        }
                                                                                true
                                                                            } else {
                                                                                false
                                                                            }
                                                                        }),
                                                                )
                                                            }
                                                            .clearAndSetSemantics { },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(modifier = Modifier.height(16.dp)) }
                            }
                        }
                        AnimatedVisibility(
                            visible = isAtTop && !isEditMode,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            enter = slideInHorizontally { it } + scaleIn(),
                            exit = slideOutHorizontally { it } + scaleOut(),
                        ) {
                            FloatingActionButton(
                                onClick = { showAddScheduleDialog = true },
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
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = isEditMode,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                            enter = slideInHorizontally { it } + scaleIn(),
                            exit = slideOutHorizontally { it } + scaleOut(),
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    val updated =
                                        sessions.mapIndexed { i, s -> s.copy(orderNo = i) }
                                    viewModel.updateSessionOrder(updated)
                                    isEditMode = false
                                },
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Done")
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
}
