package com.jaguar.attendancetracker.dependencies

import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.AttendanceRecordDao
import com.jaguar.attendancetracker.backend.daos.SemesterDao
import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.SubjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DaoModules {
    @Provides
    fun provideAttendanceRecordDao(database: AttendanceDatabase): AttendanceRecordDao =
        database.attendanceRecordDao()

    @Provides
    fun provideSessionRecordDao(database: AttendanceDatabase): SessionRecordDao =
        database.sessionRecordDao()

    @Provides
    fun provideSubjectDao(database: AttendanceDatabase): SubjectDao = database.subjectDao()

    @Provides
    fun provideSemesterDao(database: AttendanceDatabase): SemesterDao = database.semesterDao()
}