package com.jaguar.attendancetracker.ui.leave

import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal const val MAX_LEAVE_RANGE_DAYS = 366L

internal data class LeaveDateRange(val first: LocalDate, val last: LocalDate)
internal data class LeaveCounts(val attended: Long, val total: Long)

internal enum class LeaveTimetableCoverage {
    /** Caller has evidence of all effective sessions in this subject and full range. */
    COMPLETE,
    /** Some timetable or enrollment facts exist, but the full range is not confirmed. */
    PARTIAL,
    UNKNOWN,
}

internal data class LeaveOccurrence(
    val subjectId: UUID,
    val sessionId: UUID,
    val date: LocalDate,
    val status: AttendanceStatus?,
    /** An ambiguous moved/replaced slot must not be mistaken for a normal occurrence. */
    val unresolvedScheduleChange: Boolean = false,
)

internal enum class LeaveEstimateBasis { CONFIRMED_TIMETABLE, MANUAL_ESTIMATE }
internal enum class LeaveTargetRelation { AT_OR_ABOVE, BELOW }
internal enum class LeaveUnknownReason {
    INVALID_RANGE,
    PAST_RANGE,
    RANGE_TOO_LONG,
    INVALID_TARGET,
    MISSING_OR_UNTRUSTED_COUNTS,
    INVALID_COUNTS,
    NO_ATTENDANCE_HISTORY,
    INVALID_MANUAL_MISSES,
    INCOMPLETE_TIMETABLE,
    AMBIGUOUS_OCCURRENCES,
}

internal sealed interface LeaveProjection {
    data class Estimate(
        val before: LeaveCounts,
        val after: LeaveCounts,
        val plannedMisses: Long,
        val targetPercent: Int,
        val relation: LeaveTargetRelation,
        val basis: LeaveEstimateBasis,
    ) : LeaveProjection

    data class Unknown(
        val reason: LeaveUnknownReason,
        val before: LeaveCounts? = null,
        val targetPercent: Int? = null,
    ) : LeaveProjection
}

/**
 * A prospective, read-only scenario for one subject. Does not create absences, edit attendance,
 * or judge whether someone ought to miss class. [current] must be verified counted attendance
 * (PRESENT / ABSENT, including extra classes); pass null if the real history isn't established.
 *
 * The caller supplies effective occurrences, after enrollment, semester and session boundaries
 * and schedule exceptions are considered. A weekly template by itself does not establish
 * [coverage]. [manualMisses] is an explicit estimate of remaining unmarked classes this subject
 * would miss in the whole range, not a replacement for any already-recorded attendance.
 */
internal fun projectLeave(
    subjectId: UUID,
    range: LeaveDateRange,
    today: LocalDate,
    current: LeaveCounts?,
    targetPercent: Int,
    coverage: LeaveTimetableCoverage,
    occurrences: Iterable<LeaveOccurrence> = emptyList(),
    manualMisses: Int? = null,
): LeaveProjection {
    val validTarget = targetPercent.takeIf { it in 0..100 }
    val validCounts = current?.takeIf { it.total in 0..Int.MAX_VALUE.toLong() && it.attended in 0..it.total }
    fun unknown(reason: LeaveUnknownReason) = LeaveProjection.Unknown(reason, validCounts, validTarget)

    if (range.last.isBefore(range.first)) return unknown(LeaveUnknownReason.INVALID_RANGE)
    if (range.first.isBefore(today)) return unknown(LeaveUnknownReason.PAST_RANGE)
    if (ChronoUnit.DAYS.between(range.first, range.last) >= MAX_LEAVE_RANGE_DAYS) {
        return unknown(LeaveUnknownReason.RANGE_TOO_LONG)
    }
    if (validTarget == null) return unknown(LeaveUnknownReason.INVALID_TARGET)
    if (current == null) return unknown(LeaveUnknownReason.MISSING_OR_UNTRUSTED_COUNTS)
    val before = validCounts ?: return unknown(LeaveUnknownReason.INVALID_COUNTS)
    if (before.total == 0L) return unknown(LeaveUnknownReason.NO_ATTENDANCE_HISTORY)

    val misses: Long
    val basis: LeaveEstimateBasis
    if (manualMisses != null) {
        if (manualMisses < 0) return unknown(LeaveUnknownReason.INVALID_MANUAL_MISSES)
        misses = manualMisses.toLong()
        basis = LeaveEstimateBasis.MANUAL_ESTIMATE
    } else {
        if (coverage != LeaveTimetableCoverage.COMPLETE) {
            return unknown(LeaveUnknownReason.INCOMPLETE_TIMETABLE)
        }
        val byIdentity = mutableMapOf<OccurrenceIdentity, LeaveOccurrence>()
        for (occurrence in occurrences) {
            if (occurrence.subjectId != subjectId || occurrence.date !in range.first..range.last) continue
            if (occurrence.unresolvedScheduleChange) return unknown(LeaveUnknownReason.AMBIGUOUS_OCCURRENCES)

            val key = OccurrenceIdentity(occurrence.subjectId, occurrence.sessionId, occurrence.date)
            val prior = byIdentity.putIfAbsent(key, occurrence)
            if (prior != null && prior != occurrence) {
                return unknown(LeaveUnknownReason.AMBIGUOUS_OCCURRENCES)
            }
        }
        // A null status is an actual unmarked effective occurrence. All recorded statuses,
        // including CANCELLED, are excluded from the additional misses.
        misses = byIdentity.values.count { it.status == null }.toLong()
        basis = LeaveEstimateBasis.CONFIRMED_TIMETABLE
    }

    val after = LeaveCounts(before.attended, before.total + misses)
    // Bounds keep both products well below Long.MAX_VALUE, even for Int.MAX_VALUE misses.
    val relation = if (after.attended * 100L >= after.total * validTarget.toLong()) {
        LeaveTargetRelation.AT_OR_ABOVE
    } else {
        LeaveTargetRelation.BELOW
    }
    return LeaveProjection.Estimate(before, after, misses, validTarget, relation, basis)
}

private data class OccurrenceIdentity(val subjectId: UUID, val sessionId: UUID, val date: LocalDate)
