package com.jaguar.attendancetracker.ui.imExport

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.repositories.ImportExportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

@HiltViewModel
class ImExportViewModel @Inject constructor(
    private val ieRepo: ImportExportRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun exportData(
        uri: Uri, includeSubjects: Boolean, includeSchedule: Boolean, includeAttendance: Boolean
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val backupData = withContext(Dispatchers.IO) {
                    val semesters = if (includeSubjects) ieRepo.getAllSemesters().first() else null
                    val subjects = if (includeSubjects) ieRepo.getAllSubjects().first() else null
                    val schedule =
                        if (includeSchedule) ieRepo.getAllSessionRecords().first() else null
                    val attendance =
                        if (includeAttendance) ieRepo.getAllAttendanceRecords().first() else null

                    BackupData(
                        semesters = semesters,
                        subjects = subjects,
                        schedule = schedule,
                        attendance = attendance
                    )
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            AttendanceBackupFormat.write(backupData, writer)
                        }
                    }
                }

                Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val backupData = AttendanceBackupFormat.read(reader)

                            ieRepo.upsertDataInTransaction(
                                semesters = backupData.semesters,
                                subjects = backupData.subjects,
                                sessions = backupData.schedule,
                                attendance = backupData.attendance
                            )
                        }
                    }
                }
                Toast.makeText(context, "Import Successful!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                val message = if (e.message?.contains("Constraint") == true) {
                    "Data Error: Constraint violation."
                } else {
                    e.message ?: "Unknown error occurred."
                }
                Toast.makeText(context, "Import Failed: $message", Toast.LENGTH_LONG).show()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
