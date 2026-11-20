package com.jaguar.attendancetracker.ui.scheduleView

import com.jaguar.attendancetracker.backend.daos.TimetableEntry

interface ScheduleState {
    object Loading : ScheduleState
    data class Success(val sessionRecords: List<TimetableEntry>) : ScheduleState
    data class Error(val message: String) : ScheduleState
}