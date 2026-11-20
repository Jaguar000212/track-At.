package com.jaguar.attendancetracker.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
internal class TodayViewModel @Inject constructor(
    attendance: AttendanceRepository,
    schedule: ScheduleRepository,
) : ViewModel() {
    // Only poll the calendar while the screen is observed. Neither this flow nor the projection
    // materializes attendance records: Day View retains ownership of that existing behavior.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged().flatMapLatest { date ->
        combine(
            attendance.getAttendanceWithSubjects(date),
            schedule.getSessionsWithSubjects(),
        ) { records, timetable ->
            val snapshot: TodayState = todaySnapshot(date, records, timetable)
            snapshot
        }.onStart { emit(TodayState.Loading(date)) }
            .catch { emit(TodayState.Error(date)) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
        TodayState.Loading(LocalDate.now()),
    )
}
