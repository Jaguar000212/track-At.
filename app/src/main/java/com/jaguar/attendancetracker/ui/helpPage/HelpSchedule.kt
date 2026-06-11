package com.jaguar.attendancetracker.ui.helpPage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.ui.theme.AppTypography

@Composable
fun HelpSchedule() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Schedule Details", style = AppTypography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = "time to set it up!", style = AppTypography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This page shows you your schedule for the week. You can also add new classes to your schedule.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Use the floating \"+\" button and simply select the subject and the days you want to schedule it for.",
            style = AppTypography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "All the scheduled classes will appear as cards on the Schedule view.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "You can use the Delete action button to remove the schedule.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Also, press and hold the card to enter sort mode, and drag the cards to sort them.",
            style = AppTypography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}