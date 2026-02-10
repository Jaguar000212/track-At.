package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "AttendanceRecords",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class AttendanceRecord(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val subjectId: UUID,

    val date: LocalDate,
    val sessionId: UUID?,

    val status: AttendanceStatus?,
    val classType: ClassType
)