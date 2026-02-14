package com.jaguar.attendancetracker.backend.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration8TO9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE SessionRecords ADD COLUMN `orderNo` INTEGER NOT NULL DEFAULT 0"
        )
    }
}