package com.jaguar.attendancetracker.ui.subject

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.hilt.navigation.compose.hiltViewModel
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
                    fontStyle = AppTypography.bodyMedium.fontStyle,
                    fontSize = AppTypography.bodyMedium.fontSize,
                    fontWeight = AppTypography.bodyMedium.fontWeight,
                    fontFamily = AppTypography.bodyMedium.fontFamily,
                    modifier = Modifier.align(Alignment.Center)
                )
                Text(
                    (uiState as SubjectState.Error).message,
                    fontStyle = AppTypography.bodyMedium.fontStyle,
                    fontSize = AppTypography.bodyMedium.fontSize,
                    fontWeight = AppTypography.bodyMedium.fontWeight,
                    fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                .fillParentMaxHeight(0.15f)
                                .background(
                                    color = Color(
                                        SubjectColor.valueOf(subject.color).color(isDark)
                                    )
                                )
                        ) {
                            Text(
                                subject.name,
                                fontStyle = AppTypography.displaySmall.fontStyle,
                                fontSize = AppTypography.displaySmall.fontSize,
                                fontWeight = AppTypography.displaySmall.fontWeight,
                                fontFamily = AppTypography.displaySmall.fontFamily,
                                lineHeight = AppTypography.displaySmall.lineHeight,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Subject Details",
                            fontStyle = AppTypography.titleLarge.fontStyle,
                            fontSize = AppTypography.titleLarge.fontSize,
                            fontWeight = AppTypography.titleLarge.fontWeight,
                            fontFamily = AppTypography.titleLarge.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                "%.1f%%".format(currentPercent),
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                "${subject.minAttendance}.0%",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                "${subject.totalClasses}",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                "${subject.attendedClasses}",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                "${subject.totalClasses - subject.attendedClasses}",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                subject.professor,
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                            Text(
                                subject.roomNo,
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Attendance Record",
                            fontStyle = AppTypography.titleLarge.fontStyle,
                            fontSize = AppTypography.titleLarge.fontSize,
                            fontWeight = AppTypography.titleLarge.fontWeight,
                            fontFamily = AppTypography.titleLarge.fontFamily,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (records.isEmpty()) {
                        item {
                            Text(
                                "No records available!",
                                fontStyle = AppTypography.bodyMedium.fontStyle,
                                fontSize = AppTypography.bodyMedium.fontSize,
                                fontWeight = AppTypography.bodyMedium.fontWeight,
                                fontFamily = AppTypography.bodyMedium.fontFamily,
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
        fontStyle = AppTypography.titleMedium.fontStyle,
        fontSize = AppTypography.titleMedium.fontSize,
        fontWeight = AppTypography.titleMedium.fontWeight,
        fontFamily = AppTypography.titleMedium.fontFamily,
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
        fontStyle = AppTypography.bodyMedium.fontStyle,
        fontSize = AppTypography.bodyMedium.fontSize,
        fontWeight = AppTypography.bodyMedium.fontWeight,
        fontFamily = AppTypography.bodyMedium.fontFamily,
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
