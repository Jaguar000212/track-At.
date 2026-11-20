package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.jaguar.attendancetracker.backend.entities.Subject
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SubjectDao {
    @Upsert
    suspend fun upsertAll(subjects: List<Subject>)

    @Insert
    suspend fun insert(subject: Subject)

    @Update
    suspend fun update(subject: Subject)

    @Delete
    suspend fun delete(subject: Subject)

    @Query("SELECT id, name, color FROM Subjects WHERE semesterId = :term AND id IN (:ids)")
    suspend fun rolloverTemplates(term: UUID, ids: List<UUID>): List<RolloverTemplateRow>

    @Query("SELECT * FROM Subjects")
    fun getAll(): Flow<List<Subject>>

    @Query("SELECT * FROM Subjects WHERE id = :subjectId")
    suspend fun getOne(subjectId: UUID): Subject

    @Query("SELECT * FROM Subjects WHERE id IN (SELECT subjectId FROM SessionRecords)")
    fun getScheduled(): Flow<List<Subject>>

    @Query("""
        SELECT s.* FROM Subjects s JOIN Semesters t ON t.id = s.semesterId
        WHERE s.isEnded = 0 AND t.isArchived = 0 ORDER BY s.name, s.id
    """)
    fun getExtraClassSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM Subjects WHERE isEnded = 0")
    fun getSchedulable(): Flow<List<Subject>>
}
data class RolloverTemplateRow(val id: UUID, val name: String, val color: String)
