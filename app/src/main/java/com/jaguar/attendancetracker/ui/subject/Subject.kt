package com.jaguar.attendancetracker.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.format.DateTimeFormatter

@Composable
fun Subject(
    viewModel: SubjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is SubjectState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is SubjectState.Error -> {
                Text(
                    "Something went wrong :(",
                    style = AppTypography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    (uiState as SubjectState.Error).message,
                    style = AppTypography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is SubjectState.Success -> {
                val subject = state.subject
                val attendanceRecords = state.attendanceRecords
                val records = attendanceRecords.filter { it.status != null }

                val currentPercent = subject.attendancePercentage()
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 120.dp)
                                .background(
                                    color = Color(
                                        SubjectColor.valueOf(subject.color).color(isDark)
                                    )
                                )
                        ) {
                            Text(
                                subject.name,
                                style = AppTypography.displaySmall,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Subject Details",
                            style = AppTypography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Current Attendance Percent",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                "%.1f%%".format(currentPercent),
                                style = AppTypography.bodyMedium,
                                color = Color(
                                    when {
                                        currentPercent > subject.minAttendance -> StatusColor.GOOD.color(
                                            isDark
                                        )

                                        currentPercent < subject.minAttendance -> StatusColor.ALERT.color(
                                            isDark
                                        )

                                        else -> StatusColor.ALERT.color(isDark)
                                    }
                                )
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Required Attendance Percent",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                "${subject.minAttendance}.0%",
                                style = AppTypography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Total Classes",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                "${subject.totalClasses}",
                                style = AppTypography.bodyMedium,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Attended Classes",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                "${subject.attendedClasses}",
                                style = AppTypography.bodyMedium,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Total Absents",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                "${subject.totalClasses - subject.attendedClasses}",
                                style = AppTypography.bodyMedium,
                                color = Color(StatusColor.ALERT.color(isDark))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        if (subject.professor != null) Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Professor",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                subject.professor,
                                style = AppTypography.bodyMedium,
                            )
                        }
                        if (subject.roomNo != null) Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Room Number",
                                style = AppTypography.bodyMedium,
                            )
                            Text(
                                subject.roomNo,
                                style = AppTypography.bodyMedium,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Attendance Record",
                            style = AppTypography.titleLarge,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (records.isEmpty()) {
                        item {
                            Text(
                                "No records available!",
                                style = AppTypography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color.Gray)
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                HeaderCell("Date")
                                HeaderCell("Status")
                                HeaderCell("Type")
                            }
                        }
                        items(items = records, key = { it.id }) { record ->
                            Row(
                                Modifier
                                    .animateItem()
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Cell(
                                    record.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                    isDark
                                )
                                Cell(record.status.toString(), isDark)
                                Cell(record.classType.toString(), isDark)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.HeaderCell(text: String) {
    Text(
        text = text,
        style = AppTypography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp)
    )
}

@Composable
fun RowScope.Cell(text: String, isDark: Boolean) {
    Text(
        text = text,
        style = AppTypography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = when (text) {
            AttendanceStatus.ABSENT.name -> Color(StatusColor.ALERT.color(isDark))
            AttendanceStatus.CANCELLED.name -> Color(StatusColor.WARNING.color(isDark))
            else -> Color.Unspecified
        }
    )
}
