package com.jaguar.attendancetracker.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import kotlin.math.absoluteValue


@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalStdlibApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubjectCard(
    subject: Subject,
    modifier: Modifier = Modifier,
    onClick: (subject: Subject) -> Unit,
    onEdit: (subject: Subject) -> Unit,
    onCancelSchedule: (subject: Subject) -> Unit,
    onDelete: (subject: Subject) -> Unit
) {
    val context = LocalContext.current
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showCancelAlert by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val required = subject.requiredToMakeUp()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = { onClick(subject) }),
        colors = CardDefaults.cardColors()
            .copy(containerColor = Color((SubjectColor.entries.firstOrNull {
                it.name.equals(
                    subject.color, ignoreCase = true
                )
            } ?: SubjectColor.GRAY).color(isDark)))) {
        Box(
            Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    subject.name,
                    fontStyle = AppTypography.titleLarge.fontStyle,
                    fontSize = AppTypography.titleLarge.fontSize,
                    fontWeight = AppTypography.titleLarge.fontWeight,
                    fontFamily = AppTypography.titleLarge.fontFamily,
                )
                Text(
                    when {
                        required < 0 -> "Below minimum, can't skip any class, ${required.absoluteValue} needed."
                        required > 0 -> "All set, you can skip $required classes."
                        else -> "At risk, you should avoid skipping classes."
                    },
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
                Text(
                    "Present: ${subject.attendedClasses} | Absent: ${subject.totalClasses - subject.attendedClasses}",
                    fontStyle = AppTypography.labelSmall.fontStyle,
                    fontSize = AppTypography.labelSmall.fontSize,
                    fontWeight = AppTypography.labelSmall.fontWeight,
                    fontFamily = AppTypography.labelSmall.fontFamily,
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Edit this subject.") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton({
                            showEditDialog = true
                        }) {
                            Icon(
                                Icons.Outlined.Create,
                                "",
                                tint = Color(StatusColor.GOOD.color(isDark))
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("Delete this subject.") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton({
                            showDeleteAlert = true
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                "",
                                tint = Color(StatusColor.ALERT.color(isDark))
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("End scheduling of this subject.") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton({
                            if (!subject.isEnded) {
                                showCancelAlert = true
                            } else {
                                Toast.makeText(
                                    context,
                                    "Subject will be scheduled in future.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onCancelSchedule(subject.copy(isEnded = false, color = "PINK"))
                            }
                        }) {
                            Icon(
                                painterResource(R.drawable.pause),
                                "",
                                tint = Color(StatusColor.WARNING.color(isDark))
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterEnd)
                    .background(
                        color = Color(
                            when {
                                subject.attendancePercentage() > subject.minAttendance -> StatusColor.GOOD.color(
                                    isDark
                                )

                                subject.attendancePercentage() < subject.minAttendance -> StatusColor.ALERT.color(
                                    isDark
                                )

                                else -> StatusColor.WARNING.color(isDark)
                            }
                        ), shape = CircleShape
                    ), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${subject.attendancePercentage().toInt()}%", color = Color.White,
                    fontStyle = AppTypography.labelLarge.fontStyle,
                    fontSize = AppTypography.labelLarge.fontSize,
                    fontWeight = AppTypography.labelLarge.fontWeight,
                    fontFamily = AppTypography.labelLarge.fontFamily,
                )
            }
        }
    }

    if (showDeleteAlert) AlertDialog(
        onDismissRequest = { showDeleteAlert = false },
        dismissButton = {
            TextButton({ showDeleteAlert = false }) {
                Text(
                    "No",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        },
        confirmButton = {
            TextButton({
                onDelete(subject)
                showCancelAlert = false
            }) {
                Text(
                    "Yes",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        },
        modifier = Modifier,
        title = {
            Text(
                "Delete Subject",
                fontStyle = AppTypography.titleMedium.fontStyle,
                fontSize = AppTypography.titleMedium.fontSize,
                fontWeight = AppTypography.titleMedium.fontWeight,
                fontFamily = AppTypography.titleMedium.fontFamily,
            )
        },
        text = {
            Text(
                "Are you sure want to delete this subject? All the data will be deleted and this operation is not reversible.",
                fontStyle = AppTypography.bodyMedium.fontStyle,
                fontSize = AppTypography.bodyMedium.fontSize,
                fontWeight = AppTypography.bodyMedium.fontWeight,
                fontFamily = AppTypography.bodyMedium.fontFamily,
            )
        })

    if (showCancelAlert) AlertDialog(
        onDismissRequest = { showCancelAlert = false },
        dismissButton = {
            TextButton({ showCancelAlert = false }) {
                Text(
                    "No",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        },
        confirmButton = {
            TextButton({
                onCancelSchedule(subject.copy(isEnded = true, color = "GRAY"))
                showCancelAlert = false
            }) {
                Text(
                    "Yes",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        },
        modifier = Modifier,
        title = {
            Text(
                "Stop future scheduling",
                fontStyle = AppTypography.titleMedium.fontStyle,
                fontSize = AppTypography.titleMedium.fontSize,
                fontWeight = AppTypography.titleMedium.fontWeight,
                fontFamily = AppTypography.titleMedium.fontFamily,
            )
        },
        text = {
            Text(
                "Are you sure want to stop scheduling this subject? This will delete all related schedules, but won't affect your attendance data.",
                fontStyle = AppTypography.bodyMedium.fontStyle,
                fontSize = AppTypography.bodyMedium.fontSize,
                fontWeight = AppTypography.bodyMedium.fontWeight,
                fontFamily = AppTypography.bodyMedium.fontFamily,
            )
        })

    if (showEditDialog) {
        EditSubjectBottomSheet(subject, { showEditDialog = false }) {
            onEdit(it)
            showEditDialog = false
        }
    }
}