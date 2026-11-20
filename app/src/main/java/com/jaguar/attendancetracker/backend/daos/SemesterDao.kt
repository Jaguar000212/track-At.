package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SemesterWithSubjects
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface SemesterDao {
    @Transaction
    @Query("SELECT * FROM Semesters WHERE isArchived = 0 ORDER BY startDate DESC")
    fun getActiveSemestersWithSubjects(): Flow<List<SemesterWithSubjects>>

    @Transaction
    @Query("SELECT * FROM Semesters WHERE isArchived = 1 ORDER BY startDate DESC, id ASC")
    fun getArchivedSemestersWithSubjects(): Flow<List<SemesterWithSubjects>>

    @Query("SELECT * FROM Semesters")
    fun getAll(): Flow<List<Semester>>

    @androidx.room.Upsert
    suspend fun upsertAll(semesters: List<Semester>)

    @Insert
    suspend fun insert(semester: Semester)

    @Query("""
        UPDATE Semesters SET name = :name, startDate = :startDate, endDate = :endDate,
        targetAttendance = :targetAttendance WHERE id = :id AND isArchived = 0
    """)
    suspend fun updateActiveDetails(id: UUID, name: String, startDate: LocalDate, endDate: LocalDate?, targetAttendance: Int)

    @Query("UPDATE Semesters SET isArchived = :archived WHERE id = :id AND isArchived != :archived")
    suspend fun setArchived(id: UUID, archived: Boolean)

    @Query("DELETE FROM Semesters WHERE id = :id AND isArchived = 0")
    suspend fun deleteActive(id: UUID)
}
