package com.jaguar.attendancetracker.backend.entities

import androidx.room.Embedded

data class AttendanceWithSubject(
    @Embedded(prefix = "a_") val record: AttendanceRecord,
    @Embedded(prefix = "s_") val subject: Subject
)
