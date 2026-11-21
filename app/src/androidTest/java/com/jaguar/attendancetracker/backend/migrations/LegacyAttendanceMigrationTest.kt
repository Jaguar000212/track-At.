package com.jaguar.attendancetracker.backend.migrations

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.DB_VERSION
import com.jaguar.attendancetracker.backend.entities.BoardChecklistItem
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFormat
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupVersion
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LegacyAttendanceMigrationTest {
    @Test
    fun populatedV8PreservesAttendanceAndScheduleWithDefaultOrder() = verifyMigration(8, 0)

    @Test
    fun populatedV9PreservesAttendanceAndScheduleWithExistingOrder() = verifyMigration(9, 7)

    @Test
    fun populatedV10KeepsAttendanceAndSupportsBoardPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "board-migration-${UUID.randomUUID()}.db"
        val oldTables = listOf("Semesters", "Subjects", "SessionRecords", "AttendanceRecords")
        val oldSchema = """
            SELECT name, sql FROM sqlite_master WHERE tbl_name IN
                ('Semesters','Subjects','SessionRecords','AttendanceRecords') ORDER BY name
        """.trimIndent()
        val board = BoardItem(title = "Submit assignment")
        val dated = BoardItem(title = "Prepare slides", dueDate = LocalDate.of(2026, 11, 25))

        try {
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            val (expectedSchema, expectedRows) = SQLiteDatabase.openOrCreateDatabase(file, null)
                .use { legacy ->
                    legacy.setForeignKeyConstraintsEnabled(true)
                    // Independent checked-in v10 fixture; does not use current migration code.
                    instrumentation.context.assets.open("room/attendance-v10.sql").bufferedReader().use {
                        it.readText().split(';').map(String::trim).filter(String::isNotEmpty)
                            .forEach { statement -> legacy.execSQL(statement) }
                    }
                    legacy.version = 10
                    val schema = legacy.rawQuery(oldSchema, null).use(::snapshot)
                    val rows = oldTables.associateWith { table ->
                        legacy.rawQuery(rowsSql(table), null).use(::snapshot)
                            .also { assertTrue("Fixture $table must be populated", it.isNotEmpty()) }
                    }
                    schema to rows
                }

            fun open() = Room.databaseBuilder(context, AttendanceDatabase::class.java, name)
                .addMigrations(Migration8TO9, Migration9TO10, Migration10TO11, Migration11TO12, Migration12TO13).build()

            val migrated = open()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(DB_VERSION, db.version)
                assertRows(db, "SELECT id FROM Exams", emptyList())
                assertRows(db, "PRAGMA foreign_keys", listOf(listOf("1")))
                assertRows(db, "PRAGMA foreign_key_check", emptyList())
                assertEquals(expectedSchema, db.query(oldSchema).use(::snapshot))
                expectedRows.forEach { (table, rows) ->
                    assertEquals(table, rows, db.query(rowsSql(table)).use(::snapshot))
                }
                assertRows(db, "SELECT id FROM BoardItems", emptyList())
                runBlocking {
                    migrated.boardItemDao().insert(board)
                    migrated.boardItemDao().insert(dated)
                    migrated.boardItemDao().update(board.copy(isDone = true))
                }
                // Planner writes should not alter records in the legacy tables either.
                expectedRows.forEach { (table, rows) ->
                    assertEquals(table, rows, db.query(rowsSql(table)).use(::snapshot))
                }
            } finally {
                migrated.close()
            }

            val reopened = open()
            try {
                val saved = runBlocking { reopened.boardItemDao().observeAll().first() }
                assertEquals(setOf(board.copy(isDone = true), dated), saved.toSet())
            } finally {
                reopened.close()
            }
        } finally {
            assertTrue("Temporary fixture database must be removed", context.deleteDatabase(name) ||
                !context.getDatabasePath(name).exists())
        }
    }

    @Test
    fun populatedV11KeepsDataAndDetachesExamsOnlyWhenSemesterDeleted() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "exam-migration-${UUID.randomUUID()}.db"
        val oldTables = listOf("Semesters", "Subjects", "SessionRecords", "AttendanceRecords", "BoardItems")
        val oldSchema = """
            SELECT name, sql FROM sqlite_master WHERE tbl_name IN
                ('Semesters','Subjects','SessionRecords','AttendanceRecords') ORDER BY name
        """.trimIndent()
        val linked = Exam(title = "Math final", date = LocalDate.of(2026, 12, 1), semesterId = UUID(0, 1))
        val undated = Exam(title = "Presentation")
        val archiveLinked = Exam(title = "Retake", semesterId = UUID(0, 2))

        fun open() = Room.databaseBuilder(context, AttendanceDatabase::class.java, name)
            .addMigrations(Migration8TO9, Migration9TO10, Migration10TO11, Migration11TO12, Migration12TO13).build()

        try {
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            val (expectedSchema, expectedRows) = SQLiteDatabase.openOrCreateDatabase(file, null)
                .use { legacy ->
                    legacy.setForeignKeyConstraintsEnabled(true)
                    for (asset in listOf("room/attendance-v10.sql", "room/board-v11.sql")) {
                        instrumentation.context.assets.open(asset).bufferedReader().use {
                            it.readText().split(';').map(String::trim).filter(String::isNotEmpty)
                                .forEach { statement -> legacy.execSQL(statement) }
                        }
                    }
                    legacy.version = 11
                    val schema = legacy.rawQuery(oldSchema, null).use(::snapshot)
                    val rows = oldTables.associateWith { table ->
                        legacy.rawQuery(rowsSql(table), null).use(::snapshot)
                            .also { assertTrue("Fixture $table must be populated", it.isNotEmpty()) }
                    }
                    schema to rows
                }

            val migrated = open()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(DB_VERSION, db.version)
                assertEquals(expectedSchema, db.query(oldSchema).use(::snapshot))
                expectedRows.forEach { (table, rows) ->
                    assertEquals(table, rows, db.query(rowsSql(table)).use(::snapshot))
                }
                assertRows(db, "SELECT id FROM Exams", emptyList())
                assertRows(db, "PRAGMA foreign_keys", listOf(listOf("1")))
                assertRows(db,
                    "SELECT \"table\", \"from\", \"to\", on_delete FROM pragma_foreign_key_list('Exams')",
                    listOf(listOf("Semesters", "semesterId", "id", "SET NULL")))
                assertRows(db, "SELECT name FROM pragma_index_info('index_Exams_semesterId')",
                    listOf(listOf("semesterId")))

                runBlocking {
                    migrated.examDao().insert(linked)
                    migrated.examDao().insert(undated)
                    migrated.examDao().insert(archiveLinked)
                }
                db.execSQL("UPDATE Semesters SET isArchived = 1 WHERE id = X'00000000000000000000000000000001'")
                assertEquals(setOf(linked, undated, archiveLinked), runBlocking { migrated.examDao().observeAll().first().toSet() })
                db.execSQL("DELETE FROM Semesters WHERE id = X'00000000000000000000000000000001'")
                assertRows(db, "PRAGMA foreign_key_check", emptyList())
            } finally {
                migrated.close()
            }

            val reopened = open()
            try {
                val exams = runBlocking { reopened.examDao().observeAll().first().toSet() }
                assertEquals(setOf(linked.copy(semesterId = null), undated, archiveLinked), exams)
                assertEquals(expectedRows.getValue("BoardItems"),
                    reopened.openHelper.writableDatabase.query(rowsSql("BoardItems")).use(::snapshot))
            } finally {
                reopened.close()
            }
        } finally {
            assertTrue("Temporary fixture database must be removed", context.deleteDatabase(name) ||
                !context.getDatabasePath(name).exists())
        }
    }

    private fun snapshot(cursor: Cursor): List<List<Any?>> = buildList {
        while (cursor.moveToNext()) {
            add(List(cursor.columnCount) { index ->
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index).toList()
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                    else -> error("Unexpected SQLite column type")
                }
            })
        }
    }

    @Test
    fun populatedV12AddsDormantBoardPortabilityWithoutChangingOldBackups() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "board-portability-${UUID.randomUUID()}.db"
        val oldTables = listOf("Semesters", "Subjects", "SessionRecords", "AttendanceRecords", "BoardItems", "Exams")
        try {
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            val expected = SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
                legacy.setForeignKeyConstraintsEnabled(true)
                for (asset in listOf("attendance-v10.sql", "board-v11.sql", "exams-v12.sql")) {
                    instrumentation.context.assets.open("room/$asset").bufferedReader().use {
                        it.readText().split(';').map(String::trim).filter(String::isNotEmpty)
                            .forEach { statement -> legacy.execSQL(statement) }
                    }
                }
                legacy.version = 12
                oldTables.associateWith { table ->
                    legacy.rawQuery(rowsSql(table), null).use(::snapshot)
                        .also { assertTrue("Fixture $table must be populated", it.isNotEmpty()) }
                }
            }
            val migrated = Room.databaseBuilder(context, AttendanceDatabase::class.java, name)
                .addMigrations(Migration12TO13).build()
            try {
                val db = migrated.openHelper.writableDatabase
                assertEquals(13, db.version)
                for ((table, rows) in expected) assertEquals(table, rows, db.query(rowsSql(table)).use(::snapshot))
                assertRows(db, "SELECT isPinned FROM BoardItems ORDER BY id", listOf(listOf("0"), listOf("0")))
                assertRows(db, "SELECT id FROM BoardChecklistItems", emptyList())
                assertRows(db, "PRAGMA foreign_keys", listOf(listOf("1")))
                assertRows(db, "PRAGMA foreign_key_check", emptyList())
                assertRows(db, "SELECT \"table\", \"from\", \"to\", on_delete FROM pragma_foreign_key_list('BoardChecklistItems')",
                    listOf(listOf("BoardItems", "boardItemId", "id", "CASCADE")))
                assertRows(db, "SELECT name FROM pragma_index_info('index_BoardChecklistItems_boardItemId_position')",
                    listOf(listOf("boardItemId"), listOf("position")))

                runBlocking {
                    val dao = migrated.boardItemDao()
                    val parent = UUID(0, 0x21)
                    val first = BoardChecklistItem(boardItemId = parent, position = 0, text = "First")
                    val second = BoardChecklistItem(boardItemId = parent, position = 1, text = "Second", isDone = true)
                    assertEquals(1, dao.setPinned(parent, true))
                    dao.insertChecklistItem(second)
                    dao.insertChecklistItem(first)
                    assertEquals(1, dao.editDetails(parent, "Edited title", null))
                    assertEquals(1, dao.setDone(parent, true))
                    val retained = dao.observeAll().first().single { it.id == parent }
                    assertTrue(retained.isPinned && retained.isDone)
                    assertEquals(listOf(first, second), dao.checklistItems(parent))

                    val repo = PlannerRestoreRepository(migrated)
                    for (version in listOf(PlannerBackupVersion.V1, PlannerBackupVersion.V2)) {
                        val legacyWire = repo.createBackup(version)
                        assertFalse(legacyWire.contains("isPinned"))
                        assertFalse(legacyWire.contains("BoardChecklistItems"))
                        val decoded = PlannerBackupFormat.decode(legacyWire.reader())
                        assertEquals(version, decoded.version)
                        assertFalse(decoded.board.single { it.id == parent }.isPinned)
                        val newTask = BoardItem(title = "From old backup")
                        val incoming = PlannerBackupFormat.encode(decoded.copy(board = decoded.board + newTask))
                        val pending = repo.prepare(incoming.reader(), version)
                        assertEquals(PlannerMergeOutcome.UNCHANGED, pending.preview.board.single { it.incoming.id == parent }.outcome)
                        assertEquals(PlannerRestoreResult.APPLIED, pending.apply())
                        assertEquals(retained, dao.observeAll().first().single { it.id == parent })
                        assertEquals(newTask, dao.observeAll().first().single { it.id == newTask.id })
                        assertEquals(listOf(first, second), dao.checklistItems(parent))
                    }
                }
                assertRows(db, "PRAGMA foreign_key_check", emptyList())
            } finally {
                migrated.close()
            }
        } finally {
            assertTrue("Temporary fixture database must be removed", context.deleteDatabase(name) ||
                !context.getDatabasePath(name).exists())
        }
    }

    private fun rowsSql(table: String): String = if (table == "BoardItems") {
        "SELECT id, title, dueDate, isDone FROM BoardItems ORDER BY id"
    } else {
        "SELECT * FROM $table ORDER BY id"
    }

    private fun verifyMigration(fromVersion: Int, expectedOrder: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "legacy-migration-${UUID.randomUUID()}.db"
        try {
            val file = context.getDatabasePath(name)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
                legacy.setForeignKeyConstraintsEnabled(true)
                createFixture(legacy, fromVersion)
                legacy.version = fromVersion
            }

            val room = Room.databaseBuilder(context, AttendanceDatabase::class.java, name)
                .addMigrations(Migration8TO9, Migration9TO10, Migration10TO11, Migration11TO12, Migration12TO13)
                .build()
            try {
                    // Opening through Room runs its actual migration transaction and schema validation.
                    val db = room.openHelper.writableDatabase
                    assertEquals(DB_VERSION, db.version)
                    assertRows(db, "SELECT id FROM Exams", emptyList())
                    assertRows(db, "SELECT id FROM BoardItems", emptyList())
                    assertRows(db, "PRAGMA foreign_keys", listOf(listOf("1")))
                    assertRows(db, "PRAGMA foreign_key_check", emptyList())
                    assertRows(
                        db,
                        """
                        SELECT hex(s.id), s.name, sem.name, s.startDate, s.isEnded, s.color,
                            s.professor, s.roomNo, s.notes, s.totalClasses, s.attendedClasses,
                            s.minAttendance, sem.targetAttendance, sem.isArchived, sem.endDate
                        FROM Subjects s JOIN Semesters sem ON sem.id = s.semesterId ORDER BY s.name
                        """.trimIndent(),
                        listOf(
                            listOf(SUBJECT_B, "Art", "Semester 4", "20001", "1", "BLUE",
                                null, "B7", "notes", "0", "0", "80", "75", "0", null),
                            listOf(SUBJECT_A, "Math", "Semester 2", "20000", "0", "PINK",
                                "Professor", null, null, "2", "1", "70", "75", "0", null),
                        ),
                    )
                    assertRows(
                        db,
                        "SELECT hex(id), hex(subjectId), orderNo, dayOfWeek, startDate FROM SessionRecords",
                        listOf(listOf(SESSION, SUBJECT_A, expectedOrder.toString(), "2", "20002")),
                    )
                    assertRows(
                        db,
                        """
                        SELECT hex(id), hex(subjectId), CASE WHEN sessionId IS NULL THEN NULL
                            ELSE hex(sessionId) END, date, status, classType
                        FROM AttendanceRecords ORDER BY date
                        """.trimIndent(),
                        listOf(
                            listOf(PRESENT, SUBJECT_A, SESSION, "20003", "PRESENT", "REGULAR"),
                            listOf(ABSENT, SUBJECT_A, SESSION, "20010", "ABSENT", "REGULAR"),
                            listOf(CANCELLED, SUBJECT_A, SESSION, "20017", "CANCELLED", "REGULAR"),
                            listOf(EXTRA, SUBJECT_B, null, "20018", null, "EXTRA"),
                        ),
                    )

                    for (table in listOf("SessionRecords", "AttendanceRecords")) {
                        assertRows(
                            db,
                            "SELECT \"table\", \"from\", \"to\", on_update, on_delete FROM pragma_foreign_key_list('$table')",
                            listOf(listOf("Subjects", "subjectId", "id", "NO ACTION", "CASCADE")),
                        )
                        assertRows(
                            db,
                            "SELECT name, \"unique\" FROM pragma_index_list('$table') WHERE origin = 'c'",
                            listOf(listOf("index_${table}_subjectId", "0")),
                        )
                        assertRows(
                            db, "SELECT name FROM pragma_index_info('index_${table}_subjectId')",
                            listOf(listOf("subjectId")),
                        )
                    }
                    assertRows(
                        db,
                        "SELECT name FROM sqlite_master WHERE name LIKE '_migration_9_10_%' OR name = 'Subjects_Old'",
                        emptyList(),
                    )

                    // Verify the rebuilt constraints actually cascade only to the deleted parent.
                    db.execSQL("DELETE FROM Subjects WHERE id = X'$SUBJECT_A'")
                    assertRows(db, "SELECT hex(id) FROM SessionRecords", emptyList())
                    assertRows(db, "SELECT hex(id) FROM AttendanceRecords", listOf(listOf(EXTRA)))
                    assertRows(db, "PRAGMA foreign_key_check", emptyList())
            } finally {
                room.close()
            }
        } finally {
            assertTrue("Temporary fixture database must be removed", context.deleteDatabase(name) ||
                !context.getDatabasePath(name).exists())
        }
    }

    private fun createFixture(db: SQLiteDatabase, version: Int) {
        // Historical v9 entities are in 6cafe359 (immediately before the semester redesign).
        // v8 differs by the missing orderNo column; production 8→9 adds the default of zero.
        db.execSQL("""
            CREATE TABLE Subjects (
                id BLOB NOT NULL PRIMARY KEY, name TEXT NOT NULL, semester INTEGER NOT NULL,
                startDate INTEGER NOT NULL, isEnded INTEGER NOT NULL, color TEXT NOT NULL,
                professor TEXT, roomNo TEXT, notes TEXT, totalClasses INTEGER NOT NULL,
                attendedClasses INTEGER NOT NULL, minAttendance INTEGER NOT NULL
            )
        """.trimIndent())
        val orderColumn = if (version == 9) "orderNo INTEGER NOT NULL DEFAULT 0," else ""
        db.execSQL("""
            CREATE TABLE SessionRecords (
                id BLOB NOT NULL PRIMARY KEY, subjectId BLOB NOT NULL, $orderColumn
                dayOfWeek INTEGER NOT NULL, startDate INTEGER NOT NULL,
                FOREIGN KEY(subjectId) REFERENCES Subjects(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE AttendanceRecords (
                id BLOB NOT NULL PRIMARY KEY, subjectId BLOB NOT NULL, sessionId BLOB, date INTEGER NOT NULL,
                status TEXT, classType TEXT NOT NULL,
                FOREIGN KEY(subjectId) REFERENCES Subjects(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_SessionRecords_subjectId ON SessionRecords(subjectId)")
        db.execSQL("CREATE INDEX index_AttendanceRecords_subjectId ON AttendanceRecords(subjectId)")
        db.execSQL("""
            INSERT INTO Subjects VALUES
             (X'$SUBJECT_A', 'Math', 2, 20000, 0, 'PINK', 'Professor', NULL, NULL, 2, 1, 70),
             (X'$SUBJECT_B', 'Art', 4, 20001, 1, 'BLUE', NULL, 'B7', 'notes', 0, 0, 80)
        """.trimIndent())
        if (version == 9) {
            db.execSQL("INSERT INTO SessionRecords VALUES (X'$SESSION', X'$SUBJECT_A', 7, 2, 20002)")
        } else {
            db.execSQL("INSERT INTO SessionRecords VALUES (X'$SESSION', X'$SUBJECT_A', 2, 20002)")
        }
        db.execSQL("""
            INSERT INTO AttendanceRecords VALUES
             (X'$PRESENT', X'$SUBJECT_A', X'$SESSION', 20003, 'PRESENT', 'REGULAR'),
             (X'$ABSENT', X'$SUBJECT_A', X'$SESSION', 20010, 'ABSENT', 'REGULAR'),
             (X'$CANCELLED', X'$SUBJECT_A', X'$SESSION', 20017, 'CANCELLED', 'REGULAR'),
             (X'$EXTRA', X'$SUBJECT_B', NULL, 20018, NULL, 'EXTRA')
        """.trimIndent())
    }

    private fun assertRows(db: SupportSQLiteDatabase, sql: String, expected: List<List<String?>>) {
        db.query(sql).use { cursor ->
            for (row in expected) {
                assertTrue(sql, cursor.moveToNext())
                assertEquals(sql, row, List(cursor.columnCount) { cursor.getString(it) })
            }
            assertFalse(sql, cursor.moveToNext())
        }
    }

    private companion object {
        const val SUBJECT_A = "00000000000000000000000000000001"
        const val SUBJECT_B = "00000000000000000000000000000002"
        const val SESSION = "00000000000000000000000000000003"
        const val PRESENT = "00000000000000000000000000000004"
        const val ABSENT = "00000000000000000000000000000005"
        const val CANCELLED = "00000000000000000000000000000006"
        const val EXTRA = "00000000000000000000000000000007"
    }
}
