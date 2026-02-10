package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "SessionRecords",
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
data class SessionRecord(
    @PrimaryKey val id: UUID = UUID.randomUUID(),

    val subjectId: UUID,
    val dayOfWeek: Int,
    val startDate: LocalDate
)