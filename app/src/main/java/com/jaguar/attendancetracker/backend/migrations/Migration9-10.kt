package com.jaguar.attendancetracker.backend.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.UUID

object Migration9TO10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create the Semesters table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `Semesters` (
                `id` BLOB NOT NULL, 
                `name` TEXT NOT NULL, 
                `startDate` INTEGER NOT NULL, 
                `endDate` INTEGER, 
                `targetAttendance` INTEGER NOT NULL, 
                `isArchived` INTEGER NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())

        // 2. Extract existing semesters and generate UUIDs
        val semesterMap = mutableMapOf<Int, ByteArray>()
        val cursor = db.query("SELECT DISTINCT semester FROM Subjects")
        while (cursor.moveToNext()) {
            val oldSemesterInt = cursor.getInt(0)
            val newUuid = UUID.randomUUID()
            val uuidBytes = uuidToBytes(newUuid)
            semesterMap[oldSemesterInt] = uuidBytes
            
            // Insert into Semesters
            val dummyName = "Semester $oldSemesterInt"
            val nowEpoch = LocalDate.now().toEpochDay()
            db.execSQL(
                "INSERT INTO Semesters (id, name, startDate, targetAttendance, isArchived) VALUES (?, ?, ?, 75, 0)",
                arrayOf(uuidBytes, dummyName, nowEpoch)
            )
        }
        cursor.close()

        // SQLite may rewrite child foreign-key targets when a parent is renamed. Stage
        // the child rows in tables without foreign keys and remove the original children
        // before touching Subjects. Room wraps this whole migration in a transaction.
        db.execSQL("""
            CREATE TABLE `_migration_9_10_sessions` AS
            SELECT `id`, `subjectId`, `orderNo`, `dayOfWeek`, `startDate` FROM `SessionRecords`
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE `_migration_9_10_attendance` AS
            SELECT `id`, `subjectId`, `sessionId`, `date`, `status`, `classType` FROM `AttendanceRecords`
        """.trimIndent())
        db.execSQL("DROP TABLE `AttendanceRecords`")
        db.execSQL("DROP TABLE `SessionRecords`")
        db.execSQL("ALTER TABLE `Subjects` RENAME TO `Subjects_Old`")

        // 4. Create the new Subjects table under its final name, with the semesterId FK.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `Subjects` (
                `id` BLOB NOT NULL, 
                `name` TEXT NOT NULL, 
                `semesterId` BLOB NOT NULL, 
                `startDate` INTEGER NOT NULL, 
                `isEnded` INTEGER NOT NULL, 
                `color` TEXT NOT NULL, 
                `professor` TEXT, 
                `roomNo` TEXT, 
                `notes` TEXT, 
                `totalClasses` INTEGER NOT NULL, 
                `attendedClasses` INTEGER NOT NULL, 
                `minAttendance` INTEGER NOT NULL, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`semesterId`) REFERENCES `Semesters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
            )
        """.trimIndent())

        // 5. Migrate data from Subjects_Old to the new Subjects table
        val subjectsCursor = db.query("SELECT * FROM Subjects_Old")
        val idIdx = subjectsCursor.getColumnIndex("id")
        val nameIdx = subjectsCursor.getColumnIndex("name")
        val semIdx = subjectsCursor.getColumnIndex("semester")
        val startIdx = subjectsCursor.getColumnIndex("startDate")
        val endedIdx = subjectsCursor.getColumnIndex("isEnded")
        val colorIdx = subjectsCursor.getColumnIndex("color")
        val profIdx = subjectsCursor.getColumnIndex("professor")
        val roomIdx = subjectsCursor.getColumnIndex("roomNo")
        val notesIdx = subjectsCursor.getColumnIndex("notes")
        val totalIdx = subjectsCursor.getColumnIndex("totalClasses")
        val attendedIdx = subjectsCursor.getColumnIndex("attendedClasses")
        val minIdx = subjectsCursor.getColumnIndex("minAttendance")

        val fallbackSemesterId = uuidToBytes(UUID.randomUUID())
        var fallbackCreated = false

        while (subjectsCursor.moveToNext()) {
            val id = subjectsCursor.getBlob(idIdx)
            val name = subjectsCursor.getString(nameIdx)
            val oldSem = subjectsCursor.getInt(semIdx)
            
            val semesterId = semesterMap[oldSem] ?: run {
                if (!fallbackCreated) {
                    val nowEpoch = LocalDate.now().toEpochDay()
                    db.execSQL(
                        "INSERT INTO Semesters (id, name, startDate, targetAttendance, isArchived) VALUES (?, ?, ?, 75, 0)",
                        arrayOf(fallbackSemesterId, "General Semester", nowEpoch)
                    )
                    fallbackCreated = true
                }
                fallbackSemesterId
            }
            val startDate = subjectsCursor.getLong(startIdx)
            val isEnded = subjectsCursor.getInt(endedIdx)
            val color = subjectsCursor.getString(colorIdx)
            val professor = if (subjectsCursor.isNull(profIdx)) null else subjectsCursor.getString(profIdx)
            val roomNo = if (subjectsCursor.isNull(roomIdx)) null else subjectsCursor.getString(roomIdx)
            val notes = if (subjectsCursor.isNull(notesIdx)) null else subjectsCursor.getString(notesIdx)
            val totalClasses = subjectsCursor.getInt(totalIdx)
            val attendedClasses = subjectsCursor.getInt(attendedIdx)
            val minAttendance = subjectsCursor.getInt(minIdx)

            db.execSQL(
                "INSERT INTO Subjects (id, name, semesterId, startDate, isEnded, color, professor, roomNo, notes, totalClasses, attendedClasses, minAttendance) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(id, name, semesterId, startDate, isEnded, color, professor, roomNo, notes, totalClasses, attendedClasses, minAttendance)
            )
        }
        subjectsCursor.close()

        db.execSQL("DROP TABLE `Subjects_Old`")
        db.execSQL("CREATE INDEX `index_Subjects_semesterId` ON `Subjects` (`semesterId`)")

        // Use the exact v10 schema and copy the staged rows only once their parent exists.
        // AttendanceRecord.sessionId is deliberately nullable and is not a foreign key.
        db.execSQL("""
            CREATE TABLE `SessionRecords` (
                `id` BLOB NOT NULL,
                `subjectId` BLOB NOT NULL,
                `orderNo` INTEGER NOT NULL,
                `dayOfWeek` INTEGER NOT NULL,
                `startDate` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`subjectId`) REFERENCES `Subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE `AttendanceRecords` (
                `id` BLOB NOT NULL,
                `subjectId` BLOB NOT NULL,
                `sessionId` BLOB,
                `date` INTEGER NOT NULL,
                `status` TEXT,
                `classType` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`subjectId`) REFERENCES `Subjects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `SessionRecords` (`id`, `subjectId`, `orderNo`, `dayOfWeek`, `startDate`)
            SELECT `id`, `subjectId`, `orderNo`, `dayOfWeek`, `startDate` FROM `_migration_9_10_sessions`
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `AttendanceRecords` (`id`, `subjectId`, `sessionId`, `date`, `status`, `classType`)
            SELECT `id`, `subjectId`, `sessionId`, `date`, `status`, `classType` FROM `_migration_9_10_attendance`
        """.trimIndent())
        db.execSQL("CREATE INDEX `index_SessionRecords_subjectId` ON `SessionRecords` (`subjectId`)")
        db.execSQL("CREATE INDEX `index_AttendanceRecords_subjectId` ON `AttendanceRecords` (`subjectId`)")
        db.execSQL("DROP TABLE `_migration_9_10_attendance`")
        db.execSQL("DROP TABLE `_migration_9_10_sessions`")
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val b = ByteBuffer.allocate(16)
        b.putLong(uuid.mostSignificantBits)
        b.putLong(uuid.leastSignificantBits)
        return b.array()
    }
}
