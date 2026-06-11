package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCard(
    record: AttendanceRecord,
    subject: Subject,
    modifier: Modifier = Modifier,
    onPresent: () -> Unit,
    onAbsent: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val required = subject.requiredToMakeUp()
    val checked = remember(record.status) {
        record.status
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors()
            .copy(containerColor = Color((SubjectColor.valueOf(subject.color)).color(isDark)))
    ) {
        Box(
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        subject.name,
                        style = AppTypography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (record.classType == ClassType.EXTRA) Text(
                        "EXTRA",
                        style = AppTypography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier
                            .align(Alignment.Bottom)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .padding(horizontal = 4.dp)
                    )
                }
                Text(
                    when {
                        required == 0 -> "At risk, you should avoid skipping classes."
                        required > 0 -> "All set, you can skip $required classes."
                        else -> "Below minimum, ${required.absoluteValue} needed."
                    },
                    style = AppTypography.labelMedium,
                )
                if (subject.roomNo != null) Text(
                    "Room No: ${subject.roomNo}", style = AppTypography.labelSmall.copy(
                        fontWeight = FontWeight.Thin
                    ), color = Color.Gray
                )
                if (subject.professor != null) Text(
                    "Professor: ${subject.professor}", style = AppTypography.labelSmall.copy(
                        fontWeight = FontWeight.Thin
                    ), color = Color.Gray
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Mark present.") }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        state = rememberTooltipState()
                    ) {
                        ElevatedButton(
                            {
                                onPresent()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (checked == AttendanceStatus.PRESENT) Icon(
                                Icons.Outlined.Check,
                                "",
                                tint = Color(StatusColor.GOOD.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                            else Icon(
                                Icons.Outlined.Check,
                                "",
                                tint = Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Mark absent.") }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        state = rememberTooltipState()
                    ) {
                        ElevatedButton(
                            {
                                onAbsent()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (checked == AttendanceStatus.ABSENT) Icon(
                                Icons.Outlined.Clear,
                                "",
                                tint = Color(StatusColor.ALERT.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                            else Icon(
                                Icons.Outlined.Clear,
                                "",
                                tint = Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Mark cancelled.") }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        state = rememberTooltipState()
                    ) {
                        ElevatedButton(
                            {
                                onCancel()
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (checked == AttendanceStatus.CANCELLED) Icon(
                                Icons.Outlined.Delete,
                                "",
                                tint = Color(StatusColor.WARNING.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                            else Icon(
                                Icons.Outlined.Delete,
                                "",
                                tint = Color.Gray,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}