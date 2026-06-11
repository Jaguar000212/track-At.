package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCard(
    sessionRecord: SessionRecord,
    subject: Subject,
    onDelete: (sessionRecord: SessionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var showDeleteAlert by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(SubjectColor.GRAY.color(isDark)),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip { Text("Drag and drop to rearrange.") }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                state = rememberTooltipState()
            ) {
                Icon(
                    Icons.Outlined.Menu, "", tint = Color.Gray
                )
            }
            Text(
                subject.name,
                style = AppTypography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip { Text("Delete this session.") }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                state = rememberTooltipState()
            ) {
                ElevatedButton(
                    {
                        showDeleteAlert = true
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Delete, "", tint = Color(StatusColor.ALERT.color(isDark))
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
            TextButton({ onDelete(sessionRecord) }) {
                Text(
                    "Yes",
                    style = AppTypography.labelMedium,
                )
            }
        },
        title = {
            Text(
                "Delete Session",
                style = AppTypography.titleMedium,
            )
        },
        text = {
            Text(
                "Are you sure want to delete this session? All the data will be deleted and this operation is not reversible.",
                style = AppTypography.bodyMedium,
            )
        })
}