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
fun HelpDashboard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "The Dashboard", style = AppTypography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = "from where it starts.", style = AppTypography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This is where you can see all your subjects and their attendance details. You can also add new subjects here.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Use the floating \"+\" button and fill the details.",
            style = AppTypography.bodyMedium
        )

        Text(
            text = "- Required Attendance: This sets the threshold of minimum attendance required for a subject to be considered present.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "- Start Date: Marks the start date of the subject, from when the scheduling should start.",
            style = AppTypography.bodyMedium
        )
        Text(text = "Rest of the fields are self-explanatory.", style = AppTypography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "All the subjects will appear as cards on the Dashboard.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Each card has 3 action buttons, and clicking on the card will open the subject view.",
            style = AppTypography.bodyMedium
        )
        Text(text = "- Edit: To edit the subject details.", style = AppTypography.bodyMedium)
        Text(text = "- Delete: To delete the subject.", style = AppTypography.bodyMedium)
        Text(
            text = "- Toggle Scheduling: To end the scheduling of the subject, without deleting it, and thus retaining its data.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "Note: Ending the scheduling of a subject will hide it from the schedule and day views, but it will still be visible on the dashboard with its attendance details.",
            style = AppTypography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can access the Import/Export feature from the Dashboard by using the action button on the top right.",
            style = AppTypography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}