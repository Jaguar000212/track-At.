package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import java.util.UUID

/** Dedicated insert-only bindings for a transaction owned by PlannerRestoreRepository. */
@Dao
interface PlannerRestoreDao {
    @Query("SELECT * FROM Semesters ORDER BY id")
    suspend fun semesters(): List<Semester>

    @Query("SELECT * FROM Subjects ORDER BY id")
    suspend fun subjects(): List<Subject>

    @Query("SELECT * FROM SessionRecords ORDER BY id")
    suspend fun schedule(): List<SessionRecord>

    @Query("SELECT * FROM AttendanceRecords ORDER BY id")
    suspend fun attendance(): List<AttendanceRecord>

    @Query("SELECT * FROM BoardItems ORDER BY id")
    suspend fun board(): List<BoardItem>

    @Query("SELECT * FROM Exams ORDER BY id")
    suspend fun exams(): List<Exam>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExams(rows: List<Exam>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSemesters(rows: List<Semester>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubjects(rows: List<Subject>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSchedule(rows: List<SessionRecord>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttendance(rows: List<AttendanceRecord>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBoard(rows: List<BoardItem>)

    @Query("UPDATE Subjects SET totalClasses = :total, attendedClasses = :attended WHERE id = :subjectId")
    suspend fun updateDerivedTotals(subjectId: UUID, total: Int, attended: Int): Int
}
