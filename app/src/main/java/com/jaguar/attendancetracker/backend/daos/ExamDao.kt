package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.jaguar.attendancetracker.backend.entities.Exam
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface ExamDao {
    @Insert
    suspend fun insert(exam: Exam)

    @Query("UPDATE Exams SET title = :title, date = :date, semesterId = :semesterId WHERE id = :id")
    suspend fun edit(id: UUID, title: String, date: LocalDate?, semesterId: UUID?): Int

    @Query("SELECT * FROM Exams ORDER BY id")
    fun observeAll(): Flow<List<Exam>>
}
