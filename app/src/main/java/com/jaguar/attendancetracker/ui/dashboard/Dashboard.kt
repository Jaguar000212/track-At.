package com.jaguar.attendancetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.navigation.Destinations
import com.jaguar.attendancetracker.ui.components.EditSubjectBottomSheet
import com.jaguar.attendancetracker.ui.components.SubjectCard
import com.jaguar.attendancetracker.ui.theme.AppTypography


@Composable
fun SemesterHeader(
    semester: Int, expanded: Boolean, onToggle: () -> Unit
) {
    val rotationState = animateFloatAsState(if (expanded) 180f else 0f)
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable { onToggle() }
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Semester $semester",
            style = AppTypography.titleSmall,
            modifier = Modifier.weight(0.5f)
        )

        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.rotate(rotationState.value)
        )
    }
}

@Composable
fun Dashboard(
    viewModel: DashboardViewModel = hiltViewModel(), navController: NavHostController
) {
    var showAddSubjectDialog by remember { mutableStateOf(false) }
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
                val subjects: List<Subject> =
                    (uiState as DashboardState.Success).subjects.sortedBy { it.isEnded }
                val subjectsBySemester = remember(subjects) {
                    subjects.groupBy { it.semester }.toSortedMap(compareByDescending { it })
                }
                val expandedSemesters = remember {
                    mutableStateMapOf<Int, Boolean>().apply {
                        subjectsBySemester.keys.forEach { put(it, true) }
                    }
                }

                LaunchedEffect(subjectsBySemester) {
                    subjectsBySemester.keys.forEach { key ->
                        if (!expandedSemesters.containsKey(key)) {
                            expandedSemesters[key] = true
                        }
                    }
                }
                if (subjects.isEmpty()) {
                    Text(
                        "No subjects yet.",
                        style = AppTypography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        subjectsBySemester.toSortedMap().forEach { (semester, semesterSubjects) ->
                            item(key = "header_$semester") {
                                SemesterHeader(
                                    semester = semester,
                                    expanded = expandedSemesters[semester] == true,
                                    onToggle = {
                                        expandedSemesters[semester] =
                                            !(expandedSemesters[semester] ?: true)
                                    })
                            }

                            if (expandedSemesters[semester] == true) {
                                items(
                                    items = semesterSubjects, key = { it.id }) { subject ->
                                    SubjectCard(
                                        modifier = Modifier.animateItem(),
                                        subject = subject,
                                        onClick = {
                                            navController.navigate("${Destinations.SUBJECT.route}/${it.id}") {
                                                popUpTo(Destinations.DASHBOARD.route) {
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
                        onClick = { showAddSubjectDialog = true },
                        modifier = Modifier.align(Alignment.BottomEnd)
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
                if (showAddSubjectDialog) {
                    val newSubject = viewModel.newSubject()
                    EditSubjectBottomSheet(
                        subject = newSubject,
                        onDismiss = { showAddSubjectDialog = false },
                        onSave = {
                            viewModel.addSubject(it)
                            showAddSubjectDialog = false
                        })
                }
            }
        }
    }
}
