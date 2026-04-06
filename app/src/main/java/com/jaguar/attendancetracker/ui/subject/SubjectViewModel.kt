package com.jaguar.attendancetracker.ui.subject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SubjectViewModel @Inject constructor(
    private val attendanceRepo: AttendanceRepository,
    private val scheduleRepo: ScheduleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val subjectId: UUID = UUID.fromString(requireNotNull(savedStateHandle["subjectId"]))
    private val _uiState: MutableStateFlow<SubjectState> = MutableStateFlow(SubjectState.Loading)
    val uiState: StateFlow<SubjectState> get() = _uiState

    init {
        viewModelScope.launch {
            val subject = scheduleRepo.getSubject(subjectId)
            val records = attendanceRepo.getAttendanceBySubject(subjectId)

            _uiState.value = SubjectState.Success(
                subject = subject, attendanceRecords = records
            )
        }
    }
}