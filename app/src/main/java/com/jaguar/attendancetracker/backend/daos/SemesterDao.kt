package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SemesterWithSubjects
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Transaction
    @Query("SELECT * FROM Semesters WHERE isArchived = 0 ORDER BY startDate DESC")
    fun getActiveSemestersWithSubjects(): Flow<List<SemesterWithSubjects>>

    @Query("SELECT * FROM Semesters")
    fun getAll(): Flow<List<Semester>>

    @androidx.room.Upsert
    suspend fun upsertAll(semesters: List<Semester>)

    @Insert
    suspend fun insert(semester: Semester)

    @Update
    suspend fun update(semester: Semester)

    @Delete
    suspend fun delete(semester: Semester)
}
