package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jaguar.attendancetracker.backend.entities.BoardChecklistItem
import com.jaguar.attendancetracker.backend.entities.BoardItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface BoardItemDao {
    @Insert
    suspend fun insert(item: BoardItem)

    @Update
    suspend fun update(item: BoardItem)

    @Query("UPDATE BoardItems SET title = :title, dueDate = :dueDate WHERE id = :id")
    suspend fun editDetails(id: UUID, title: String, dueDate: LocalDate?): Int

    @Query("UPDATE BoardItems SET isDone = :isDone WHERE id = :id")
    suspend fun setDone(id: UUID, isDone: Boolean): Int

    @Query("UPDATE BoardItems SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: UUID, isPinned: Boolean): Int

    @Insert
    suspend fun insertChecklistItem(item: BoardChecklistItem)

    @Query("SELECT * FROM BoardChecklistItems WHERE boardItemId = :boardItemId ORDER BY position, id")
    suspend fun checklistItems(boardItemId: UUID): List<BoardChecklistItem>

    @Query("SELECT * FROM BoardChecklistItems WHERE boardItemId = :parent ORDER BY position, id")
    fun observeChecklist(parent: UUID): Flow<List<BoardChecklistItem>>

    @Query("UPDATE BoardChecklistItems SET position = :position WHERE id = :id AND boardItemId = :parent")
    suspend fun setChecklistPosition(parent: UUID, id: UUID, position: Int): Int

    @Query("UPDATE BoardChecklistItems SET text = :text WHERE id = :id AND boardItemId = :parent")
    suspend fun editChecklistItem(parent: UUID, id: UUID, text: String): Int

    @Query("UPDATE BoardChecklistItems SET isDone = :done WHERE id = :id AND boardItemId = :parent")
    suspend fun setChecklistDone(parent: UUID, id: UUID, done: Boolean): Int

    @Query("SELECT * FROM BoardItems ORDER BY id")
    fun observeAll(): Flow<List<BoardItem>>
}
