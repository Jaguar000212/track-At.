package com.jaguar.attendancetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SemesterWithSubjects
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.navigation.Destinations
import com.jaguar.attendancetracker.ui.components.EditSemesterBottomSheet
import com.jaguar.attendancetracker.ui.components.EditSubjectBottomSheet
import com.jaguar.attendancetracker.ui.components.SubjectCard
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.LocalDate
import java.util.UUID


@Composable
fun SemesterHeader(
    semester: Semester,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: (Semester) -> Unit,
    onDelete: (Semester) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }

    val rotationState = animateFloatAsState(if (expanded) 180f else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = semester.name,
            style = AppTypography.titleSmall,
            modifier = Modifier.weight(1f)
        )

        ElevatedButton(
            {
                showEditDialog = true
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Outlined.Create,
                contentDescription = "Edit semester",
                tint = Color(StatusColor.GOOD.color(isDark)),
                modifier = Modifier.padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        ElevatedButton(
            {
                showDeleteAlert = true
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
            },
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete semester",
                tint = Color(StatusColor.ALERT.color(isDark)),
                modifier = Modifier.padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.rotate(rotationState.value)
        )
    }

    if (showDeleteAlert) AlertDialog(
        onDismissRequest = { showDeleteAlert = false },
        dismissButton = {
            TextButton({ showDeleteAlert = false }) {
                Text(
                    "No",
                    style = AppTypography.labelMedium,
                )
            }
        },
        confirmButton = {
            TextButton({
                onDelete(semester)
                showDeleteAlert = false
            }) {
                Text(
                    "Yes",
                    style = AppTypography.labelMedium,
                )
            }
        },
        title = {
            Text(
                "Delete Semester",
                style = AppTypography.titleMedium,
            )
        },
        text = {
            Text(
                "Are you sure you want to delete \"${semester.name}\"? This will also delete every subject in it, along with all of their attendance and schedule data. This operation is not reversible.",
                style = AppTypography.bodyMedium,
            )
        })

    if (showEditDialog) {
        EditSemesterBottomSheet(
            semester = semester,
            onDismiss = { showEditDialog = false },
            onSave = {
                onEdit(it)
                showEditDialog = false
            }
        )
    }
}

@Composable
fun Dashboard(
    viewModel: DashboardViewModel = hiltViewModel(), navController: NavHostController
) {
    val haptic = LocalHapticFeedback.current
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddSemesterDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (uiState) {
            DashboardState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is DashboardState.Error -> {
                Text(
                    "Something went wrong :(",
                    style = AppTypography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    (uiState as DashboardState.Error).message,
                    style = AppTypography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is DashboardState.Success -> {
                val semesters: List<SemesterWithSubjects> =
                    (uiState as DashboardState.Success).semesters
                
                val expandedSemesters = remember {
                    mutableStateMapOf<UUID, Boolean>().apply {
                        semesters.forEachIndexed { index, semesterWithSubjects ->
                            put(semesterWithSubjects.semester.id, index == 0)
                        }
                    }
                }

                LaunchedEffect(semesters) {
                    semesters.forEachIndexed { index, semesterWithSubjects ->
                        val key = semesterWithSubjects.semester.id
                        if (!expandedSemesters.containsKey(key)) {
                            expandedSemesters[key] = index == 0
                        }
                    }
                }
                
                if (semesters.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "No semesters yet.",
                            style = AppTypography.bodyMedium
                        )
                        TextButton(onClick = {
                            viewModel.addSemester(
                                Semester(
                                    name = "Semester 1",
                                    startDate = LocalDate.now(),
                                    endDate = null
                                )
                            )
                        }) {
                            Text("Create Semester 1")
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        semesters.forEach { semesterWithSubjects ->
                            val semester = semesterWithSubjects.semester
                            item(key = "header_${semester.id}") {
                                SemesterHeader(
                                    semester = semester,
                                    expanded = expandedSemesters[semester.id] == true,
                                    onToggle = {
                                        expandedSemesters[semester.id] =
                                            !(expandedSemesters[semester.id] ?: false)
                                    },
                                    onEdit = { viewModel.updateSemester(it) },
                                    onDelete = { viewModel.deleteSemester(it) })
                            }

                            if (expandedSemesters[semester.id] == true) {
                                if (semesterWithSubjects.subjects.isEmpty()) {
                                    item {
                                        Text(
                                            "No subjects in this semester.",
                                            style = AppTypography.bodySmall,
                                            modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp)
                                        )
                                    }
                                } else {
                                    items(
                                        items = semesterWithSubjects.subjects, key = { it.id }) { subject ->
                                        SubjectCard(
                                            modifier = Modifier.animateItem(),
                                            subject = subject,
                                            availableSemesters = semesters.map { it.semester },
                                            onClick = {
                                                navController.navigate("${Destinations.SUBJECT.route}/${it.id}") {
                                                    popUpTo(Destinations.DAYVIEW.route) {
                                                        inclusive = false
                                                    }
                                                }
                                            },
                                            onEdit = { viewModel.editSubject(it) },
                                            onCancelSchedule = { viewModel.cancelScheduling(it) },
                                            onDelete = { viewModel.deleteSubject(it) })
                                    }
                                }
                            }
                        }
                        item(key = "add_semester") {
                            TextButton(
                                onClick = { showAddSemesterDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Semester", style = AppTypography.labelMedium)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
                AnimatedVisibility(
                    visible = isAtTop,
                    enter = slideInHorizontally { it } + scaleIn(),
                    exit = slideOutHorizontally { it } + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)) {
                    FloatingActionButton(
                        onClick = {
                            showAddSubjectDialog = true
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        }, modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Add Subject",
                                style = AppTypography.labelMedium,
                            )
                        }
                    }
                }
                if (showAddSubjectDialog && semesters.isNotEmpty()) {
                    val newSubject = viewModel.newSubject(semesters.first().semester.id)
                    EditSubjectBottomSheet(
                        subject = newSubject,
                        onDismiss = { showAddSubjectDialog = false },
                        onSave = {
                            viewModel.addSubject(it)
                            showAddSubjectDialog = false
                        },
                        availableSemesters = semesters.map { it.semester })
                }
                if (showAddSemesterDialog) {
                    EditSemesterBottomSheet(
                        semester = viewModel.newSemester(),
                        onDismiss = { showAddSemesterDialog = false },
                        onSave = {
                            viewModel.addSemester(it)
                            showAddSemesterDialog = false
                        })
                }
            }
        }
    }
}
