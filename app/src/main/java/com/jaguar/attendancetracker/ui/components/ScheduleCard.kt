package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.StatusColor
import com.jaguar.attendancetracker.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCard(
    sessionRecord: SessionRecord,
    subject: Subject,
    onDelete: (sessionRecord: SessionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteAlert by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text(
                subject.name,
                fontStyle = AppTypography.titleLarge.fontStyle,
                fontSize = AppTypography.titleLarge.fontSize,
                fontWeight = AppTypography.titleLarge.fontWeight,
                fontFamily = AppTypography.titleLarge.fontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Delete this session.") } },
                state = rememberTooltipState()
            ) {
                IconButton({
                    showDeleteAlert = true
                }) {
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
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        },
        confirmButton = {
            TextButton({ onDelete(sessionRecord) }) {
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
                "Delete Session",
                fontStyle = AppTypography.titleMedium.fontStyle,
                fontSize = AppTypography.titleMedium.fontSize,
                fontWeight = AppTypography.titleMedium.fontWeight,
                fontFamily = AppTypography.titleMedium.fontFamily,
            )
        },
        text = {
            Text(
                "Are you sure want to delete this session? All the data will be deleted and this operation is not reversible.",
                fontStyle = AppTypography.bodyMedium.fontStyle,
                fontSize = AppTypography.bodyMedium.fontSize,
                fontWeight = AppTypography.bodyMedium.fontWeight,
                fontFamily = AppTypography.bodyMedium.fontFamily,
            )
        })
}