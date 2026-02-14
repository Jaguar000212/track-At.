package com.jaguar.attendancetracker.backend.entities

import androidx.room.Embedded
import androidx.room.Relation

data class AttendanceWithSubject(
    @Embedded val record: AttendanceRecord,

    @Relation(parentColumn = "subjectId", entityColumn = "id") val subject: Subject
)
