package com.jaguar.attendancetracker.backend

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jaguar.attendancetracker.backend.daos.AttendanceRecordDao
import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.typeConverters.DateTimeConverter

@TypeConverters(DateTimeConverter::class)
@Database(
    entities = [AttendanceRecord::class, SessionRecord::class, Subject::class],
    version = 8,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun sessionRecordDao(): SessionRecordDao
    abstract fun subjectDao(): SubjectDao
}