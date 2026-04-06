package com.jaguar.attendancetracker.ui.subject

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Subject

interface SubjectState {
    object Loading : SubjectState
    data class Success(
        val subject: Subject, val attendanceRecords: List<AttendanceRecord> = emptyList()
    ) : SubjectState

    data class Error(val message: String) : SubjectState
}