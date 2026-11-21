package com.jaguar.attendancetracker.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

internal sealed interface AgendaState {
    data object Loading : AgendaState
    data object Error : AgendaState
    data class Ready(val entries: AgendaEntries) : AgendaState
}

@HiltViewModel
internal class AgendaViewModel @Inject constructor(db: AttendanceDatabase) : ViewModel() {
    val state = combine(db.boardItemDao().observeAll(), db.examDao().observeAll()) { tasks, exams ->
        val result: AgendaState = AgendaState.Ready(projectAgenda(tasks, exams))
        result
    }.catch { emit(AgendaState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), AgendaState.Loading)
}
