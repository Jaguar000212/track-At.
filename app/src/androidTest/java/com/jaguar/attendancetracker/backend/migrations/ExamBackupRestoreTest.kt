package com.jaguar.attendancetracker.backend.migrations

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreResult
import com.jaguar.attendancetracker.ui.imExport.BackupData
import com.jaguar.attendancetracker.ui.imExport.PlannerBackup
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFiles
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFormat
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupVersion
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExamBackupRestoreTest {
    private val date = LocalDate.of(2024, 2, 29)
    private val term = Semester(id(1), "Spring", date, null, 80, false)
    private val math = Subject(id(2), "Math", term.id, date, false, "PINK", null, null, null, 1, 1)
    private val mark = AttendanceRecord(id(3), math.id, null, date, AttendanceStatus.PRESENT, ClassType.EXTRA)
    private val task = BoardItem(id(4), "Submit assignment")
    private val linked = Exam(id(5), "Math final λ", date, term.id)
    private val undated = Exam(id(6), "Retake")
    private val extra = Exam(id(7), "Independent exam", date)

    @Test
    fun v1AddsAttendanceAndBoardWithoutTouchingDatedOrUndatedExams() = withDbs { db, _ ->
        val dao = db.plannerRestoreDao()
        dao.insertSemesters(listOf(term))
        dao.insertExams(listOf(linked, undated))
        val repo = PlannerRestoreRepository(db)
        val oldFile = PlannerBackupFormat.encode(snapshot())
        // The newer importer accepts old files. External/default transport still does so too.
        val prepared = repo.prepare(oldFile.reader(), supportedVersion = PlannerBackupVersion.V2)
        assertTrue(prepared.preview.exams.isEmpty())
        assertTrue(dao.attendance().isEmpty())
        // An exam-only edit does not stale or change the outcome of a v1 preview.
        db.openHelper.writableDatabase.execSQL("UPDATE Exams SET title = 'Retake edited' WHERE date IS NULL")
        assertEquals(PlannerRestoreResult.APPLIED, prepared.apply())
        assertEquals(listOf(linked, undated.copy(title = "Retake edited")), dao.exams())
        assertEquals(listOf(mark), dao.attendance())
        assertEquals(listOf(task), dao.board())
        assertEquals(PlannerRestoreResult.NOTHING_TO_APPLY, repo.prepare(oldFile.reader()).apply())
        assertNull(PlannerBackupFormat.decode(repo.createBackup().reader()).exams)
    }

    @Test
    fun v2ExamRoundTripKeepsConflictsRejectsStaleAndRepeatsWithoutWrites() = withDbs { source, db ->
        val sourceDao = source.plannerRestoreDao()
        sourceDao.insertSemesters(listOf(term))
        sourceDao.insertSubjects(listOf(math))
        sourceDao.insertAttendance(listOf(mark))
        sourceDao.insertBoard(listOf(task))
        sourceDao.insertExams(listOf(linked, undated, extra))
        val output = ByteArrayOutputStream()
        val sourceRepo = PlannerRestoreRepository(source)
        PlannerBackupFiles(sourceRepo, PlannerBackupVersion.V2).write { output }
        val bytes = output.toByteArray()

        val repo = PlannerRestoreRepository(db)
        val dao = db.plannerRestoreDao()
        try {
            PlannerBackupFiles(repo).read { bytes.inputStream() }
            fail("The current visible/default transport must still reject v2")
        } catch (_: IllegalArgumentException) {
            assertTrue(dao.exams().isEmpty())
        }

        dao.insertExams(listOf(undated.copy(title = "Local retake")))
        val files = PlannerBackupFiles(repo, PlannerBackupVersion.V2)
        val stale = files.read { bytes.inputStream() }
        assertEquals(listOf(PlannerMergeOutcome.INSERT, PlannerMergeOutcome.KEEP_LOCAL_CONFLICT, PlannerMergeOutcome.INSERT),
            stale.preview.exams.map { it.outcome })
        db.openHelper.writableDatabase.execSQL("UPDATE Exams SET title = 'Edited locally' WHERE date IS NULL")
        assertEquals(PlannerRestoreResult.STALE_PREVIEW, stale.apply())
        assertEquals(listOf(undated.copy(title = "Edited locally")), dao.exams())
        assertEquals(PlannerRestoreResult.APPLIED, files.read { bytes.inputStream() }.apply())
        assertEquals(listOf(linked, undated.copy(title = "Edited locally"), extra), dao.exams())
        assertEquals(listOf(mark), dao.attendance())
        assertEquals(listOf(task), dao.board())
        assertEquals(PlannerRestoreResult.NOTHING_TO_APPLY, files.read { bytes.inputStream() }.apply())
        val emptyV2 = PlannerBackupFormat.encode(snapshot(exams = emptyList()))
        assertEquals(PlannerRestoreResult.NOTHING_TO_APPLY, repo.prepare(emptyV2.reader(), PlannerBackupVersion.V2).apply())
        assertEquals(listOf(linked, undated.copy(title = "Edited locally"), extra), dao.exams())
    }

    @Test
    fun lateExamFailureRollsBackEarlierAttendanceAndBoardWrites() = withDbs { db, _ ->
        val dao = db.plannerRestoreDao()
        dao.insertExams(listOf(undated))
        val file = PlannerBackupFormat.encode(snapshot(exams = listOf(linked)))
        val pending = PlannerRestoreRepository(db).prepare(file.reader(), PlannerBackupVersion.V2)
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_late_exam_insert BEFORE INSERT ON Exams
            WHEN (SELECT COUNT(*) FROM Semesters) = 1
                AND (SELECT COUNT(*) FROM AttendanceRecords) = 1
                AND (SELECT COUNT(*) FROM BoardItems) = 1
                AND (SELECT attendedClasses FROM Subjects) = 1
            BEGIN SELECT RAISE(ABORT, 'intentional late exam fixture failure'); END
        """.trimIndent())
        try {
            pending.apply()
            fail("Expected the exam insertion to roll back all writes")
        } catch (e: SQLiteConstraintException) {
            assertTrue(e.message.orEmpty().contains("intentional late exam fixture failure"))
        }
        assertTrue(dao.semesters().isEmpty())
        assertTrue(dao.subjects().isEmpty())
        assertTrue(dao.attendance().isEmpty())
        assertTrue(dao.board().isEmpty())
        assertEquals(listOf(undated), dao.exams())
    }

    private fun snapshot(exams: List<Exam>? = null) = PlannerBackup(
        BackupData(timestamp = 42, semesters = listOf(term), subjects = listOf(math),
            schedule = emptyList(), attendance = listOf(mark)),
        board = listOf(task),
        exams = exams,
    )

    private fun withDbs(block: suspend (AttendanceDatabase, AttendanceDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val second = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking { block(first, second) }
        } finally {
            second.close()
            first.close()
        }
    }

    private fun id(n: Int) = UUID(0, n.toLong())
}
