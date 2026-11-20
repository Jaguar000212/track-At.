package com.jaguar.attendancetracker.backend

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jaguar.attendancetracker.backend.daos.AttendanceRecordDao
import com.jaguar.attendancetracker.backend.daos.BoardItemDao
import com.jaguar.attendancetracker.backend.daos.ExamDao
import com.jaguar.attendancetracker.backend.daos.PlannerRestoreDao
import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import com.jaguar.attendancetracker.backend.daos.SemesterDao
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.typeConverters.DateTimeConverter
import com.jaguar.attendancetracker.backend.typeConverters.UUIDConverter

@TypeConverters(DateTimeConverter::class, UUIDConverter::class)
@Database(
    entities = [AttendanceRecord::class, SessionRecord::class, Subject::class, Semester::class, BoardItem::class, Exam::class],
    version = DB_VERSION,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun attendanceRecordDao(): AttendanceRecordDao
    abstract fun sessionRecordDao(): SessionRecordDao
    abstract fun subjectDao(): SubjectDao
    abstract fun semesterDao(): SemesterDao
    abstract fun boardItemDao(): BoardItemDao
    abstract fun examDao(): ExamDao
    abstract fun plannerRestoreDao(): PlannerRestoreDao
}
