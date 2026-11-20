package com.jaguar.attendancetracker.ui.scheduleView

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.daos.SessionRecordDao
import com.jaguar.attendancetracker.backend.daos.TimetableGroup
import com.jaguar.attendancetracker.backend.daos.TimetableOrderRow
import com.jaguar.attendancetracker.backend.daos.timetableEntryOrder
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableReorderTest {
    @Test fun reordersOnlyExplicitWeekdayGroupWithoutChangingOppositeRows() = withFixture { db, f ->
        val dao = db.sessionRecordDao()
        val active = TimetableGroup(1, false)
        val archived = TimetableGroup(1, true)
        assertEquals(listOf(f.a, f.b, f.x, f.y, f.next), dao.observeTimetable().first()
            .sortedWith(timetableEntryOrder).map { it.order.session })
        assertTrue(dao.reorderGroups(dao.snapshot(1), mapOf(active to listOf(f.b.id, f.a.id))))
        val afterActive = dao.getAll().first().associateBy { it.id }
        assertEquals(f.x, afterActive[f.x.id])
        assertEquals(f.y, afterActive[f.y.id])
        assertEquals(f.next, afterActive[f.next.id])
        assertEquals(f.b.copy(orderNo = 1), afterActive[f.b.id])
        assertEquals(f.a.copy(orderNo = 3), afterActive[f.a.id])
        assertTrue(dao.reorderGroups(dao.snapshot(1), mapOf(archived to listOf(f.y.id, f.x.id))))
        val afterArchived = dao.getAll().first().associateBy { it.id }
        assertEquals(afterActive[f.a.id], afterArchived[f.a.id])
        assertEquals(afterActive[f.b.id], afterArchived[f.b.id])
        assertEquals(f.y.copy(orderNo = 0), afterArchived[f.y.id])
        assertEquals(f.x.copy(orderNo = 2), afterArchived[f.x.id])
        assertEquals(f.next, afterArchived[f.next.id])
    }

    @Test fun reorderRejectsCrossGroupAndArchiveChangedSinceDragWithoutWrites() = withFixture { db, f ->
        val dao = db.sessionRecordDao()
        val baseline = dao.snapshot(1)
        val active = TimetableGroup(1, false)
        assertFalse(dao.reorderGroups(baseline, mapOf(active to listOf(f.b.id, f.x.id))))
        assertEquals(f.rows.toSet(), dao.getAll().first().toSet())
        db.semesterDao().setArchived(f.active.id, true)
        assertFalse(dao.reorderGroups(baseline, mapOf(active to listOf(f.b.id, f.a.id))))
        assertEquals(f.rows.toSet(), dao.getAll().first().toSet())
        assertEquals(true, dao.observeTimetable().first().single { it.order.session.id == f.a.id }.order.archived)
    }

    private suspend fun SessionRecordDao.snapshot(
        day: Int,
    ): Map<Int, List<TimetableOrderRow>> = readTimetableOrder(setOf(day)).groupBy { it.session.dayOfWeek }

    private fun withFixture(test: suspend (AttendanceDatabase, Fixture) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val date = LocalDate.of(2025, 1, 6)
        val active = Semester(name = "Active fixture", startDate = date, endDate = null)
        val archived = Semester(name = "Archived fixture", startDate = date, endDate = null, isArchived = true)
        val ongoing = Subject(name = "Active subject", semesterId = active.id, startDate = date,
            isEnded = false, color = "PINK", professor = null, roomNo = null, notes = null)
        val historical = Subject(name = "Archived subject", semesterId = archived.id, startDate = date,
            isEnded = false, color = "PINK", professor = null, roomNo = null, notes = null)
        fun row(subject: Subject, order: Int, weekday: Int = 1) =
            SessionRecord(subjectId = subject.id, orderNo = order, dayOfWeek = weekday, startDate = date)
        val f = Fixture(active, row(ongoing, 0), row(ongoing, 5), row(historical, 0), row(historical, 2), row(ongoing, 7, 2))
        try {
            runBlocking {
                val dao = db.plannerRestoreDao()
                dao.insertSemesters(listOf(active, archived))
                dao.insertSubjects(listOf(ongoing, historical))
                dao.insertSchedule(f.rows)
                test(db, f)
            }
        } finally { db.close() }
    }

    private data class Fixture(
        val active: Semester, val a: SessionRecord, val b: SessionRecord,
        val x: SessionRecord, val y: SessionRecord, val next: SessionRecord,
    ) {
        val rows: List<SessionRecord> get() = listOf(a, b, x, y, next)
    }
}
