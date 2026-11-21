package com.jaguar.attendancetracker.backend.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration12TO13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `BoardItems` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE `BoardChecklistItems` (
                `id` BLOB NOT NULL,
                `boardItemId` BLOB NOT NULL,
                `position` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `isDone` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`boardItemId`) REFERENCES `BoardItems`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX `index_BoardChecklistItems_boardItemId_position` ON `BoardChecklistItems` (`boardItemId`, `position`)")
    }
}
