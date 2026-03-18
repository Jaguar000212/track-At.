package com.jaguar.attendancetracker.backend.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SessionRecordDao {
    @Insert
    suspend fun insert(record: SessionRecord)

    @Update
    suspend fun update(record: SessionRecord)

    @Update
    suspend fun updateAll(records: List<SessionRecord>)

    @Delete
    suspend fun delete(record: SessionRecord)

    @Query("DELETE FROM SessionRecords where subjectId = :subjectId")
    suspend fun deleteBySubject(subjectId: UUID)

    @Query("SELECT * FROM SessionRecords")
    fun getAllRecords(): Flow<List<SessionRecord>>

    @Query("SELECT * FROM SessionRecords WHERE subjectId = :subjectId order by dayOfWeek")
    suspend fun getSubjectSessionRecords(subjectId: UUID): List<SessionRecord>

    @Query("SELECT * FROM SessionRecords WHERE dayOfWeek = :day")
    suspend fun getSessionsByDay(day: Int): List<SessionRecord>

    @Query("SELECT * FROM SessionRecords ORDER BY dayOfWeek, orderNo")
    fun getSessionsWithSubjects(): Flow<List<SessionsWithSubject>>
}
