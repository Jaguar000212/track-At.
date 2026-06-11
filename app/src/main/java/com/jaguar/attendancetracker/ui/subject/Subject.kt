package com.jaguar.attendancetracker.ui.subject

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.format.DateTimeFormatter

@Composable
fun Subject(
    viewModel: SubjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                val attendanceRecords = state.attendanceRecords.filter { it.status != null }

                var presentFilter by remember { mutableStateOf(false) }
                var absentFilter by remember { mutableStateOf(false) }
                var cancelFilter by remember { mutableStateOf(false) }
                val records by remember(
                    attendanceRecords, presentFilter, absentFilter, cancelFilter
                ) {
                    derivedStateOf {
                        if (presentFilter || absentFilter || cancelFilter) attendanceRecords.filter {
                            when (it.status) {
                                AttendanceStatus.PRESENT -> presentFilter
                                AttendanceStatus.ABSENT -> absentFilter
                                AttendanceStatus.CANCELLED -> cancelFilter
                                null -> false
                            }
                        }
                        else attendanceRecords
                    }
                }

                val currentPercent = subject.attendancePercentage()
                var animationTarget by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(currentPercent) {
                    animationTarget = (currentPercent / 100f).coerceIn(0f, 1f)
                }
                val animatedProgress by animateFloatAsState(
                    targetValue = animationTarget,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "ProgressAnimation"
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    item {
                        Text(
                            subject.name,
                            style = AppTypography.displaySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (subject.professor != null) {
                            Text(
                                "Prof. ${subject.professor}",
                                style = AppTypography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                        if (subject.roomNo != null) {
                            Text(
                                "at Room ${subject.roomNo}",
                                style = AppTypography.bodyMedium,
                                color = Color.Gray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Overview",
                                    style = AppTypography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Text(
                                        subject.totalClasses.toString(),
                                        style = AppTypography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        " Classes", style = AppTypography.bodySmall.copy(
                                            fontWeight = FontWeight.Thin
                                        ), color = Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Text(
                                        subject.attendedClasses.toString(),
                                        style = AppTypography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        " Present", style = AppTypography.bodySmall.copy(
                                            fontWeight = FontWeight.Thin
                                        ), color = Color.Gray
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    Text(
                                        (subject.totalClasses - subject.attendedClasses).toString(),
                                        style = AppTypography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        " Absent", style = AppTypography.bodySmall.copy(
                                            fontWeight = FontWeight.Thin
                                        ), color = Color.Gray
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${subject.minAttendance}%",
                                        style = AppTypography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        " Required", style = AppTypography.bodySmall.copy(
                                            fontWeight = FontWeight.Thin
                                        ), color = Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(120.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 8.dp,
                                    )
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val strokeWidthPx = 8.dp.toPx()
                                        val sweepAngle = 2f

                                        val startAngle =
                                            (subject.minAttendance / 100f * 360f) - 90f - (sweepAngle / 2)

                                        drawArc(
                                            color = Color.Gray,
                                            startAngle = startAngle,
                                            sweepAngle = sweepAngle,
                                            useCenter = false,
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            "${"%.1f".format(currentPercent)}%",
                                            style = AppTypography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold
                                            ),

                                            color = when {
                                                currentPercent < subject.minAttendance - 5 -> Color(
                                                    StatusColor.ALERT.color(
                                                        isSystemInDarkTheme()
                                                    )
                                                )

                                                currentPercent > subject.minAttendance + 5 -> Color(
                                                    StatusColor.GOOD.color(isSystemInDarkTheme())
                                                )

                                                else -> Color(
                                                    StatusColor.WARNING.color(
                                                        isSystemInDarkTheme()
                                                    )
                                                )
                                            }
                                        )
                                        Text(
                                            "PRESENT", style = AppTypography.bodySmall.copy(
                                                fontWeight = FontWeight.Thin
                                            ), color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
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
                            Text(
                                "Records",
                                style = AppTypography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        item {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                FilterChip(
                                    label = { Text("Present") }, onClick = {
                                        presentFilter = !presentFilter
                                        absentFilter = false
                                        cancelFilter = false
                                    }, selected = presentFilter
                                )
                                FilterChip(
                                    label = { Text("Absent") }, onClick = {
                                        absentFilter = !absentFilter
                                        presentFilter = false
                                        cancelFilter = false
                                    }, selected = absentFilter
                                )
                                FilterChip(
                                    label = { Text("Cancelled") }, onClick = {
                                        cancelFilter = !cancelFilter
                                        presentFilter = false
                                        absentFilter = false
                                    }, selected = cancelFilter
                                )
                            }
                        }

                        items(records, key = { it.id }) {
                            it.Card()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceRecord.Card() {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer, CircleShape
                    ), contentAlignment = Alignment.Center
            ) {
                when (status) {
                    AttendanceStatus.PRESENT -> Text(
                        "P", style = AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ), color = Color(StatusColor.GOOD.color(isSystemInDarkTheme()))
                    )

                    AttendanceStatus.ABSENT -> Text(
                        "A", style = AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ), color = Color(StatusColor.ALERT.color(isSystemInDarkTheme()))
                    )

                    AttendanceStatus.CANCELLED -> Text(
                        "C", style = AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold
                        ), color = Color(StatusColor.WARNING.color(isSystemInDarkTheme()))
                    )

                    null -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    style = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = classType.toString(),
                    style = AppTypography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
