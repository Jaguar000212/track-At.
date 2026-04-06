package com.jaguar.attendancetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository, private val attendanceRepo: AttendanceRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardState> =
        scheduleRepo.getAllSubjects().map<List<Subject>, DashboardState> {
            DashboardState.Success(it)
        }.onStart {
            emit(DashboardState.Loading)
        }.catch {
            emit(DashboardState.Error(it.message ?: "Unknown error"))
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState.Loading
        )

    fun newSubject(): Subject {
        return Subject(
            name = "",
            semester = 1,
            startDate = LocalDate.now(ZoneId.systemDefault()),
            isEnded = false,
            color = "PINK",
            professor = null,
            roomNo = null,
            notes = null
        )
    }

    fun addSubject(subject: Subject) {
        viewModelScope.launch {
            scheduleRepo.addSubject(subject)
        }
    }

    fun editSubject(subject: Subject) {
        viewModelScope.launch {
            scheduleRepo.editSubject(subject)
        }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch {
            scheduleRepo.deleteSubject(subject)
        }
    }

    fun cancelScheduling(subject: Subject) {
        viewModelScope.launch {
            attendanceRepo.deleteAttendanceBySubject(subject.id)
            scheduleRepo.cancelScheduling(subject)
        }
    }
}