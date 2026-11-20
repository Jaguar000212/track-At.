package com.jaguar.attendancetracker.backend.rollover

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.backend.enums.SubjectColor
import com.jaguar.attendancetracker.backend.repositories.PlannerRestoreRepository
import com.jaguar.attendancetracker.backend.repositories.RolloverCoursePreview
import com.jaguar.attendancetracker.backend.repositories.RolloverInputIssue
import com.jaguar.attendancetracker.backend.repositories.RolloverPreparation
import com.jaguar.attendancetracker.backend.repositories.RolloverResult
import com.jaguar.attendancetracker.backend.repositories.RolloverTermDetails
import com.jaguar.attendancetracker.backend.repositories.TermRolloverRepository
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupFormat
import com.jaguar.attendancetracker.ui.imExport.PlannerBackupVersion
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class TermRolloverTest {
    @Test fun rolloverCreatesOnlySelectedFreshCoursesAndKeepsSourceAndBackup() = withFixture { db, f ->
        val dao = db.plannerRestoreDao()
        val repo = TermRolloverRepository(db)
        val before = dao.subjects()
        val pending = (repo.prepare(f.source.id, f.destination, listOf(f.first.id)) as RolloverPreparation.Ready).confirmation
        assertEquals(f.source.name, pending.preview.sourceName)
        assertEquals(f.destination, pending.preview.newTerm)
        assertEquals(listOf(RolloverCoursePreview(f.first.name, SubjectColor.PINK)), pending.preview.courses)
        assertEquals(listOf(f.source), dao.semesters())
        assertEquals(before, dao.subjects())
        val results = coroutineScope {
            val one = async { pending.apply() }
            val two = async { pending.apply() }
            listOf(one.await(), two.await())
        }
        assertEquals(setOf(RolloverResult.CREATED, RolloverResult.ALREADY_CREATED), results.toSet())
        val created = dao.semesters().single { it.id != f.source.id }
        assertEquals(f.source.copy(id = created.id, startDate = f.destination.startDate, endDate = f.destination.endDate,
            targetAttendance = f.destination.targetAttendance, isArchived = false), created)
        val newCourse = dao.subjects().single { it.semesterId == created.id }
        assertFalse(newCourse.id in before.map { it.id })
        assertEquals(f.first.copy(id = newCourse.id, semesterId = created.id, startDate = created.startDate,
            isEnded = false, professor = null, roomNo = null, notes = null, totalClasses = 0, attendedClasses = 0,
            minAttendance = created.targetAttendance), newCourse)
        assertEquals(f.source, dao.semesters().single { it.id == f.source.id })
        assertEquals(before, dao.subjects().filter { it.semesterId == f.source.id })
        assertEquals(listOf(f.mark), dao.attendance())
        assertEquals(listOf(f.slot), dao.schedule())
        assertEquals(listOf(f.exam), dao.exams())
        assertEquals(listOf(f.task), dao.board())

        val backup = PlannerBackupFormat.decode(PlannerRestoreRepository(db).createBackup(PlannerBackupVersion.V2).reader())
        assertEquals(10, backup.attendance.version)
        assertEquals(setOf(f.source, created), backup.attendance.semesters?.toSet())
        assertEquals(listOf(f.slot), backup.attendance.schedule)
        assertEquals(listOf(f.mark), backup.attendance.attendance)
        assertEquals(listOf(f.exam), backup.exams)
        assertEquals(listOf(f.task), backup.board)
    }

    @Test fun rolloverRejectsInvalidOrStaleSelectionsBeforeAnyWrites() = withFixture { db, f ->
        val repo = TermRolloverRepository(db)
        assertEquals(RolloverPreparation.Invalid(RolloverInputIssue.INVALID_TERM),
            repo.prepare(f.source.id, f.destination.copy(endDate = f.destination.startDate.minusDays(1)), listOf(f.first.id)))
        assertEquals(RolloverPreparation.Invalid(RolloverInputIssue.INVALID_SELECTION),
            repo.prepare(f.source.id, f.destination, listOf(f.first.id, f.first.id)))
        assertEquals(RolloverPreparation.Invalid(RolloverInputIssue.SOURCE_CHANGED),
            repo.prepare(f.source.id, f.destination, listOf(UUID.randomUUID())))
        val pending = (repo.prepare(f.source.id, f.destination, listOf(f.first.id)) as RolloverPreparation.Ready).confirmation
        db.subjectDao().update(f.first.copy(name = "Changed after preview"))
        assertEquals(RolloverResult.STALE_PREVIEW, pending.apply())
        assertEquals(listOf(f.source), db.plannerRestoreDao().semesters())
        assertEquals(2, db.plannerRestoreDao().subjects().size)
    }

    @Test fun lateRolloverCourseConstraintRollsBackWholeNewTerm() = withFixture { db, f ->
        val dao = db.plannerRestoreDao()
        val before = dao.subjects()
        val pending = (TermRolloverRepository(db).prepare(f.source.id, f.destination, listOf(f.first.id, f.second.id))
            as RolloverPreparation.Ready).confirmation
        // ABORT only after the new term and first course reached SQLite.
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_second_rollover BEFORE INSERT ON Subjects
            WHEN NEW.name = 'Second' AND EXISTS(
                SELECT 1 FROM Subjects WHERE semesterId = NEW.semesterId AND name = 'First'
            )
            BEGIN SELECT RAISE(ABORT, 'late rollover test'); END
        """.trimIndent())
        var rejected = false
        try { pending.apply() } catch (_: SQLiteConstraintException) { rejected = true }
        assertTrue(rejected)
        assertEquals(listOf(f.source), dao.semesters())
        assertEquals(before, dao.subjects())
        assertEquals(listOf(f.mark), dao.attendance())
    }

    private fun withFixture(test: suspend (AttendanceDatabase, Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val start = LocalDate.of(2025, 1, 1)
        val source = Semester(name = "Same name is okay", startDate = start, endDate = null, isArchived = true)
        val first = Subject(name = "First", semesterId = source.id, startDate = start, isEnded = true,
            color = "PINK", professor = "Not copied", roomNo = "Not copied", notes = "Not copied",
            totalClasses = 1, attendedClasses = 1, minAttendance = 90)
        val second = first.copy(id = UUID.randomUUID(), name = "Second", totalClasses = 0, attendedClasses = 0)
        val slot = SessionRecord(subjectId = first.id, dayOfWeek = start.dayOfWeek.value, startDate = start)
        val mark = AttendanceRecord(subjectId = first.id, sessionId = slot.id, date = start,
            status = AttendanceStatus.PRESENT, classType = ClassType.REGULAR)
        val exam = Exam(title = "Old exam", date = start, semesterId = source.id)
        val task = BoardItem(title = "Old task", dueDate = start)
        val destination = RolloverTermDetails(source.name, start.plusYears(1), start.plusYears(1).plusMonths(5), 80)
        try {
            runBlocking {
                val dao = db.plannerRestoreDao()
                dao.insertSemesters(listOf(source))
                dao.insertSubjects(listOf(first, second))
                dao.insertSchedule(listOf(slot))
                dao.insertAttendance(listOf(mark))
                dao.insertExams(listOf(exam))
                dao.insertBoard(listOf(task))
                test(db, Fixture(source, first, second, slot, mark, exam, task, destination))
            }
        } finally { db.close() }
    }

    private data class Fixture(
        val source: Semester, val first: Subject, val second: Subject, val slot: SessionRecord,
        val mark: AttendanceRecord, val exam: Exam, val task: BoardItem, val destination: RolloverTermDetails,
    )
}
