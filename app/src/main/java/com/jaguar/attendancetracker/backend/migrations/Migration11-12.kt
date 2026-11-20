package com.jaguar.attendancetracker.backend.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration11TO12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE `Exams` (
                `id` BLOB NOT NULL,
                `title` TEXT NOT NULL,
                `date` INTEGER,
                `semesterId` BLOB,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`semesterId`) REFERENCES `Semesters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX `index_Exams_semesterId` ON `Exams` (`semesterId`)")
    }
}
