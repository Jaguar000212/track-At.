package com.jaguar.attendancetracker.ui.dayView

import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import java.time.LocalDate

interface DayState {
    val date: LocalDate

    data class Loading(override val date: LocalDate) : DayState
    data class Success(override val date: LocalDate, val dayRecords: List<AttendanceWithSubject>) :
        DayState

    data class Error(override val date: LocalDate, val message: String) : DayState
}