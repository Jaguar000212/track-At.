package com.jaguar.attendancetracker.ui.dayView

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DayViewModel @Inject constructor(
    private val attendanceRepo: AttendanceRepository, private val scheduleRepo: ScheduleRepository
) : ViewModel() {
    private val _currentDate = MutableStateFlow(LocalDate.now(ZoneId.systemDefault()))

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DayState> = _currentDate.flatMapLatest { date ->
        attendanceRepo.getAttendanceWithSubjects(date)
            .map<List<AttendanceWithSubject>, DayState> { records ->
                DayState.Success(date, records)
            }.onStart {
                emit(DayState.Loading(date))
            }.catch { e ->
                emit(DayState.Error(date, e.message ?: "Unknown error"))
            }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), DayState.Loading(_currentDate.value)
    )

    fun loadDate(date: LocalDate) {
        _currentDate.value = date
        viewModelScope.launch {
            ensureAttendanceRecordsExist(date)
        }
    }

    fun getSubjects(): Flow<List<Subject>> {
        return scheduleRepo.getSchedulableSubjects()
    }

    private suspend fun ensureAttendanceRecordsExist(date: LocalDate) {
        val dayOfWeek = date.dayOfWeek.value

        val sessions = scheduleRepo.getSessionsByDay(dayOfWeek).sortedBy { it.orderNo }
        val existingRecords = attendanceRepo.getAttendanceByDate(date)
        val recordedSessionIds = existingRecords.map { it.sessionId }.toSet()

        sessions.forEach { session ->
            if (session.id !in recordedSessionIds && session.startDate <= date) {
                attendanceRepo.addAttendance(
                    AttendanceRecord(
                        id = UUID.randomUUID(),
                        sessionId = session.id,
                        date = date,
                        status = null,
                        subjectId = session.subjectId,
                        classType = ClassType.REGULAR
                    )
                )
            }
        }
    }

    fun markPresent(record: AttendanceWithSubject) {
        viewModelScope.launch { attendanceRepo.markPresent(record) }
    }

    fun markAbsent(record: AttendanceWithSubject) {
        viewModelScope.launch { attendanceRepo.markAbsent(record) }
    }

    fun markCancelled(record: AttendanceWithSubject) {
        viewModelScope.launch { attendanceRepo.markCancelled(record) }
    }

    fun addExtraClass(record: AttendanceRecord) {
        viewModelScope.launch { attendanceRepo.addAttendance(record) }
    }
}
