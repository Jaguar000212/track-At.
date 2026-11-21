package com.jaguar.attendancetracker.ui.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.reminders.ExamReminderCoordinator
import com.jaguar.attendancetracker.backend.reminders.ExamReminderPolicy
import com.jaguar.attendancetracker.backend.reminders.ReminderChoice
import com.jaguar.attendancetracker.backend.reminders.ReminderEnable
import com.jaguar.attendancetracker.backend.reminders.ReminderExam
import com.jaguar.attendancetracker.backend.reminders.ReminderTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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
    val originalDate: LocalDate? = null,
)

@HiltViewModel
internal class ExamsViewModel @Inject constructor(
    private val db: AttendanceDatabase,
    private val reminders: ExamReminderCoordinator,
) : ViewModel() {
    val state = combine(db.examDao().observeAll(), db.semesterDao().getAll()) { exams, semesters ->
        val ready: ExamsState = ExamsState.Ready(exams, semesters)
        ready
    }.catch { emit(ExamsState.Unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExamsState.Loading)

    // Navigation-scoped memory only: rotation retains it; no disk, logs or SavedState.
    private val editing = MutableStateFlow<ExamDraft?>(null)
    val draft = editing.asStateFlow()

    private val reminderState = MutableStateFlow(ReminderUiState())
    val reminder = reminderState.asStateFlow()
    private var permissionAttempt: ReminderPermissionAttempt? = null
    private var refreshing: Job? = null

    init { viewModelScope.launch { reminders.revisions.collect { refreshReminders() } } }

    fun refreshReminders() {
        val ids = (state.value as? ExamsState.Ready)?.exams.orEmpty().filter { it.date != null }.map { it.id }
        refreshing?.cancel()
        refreshing = viewModelScope.launch {
            try {
                val statuses = reminders.statuses(ids)
                reminderState.value = reminderState.value.copy(statuses = statuses, statusUnavailable = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reminderState.value = reminderState.value.copy(statuses = emptyMap(), statusUnavailable = true)
            }
        }
    }

    fun openReminder(exam: Exam) {
        val ui = reminderState.value
        if (exam.date == null || editing.value != null || ui.editor != null || ui.opening) return
        reminderState.value = ui.copy(notice = null, opening = true)
        viewModelScope.launch {
            try {
                val item = ReminderExam(exam.id, exam.date)
                val existing = reminders.statuses(listOf(exam.id))[exam.id]?.takeIf { it.examDate == item.date }?.time
                val zone = existing?.zone ?: ZoneId.systemDefault()
                val now = Instant.now()
                val choice = existing?.let { ExamReminderPolicy.choose(item, it.local, zone, now) }
                    ?: ExamReminderPolicy.suggest(item, zone, now)
                val local = (choice as? ReminderChoice.Available)?.time?.local ?: existing?.local
                setReminderEditor(ReminderEditorState(
                    UUID.randomUUID(), item, zone, local?.toLocalDate(), local?.toLocalTime(),
                    choice, replacing = existing != null,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                reminderState.value = reminderState.value.copy(notice = ReminderFeedback.FAILED)
            } finally {
                reminderState.value = reminderState.value.copy(opening = false)
                refreshReminders()
            }
        }
    }

    fun changeReminderDate(value: LocalDate) {
        val editor = reminderState.value.editor ?: return
        if (!editor.working) setReminderEditor(editor.change(value, editor.clock, Instant.now()).copy(id = UUID.randomUUID()))
    }

    fun changeReminderClock(value: LocalTime) {
        val editor = reminderState.value.editor ?: return
        if (!editor.working) setReminderEditor(editor.change(editor.date, value, Instant.now()).copy(id = UUID.randomUUID()))
    }

    fun closeReminder() {
        if (reminderState.value.editor?.working == true) return
        // Keep an outstanding launcher token until its callback; it cannot authorize a new editor.
        setReminderEditor(null)
    }

    fun requestReminderEnable(needsPermission: Boolean): Boolean {
        val editor = reminderState.value.editor ?: return false
        val time = editor.confirmedTime() ?: return false
        if (editor.working || permissionAttempt != null) return false
        val fresh = editor.change(time.local.toLocalDate(), time.local.toLocalTime(), Instant.now())
        if (fresh.confirmedTime() != time) {
            setReminderEditor(fresh)
            return false
        }
        if (needsPermission) {
            if (editor.permissionDenied) {
                setReminderEditor(editor.copy(notice = ReminderFeedback.PERMISSION_DENIED))
                return false
            }
            permissionAttempt = ReminderPermissionAttempt(editor.id, editor.exam, time)
            reminderState.value = reminderState.value.copy(permissionWaiting = true)
            return true
        }
        enableReminder(editor, time)
        return false
    }

    fun reminderPermissionResult(granted: Boolean) {
        val attempt = permissionAttempt ?: return
        permissionAttempt = null
        reminderState.value = reminderState.value.copy(permissionWaiting = false)
        val editor = reminderState.value.editor ?: return
        if (!attempt.matches(editor) || editor.working) return
        if (granted) enableReminder(editor, attempt.time)
        else setReminderEditor(editor.copy(permissionDenied = true, notice = ReminderFeedback.PERMISSION_DENIED))
    }

    private fun enableReminder(editor: ReminderEditorState, time: ReminderTime) {
        setReminderEditor(editor.copy(working = true, notice = null))
        viewModelScope.launch {
            try {
                when (reminders.enable(editor.exam, time)) {
                    ReminderEnable.SCHEDULED -> setReminderEditor(null)
                    ReminderEnable.NOTIFICATIONS_OFF ->
                        setReminderEditor(editor.copy(notice = ReminderFeedback.NOTIFICATIONS_OFF))
                    ReminderEnable.STALE_OR_INVALID ->
                        setReminderEditor(editor.change(time.local.toLocalDate(), time.local.toLocalTime(), Instant.now()).copy(notice = ReminderFeedback.STALE))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                setReminderEditor(editor.copy(notice = ReminderFeedback.FAILED))
            } finally { refreshReminders() }
        }
    }

    fun disableReminder() {
        val editor = reminderState.value.editor ?: return
        if (editor.working || permissionAttempt != null) return
        setReminderEditor(editor.copy(working = true, notice = null))
        viewModelScope.launch {
            try {
                reminders.disable(editor.exam.id)
                reminderState.value = reminderState.value.copy(editor = null, notice = ReminderFeedback.DISABLED)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                setReminderEditor(editor.copy(notice = ReminderFeedback.FAILED))
            } finally { refreshReminders() }
        }
    }

    private fun setReminderEditor(editor: ReminderEditorState?) {
        reminderState.value = reminderState.value.copy(editor = editor)
    }

    fun edit(exam: Exam?) {
        if (editing.value != null || reminderState.value.editor != null || reminderState.value.opening) return
        editing.value = exam?.let { ExamDraft(it.id, it.title, it.date, it.semesterId, originalDate = it.date) } ?: ExamDraft()
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
                suspend fun saveRow() = db.withTransaction {
                    // Room validates the FK; a concurrently removed semester never gets silently
                    // changed into a different selected value by saving the editor.
                    val dao = db.examDao()
                    if (value.id == null) {
                        dao.insert(Exam(title = value.title, date = value.date, semesterId = value.semesterId))
                    } else {
                        check(dao.edit(value.id, value.title, value.date, value.semesterId) == 1)
                    }
                }
                if (value.id == null) saveRow()
                else {
                    reminders.changeExamDate(value.id, value.date, ::saveRow)
                    if (value.originalDate != value.date) {
                        reminderState.value = reminderState.value.copy(notice = ReminderFeedback.DATE_CHANGED)
                    }
                }
                editing.value = null
                refreshReminders()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                editing.value = value.copy(saving = false, failed = true)
            }
        }
    }
}
