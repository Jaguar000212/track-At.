package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface AttendanceRecordDao {
    @Insert
    suspend fun insert(record: AttendanceRecord)

    @Update
    suspend fun update(record: AttendanceRecord)

    @Delete
    suspend fun delete(record: AttendanceRecord)

    @Query("DELETE FROM AttendanceRecords WHERE subjectId = :subjectId AND status IS NULL")
    suspend fun deleteBySubject(subjectId: UUID)

    @Query("SELECT * FROM AttendanceRecords")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM AttendanceRecords WHERE subjectId = :subjectId ORDER BY date DESC")
    suspend fun getSubjectAttendanceRecords(subjectId: UUID): List<AttendanceRecord>

    @Query("SELECT * FROM attendancerecords WHERE date = :date")
    suspend fun getAttendanceRecordByDate(date: LocalDate): List<AttendanceRecord>

    @Query(
        """
        SELECT 
            AR.id as a_id,
            AR.subjectId as a_subjectId,
            AR.date as a_date,
            AR.sessionId as a_sessionId,
            AR.status as a_status,
            AR.classType as a_classType,
            S.id AS s_id,
            S.name AS s_name,
            S.semester AS s_semester,
            S.startDate AS s_startDate,
            S.isEnded AS s_isEnded,
            S.color AS s_color,
            S.professor AS s_professor,
            S.roomNo AS s_roomNo,
            S.notes AS s_notes,
            S.totalClasses AS s_totalClasses,
            S.attendedClasses AS s_attendedClasses,
            S.minAttendance AS s_minAttendance
        FROM AttendanceRecords as AR INNER JOIN Subjects AS S ON AR.subjectId = s.id 
        WHERE date = :date
        """
    )
    fun getAttendanceWithSubjects(date: LocalDate): Flow<List<AttendanceWithSubject>>

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status IN ('PRESENT', 'ABSENT')")
    suspend fun countTotalClasses(subjectId: UUID): Int

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status = 'PRESENT'")
    suspend fun countAttendedClasses(subjectId: UUID): Int
}