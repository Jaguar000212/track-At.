package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "Exams",
    foreignKeys = [ForeignKey(
        entity = Semester::class,
        parentColumns = ["id"],
        childColumns = ["semesterId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("semesterId")],
)
data class Exam(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val title: String,
    val date: LocalDate? = null,
    val semesterId: UUID? = null,
) {
    init {
        require(title.isNotBlank()) { "Exam title is required" }
    }
}
