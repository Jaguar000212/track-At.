package com.jaguar.attendancetracker.ui.scheduleView

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.daos.TimetableEntry
import com.jaguar.attendancetracker.backend.daos.group
import com.jaguar.attendancetracker.backend.daos.timetableEntryOrder
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        scheduleRepo.observeTimetable().map<List<TimetableEntry>, ScheduleState> {
            ScheduleState.Success(it.sortedWith(timetableEntryOrder))
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

    private val _reorderError = MutableStateFlow<ReorderError?>(null)
    internal val reorderError: StateFlow<ReorderError?> = _reorderError
    fun dismissReorderError() { _reorderError.value = null }

    internal fun updateSessionOrder(baseline: List<TimetableEntry>, desired: List<TimetableEntry>) {
        val beforeGroups = baseline.groupBy { it.order.group }
        val changed = desired.groupBy { it.order.group }.mapValues { (_, group) ->
            group.map { it.order.session.id }
        }.filter { (group, ids) -> ids != beforeGroups.getValue(group).map { it.order.session.id } }
        if (changed.isEmpty()) return
        val days = changed.keys.mapTo(HashSet()) { it.day }
        val expected = baseline.filter { it.order.session.dayOfWeek in days }
            .groupBy({ it.order.session.dayOfWeek }, { it.order })
        viewModelScope.launch {
            try {
                if (!scheduleRepo.reorderSessions(expected, changed)) _reorderError.value = ReorderError.STALE
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _reorderError.value = ReorderError.SAVE
            }
        }
    }

    internal enum class ReorderError { STALE, SAVE }

    fun deleteSession(sessionRecord: SessionRecord) {
        viewModelScope.launch { scheduleRepo.deleteSession(sessionRecord) }
    }
}