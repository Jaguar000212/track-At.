package com.jaguar.attendancetracker.ui.imexport

import com.jaguar.attendancetracker.backend.DB_VERSION
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject

data class BackupData(
    val version: Int = DB_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val subjects: List<Subject>? = null,
    val schedule: List<SessionRecord>? = null,
    val attendance: List<AttendanceRecord>? = null
)
