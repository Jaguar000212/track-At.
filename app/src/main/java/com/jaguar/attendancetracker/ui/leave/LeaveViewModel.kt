package com.jaguar.attendancetracker.ui.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

internal sealed interface LeaveScreenState {
    data object Loading : LeaveScreenState
    data object Unavailable : LeaveScreenState
    data class Ready(val snapshot: LeaveSnapshot) : LeaveScreenState
}

internal suspend fun readLeaveSnapshot(db: AttendanceDatabase): LeaveSnapshot = db.withTransaction {
    val dao = db.plannerRestoreDao()
    LeaveSnapshot(dao.semesters(), dao.subjects(), dao.schedule(), dao.attendance(), dao.exams())
}

@HiltViewModel
internal class LeaveViewModel @Inject constructor(db: AttendanceDatabase) : ViewModel() {
    val state = db.invalidationTracker.createFlow("Semesters", "Subjects", "SessionRecords", "AttendanceRecords", "Exams")
        .map {
            val ready: LeaveScreenState = LeaveScreenState.Ready(readLeaveSnapshot(db))
            ready
        }.catch { emit(LeaveScreenState.Unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaveScreenState.Loading)
}
