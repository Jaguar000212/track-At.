package com.jaguar.attendancetracker.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography
import kotlin.math.absoluteValue


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
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showCancelAlert by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val editTooltipState = rememberTooltipState()
    LaunchedEffect(editTooltipState.isVisible) {
        if (editTooltipState.isVisible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val deleteTooltipState = rememberTooltipState()
    LaunchedEffect(deleteTooltipState.isVisible) {
        if (deleteTooltipState.isVisible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val cancelTooltipState = rememberTooltipState()
    LaunchedEffect(cancelTooltipState.isVisible) {
        if (cancelTooltipState.isVisible) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val required = subject.requiredToMakeUp()
    val currentPercent = subject.attendancePercentage()
    var animationTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPercent) {
        animationTarget = (currentPercent / 100f).coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 500),
        label = "ProgressAnimation"
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(onClick = { onClick(subject) }),
        colors = CardDefaults.cardColors()
            .copy(containerColor = Color(SubjectColor.valueOf(subject.color).color(isDark)))
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                Modifier
                    .padding(8.dp)
                    .weight(1f)
            ) {
                Text(
                    text = subject.name,
                    style = AppTypography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when {
                        required < 0 -> "Below minimum, ${required.absoluteValue} needed."
                        required > 0 -> "All set, you can skip $required classes."
                        else -> "At risk, you should avoid skipping classes."
                    },
                    style = AppTypography.labelMedium,
                )
                Row(
                    verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        subject.attendedClasses.toString(), style = AppTypography.bodySmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        " Present", style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.Thin
                        ), color = Color.Gray
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        (subject.totalClasses - subject.attendedClasses).toString(),
                        style = AppTypography.bodySmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        " Absent", style = AppTypography.labelSmall.copy(
                            fontWeight = FontWeight.Thin
                        ), color = Color.Gray
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Edit this subject.") }
                        },
                        state = editTooltipState
                    ) {
                        ElevatedButton(
                            {
                                showEditDialog = true
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Create,
                                "",
                                tint = Color(StatusColor.GOOD.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("Delete this subject.") }
                        },
                        state = deleteTooltipState
                    ) {
                        ElevatedButton(
                            {
                                showDeleteAlert = true
                                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                "",
                                tint = Color(StatusColor.ALERT.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip { Text("End scheduling of this subject.") }
                        },
                        state = cancelTooltipState
                    ) {
                        ElevatedButton(
                            {
                                if (!subject.isEnded) {
                                    showCancelAlert = true
                                    haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Subject will be scheduled in future.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onCancelSchedule(
                                        subject.copy(
                                            isEnded = false, color = "PINK"
                                        )
                                    )
                                }
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painterResource(R.drawable.pause),
                                "",
                                tint = Color(StatusColor.WARNING.color(isDark)),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

            }
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(84.dp), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "${"%.1f".format(currentPercent)}%", style = AppTypography.bodyMedium.copy(
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
                onDelete(subject)
                showCancelAlert = false
            }) {
                Text(
                    "Yes",
                    style = AppTypography.labelMedium,
                )
            }
        },
        modifier = Modifier,
        title = {
            Text(
                "Delete Subject",
                style = AppTypography.titleMedium,
            )
        },
        text = {
            Text(
                "Are you sure want to delete this subject? All the data will be deleted and this operation is not reversible.",
                style = AppTypography.bodyMedium,
            )
        })

    if (showCancelAlert) AlertDialog(
        onDismissRequest = { showCancelAlert = false },
        dismissButton = {
            TextButton({ showCancelAlert = false }) {
                Text(
                    "No",
                    style = AppTypography.labelMedium,
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
                    style = AppTypography.labelMedium,
                )
            }
        },
        modifier = Modifier,
        title = {
            Text(
                "Stop future scheduling",
                style = AppTypography.titleMedium,
            )
        },
        text = {
            Text(
                "Are you sure want to stop scheduling this subject? This will delete all related schedules, but won't affect your attendance data.",
                style = AppTypography.bodyMedium,
            )
        })

    if (showEditDialog) {
        EditSubjectBottomSheet(subject, { showEditDialog = false }) {
            onEdit(it)
            showEditDialog = false
        }
    }
}