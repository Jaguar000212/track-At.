package com.jaguar.attendancetracker.backend.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration10TO11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE `BoardItems` (
                `id` BLOB NOT NULL,
                `title` TEXT NOT NULL,
                `dueDate` INTEGER,
                `isDone` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}
