package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Query
import java.time.LocalDate

data class CampusBriefAttendanceCounts(val marked: Long, val unmarked: Long)

/** Aggregate-only reads: no titles, names, or full attendance rows reach the launcher code. */
@Dao
interface CampusBriefDao {
    @Query("""
        SELECT COUNT(CASE WHEN status IN ('PRESENT', 'ABSENT') THEN 1 END) AS marked,
               COUNT(CASE WHEN status IS NULL THEN 1 END) AS unmarked
        FROM AttendanceRecords WHERE date = :date
    """)
    suspend fun attendance(date: LocalDate): CampusBriefAttendanceCounts

    @Query("SELECT COUNT(*) FROM BoardItems WHERE isDone = 0 AND dueDate BETWEEN :first AND :last")
    suspend fun dueTasks(first: LocalDate, last: LocalDate): Long

    @Query("SELECT COUNT(*) FROM Exams WHERE date BETWEEN :first AND :last")
    suspend fun datedExams(first: LocalDate, last: LocalDate): Long
}
