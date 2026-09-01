package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSemesterBottomSheet(
    semester: Semester, onDismiss: () -> Unit, onSave: (semester: Semester) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    var name: String by remember { mutableStateOf(semester.name) }
    var startDate: LocalDate by remember { mutableStateOf(semester.startDate) }
    var hasEndDate: Boolean by remember { mutableStateOf(semester.endDate != null) }
    var endDate: LocalDate by remember { mutableStateOf(semester.endDate ?: semester.startDate) }
    var targetAttendance: Int by remember { mutableIntStateOf(semester.targetAttendance) }

    var selectedTab: Int by remember { mutableIntStateOf(0) }

    val startDatePickerState = rememberDatePickerState(
        initialDisplayMode = DisplayMode.Input,
        initialSelectedDateMillis = startDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    )
    startDatePickerState.selectedDateMillis?.let { millis ->
        startDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    val endDatePickerState = rememberDatePickerState(
        initialDisplayMode = DisplayMode.Input,
        initialSelectedDateMillis = endDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    )
    endDatePickerState.selectedDateMillis?.let { millis ->
        endDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    ModalBottomSheet({ onDismiss() }) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                if (semester.name.isEmpty()) "New Semester" else "Edit Semester",
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
                        "End Date",
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
                                "Semester Name",
                                style = AppTypography.titleSmall,
                            )
                        },
                        keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Words),
                        placeholder = {
                            Text(
                                "e.g. Semester 5",
                                style = AppTypography.labelMedium,
                            )
                        },
                        singleLine = true,
                        isError = name.isEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Target Attendance : $targetAttendance%",
                            style = AppTypography.labelMedium,
                        )
                        Slider(
                            value = targetAttendance.toFloat(), onValueChange = {
                                targetAttendance = (it / 5).roundToInt() * 5
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }, onValueChangeFinished = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }, valueRange = 0f..100f, steps = 19
                        )
                    }
                }

                1 -> DatePicker(
                    startDatePickerState, modifier = Modifier.padding(16.dp)
                )

                2 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Checkbox(checked = hasEndDate, onCheckedChange = { hasEndDate = it })
                        Text(
                            "This semester has a known end date",
                            style = AppTypography.labelMedium,
                        )
                    }
                    if (hasEndDate) DatePicker(
                        endDatePickerState, modifier = Modifier.padding(16.dp)
                    )
                }
            }

            TextButton(
                {
                    if (name.isNotEmpty()) onSave(
                        semester.copy(
                            name = name,
                            startDate = startDate,
                            endDate = if (hasEndDate) endDate else null,
                            targetAttendance = targetAttendance
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
