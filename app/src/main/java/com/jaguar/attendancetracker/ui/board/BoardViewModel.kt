package com.jaguar.attendancetracker.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.BoardChecklistItem
import com.jaguar.attendancetracker.backend.entities.MAX_BOARD_CHECKLIST_TEXT
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

internal sealed interface BoardState {
    data object Loading : BoardState
    data object Unavailable : BoardState
    data class Ready(val items: List<BoardItem>) : BoardState
}

@HiltViewModel
internal class BoardViewModel @Inject constructor(private val database: AttendanceDatabase) : ViewModel() {
    private val dao = database.boardItemDao()

    val state = dao.observeAll().map<List<BoardItem>, BoardState> { BoardState.Ready(it) }
        .catch { emit(BoardState.Unavailable) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoardState.Loading)

    suspend fun save(id: UUID?, title: String, dueDate: LocalDate?): Boolean = attempt {
        if (title.isBlank()) false
        else if (id == null) {
            dao.insert(BoardItem(title = title, dueDate = dueDate))
            true
        } else dao.editDetails(id, title, dueDate) == 1
    }

    suspend fun setDone(id: UUID, done: Boolean): Boolean = attempt { dao.setDone(id, done) == 1 }

    suspend fun setPinned(id: UUID, pinned: Boolean): Boolean = attempt { dao.setPinned(id, pinned) == 1 }

    fun observeChecklist(parent: UUID): Flow<List<BoardChecklistItem>> = dao.observeChecklist(parent)

    suspend fun saveChecklist(parent: UUID, id: UUID?, text: String): Boolean = attempt {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_BOARD_CHECKLIST_TEXT) return@attempt false
        if (id != null) return@attempt dao.editChecklistItem(parent, id, trimmed) == 1
        database.withTransaction {
            val existing = dao.checklistItems(parent)
            val last = existing.lastOrNull()?.position
            val newPosition = if (last == Int.MAX_VALUE) {
                // A valid imported extreme must not wrap. Keep exactly the visible order,
                // including stable UUID ties, and normalize only with the confirmed append.
                existing.forEachIndexed { index, item ->
                    if (item.position != index) check(dao.setChecklistPosition(parent, item.id, index) == 1)
                }
                existing.size
            } else (last ?: -1) + 1
            dao.insertChecklistItem(BoardChecklistItem(boardItemId = parent, position = newPosition, text = trimmed))
        }
        true
    }

    suspend fun setChecklistDone(parent: UUID, id: UUID, done: Boolean): Boolean =
        attempt { dao.setChecklistDone(parent, id, done) == 1 }

    private suspend fun attempt(action: suspend () -> Boolean): Boolean = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
