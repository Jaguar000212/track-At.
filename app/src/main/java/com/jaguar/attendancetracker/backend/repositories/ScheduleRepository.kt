package com.jaguar.attendancetracker.backend.repositories

import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import com.jaguar.attendancetracker.backend.entities.Subject
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class ScheduleRepository @Inject constructor(
    private val sessionRecordDao: SessionRecordDao,
    private val subjectDao: SubjectDao
) {
    fun getAllSessionRecords(): Flow<List<SessionRecord>> = sessionRecordDao.getAllRecords()

    suspend fun getSessionsByDay(day: Int): List<SessionRecord> =
        sessionRecordDao.getSessionsByDay(day)

    fun getSessionsWithSubjects(): Flow<List<SessionsWithSubject>> =
        sessionRecordDao.getSessionsWithSubjects()

    suspend fun getSubjectSessionRecords(subjectId: UUID): List<SessionRecord> =
        sessionRecordDao.getSubjectSessionRecords(subjectId)

    suspend fun addSession(sessionRecord: SessionRecord) {
        sessionRecordDao.insert(sessionRecord)
    }

    suspend fun deleteSession(sessionRecord: SessionRecord) {
        sessionRecordDao.delete(sessionRecord)
    }

    suspend fun cancelScheduling(subject: Subject) {
        subjectDao.update(subject)
        sessionRecordDao.deleteBySubject(subject.id)
    }

    fun getScheduledSubjects(): Flow<List<Subject>> = subjectDao.getScheduledSubjects()
    fun getSchedulableSubjects(): Flow<List<Subject>> = subjectDao.getSchedulableSubjects()

    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAllSubjects()
    suspend fun getSubject(subjectId: UUID): Subject = subjectDao.getSubject(subjectId)
    suspend fun addSubject(subject: Subject) {
        subjectDao.insert(subject)
    }

    suspend fun editSubject(subject: Subject) {
        subjectDao.update(subject)
    }

    suspend fun deleteSubject(subject: Subject) {
        subjectDao.delete(subject)
    }
}