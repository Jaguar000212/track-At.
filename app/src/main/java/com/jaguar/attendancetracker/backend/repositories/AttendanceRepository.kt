package com.jaguar.attendancetracker.backend.repositories

import com.jaguar.attendancetracker.backend.daos.AttendanceRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AttendanceRepository @Inject constructor(
    private val attendanceRecordDao: AttendanceRecordDao,
    private val subjectDao: SubjectDao,
) {
    fun getAllAttendanceRecords(): Flow<List<AttendanceRecord>> =
        attendanceRecordDao.getAllRecords()

    suspend fun getAttendanceRecordsByDate(date: LocalDate): List<AttendanceRecord> =
        attendanceRecordDao.getAttendanceRecordByDate(date)

    fun getAttendanceWithSubjects(date: LocalDate) =
        attendanceRecordDao.getAttendanceWithSubjects(date)

    suspend fun deleteBySubject(subjectId: UUID) {
        attendanceRecordDao.deleteBySubject(subjectId)
    }

    suspend fun addAttendanceRecord(record: AttendanceRecord) {
        attendanceRecordDao.insert(record)
    }

    private suspend fun updateSubjectStats(subjectId: UUID) {
        val total = attendanceRecordDao.countTotalClasses(subjectId)
        val attended = attendanceRecordDao.countAttendedClasses(subjectId)
        val subject = subjectDao.getSubject(subjectId).copy(
            totalClasses = total,
            attendedClasses = attended
        )
        subjectDao.update(subject)
    }

    suspend fun markPresent(recordWithSubject: AttendanceWithSubject) {
        val record = recordWithSubject.record.copy(status = AttendanceStatus.PRESENT)
        attendanceRecordDao.update(record)
        updateSubjectStats(record.subjectId)
    }

    suspend fun markAbsent(recordWithSubject: AttendanceWithSubject) {
        val record = recordWithSubject.record.copy(status = AttendanceStatus.ABSENT)
        attendanceRecordDao.update(record)
        updateSubjectStats(record.subjectId)
    }

    suspend fun markCancelled(recordWithSubject: AttendanceWithSubject) {
        if (recordWithSubject.record.classType == ClassType.REGULAR) {
            val record = recordWithSubject.record.copy(status = AttendanceStatus.CANCELLED)
            attendanceRecordDao.update(record)
        } else attendanceRecordDao.delete(recordWithSubject.record)
        updateSubjectStats(recordWithSubject.record.subjectId)
    }

    suspend fun getSubjectAttendanceRecords(
        subjectId: UUID
    ): List<AttendanceRecord> = attendanceRecordDao.getSubjectAttendanceRecords(subjectId)

}