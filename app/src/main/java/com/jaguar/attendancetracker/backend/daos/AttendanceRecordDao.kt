package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.enums.ClassType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface AttendanceRecordDao {
    @Upsert
    suspend fun upsertAll(records: List<AttendanceRecord>)

    @Insert
    suspend fun insert(record: AttendanceRecord)

    @Query("""
        SELECT EXISTS(SELECT 1 FROM Subjects s JOIN Semesters t ON t.id = s.semesterId
        WHERE s.id = :subjectId AND t.isArchived = 0 AND (:requireOngoing = 0 OR s.isEnded = 0))
    """)
    suspend fun isOperationalSubjectEligible(subjectId: UUID, requireOngoing: Boolean): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM AttendanceRecords WHERE date = :date AND sessionId = :sessionId)")
    suspend fun hasMaterializedSession(date: LocalDate, sessionId: UUID): Boolean

    /** Serialize the eligibility check with archive updates and the new record insert. */
    @Transaction
    suspend fun insertOperational(record: AttendanceRecord): Boolean {
        val extra = record.classType == ClassType.EXTRA
        if (!isOperationalSubjectEligible(record.subjectId, requireOngoing = extra)) return false
        val session = record.sessionId
        if (!extra && session != null && hasMaterializedSession(record.date, session)) return false
        insert(record)
        return true
    }

    @Update
    suspend fun update(record: AttendanceRecord)

    @Delete
    suspend fun delete(record: AttendanceRecord)

    @Query("DELETE FROM AttendanceRecords WHERE subjectId = :subjectId AND status IS NULL")
    suspend fun deleteBySubject(subjectId: UUID)

    @Query("SELECT * FROM AttendanceRecords")
    fun getAll(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM AttendanceRecords WHERE date BETWEEN :first AND :last ORDER BY date, id")
    suspend fun readDateWindow(first: LocalDate, last: LocalDate): List<AttendanceRecord>

    @Query("SELECT * FROM AttendanceRecords WHERE subjectId = :subjectId ORDER BY date DESC")
    suspend fun getBySubject(subjectId: UUID): List<AttendanceRecord>

    @Query("SELECT * FROM AttendanceRecords WHERE date = :date")
    suspend fun getByDate(date: LocalDate): List<AttendanceRecord>

    @Transaction
    @Query("SELECT * FROM AttendanceRecords WHERE date = :date")
    fun getWithSubjects(date: LocalDate): Flow<List<AttendanceWithSubject>>

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status IN ('PRESENT', 'ABSENT')")
    suspend fun countTotalClasses(subjectId: UUID): Int

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status = 'PRESENT'")
    suspend fun countAttendedClasses(subjectId: UUID): Int
}