package com.jaguar.attendancetracker.ui.imExport

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.backend.repositories.PreparedPlannerRestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.CharacterCodingException
import javax.inject.Inject

internal enum class PlannerPicker { OPEN, CREATE }

internal sealed interface PlannerBackupState {
    data object Idle : PlannerBackupState
    data class Picking(val kind: PlannerPicker) : PlannerBackupState
    data class Busy(@param:StringRes val message: Int) : PlannerBackupState
    data class Review(val counts: List<PlannerBackupCounts>, val includesExams: Boolean) : PlannerBackupState
    data class Notice(@param:StringRes val message: Int, val isError: Boolean = false) : PlannerBackupState
}

internal data class PlannerBackupCounts(
    @param:StringRes val label: Int,
    val insert: Int,
    val identical: Int,
    val conflict: Int,
    val blocked: Int,
)

@HiltViewModel
internal class PlannerBackupViewModel @Inject constructor(
    database: AttendanceDatabase,
    @param:ApplicationContext context: Context,
) : ViewModel() {
    private val resolver = context.contentResolver
    private val files = PlannerBackupFiles(PlannerRestoreRepository(database), PlannerBackupVersion.V2)
    private val state = MutableStateFlow<PlannerBackupState>(PlannerBackupState.Idle)
    val uiState = state.asStateFlow()
    private var reading: Job? = null
    private var readingRequest: Any? = null

    // Never store a preview token, URI or student records in saved state, logging or analytics.
    private var pending: PreparedPlannerRestore? = null

    fun beginPicker(kind: PlannerPicker): Boolean {
        val current = state.value
        if (current !is PlannerBackupState.Idle && current !is PlannerBackupState.Notice) return false
        state.value = PlannerBackupState.Picking(kind)
        return true
    }

    fun pickerFailed(kind: PlannerPicker) {
        if (state.value == PlannerBackupState.Picking(kind)) {
            state.value = PlannerBackupState.Notice(R.string.planner_backup_picker_failed, isError = true)
        }
    }

    fun pickerResult(kind: PlannerPicker, uri: Uri?) {
        if (state.value != PlannerBackupState.Picking(kind)) return
        if (uri == null) {
            state.value = PlannerBackupState.Idle
            return
        }
        if (uri.scheme != "content") {
            pickerFailed(kind)
            return
        }
        when (kind) {
            PlannerPicker.OPEN -> read(uri)
            PlannerPicker.CREATE -> save(uri)
        }
    }

    private fun read(uri: Uri) {
        state.value = PlannerBackupState.Busy(R.string.planner_backup_reading)
        val request = Any()
        readingRequest = request
        reading = viewModelScope.launch {
            try {
                val checked = files.read { resolver.openInputStream(uri) }
                currentCoroutineContext().ensureActive()
                if (readingRequest !== request) return@launch
                pending = checked
                val includesExams = checked.version == PlannerBackupVersion.V2
                state.value = PlannerBackupState.Review(checked.preview.toCounts(includesExams), includesExams)
            } catch (e: CancellationException) {
                throw e
            } catch (_: CharacterCodingException) {
                currentCoroutineContext().ensureActive()
                if (readingRequest !== request) return@launch
                invalidBackup()
            } catch (_: IllegalArgumentException) {
                currentCoroutineContext().ensureActive()
                if (readingRequest !== request) return@launch
                invalidBackup()
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (readingRequest !== request) return@launch
                state.value = PlannerBackupState.Notice(R.string.planner_backup_read_failed, isError = true)
            } finally {
                if (readingRequest === request) {
                    readingRequest = null
                    reading = null
                }
            }
        }
    }

    private fun invalidBackup() {
        state.value = PlannerBackupState.Notice(R.string.planner_backup_invalid, isError = true)
    }

    private fun save(uri: Uri) {
        state.value = PlannerBackupState.Busy(R.string.planner_backup_writing)
        viewModelScope.launch {
            try {
                files.write { resolver.openOutputStream(uri, "wt") }
                state.value = PlannerBackupState.Notice(R.string.planner_backup_saved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = PlannerBackupState.Notice(R.string.planner_backup_write_failed, isError = true)
            }
        }
    }

    fun discardPreview() {
        val current = state.value
        if (current is PlannerBackupState.Review ||
            current == PlannerBackupState.Busy(R.string.planner_backup_reading)
        ) {
            readingRequest = null
            reading?.cancel()
            reading = null
            pending = null
            state.value = PlannerBackupState.Idle
        }
    }

    fun apply() {
        val preview = state.value as? PlannerBackupState.Review ?: return
        if (preview.counts.none { it.insert > 0 }) return
        val confirmed = pending ?: return
        // Remove the handle and disable the action synchronously so multiple taps cannot apply twice.
        pending = null
        state.value = PlannerBackupState.Busy(R.string.planner_backup_applying)
        viewModelScope.launch {
            try {
                val result = confirmed.apply()
                val message = when (result) {
                    PlannerRestoreResult.APPLIED -> R.string.planner_backup_applied
                    PlannerRestoreResult.NOTHING_TO_APPLY -> R.string.planner_backup_no_changes
                    PlannerRestoreResult.STALE_PREVIEW -> R.string.planner_backup_stale
                }
                state.value = PlannerBackupState.Notice(message)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.value = PlannerBackupState.Notice(R.string.planner_backup_restore_failed, isError = true)
            }
        }
    }
}

private fun PlannerMergePreview.toCounts(includesExams: Boolean): List<PlannerBackupCounts> = buildList {
    add(semesters.counts(R.string.planner_backup_semesters))
    add(subjects.counts(R.string.planner_backup_subjects))
    add(schedule.counts(R.string.planner_backup_schedule))
    add(attendance.counts(R.string.planner_backup_attendance))
    add(board.counts(R.string.planner_backup_board))
    if (includesExams) add(exams.counts(R.string.exams_title))
}

private fun List<PlannerMergeRow<*>>.counts(@StringRes label: Int): PlannerBackupCounts = PlannerBackupCounts(
    label = label,
    insert = count { it.outcome == PlannerMergeOutcome.INSERT },
    identical = count { it.outcome == PlannerMergeOutcome.UNCHANGED },
    conflict = count { it.outcome == PlannerMergeOutcome.KEEP_LOCAL_CONFLICT },
    blocked = count { it.outcome == PlannerMergeOutcome.BLOCKED_DEPENDENCY },
)
