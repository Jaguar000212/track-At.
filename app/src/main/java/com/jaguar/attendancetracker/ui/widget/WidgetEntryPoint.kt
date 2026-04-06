package com.jaguar.attendancetracker.ui.widget

import com.jaguar.attendancetracker.backend.repositories.AttendanceRepository
import com.jaguar.attendancetracker.backend.repositories.ScheduleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun attendanceRepo(): AttendanceRepository
    fun scheduleRepo(): ScheduleRepository
}