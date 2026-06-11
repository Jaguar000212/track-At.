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
fun HelpImExport() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Import/Export", style = AppTypography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        Text(text = "your saviour (and your buddy's).", style = AppTypography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This feature allows you to backup your data or import data from another device. Keep your attendance records safe!",
            style = AppTypography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select the data you want to backup, then click \"Export\".",
            style = AppTypography.bodyMedium
        )
        Text(text = "Choose the save location, and it's done.", style = AppTypography.bodyMedium)
        Text(
            text = "You can choose to import only subjects and schedule, share it with a friend, and they can use it to import the schedule and set up their app.",
            style = AppTypography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Use the floating button and simply select the file you backed up to import it back.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "The Import functionality is backward compatible with the past versions of the app, but not vice-versa.",
            style = AppTypography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}