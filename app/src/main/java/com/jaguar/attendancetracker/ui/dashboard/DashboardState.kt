package com.jaguar.attendancetracker.ui.dashboard

import com.jaguar.attendancetracker.backend.entities.Subject

sealed interface DashboardState {
    object Loading : DashboardState
    data class Success(val subjects: List<Subject>) : DashboardState
    data class Error(val message: String) : DashboardState
}
