package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jaguar.attendancetracker.backend.entities.Exam
import kotlinx.coroutines.flow.Flow

@Dao
interface ExamDao {
    @Insert
    suspend fun insert(exam: Exam)

    @Query("SELECT * FROM Exams ORDER BY id")
    fun observeAll(): Flow<List<Exam>>
}
