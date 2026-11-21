package com.jaguar.attendancetracker.ui.rollover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.repositories.PreparedTermRollover
import com.jaguar.attendancetracker.backend.repositories.RolloverInputIssue
import com.jaguar.attendancetracker.backend.repositories.RolloverPreparation
import com.jaguar.attendancetracker.backend.repositories.RolloverPreview
import com.jaguar.attendancetracker.backend.repositories.RolloverResult
import com.jaguar.attendancetracker.backend.repositories.RolloverSource
import com.jaguar.attendancetracker.backend.repositories.RolloverTermDetails
import com.jaguar.attendancetracker.backend.repositories.TermRolloverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal data class RolloverDraft(
    val details: RolloverTermDetails = RolloverTermDetails("", LocalDate.now(), null, 75),
    val selected: Set<UUID> = emptySet(),
    val selectedSlots: Set<UUID> = emptySet(),
)

internal enum class RolloverNotice { UNAVAILABLE, INVALID_TERM, INVALID_SELECTION, INVALID_SLOT_DATES, STALE, READ_FAILED, WRITE_FAILED, TIMETABLE_FULL }
internal data class RolloverState(
    val source: RolloverSource? = null,
    val draft: RolloverDraft = RolloverDraft(),
    val loading: Boolean = true,
    val working: Boolean = false,
    val review: RolloverPreview? = null,
    val notice: RolloverNotice? = null,
    val created: Boolean = false,
)

@HiltViewModel
internal class RolloverViewModel @Inject constructor(
    private val repository: TermRolloverRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val id = savedStateHandle.get<String>("termId")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }
    private val mutable = MutableStateFlow(RolloverState())
    val state = mutable.asStateFlow()
    private var pending: PreparedTermRollover? = null

    init { viewModelScope.launch { reload() } }

    private suspend fun reload(notice: RolloverNotice? = null) {
        mutable.value = mutable.value.copy(loading = true, review = null, notice = notice)
        try {
            val source = id?.let { repository.readSource(it) }
            val current = mutable.value
            val allowed = source?.courses?.map { it.id }?.toSet().orEmpty()
            val selected = current.draft.selected.intersect(allowed)
            val slots = source?.slots.orEmpty().filter { it.row.subjectId in selected }.mapTo(HashSet()) { it.row.id }
            mutable.value = current.copy(source = source, loading = false, working = false,
                draft = current.draft.copy(selected = selected, selectedSlots = current.draft.selectedSlots.intersect(slots)),
                notice = if (source == null) RolloverNotice.UNAVAILABLE else notice)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(source = null, loading = false, working = false,
                notice = RolloverNotice.READ_FAILED)
        }
    }

    fun change(draft: RolloverDraft) {
        val current = mutable.value
        if (!current.loading && !current.working && current.review == null && !current.created) {
            val allowed = current.source?.slots.orEmpty().filter { it.row.subjectId in draft.selected }
                .mapTo(HashSet()) { it.row.id }
            mutable.value = current.copy(draft = draft.copy(selectedSlots = draft.selectedSlots.intersect(allowed)), notice = null)
        }
    }

    fun review() {
        val current = mutable.value
        val source = current.source ?: return
        val termId = id ?: return
        if (current.loading || current.working || current.review != null || current.created) return
        mutable.value = current.copy(working = true, notice = null)
        viewModelScope.launch {
            try {
                when (val prepared = repository.prepare(termId, current.draft.details,
                    source.courses.filter { it.id in current.draft.selected }.map { it.id },
                    source.slots.filter { it.row.id in current.draft.selectedSlots })) {
                    is RolloverPreparation.Ready -> {
                        pending = prepared.confirmation
                        mutable.value = current.copy(working = false, review = prepared.confirmation.preview, notice = null)
                    }
                    is RolloverPreparation.Invalid -> when (prepared.issue) {
                        RolloverInputIssue.SOURCE_CHANGED -> reload(RolloverNotice.STALE)
                        RolloverInputIssue.INVALID_TERM -> mutable.value =
                            current.copy(working = false, notice = RolloverNotice.INVALID_TERM)
                        RolloverInputIssue.INVALID_SELECTION -> mutable.value =
                            current.copy(working = false, notice = RolloverNotice.INVALID_SELECTION)
                        RolloverInputIssue.INVALID_SLOT_DATES -> mutable.value =
                            current.copy(working = false, notice = RolloverNotice.INVALID_SLOT_DATES)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = current.copy(working = false, notice = RolloverNotice.READ_FAILED)
            }
        }
    }

    fun editAgain() {
        val current = mutable.value
        if (current.working) return
        pending = null
        mutable.value = current.copy(review = null, notice = null)
    }

    fun confirm() {
        val current = mutable.value
        val confirmation = pending ?: return
        if (current.working || current.created || current.review == null) return
        pending = null
        mutable.value = current.copy(working = true, notice = null)
        viewModelScope.launch {
            try {
                when (confirmation.apply()) {
                    RolloverResult.CREATED, RolloverResult.ALREADY_CREATED ->
                        mutable.value = current.copy(working = false, review = null, notice = null, created = true)
                    RolloverResult.STALE_PREVIEW -> reload(RolloverNotice.STALE)
                    RolloverResult.TIMETABLE_FULL ->
                        mutable.value = current.copy(working = false, review = null, notice = RolloverNotice.TIMETABLE_FULL)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutable.value = current.copy(working = false, review = null, notice = RolloverNotice.WRITE_FAILED)
            }
        }
    }
}
