package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSubjectBottomSheet(
    subject: Subject, onDismiss: () -> Unit, onSave: (subject: Subject) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    var name: String by remember { mutableStateOf(subject.name) }
    var semester: Int by remember { mutableIntStateOf(subject.semester) }
    var startDate: LocalDate by remember { mutableStateOf(subject.startDate) }
    var color: String by remember { mutableStateOf(subject.color) }
    var minAttendance: Int by remember { mutableIntStateOf(subject.minAttendance) }
    var professor: String? by remember { mutableStateOf(subject.professor) }
    var roomNo: String? by remember { mutableStateOf(subject.roomNo) }
    var notes: String? by remember { mutableStateOf(subject.notes) }

    var selectedTab: Int by remember { mutableIntStateOf(0) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showSemPicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialDisplayMode = DisplayMode.Input,
        initialSelectedDateMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    )
    datePickerState.selectedDateMillis?.let { millis ->
        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    ModalBottomSheet({ onDismiss() }) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                if (subject.name.isEmpty()) "New Subject" else "Edit Subject",
                style = AppTypography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(8.dp))

            TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = {
                    Text(
                        "Details",
                        style = AppTypography.labelMedium,
                    )
                })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = {
                    Text(
                        "Start Date",
                        style = AppTypography.labelMedium,
                    )
                })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = {
                    Text(
                        "Extras",
                        style = AppTypography.labelMedium,
                    )
                })
            }

            when (selectedTab) {
                0 -> {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            Text(
                                "Subject Name",
                                style = AppTypography.titleSmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
                        placeholder = {
                            Text(
                                "Enter subject name",
                                style = AppTypography.labelMedium,
                            )
                        },
                        singleLine = true,
                        isError = name.isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = showSemPicker,
                            onExpandedChange = { showSemPicker = it },
                        ) {
                            TextField(
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(0.5f),
                                value = semester.toString(),
                                onValueChange = { },
                                readOnly = true,
                                label = {
                                    Text(
                                        "Semester",
                                        style = AppTypography.titleSmall,
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSemPicker) },
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = showSemPicker,
                                onDismissRequest = { showSemPicker = false }) {
                                (1..10).forEach {
                                    DropdownMenuItem(text = {
                                        Text(
                                            it.toString(),
                                            style = AppTypography.labelMedium,
                                        )
                                    }, onClick = {
                                        semester = it
                                        showSemPicker = false
                                    })
                                }
                            }
                        }

                        if (!subject.isEnded) ExposedDropdownMenuBox(
                            expanded = showColorPicker,
                            onExpandedChange = { showColorPicker = it },
                        ) {
                            TextField(
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                value = color,
                                onValueChange = { },
                                readOnly = true,
                                label = {
                                    Text(
                                        "Card Color",
                                        style = AppTypography.titleSmall,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Favorite,
                                        "",
                                        tint = Color(SubjectColor.valueOf(color).color(isDark))
                                    )
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showColorPicker) },
                                colors = ExposedDropdownMenuDefaults.textFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = showColorPicker,
                                onDismissRequest = { showColorPicker = false }) {
                                SubjectColor.entries.filter { it.showInSelection }.forEach {
                                    DropdownMenuItem(text = {
                                        Text(
                                            it.name,
                                            style = AppTypography.labelMedium,
                                        )
                                    }, onClick = {
                                        color = it.name
                                        showColorPicker = false
                                    }, leadingIcon = {
                                        Icon(
                                            Icons.Filled.Favorite,
                                            "",
                                            tint = Color(it.color(isDark))
                                        )
                                    })
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Required Attendance : $minAttendance",
                            style = AppTypography.labelMedium,
                        )
                        Slider(
                            value = minAttendance.toFloat(), onValueChange = {
                                minAttendance = (it / 5).roundToInt() * 5
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }, onValueChangeFinished = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }, valueRange = 0f..100f, steps = 19
                        )
                    }
                }

                1 -> DatePicker(
                    datePickerState, modifier = Modifier.padding(16.dp)
                )

                2 -> {
                    TextField(
                        value = professor ?: "",
                        onValueChange = { professor = it },
                        label = {
                            Text(
                                "Professor Name",
                                style = AppTypography.titleSmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
                        placeholder = {
                            Text(
                                "Enter professor's name",
                                style = AppTypography.labelMedium,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    TextField(
                        value = roomNo ?: "",
                        onValueChange = { roomNo = it },
                        label = {
                            Text(
                                "Room Number",
                                style = AppTypography.titleSmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
                        placeholder = {
                            Text(
                                "Enter room number",
                                style = AppTypography.labelMedium,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    TextField(
                        value = notes ?: "",
                        onValueChange = { notes = it },
                        label = {
                            Text(
                                "Note",
                                style = AppTypography.titleSmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
                        placeholder = {
                            Text(
                                "Anything important",
                                style = AppTypography.labelMedium,
                            )
                        },
                        minLines = 3,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            TextButton(
                {
                    if (name.isNotEmpty() && semester in 1..10) onSave(
                        subject.copy(
                            name = name,
                            semester = semester,
                            startDate = startDate,
                            color = color,
                            minAttendance = minAttendance,
                            professor = if (professor?.isNotEmpty() ?: false) professor else null,
                            roomNo = if (roomNo?.isNotEmpty() ?: false) roomNo else null,
                            notes = if (notes?.isNotEmpty() ?: false) notes else null
                        )
                    )
                }, modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
            ) {
                Text(
                    "Finish",
                    style = AppTypography.labelMedium,
                )
            }
        }
    }
}