package com.jaguar.attendancetracker.backend.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/** Internal planner storage only. No attendance, course or semester links. */
@Entity(tableName = "BoardItems")
data class BoardItem(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val title: String,
    val dueDate: LocalDate? = null,
    val isDone: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
) {
    init {
        require(title.isNotBlank()) { "Board item title is required" }
    }
}
