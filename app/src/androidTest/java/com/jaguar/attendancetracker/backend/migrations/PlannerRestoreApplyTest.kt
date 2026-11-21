package com.jaguar.attendancetracker.backend.migrations

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.PlannerRestoreDao
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.repositories.ImportExportRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.ui.imExport.AttendanceBackupFormat
import com.jaguar.attendancetracker.ui.imExport.BackupData
import com.jaguar.attendancetracker.ui.imExport.PlannerBackup
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFormat
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupVersion
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlannerRestoreApplyTest {
    private val date = LocalDate.of(2024, 2, 29)
    private val term = Semester(id(1), "Spring", date, null, 80, false)
    private val math = Subject(id(2), "Math", term.id, date, false, "PINK", "Tutor", "A7",
        "Student-authored notes", minAttendance = 83)
    private val session = SessionRecord(id(3), math.id, 0, 4, date)
    private val localMark = AttendanceRecord(
        id(4), math.id, null, date, AttendanceStatus.PRESENT, ClassType.EXTRA,
    )
    private val otherMark = AttendanceRecord(
        id(5), math.id, session.id, date, AttendanceStatus.ABSENT, ClassType.REGULAR,
    )
    private val kept = BoardItem(id(6), "Keep my task")
    private val added = BoardItem(id(7), "Add this task", date)

    @Test
    fun restoresConservativelyUpdatesOnlyDerivedTotalsAndRepeatsAsNoOp() = withDb { db, dao ->
        dao.insertSemesters(listOf(term))
        val baseline = math.copy(totalClasses = 1, attendedClasses = 1)
        dao.insertSubjects(listOf(baseline))
        dao.insertAttendance(listOf(localMark))
        dao.insertBoard(listOf(kept))
        val incoming = backup(
            math.copy(totalClasses = 1), attendance = listOf(otherMark),
            board = listOf(kept.copy(title = "Do not overwrite"), added),
        )
        val repo = PlannerRestoreRepository(db)
        val initial = repo.prepare(incoming.reader())
        assertEquals(listOf(kept), dao.board())
        assertEquals(listOf(baseline), dao.subjects())
        assertEquals(listOf(localMark), dao.attendance())
        assertEquals(PlannerMergeOutcome.KEEP_LOCAL_CONFLICT, initial.preview.board.first().outcome)

        assertEquals(PlannerRestoreResult.APPLIED, initial.apply())
        assertEquals(listOf(kept, added), dao.board())
        assertEquals(listOf(session), dao.schedule())
        assertEquals(listOf(localMark, otherMark), dao.attendance())
        assertEquals(listOf(baseline.copy(totalClasses = 2)), dao.subjects())
        assertEquals(PlannerRestoreResult.NOTHING_TO_APPLY, repo.prepare(incoming.reader()).apply())
        assertEquals(listOf(baseline.copy(totalClasses = 2)), dao.subjects())
    }

    @Test
    fun stalePreviewWritesNothingAndInconsistentLocalTotalsDoNotBlockBoard() = withDb { db, dao ->
        dao.insertSemesters(listOf(term))
        val historical = math.copy(totalClasses = 42, attendedClasses = 12)
        dao.insertSubjects(listOf(historical))
        val incoming = backup(math.copy(totalClasses = 1), listOf(otherMark), listOf(added))
        val repo = PlannerRestoreRepository(db)
        val stale = repo.prepare(incoming.reader())
        dao.insertBoard(listOf(kept))
        assertEquals(PlannerRestoreResult.STALE_PREVIEW, stale.apply())
        assertTrue(dao.schedule().isEmpty())
        assertEquals(listOf(kept), dao.board())

        val fresh = repo.prepare(incoming.reader())
        assertEquals(PlannerMergeBlockReason.INCONSISTENT_LOCAL_TOTALS,
            fresh.preview.attendance.single().dependency?.reason)
        assertEquals(PlannerRestoreResult.APPLIED, fresh.apply())
        assertEquals(listOf(kept, added), dao.board())
        assertEquals(listOf(historical), dao.subjects())
        assertTrue(dao.attendance().isEmpty())
    }

    @Test
    fun lateBoardInsertFailureRollsBackEarlierWritesAndKeepsExistingData() = withDb { db, dao ->
        dao.insertBoard(listOf(kept))
        val repo = PlannerRestoreRepository(db)
        val pending = repo.prepare(backup(math.copy(totalClasses = 1), listOf(otherMark), listOf(added)).reader())
        // Fail at the final table *only if* the preceding inserts and derived update are visible
        // inside the transaction. Catch outside apply() so Room must actually roll back.
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_late_board_insert BEFORE INSERT ON BoardItems
            WHEN (SELECT COUNT(*) FROM Semesters) = 1
                AND (SELECT COUNT(*) FROM SessionRecords) = 1
                AND (SELECT COUNT(*) FROM AttendanceRecords) = 1
                AND (SELECT totalClasses FROM Subjects) = 1
            BEGIN SELECT RAISE(ABORT, 'intentional late restore fixture failure'); END
        """.trimIndent())
        try {
            pending.apply()
            fail("Expected the late Board failure to abort the transaction")
        } catch (e: SQLiteConstraintException) {
            assertTrue(e.message.orEmpty().contains("intentional late restore fixture failure"))
        }
        assertTrue(dao.semesters().isEmpty())
        assertTrue(dao.subjects().isEmpty())
        assertTrue(dao.schedule().isEmpty())
        assertTrue(dao.attendance().isEmpty())
        assertEquals(listOf(kept), dao.board())
    }

    @Test
    fun partialLegacyBackupCountsCannotInventMarksDuringPlannerRestore() = withDb { source, sourceDao ->
        val legacy = AttendanceBackupFormat.read("""
            {
              "version": 9, "timestamp": 42,
              "subjects": [{
                "id": "${math.id}", "name": "Math", "semester": 2, "startDate": "2024-02-29",
                "isEnded": false, "color": "PINK", "notes": "Synthetic notes",
                "totalClasses": 2, "attendedClasses": 1, "minAttendance": 75
              }],
              "schedule": [{
                "id": "${session.id}", "subjectId": "${math.id}",
                "orderNo": 0, "dayOfWeek": 4, "startDate": "2024-02-29"
              }]
            }
        """.trimIndent().reader())
        assertNull(legacy.attendance)
        val legacyTerm = requireNotNull(legacy.semesters).single()
        val legacySubject = requireNotNull(legacy.subjects).single()
        val slot = requireNotNull(legacy.schedule).single()
        assertEquals("Semester 2", legacyTerm.name)
        assertEquals(legacyTerm.id, legacySubject.semesterId)
        assertEquals(legacySubject.id, slot.subjectId)
        ImportExportRepository(source, source.attendanceRecordDao(), source.subjectDao(),
            source.sessionRecordDao(), source.semesterDao()).upsertDataInTransaction(
            legacy.semesters, legacy.subjects, legacy.schedule, legacy.attendance,
        )
        val stored = sourceDao.subjects().single()
        assertEquals(2, stored.totalClasses)
        assertEquals(1, stored.attendedClasses)
        assertEquals("Synthetic notes", stored.notes)
        assertEquals(listOf(slot), sourceDao.schedule())
        assertTrue(sourceDao.attendance().isEmpty())
        sourceDao.insertBoard(listOf(added))
        val planner = PlannerRestoreRepository(source).createBackup(PlannerBackupVersion.V2)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            val targetDao = target.plannerRestoreDao()
            val pending = PlannerRestoreRepository(target).prepare(planner.reader(), PlannerBackupVersion.V2)
            assertEquals(PlannerBackupVersion.V2, pending.version)
            assertEquals(PlannerMergeOutcome.INSERT, pending.preview.semesters.single().outcome)
            assertEquals(PlannerMergeOutcome.INSERT, pending.preview.board.single().outcome)
            assertEquals(PlannerMergeOutcome.BLOCKED_DEPENDENCY, pending.preview.subjects.single().outcome)
            assertEquals(PlannerMergeBlockReason.INCONSISTENT_INCOMING_TOTALS,
                pending.preview.subjects.single().dependency?.reason)
            assertEquals(PlannerMergeOutcome.BLOCKED_DEPENDENCY, pending.preview.schedule.single().outcome)
            assertEquals(PlannerMergeBlockReason.SUBJECT_BLOCKED, pending.preview.schedule.single().dependency?.reason)
            assertTrue(pending.preview.attendance.isEmpty())
            assertTrue(targetDao.semesters().isEmpty())
            assertTrue(targetDao.board().isEmpty())

            assertEquals(PlannerRestoreResult.APPLIED, pending.apply())
            assertEquals(listOf(legacyTerm), targetDao.semesters())
            assertEquals(listOf(added), targetDao.board())
            assertTrue(targetDao.subjects().isEmpty())
            assertTrue(targetDao.schedule().isEmpty())
            assertTrue(targetDao.attendance().isEmpty())
            assertEquals(listOf(stored), sourceDao.subjects())
            assertEquals(listOf(slot), sourceDao.schedule())
            assertTrue(sourceDao.attendance().isEmpty())
        } finally {
            target.close()
        }
    }

    private fun backup(subject: Subject, attendance: List<AttendanceRecord>, board: List<BoardItem>) =
        PlannerBackupFormat.encode(PlannerBackup(BackupData(timestamp = 42L, semesters = listOf(term),
            subjects = listOf(subject), schedule = listOf(session), attendance = attendance), board))

    private fun withDb(block: suspend (AttendanceDatabase, PlannerRestoreDao) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking { block(db, db.plannerRestoreDao()) }
        } finally {
            db.close()
        }
    }

    private fun id(n: Int) = UUID(0, n.toLong())
}
