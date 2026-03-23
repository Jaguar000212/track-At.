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
fun HelpSubject() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(text = "Subject Details", style = AppTypography.titleLarge)
        Text(text = "keeps you updated.", style = AppTypography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This page shows you the details for a specific subject. You can see how many classes you attended and how many you missed.",
            style = AppTypography.bodyMedium
        )
        Text(
            text = "You can also view the attendance records for that subject.",
            style = AppTypography.bodyMedium
        )
    }
}