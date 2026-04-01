package com.jaguar.attendancetracker.ui.helpPage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.ui.theme.AppTypography

@Composable
fun HelpDay() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "The Day View", style = AppTypography.titleLarge)
        Text(text = "mark it, forget it.", style = AppTypography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This page shows you the attendance details for a specific day. You can see which classes you attended and which ones you missed.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Click on the date and choose the date on the calendar to check the classes. You can use the arrow buttons to move a day forward or backward.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Each class will appear as a card on the Day view.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "You can use the action buttons on each card to mark the attendance for that class.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "- Mark Present: To mark the class as attended.",
            style = AppTypography.bodyMedium
        )
        Text(text = "- Mark Absent: To mark the class as missed.", style = AppTypography.bodyMedium)
        Text(text = "- Cancel: To mark the class as cancelled.", style = AppTypography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can use the floating \"+\" button to add an extra class to the day view.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "This is useful when you attend a class that was not scheduled, or when you have a one-time class that you want to add to the schedule.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "It will appear as a card on the Day view, and you can use the same \"Cancel\" action button to remove it.",
            style = AppTypography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}