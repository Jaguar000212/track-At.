package com.jaguar.attendancetracker.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.BoardItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
internal class BoardViewModel @Inject constructor(database: AttendanceDatabase) : ViewModel() {
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

    private suspend fun attempt(action: suspend () -> Boolean): Boolean = try {
        action()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
