package com.jaguar.attendancetracker.ui.insights

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class AttendanceTrendTest {
    private val today = LocalDate.of(2027, 1, 6)
    private val currentMonday = LocalDate.of(2027, 1, 4)
    private val otherSubject = id(2)
    private val subject = id(1)

    @Test fun mondayBoundariesAcrossNewYearAndFutureDatesAreDeterministic() {
        val first = currentMonday.minusWeeks(7)
        val before = currentMonday.minusDays(1)
        val records = listOf(
            row(1, first, AttendanceStatus.PRESENT),
            row(2, first.minusDays(1), AttendanceStatus.PRESENT),
            row(3, before, AttendanceStatus.ABSENT),
            row(4, currentMonday, AttendanceStatus.PRESENT),
            row(5, today, AttendanceStatus.ABSENT),
            row(6, today.plusDays(1), AttendanceStatus.PRESENT),
        )
        val trend = attendanceTrend(records, today)
        assertEquals(8, trend.size)
        assertEquals(first, trend.first().firstDate)
        assertEquals(AttendanceRate(1, 1), trend.first().rate)
        assertEquals(LocalDate.of(2026, 12, 28), trend[6].firstDate)
        assertEquals(before, trend[6].throughDate)
        assertEquals(AttendanceRate(0, 1), trend[6].rate)
        assertEquals(today, trend.last().throughDate)
        assertEquals(AttendanceRate(1, 2), trend.last().rate)
    }

    @Test fun onlyStoredPresentAndAbsentCountTowardRate() {
        val mixed = attendanceTrend(listOf(
            row(1, today, AttendanceStatus.PRESENT),
            row(2, today, AttendanceStatus.PRESENT),
            row(3, today, AttendanceStatus.ABSENT),
            row(4, today, null),
            row(5, today, AttendanceStatus.CANCELLED),
        ), today).last()
        assertEquals(AttendanceTrendWeek(currentMonday, today, 2, 1, 1, 1, 0), mixed)
        assertEquals(AttendanceRate(2, 3), mixed.rate)
    }

    @Test fun noRecordsOrOnlyUnmarkedAndCancelledHaveNoRate() {
        assertEquals(List(8) { null }, attendanceTrend(emptyList(), today).map { it.rate })
        val week = attendanceTrend(listOf(row(1, today, null), row(2, today, AttendanceStatus.CANCELLED)), today).last()
        assertEquals(1L, week.unmarked)
        assertEquals(1L, week.cancelled)
        assertNull(week.rate)
    }

    @Test fun chooseSubjectWithoutSilentlyDroppingOtherHistoryFromAllScope() {
        val records = listOf(row(1, today, AttendanceStatus.PRESENT),
            row(2, today, AttendanceStatus.ABSENT, chosen = otherSubject))
        assertEquals(AttendanceRate(1, 2), attendanceTrend(records, today).last().rate)
        assertEquals(AttendanceRate(1, 1), attendanceTrend(records, today, subject).last().rate)
        assertNull(attendanceTrend(records, today, id(99)).last().rate)
    }

    @Test fun ambiguousRegularMatchesUseSubjectSessionAndDateAndNeverInventOrCombine() {
        val session = id(30)
        val conflicting = listOf(
            row(1, today, AttendanceStatus.PRESENT, session = session),
            row(2, today, AttendanceStatus.ABSENT, session = session),
            row(3, today, null, session = session),
            row(4, today, AttendanceStatus.PRESENT, chosen = otherSubject, session = session),
            row(5, today.minusDays(1), AttendanceStatus.ABSENT, session = session),
            row(6, today, AttendanceStatus.PRESENT, session = null),
        )
        val week = attendanceTrend(conflicting, today).last()
        assertEquals(AttendanceRate(1, 2), week.rate)
        assertEquals(4L, week.excludedRows)
        assertEquals(0L, week.unmarked)
    }

    @Test fun extraClassesRemainIndependentAndDuplicatedExtraIdsAreFlagged() {
        val a = row(1, today, AttendanceStatus.PRESENT).copy(classType = ClassType.EXTRA, sessionId = null)
        val b = row(2, today, AttendanceStatus.ABSENT).copy(classType = ClassType.EXTRA, sessionId = null)
        val duplicate = row(3, today, AttendanceStatus.PRESENT).copy(classType = ClassType.EXTRA, sessionId = null)
        val week = attendanceTrend(listOf(a, b, duplicate, duplicate.copy(status = null)), today).last()
        assertEquals(AttendanceRate(1, 2), week.rate)
        assertEquals(2L, week.excludedRows)
    }

    private fun row(
        n: Int,
        date: LocalDate,
        status: AttendanceStatus?,
        chosen: UUID = subject,
        session: UUID? = id(n + 99),
    ) = AttendanceRecord(id(n), chosen, session, date, status, ClassType.REGULAR)

    private fun id(n: Int) = UUID(0, n.toLong())
}
