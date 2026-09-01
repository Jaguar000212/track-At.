package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "Semesters")
data class Semester(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val targetAttendance: Int = 75,
    val isArchived: Boolean = false
)
