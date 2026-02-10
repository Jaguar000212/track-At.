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

    @Query(
        """
    SELECT 
        SR.id AS sr_id,
        SR.subjectId AS sr_subjectId,
        SR.dayOfWeek AS sr_dayOfWeek,
        SR.startDate AS sr_startDate,

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

    FROM SessionRecords AS SR
    INNER JOIN Subjects AS S
        ON SR.subjectId = S.id
"""
    )
    fun getSessionsWithSubjects(): Flow<List<SessionsWithSubject>>

}
