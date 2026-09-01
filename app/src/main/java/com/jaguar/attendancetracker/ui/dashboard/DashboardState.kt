package com.jaguar.attendancetracker.ui.dashboard

import com.jaguar.attendancetracker.backend.entities.SemesterWithSubjects

sealed interface DashboardState {
    object Loading : DashboardState
    data class Success(val semesters: List<SemesterWithSubjects>) : DashboardState
    data class Error(val message: String) : DashboardState
}
