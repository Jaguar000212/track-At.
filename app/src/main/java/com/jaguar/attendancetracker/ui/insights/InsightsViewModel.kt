package com.jaguar.attendancetracker.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal data class InsightsSubject(val id: UUID, val name: String, val termName: String, val archived: Boolean)

internal sealed interface InsightsState {
    data object Loading : InsightsState
    data object Unavailable : InsightsState
    data class Ready(val today: LocalDate, val rows: List<AttendanceRecord>, val subjects: List<InsightsSubject>) : InsightsState
}

/** One consistent read snapshot. No Day View generation or timetable is involved. */
internal suspend fun readInsightsSnapshot(db: AttendanceDatabase, today: LocalDate): InsightsState.Ready =
    db.withTransaction {
        val terms = db.plannerRestoreDao().semesters().associateBy { it.id }
        val subjects = db.plannerRestoreDao().subjects().mapNotNull { subject ->
            val term = terms[subject.semesterId] ?: return@mapNotNull null
            InsightsSubject(subject.id, subject.name, term.name, term.isArchived)
        }.sortedWith(compareBy<InsightsSubject> { it.archived }.thenBy { it.name }.thenBy { it.termName }.thenBy { it.id })
        InsightsState.Ready(today, db.attendanceRecordDao().readDateWindow(attendanceTrendStart(today), today), subjects)
    }

@HiltViewModel
internal class InsightsViewModel @Inject constructor(db: AttendanceDatabase) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val state = flow {
        while (true) {
            emit(LocalDate.now())
            delay(30_000)
        }
    }.distinctUntilChanged().flatMapLatest { today ->
        db.invalidationTracker.createFlow("AttendanceRecords", "Subjects", "Semesters")
            .map {
                val ready: InsightsState = readInsightsSnapshot(db, today)
                ready
            }.catch { emit(InsightsState.Unavailable) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState.Loading)
}
