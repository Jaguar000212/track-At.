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
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

@Dao
interface AttendanceRecordDao {
    @Upsert
    suspend fun upsertAll(records: List<AttendanceRecord>)

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

    @Transaction
    @Query("SELECT * FROM AttendanceRecords WHERE date = :date")
    fun getAttendanceWithSubjects(date: LocalDate): Flow<List<AttendanceWithSubject>>

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status IN ('PRESENT', 'ABSENT')")
    suspend fun countTotalClasses(subjectId: UUID): Int

    @Query("SELECT count(*) FROM AttendanceRecords WHERE subjectId = :subjectId AND status = 'PRESENT'")
    suspend fun countAttendedClasses(subjectId: UUID): Int
}