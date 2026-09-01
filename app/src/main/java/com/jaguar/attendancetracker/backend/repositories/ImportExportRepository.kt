package com.jaguar.attendancetracker.backend.repositories

import androidx.room.withTransaction
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.AttendanceRecordDao
import com.jaguar.attendancetracker.backend.daos.SemesterDao
import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import javax.inject.Inject

class ImportExportRepository @Inject constructor(
    private val database: AttendanceDatabase,
    private val attendanceRecordDao: AttendanceRecordDao,
    private val subjectDao: SubjectDao,
    private val sessionRecordDao: SessionRecordDao,
    private val semesterDao: SemesterDao
) {
    fun getAllSemesters() = semesterDao.getAll()
    fun getAllSubjects() = subjectDao.getAll()
    fun getAllSessionRecords() = sessionRecordDao.getAll()
    fun getAllAttendanceRecords() = attendanceRecordDao.getAll()

    suspend fun upsertDataInTransaction(
        semesters: List<com.jaguar.attendancetracker.backend.entities.Semester>?,
        subjects: List<com.jaguar.attendancetracker.backend.entities.Subject>?,
        sessions: List<com.jaguar.attendancetracker.backend.entities.SessionRecord>?,
        attendance: List<com.jaguar.attendancetracker.backend.entities.AttendanceRecord>?
    ) {
        database.withTransaction {
            semesterDao.upsertAll(semesters ?: emptyList())
            subjectDao.upsertAll(subjects ?: emptyList())
            sessionRecordDao.upsertAll(sessions ?: emptyList())
            attendanceRecordDao.upsertAll(attendance ?: emptyList())
        }
    }
}
