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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.ui.theme.AppTypography

@Composable
fun HelpImExport() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.nav_import_export),
            style = AppTypography.titleLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.attendance_backup_private), style = AppTypography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.attendance_backup_contents), style = AppTypography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.help_attendance_backup_steps), style = AppTypography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.help_attendance_backup_planner), style = AppTypography.bodyMedium)
        Spacer(modifier = Modifier.height(64.dp))
    }
}
