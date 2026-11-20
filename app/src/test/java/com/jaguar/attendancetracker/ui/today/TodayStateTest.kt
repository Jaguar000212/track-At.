package com.jaguar.attendancetracker.ui.today

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class TodayStateTest {
    private val date = LocalDate.of(2026, 11, 20)
    private val subject = Subject(
        name = "Math", semesterId = UUID.randomUUID(), startDate = date.minusMonths(1),
        isEnded = false, color = "PINK", professor = null, roomNo = null, notes = null,
    )

    @Test
    fun emptyTimetableStillShowsSavedExtraClassesAndCounts() {
        val present = record(AttendanceStatus.PRESENT)
        val absent = record(AttendanceStatus.ABSENT)
        val unmarked = record()
        val otherDay = record(recordDate = date.minusDays(1))
        val state = todaySnapshot(date, listOf(present, absent, unmarked, otherDay), emptyList())

        assertEquals(listOf(present, absent, unmarked), state.records)
        assertEquals(2, state.markedCount)
        assertEquals(1, state.pendingCount)
        assertEquals(emptyList<SessionsWithSubject>(), state.scheduled)
    }

    @Test
    fun eligibleUnrecordedSessionsAreOrderedAndCancelledSessionDoesNotReappear() {
        val cancelled = session(order = 0)
        val later = session(order = 7)
        val first = session(order = 1, start = date)
        val otherWeekday = session(order = 2, day = date.minusDays(1).dayOfWeek.value)
        val future = session(order = 3, start = date.plusDays(1))
        val cancelledRecord = record(AttendanceStatus.CANCELLED, cancelled.session.id)
        val state = todaySnapshot(
            date, listOf(cancelledRecord), listOf(later, cancelled, future, otherWeekday, first),
        )

        assertEquals(listOf(first, later), state.scheduled)
        assertEquals(listOf(cancelledRecord), state.records)
        assertEquals(0, state.markedCount)
        assertEquals(2, state.pendingCount)
    }

    @Test
    fun priorAttendanceDoesNotSuppressTodaysRecurringSlot() {
        val slot = session()
        val past = record(AttendanceStatus.PRESENT, slot.session.id, date.minusWeeks(1))
        val state = todaySnapshot(date, listOf(past), listOf(slot))

        assertEquals(emptyList<AttendanceWithSubject>(), state.records)
        assertEquals(listOf(slot), state.scheduled)
    }

    @Test
    fun pendingCountStaysStableWhenDayViewMaterializesScheduledRows() {
        val slot = session()
        val timetable = listOf(slot)
        val beforeOpeningDay = todaySnapshot(date, emptyList(), timetable)
        val afterOpeningDay = todaySnapshot(date, listOf(record(sessionId = slot.session.id)), timetable)
        val afterMarking = todaySnapshot(
            date, listOf(record(AttendanceStatus.PRESENT, slot.session.id)), timetable,
        )
        val afterCancelling = todaySnapshot(
            date, listOf(record(AttendanceStatus.CANCELLED, slot.session.id)), timetable,
        )

        assertEquals(1, beforeOpeningDay.pendingCount)
        assertEquals(1, afterOpeningDay.pendingCount)
        assertEquals(0, afterMarking.pendingCount)
        assertEquals(0, afterCancelling.pendingCount)
    }

    private fun session(
        order: Int = 0, day: Int = date.dayOfWeek.value, start: LocalDate = date.minusDays(1),
    ) = SessionsWithSubject(
        SessionRecord(subjectId = subject.id, orderNo = order, dayOfWeek = day, startDate = start),
        subject,
    )

    private fun record(
        status: AttendanceStatus? = null, sessionId: UUID? = null, recordDate: LocalDate = date,
    ) = AttendanceWithSubject(
        AttendanceRecord(
            subjectId = subject.id, sessionId = sessionId, date = recordDate, status = status,
            classType = if (sessionId == null) ClassType.EXTRA else ClassType.REGULAR,
        ),
        subject,
    )
}
