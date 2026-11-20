package com.jaguar.attendancetracker.ui.imExport

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject

/** Version of the legacy attendance JSON wire format, independent of Room. */
internal const val ATTENDANCE_BACKUP_VERSION = 10

data class BackupData(
    val version: Int = ATTENDANCE_BACKUP_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val semesters: List<Semester>? = null,
    val subjects: List<Subject>? = null,
    val schedule: List<SessionRecord>? = null,
    val attendance: List<AttendanceRecord>? = null
)
