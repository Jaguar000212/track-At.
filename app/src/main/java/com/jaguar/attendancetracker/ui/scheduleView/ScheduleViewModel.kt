package com.jaguar.attendancetracker.ui.scheduleView

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepo: ScheduleRepository,
) : ViewModel() {

    val uiState: StateFlow<ScheduleState> =
        scheduleRepo.getSessionsWithSubjects().map<List<SessionsWithSubject>, ScheduleState> {
            ScheduleState.Success(it)
        }.onStart {
            emit(ScheduleState.Loading)
        }.catch {
            emit(ScheduleState.Error(it.message ?: "Unknown error"))
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleState.Loading
        )

    fun getAllSubjects(): Flow<List<Subject>> {
        return scheduleRepo.getSchedulableSubjects()
    }

    fun addSession(sessionRecord: SessionRecord) {
        viewModelScope.launch { scheduleRepo.addSession(sessionRecord) }
    }

    fun deleteSession(sessionRecord: SessionRecord) {
        viewModelScope.launch { scheduleRepo.deleteSession(sessionRecord) }
    }
}