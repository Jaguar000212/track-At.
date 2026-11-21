package com.jaguar.attendancetracker.backend.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

internal const val MAX_BOARD_CHECKLIST_TEXT = 200

@Entity(
    tableName = "BoardChecklistItems",
    foreignKeys = [
        ForeignKey(
            entity = BoardItem::class,
            parentColumns = ["id"],
            childColumns = ["boardItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["boardItemId", "position"])],
)
data class BoardChecklistItem(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val boardItemId: UUID,
    val position: Int,
    val text: String,
    val isDone: Boolean = false,
) {
    init {
        require(position >= 0) { "Checklist position must be non-negative" }
        require(text.isNotBlank() && text.length <= MAX_BOARD_CHECKLIST_TEXT) { "Invalid checklist text" }
    }
}
