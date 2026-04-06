package com.jaguar.attendancetracker.ui.imexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ImExport(
    viewModel: ImExportViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {

        var isSubjectsChecked by remember { mutableStateOf(true) }
        var isScheduleChecked by remember { mutableStateOf(true) }
        var isAttendanceChecked by remember { mutableStateOf(true) }

        val exportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                viewModel.exportData(
                    uri, isSubjectsChecked, isScheduleChecked, isAttendanceChecked
                )
            }
        }

        val importLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                viewModel.importData(uri)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = "Export Data", style = AppTypography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Export your attendance data to a JSON file for backup or analysis. Later, you can import this data back into the app if needed.",
                style = AppTypography.bodyMedium
            )
            Text(
                text = "Pro-tip: Share this file with your friends and save them the trouble of setting up their schedule from scratch!",
                style = AppTypography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                "Select Data to Export:",
                style = AppTypography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isSubjectsChecked, onCheckedChange = {
                    isSubjectsChecked = it
                    if (!it) {
                        isScheduleChecked = false
                        isAttendanceChecked = false
                    }
                })
                Spacer(Modifier.width(8.dp))
                Text("Subjects", style = AppTypography.bodyMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = isScheduleChecked, onCheckedChange = {
                    isScheduleChecked = it
                    if (it) {
                        isSubjectsChecked = true
                    } else {
                        isAttendanceChecked = false
                    }
                })
                Spacer(Modifier.width(8.dp))
                Text("Schedule", style = AppTypography.bodyMedium)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = isAttendanceChecked, onCheckedChange = {
                        isAttendanceChecked = it
                        if (it) {
                            isSubjectsChecked = true
                            isScheduleChecked = true
                        }
                    })
                Spacer(Modifier.width(8.dp))
                Text("Attendance", style = AppTypography.bodyMedium)
            }

            Button(
                onClick = {
                    val timestamp =
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                    exportLauncher.launch("attendance_backup_$timestamp.json")
                }, modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.End)
            ) {
                Icon(painterResource(R.drawable.export_data), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Export Data",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }

        }

        FloatingActionButton(
            onClick = { importLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(painterResource(R.drawable.import_data), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Import Data",
                    fontStyle = AppTypography.labelMedium.fontStyle,
                    fontSize = AppTypography.labelMedium.fontSize,
                    fontWeight = AppTypography.labelMedium.fontWeight,
                    fontFamily = AppTypography.labelMedium.fontFamily,
                )
            }
        }
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing...", style = AppTypography.titleMedium)
                }
            }
        }
    }
}