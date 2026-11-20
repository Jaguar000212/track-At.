package com.jaguar.attendancetracker.ui.leave

import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.ui.leave.LeaveEstimateBasis.CONFIRMED_TIMETABLE
import com.jaguar.attendancetracker.ui.leave.LeaveEstimateBasis.MANUAL_ESTIMATE
import com.jaguar.attendancetracker.ui.leave.LeaveTargetRelation.AT_OR_ABOVE
import com.jaguar.attendancetracker.ui.leave.LeaveTargetRelation.BELOW
import com.jaguar.attendancetracker.ui.leave.LeaveTimetableCoverage.COMPLETE
import com.jaguar.attendancetracker.ui.leave.LeaveTimetableCoverage.PARTIAL
import com.jaguar.attendancetracker.ui.leave.LeaveTimetableCoverage.UNKNOWN
import com.jaguar.attendancetracker.ui.leave.LeaveUnknownReason.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class LeaveProjectionTest {
    private val today = LocalDate.of(2026, 11, 20) // Friday
    private val friday = today
    private val saturday = today.plusDays(1)
    private val sunday = today.plusDays(2)
    private val subject = id(1)
    private val other = id(2)
    private val interval = LeaveDateRange(friday, sunday)
    private val baseline = LeaveCounts(8, 10)

    @Test fun inclusiveRangeUsesSaturdaySlotsAndCountsSharedWeeklyUuidOnEachDate() {
        val events = listOf(
            slot(1, friday),
            slot(1, saturday), // Same UUID and different date is a different occurrence.
            slot(2, sunday),
            slot(2, sunday), // Same identity and status counts only once.
            slot(3, friday.minusDays(1)),
            slot(4, sunday.plusDays(1)),
        )
        assertEquals(LeaveProjection.Estimate(baseline, LeaveCounts(8, 13), 3, 75, BELOW, CONFIRMED_TIMETABLE),
            projection(occurrences = events))
    }

    @Test fun onlyUnmarkedMatchesCountAndOtherSubjectCannotSuppressSharedUuid() {
        val sameDay = LeaveDateRange(today, today)
        val events = listOf(
            slot(1, today),
            slot(1, today, AttendanceStatus.CANCELLED, subjectId = other),
            slot(2, today, AttendanceStatus.PRESENT),
            slot(3, today, AttendanceStatus.ABSENT),
            slot(4, today, AttendanceStatus.CANCELLED),
            slot(5, today, subjectId = other),
        )
        assertEquals(LeaveProjection.Estimate(baseline, LeaveCounts(8, 11), 1, 75, BELOW, CONFIRMED_TIMETABLE),
            projection(range = sameDay, occurrences = events))
    }

    @Test fun weekendOrEmptyTimetableIsZeroOnlyIfCoverageIsExplicitlyComplete() {
        val weekend = LeaveDateRange(saturday, sunday)
        assertEquals(LeaveProjection.Estimate(baseline, baseline, 0, 75, AT_OR_ABOVE, CONFIRMED_TIMETABLE),
            projection(range = weekend))
        assertEquals(INCOMPLETE_TIMETABLE, reason(projection(range = weekend, coverage = UNKNOWN)))
        assertEquals(INCOMPLETE_TIMETABLE,
            reason(projection(range = weekend, coverage = PARTIAL, occurrences = listOf(slot(1, sunday)))))
    }

    @Test fun conflictingDuplicateAndUnresolvedRescheduleAreUnknownUntilExplicitManualEstimate() {
        val contradictory = listOf(slot(1, friday), slot(1, friday, AttendanceStatus.CANCELLED))
        val moved = listOf(slot(1, friday, unresolved = true), slot(2, sunday))
        for (events in listOf(contradictory, moved)) {
            assertEquals(AMBIGUOUS_OCCURRENCES, reason(projection(occurrences = events)))
            assertEquals(LeaveProjection.Estimate(baseline, LeaveCounts(8, 12), 2, 75, BELOW, MANUAL_ESTIMATE),
                projection(occurrences = events, manual = 2))
        }
        assertEquals(LeaveProjection.Estimate(baseline, baseline, 0, 75, AT_OR_ABOVE, MANUAL_ESTIMATE),
            projection(coverage = UNKNOWN, manual = 0))
        assertEquals(INVALID_MANUAL_MISSES, reason(projection(manual = -1)))
    }

    @Test fun rangeRejectsPastReversedAndOverLimitWithoutClamping() {
        assertEquals(PAST_RANGE, reason(projection(range = LeaveDateRange(today.minusDays(1), today), manual = 2)))
        assertEquals(INVALID_RANGE, reason(projection(range = LeaveDateRange(sunday, friday))))
        assertEquals(RANGE_TOO_LONG,
            reason(projection(range = LeaveDateRange(today, today.plusDays(MAX_LEAVE_RANGE_DAYS)))))
        assertEquals(AT_OR_ABOVE,
            (projection(range = LeaveDateRange(today, today.plusDays(MAX_LEAVE_RANGE_DAYS - 1))) as
                LeaveProjection.Estimate).relation)
        assertEquals(RANGE_TOO_LONG, reason(projection(range = LeaveDateRange(today, LocalDate.MAX))))
    }

    @Test fun malformedOrUnprovenCountsAndNoHistoryRemainUnknownWithManualInput() {
        assertEquals(MISSING_OR_UNTRUSTED_COUNTS, reason(projection(counts = null, manual = 2)))
        assertEquals(LeaveProjection.Unknown(NO_ATTENDANCE_HISTORY, LeaveCounts(0, 0), 75),
            projection(counts = LeaveCounts(0, 0), manual = 2))
        for (counts in listOf(LeaveCounts(-1, 10), LeaveCounts(4, 3), LeaveCounts(0, -1),
            LeaveCounts(0, Long.MAX_VALUE))) {
            assertEquals(INVALID_COUNTS, reason(projection(counts = counts)))
        }
        assertEquals(INVALID_TARGET, reason(projection(target = -1)))
        assertEquals(INVALID_TARGET, reason(projection(target = 101)))
    }

    @Test fun targetsUseExactCrossMultiplicationAndStayBounded() {
        assertEquals(AT_OR_ABOVE,
            (projection(counts = LeaveCounts(3, 4), target = 75) as LeaveProjection.Estimate).relation)
        assertEquals(BELOW,
            (projection(counts = LeaveCounts(3, 4), target = 76) as LeaveProjection.Estimate).relation)
        assertEquals(BELOW,
            (projection(counts = LeaveCounts(7499, 10000), target = 75) as LeaveProjection.Estimate).relation)
        assertEquals(AT_OR_ABOVE,
            (projection(counts = LeaveCounts(0, 1), target = 0) as LeaveProjection.Estimate).relation)
        assertEquals(AT_OR_ABOVE,
            (projection(counts = LeaveCounts(4, 4), target = 100) as LeaveProjection.Estimate).relation)
        val max = Int.MAX_VALUE.toLong()
        val large = projection(counts = LeaveCounts(max, max), target = 100, manual = Int.MAX_VALUE)
        assertEquals(LeaveProjection.Estimate(LeaveCounts(max, max), LeaveCounts(max, 2 * max),
            max, 100, BELOW, MANUAL_ESTIMATE), large)
    }

    private fun projection(
        range: LeaveDateRange = interval,
        counts: LeaveCounts? = baseline,
        target: Int = 75,
        coverage: LeaveTimetableCoverage = COMPLETE,
        occurrences: List<LeaveOccurrence> = emptyList(),
        manual: Int? = null,
    ) = projectLeave(subject, range, today, counts, target, coverage, occurrences, manual)

    private fun slot(n: Int, date: LocalDate, status: AttendanceStatus? = null,
        subjectId: UUID = subject, unresolved: Boolean = false) =
        LeaveOccurrence(subjectId, id(n), date, status, unresolved)

    private fun reason(projection: LeaveProjection) = (projection as LeaveProjection.Unknown).reason

    private fun id(n: Int) = UUID(0, n.toLong())
}
