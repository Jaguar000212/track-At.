package com.jaguar.attendancetracker.ui.scheduleView

import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject

interface ScheduleState {
    object Loading : ScheduleState
    data class Success(val sessionRecords: List<SessionsWithSubject>) : ScheduleState
    data class Error(val message: String) : ScheduleState
}