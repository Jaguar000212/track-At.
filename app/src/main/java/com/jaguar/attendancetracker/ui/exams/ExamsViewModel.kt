package com.jaguar.attendancetracker.ui.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal sealed interface ExamsState {
    data object Loading : ExamsState
    data object Unavailable : ExamsState
    data class Ready(val exams: List<Exam>, val semesters: List<Semester>) : ExamsState
}

internal data class ExamDraft(
    val id: UUID? = null,
    val title: String = "",
    val date: LocalDate? = null,
    val semesterId: UUID? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
internal class ExamsViewModel @Inject constructor(private val db: AttendanceDatabase) : ViewModel() {
    val state = combine(db.examDao().observeAll(), db.semesterDao().getAll()) { exams, semesters ->
        val ready: ExamsState = ExamsState.Ready(exams, semesters)
        ready
    }.catch { emit(ExamsState.Unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamsState.Loading)

    // Navigation-scoped memory only: rotation retains it; no disk, logs or SavedState.
    private val editing = MutableStateFlow<ExamDraft?>(null)
    val draft = editing.asStateFlow()

    fun edit(exam: Exam?) {
        if (editing.value != null) return
        editing.value = exam?.let { ExamDraft(it.id, it.title, it.date, it.semesterId) } ?: ExamDraft()
    }

    fun change(next: ExamDraft) {
        editing.value?.takeIf { !it.saving && it.id == next.id }?.let {
            editing.value = next.copy(saving = false, failed = false)
        }
    }

    fun cancel() {
        if (editing.value?.saving != true) editing.value = null
    }

    fun save() {
        val value = editing.value ?: return
        if (value.saving || value.title.isBlank()) return
        editing.value = value.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                db.withTransaction {
                    // Room validates the FK; a concurrently removed semester never gets silently
                    // changed into a different selected value by saving the editor.
                    val dao = db.examDao()
                    if (value.id == null) {
                        dao.insert(Exam(title = value.title, date = value.date, semesterId = value.semesterId))
                    } else {
                        check(dao.edit(value.id, value.title, value.date, value.semesterId) == 1)
                    }
                }
                editing.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                editing.value = value.copy(saving = false, failed = true)
            }
        }
    }
}
