package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM BoardItems ORDER BY id")
    fun observeAll(): Flow<List<BoardItem>>
}
