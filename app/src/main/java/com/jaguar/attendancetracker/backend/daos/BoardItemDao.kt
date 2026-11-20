package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jaguar.attendancetracker.backend.entities.BoardItem
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardItemDao {
    @Insert
    suspend fun insert(item: BoardItem)

    @Update
    suspend fun update(item: BoardItem)

    @Query("SELECT * FROM BoardItems ORDER BY id")
    fun observeAll(): Flow<List<BoardItem>>
}
