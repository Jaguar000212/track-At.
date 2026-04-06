package com.jaguar.attendancetracker.ui.dayView

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.ui.components.SessionCard
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DayView(
    viewModel: DayViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val subjects by viewModel.getSubjects().collectAsStateWithLifecycle(emptyList())
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val isDark = isSystemInDarkTheme()
    val currentDate = remember(uiState) {
        uiState.date
    }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    )

    var showAddClassDialog by remember { mutableStateOf(false) }
    var showDatePicker: Boolean by remember { mutableStateOf(false) }

    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            IconButton(
                onClick = { viewModel.loadDate(currentDate.minusDays(1)) },
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null)
            }

            AnimatedContent(
                targetState = currentDate, transitionSpec = {
                    if (targetState.isAfter(initialState)) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith slideOutHorizontally { width -> width } + fadeOut()
                    }.using(SizeTransform(clip = false))
                }, label = "DateAnimation"
            ) { targetDate ->
                Column(
                    verticalArrangement = Arrangement.SpaceAround,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(8.dp)
                ) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            targetDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                                ?: "Today",
                            fontStyle = AppTypography.titleLarge.fontStyle,
                        )
                    }
                    Text(
                        targetDate.dayOfWeek?.name ?: "MONDAY",
                        fontStyle = AppTypography.titleSmall.fontStyle,
                    )
                }
            }

            IconButton(
                onClick = { viewModel.loadDate(currentDate.plusDays(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (uiState) {
                is DayState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is DayState.Error -> {
                    Text(
                        "Something went wrong :(",
                        fontStyle = AppTypography.bodyMedium.fontStyle,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Text(
                        (uiState as DayState.Error).message,
                        fontStyle = AppTypography.bodyMedium.fontStyle,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is DayState.Success -> {
                    viewModel.loadDate(uiState.date)
                    val dayRecords = (uiState as DayState.Success).dayRecords
                    if (dayRecords.isEmpty()) Text(
                        "No session for today.",
                        fontStyle = AppTypography.bodyMedium.fontStyle,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    FilterChip(
                                        selected = dayRecords.all { it.record.status == AttendanceStatus.PRESENT },
                                        onClick = { dayRecords.forEach { viewModel.markPresent(it) } },
                                        label = {
                                            Text(
                                                "Present",
                                                fontStyle = AppTypography.labelMedium.fontStyle,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Check, "") },
                                        colors = FilterChipDefaults.filterChipColors().copy(
                                            selectedContainerColor = Color(
                                                StatusColor.GOOD.color(isDark)
                                            )
                                        )
                                    )
                                    FilterChip(
                                        selected = dayRecords.all { it.record.status == AttendanceStatus.ABSENT },
                                        onClick = { dayRecords.forEach { viewModel.markAbsent(it) } },
                                        label = {
                                            Text(
                                                "Absent",
                                                fontStyle = AppTypography.labelMedium.fontStyle,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Close, "") },
                                        colors = FilterChipDefaults.filterChipColors().copy(
                                            selectedContainerColor = Color(
                                                StatusColor.ALERT.color(isDark)
                                            )
                                        )
                                    )
                                    FilterChip(
                                        selected = dayRecords.all { it.record.status == AttendanceStatus.CANCELLED },
                                        onClick = { dayRecords.forEach { viewModel.markCancelled(it) } },
                                        label = {
                                            Text(
                                                "Cancel",
                                                fontStyle = AppTypography.labelMedium.fontStyle,
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, "") },
                                        colors = FilterChipDefaults.filterChipColors().copy(
                                            selectedContainerColor = Color(
                                                StatusColor.WARNING.color(isDark)
                                            )
                                        )
                                    )
                                }
                                HorizontalDivider(Modifier.padding(8.dp))
                            }
                            items(dayRecords, key = { it.record.id }) {
                                SessionCard(
                                    modifier = Modifier.animateItem(),
                                    record = it.record,
                                    subject = it.subject,
                                    onPresent = {
                                        viewModel.markPresent(it)
                                    },
                                    onAbsent = {
                                        viewModel.markAbsent(it)
                                    },
                                    onCancel = {
                                        viewModel.markCancelled(it)
                                    })
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isAtTop,
                        enter = slideInHorizontally { it } + scaleIn(),
                        exit = slideOutHorizontally { it } + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)) {
                        FloatingActionButton(
                            onClick = { showAddClassDialog = true }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Extra Class",
                                    fontStyle = AppTypography.labelMedium.fontStyle,
                                )
                            }
                        }
                    }


                    if (showAddClassDialog) {
                        if (subjects.isEmpty()) {
                            AlertDialog(onDismissRequest = { showAddClassDialog = false }, title = {
                                Text(
                                    "No Subjects Available",
                                    fontStyle = AppTypography.titleMedium.fontStyle,
                                )
                            }, text = {
                                Text(
                                    "Please add a subject before adding a class.",
                                    fontStyle = AppTypography.bodyMedium.fontStyle,
                                )
                            }, confirmButton = {
                                TextButton({ showAddClassDialog = false }) {
                                    Text(
                                        "OK",
                                        fontStyle = AppTypography.labelMedium.fontStyle,
                                    )
                                }
                            })
                            return
                        } else {
                            var subjectSelectionMenu: Boolean by remember { mutableStateOf(false) }
                            var subjectId: UUID by remember { mutableStateOf(subjects[0].id) }

                            AlertDialog(
                                onDismissRequest = { showAddClassDialog = false },
                                dismissButton = {
                                    TextButton({ showAddClassDialog = false }) {
                                        Text(
                                            "No",
                                            fontStyle = AppTypography.labelMedium.fontStyle,
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton({
                                        viewModel.addExtraClass(
                                            AttendanceRecord(
                                                subjectId = subjectId,
                                                date = currentDate,
                                                sessionId = null,
                                                status = null,
                                                classType = ClassType.EXTRA
                                            )
                                        )
                                        showAddClassDialog = false
                                    }) {
                                        Text(
                                            "Yes",
                                            fontStyle = AppTypography.labelMedium.fontStyle,
                                        )
                                    }
                                },
                                modifier = Modifier,
                                title = {
                                    Text(
                                        "Select Subject",
                                        fontStyle = AppTypography.titleMedium.fontStyle,
                                    )
                                },
                                text = {
                                    ExposedDropdownMenuBox(
                                        expanded = subjectSelectionMenu,
                                        onExpandedChange = { subjectSelectionMenu = it },
                                    ) {
                                        TextField(
                                            subjects.firstOrNull { it.id == subjectId }?.name
                                                ?: "No Subject",
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                            onValueChange = { },
                                            readOnly = true,
                                            label = {
                                                Text(
                                                    "Subject",
                                                    fontStyle = AppTypography.titleSmall.fontStyle,
                                                )
                                            },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(
                                                    expanded = subjectSelectionMenu
                                                )
                                            },
                                            colors = ExposedDropdownMenuDefaults.textFieldColors()

                                        )
                                        ExposedDropdownMenu(
                                            expanded = subjectSelectionMenu,
                                            onDismissRequest = { subjectSelectionMenu = false }) {
                                            subjects.forEach { subject ->
                                                DropdownMenuItem({
                                                    Text(
                                                        subject.name,
                                                        fontStyle = AppTypography.labelLarge.fontStyle,
                                                    )
                                                }, onClick = {
                                                    subjectId = subject.id
                                                    subjectSelectionMenu = false
                                                })
                                            }
                                        }
                                    }
                                })
                        }
                    }
                    if (showDatePicker) {
                        DatePickerDialog(onDismissRequest = {
                            showDatePicker = false
                        }, confirmButton = {
                            TextButton(
                                onClick = {
                                    showDatePicker = false
                                    viewModel.loadDate(date = datePickerState.selectedDateMillis?.let {
                                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                                    } ?: LocalDate.now())
                                }) {
                                Text(
                                    "OK",
                                    fontStyle = AppTypography.labelMedium.fontStyle,
                                )
                            }
                        }, dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text(
                                    "Cancel",
                                    fontStyle = AppTypography.labelMedium.fontStyle,
                                )
                            }
                        }) {
                            DatePicker(state = datePickerState)
                        }
                    }
                }
            }
        }
    }
}