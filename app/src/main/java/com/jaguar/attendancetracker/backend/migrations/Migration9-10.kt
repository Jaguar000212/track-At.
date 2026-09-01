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

        // 3. Rename the old Subjects table out of the way instead of dropping it.
        // SessionRecords/AttendanceRecords have `ON DELETE CASCADE` foreign keys pointing at
        // a table named `Subjects`. A plain rename doesn't touch any rows, so it can't trigger
        // those cascades - whereas `DROP TABLE Subjects` while FK enforcement is on would
        // implicitly DELETE every row first and wipe SessionRecords/AttendanceRecords with it.
        // (`PRAGMA foreign_keys = OFF` can't be used to avoid this: Room enables FK enforcement
        // in onConfigure(), which runs before the transaction that wraps this migrate() call, so
        // toggling the pragma in here is a documented SQLite no-op.)
        db.execSQL("ALTER TABLE Subjects RENAME TO Subjects_Old")

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

        // 6. Drop the old table. Nothing references `Subjects_Old` by name anymore
        // (SessionRecords/AttendanceRecords point at `Subjects`, which now exists again
        // fully populated), so this drop cannot cascade into either of them.
        db.execSQL("DROP TABLE Subjects_Old")

        // 7. Create Index
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Subjects_semesterId` ON `Subjects` (`semesterId`)")
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val b = ByteBuffer.allocate(16)
        b.putLong(uuid.mostSignificantBits)
        b.putLong(uuid.leastSignificantBits)
        return b.array()
    }
}
