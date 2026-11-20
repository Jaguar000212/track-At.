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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.daos.TimetableEntry
import com.jaguar.attendancetracker.backend.daos.group
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DayOfWeek.of(day).name,
            style = AppTypography.titleSmall,
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
    val reorderError by viewModel.reorderError.collectAsStateWithLifecycle()
    reorderError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissReorderError,
            title = { Text(stringResource(R.string.timetable_order_not_saved)) },
            text = { Text(stringResource(if (error == ScheduleViewModel.ReorderError.STALE)
                R.string.timetable_order_stale else R.string.timetable_order_error)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissReorderError) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    val subjectsSchedulable by viewModel.getAllSubjects().collectAsStateWithLifecycle(emptyList())

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState,
            contentKey = { if (it is ScheduleState.Success) "timetable" else it::class },
            transitionSpec = {
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
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                targetState.message,
                                style = AppTypography.bodyMedium,
                            )
                        }
                    }
                }

                is ScheduleState.Success -> {
                    var showAddScheduleDialog by remember { mutableStateOf(false) }
                    var baseline by remember { mutableStateOf<List<TimetableEntry>?>(null) }
                    var draft by remember { mutableStateOf<List<TimetableEntry>?>(null) }
                    val isEditMode = draft != null
                    val entries = draft ?: targetState.sessionRecords
                    fun move(fromKey: Any, toKey: Any): Boolean {
                        val current = draft ?: targetState.sessionRecords
                        val from = current.indexOfFirst { it.order.session.id == fromKey }
                        val to = current.indexOfFirst { it.order.session.id == toKey }
                        if (from !in current.indices || to !in current.indices || from == to ||
                            current[from].order.group != current[to].order.group
                        ) return false
                        if (baseline == null) baseline = current
                        draft = current.toMutableList().apply { add(to, removeAt(from)) }
                        return true
                    }

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

                    val reorderableLazyColumnState =
                        rememberReorderableLazyListState(listState) { from, to ->
                            if (move(from.key, to.key)) {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        }

                    val entriesByDay = remember(entries) { entries.groupBy { it.order.session.dayOfWeek } }
                    val expandedDays = remember { mutableStateMapOf<Int, Boolean>() }
                    val moveUp = stringResource(R.string.timetable_move_up)
                    val moveDown = stringResource(R.string.timetable_move_down)

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (entries.isEmpty()) {
                            Text(
                                "No sessions yet.",
                                style = AppTypography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                entriesByDay.toSortedMap().forEach { (day, dayEntries) ->
                                    item(key = "header_$day") {
                                        DayHeader(
                                            day = day,
                                            expanded = expandedDays[day] != false,
                                            onToggle = {
                                                expandedDays[day] = !(expandedDays[day] ?: true)
                                            })
                                    }

                                    if (expandedDays[day] != false) {
                                        items(dayEntries, key = { it.order.session.id }) { entry ->
                                            val session = entry.order.session
                                            ReorderableItem(
                                                state = reorderableLazyColumnState,
                                                key = session.id,
                                                modifier = Modifier.animateItem(),
                                            ) {
                                                val interactionSource = remember { MutableInteractionSource() }
                                                Column(
                                                    modifier = if (isEditMode) {
                                                        Modifier.rotate(shakeRotation).draggableHandle(
                                                            onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                                            onDragStopped = { haptic.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                                            interactionSource = interactionSource,
                                                        )
                                                    } else {
                                                        Modifier.pointerInput(entries) {
                                                            detectTapGestures(onLongPress = {
                                                                baseline = entries
                                                                draft = entries
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            })
                                                        }
                                                    },
                                                ) {
                                                    if (entry.order.archived) {
                                                        Text(
                                                            stringResource(R.string.timetable_archived_session),
                                                            style = AppTypography.labelMedium,
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                        )
                                                    }
                                                    val index = entries.indexOfFirst { it.order.session.id == session.id }
                                                    val actions = buildList {
                                                        entries.getOrNull(index - 1)?.order?.takeIf { it.group == entry.order.group }?.let { previous ->
                                                            add(CustomAccessibilityAction(moveUp) { move(session.id, previous.session.id) })
                                                        }
                                                        entries.getOrNull(index + 1)?.order?.takeIf { it.group == entry.order.group }?.let { next ->
                                                            add(CustomAccessibilityAction(moveDown) { move(session.id, next.session.id) })
                                                        }
                                                    }
                                                    ScheduleCard(
                                                        sessionRecord = session,
                                                        subject = entry.subject,
                                                        onDelete = viewModel::deleteSession,
                                                        modifier = Modifier.semantics { customActions = actions },
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
                                onClick = {
                                    showAddScheduleDialog = true
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Add Session",
                                        style = AppTypography.labelMedium,
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
                                    val before = baseline
                                    val after = draft
                                    if (before != null && after != null) viewModel.updateSessionOrder(before, after)
                                    draft = null
                                    baseline = null
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
