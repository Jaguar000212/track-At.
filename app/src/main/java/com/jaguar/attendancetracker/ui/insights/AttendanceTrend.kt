package com.jaguar.attendancetracker.ui.insights

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID

internal const val ATTENDANCE_TREND_WEEKS = 8

internal fun attendanceTrendStart(through: LocalDate): LocalDate =
    through.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .minusWeeks((ATTENDANCE_TREND_WEEKS - 1).toLong())

internal data class AttendanceRate(val attended: Long, val marked: Long)

internal data class AttendanceTrendWeek(
    val firstDate: LocalDate,
    val throughDate: LocalDate,
    val present: Long,
    val absent: Long,
    val unmarked: Long,
    val cancelled: Long,
    /** Saved rows excluded because their regular occurrence identity is ambiguous or missing. */
    val excludedRows: Long,
) {
    val rate: AttendanceRate?
        get() = (present + absent).takeIf { it > 0L }?.let { AttendanceRate(present, it) }
}

/**
 * Counts actual saved attendance rows for eight Monday–Sunday weeks, ending on the supplied
 * local day without looking into the future. A null subject scope means all visible history,
 * including already-archived subjects. This function never reads a timetable or creates rows.
 *
 * An extra class has its own stable record ID and does not share a regular slot. If multiple
 * regular rows claim the same subject/session/day, do not guess which mark is authoritative.
 */
internal fun attendanceTrend(
    records: Iterable<AttendanceRecord>,
    today: LocalDate,
    subjectId: UUID? = null,
): List<AttendanceTrendWeek> {
    val firstMonday = attendanceTrendStart(today)
    val weeks = Array(ATTENDANCE_TREND_WEEKS) { WeekCounters() }
    val regular = mutableMapOf<RegularOccurrence, AttendanceRecord>()
    val duplicated = mutableSetOf<RegularOccurrence>()
    val extras = mutableMapOf<UUID, AttendanceRecord>()
    val duplicateExtras = mutableSetOf<UUID>()

    fun counters(date: LocalDate) = weeks[(ChronoUnit.DAYS.between(firstMonday, date) / 7L).toInt()]
    for (record in records) {
        if (record.date !in firstMonday..today || (subjectId != null && record.subjectId != subjectId)) continue
        if (record.classType == ClassType.EXTRA) {
            if (record.id in duplicateExtras) counters(record.date).excludedRows++
            else {
                val prior = extras.putIfAbsent(record.id, record)
                if (prior != null) {
                    duplicateExtras.add(record.id)
                    extras.remove(record.id)
                    counters(prior.date).excludedRows++
                    counters(record.date).excludedRows++
                }
            }
        } else {
            val key = record.sessionId?.let { RegularOccurrence(record.subjectId, it, record.date) }
            when {
                key == null -> counters(record.date).excludedRows++
                key in duplicated -> counters(record.date).excludedRows++
                regular.putIfAbsent(key, record) != null -> {
                    duplicated.add(key)
                    regular.remove(key)
                    counters(record.date).excludedRows += 2
                }
            }
        }
    }

    for (record in regular.values + extras.values) counters(record.date).count(record.status)
    return weeks.mapIndexed { index, counters ->
        val first = firstMonday.plusWeeks(index.toLong())
        AttendanceTrendWeek(
            first, minOf(first.plusDays(6), today), counters.present, counters.absent,
            counters.unmarked, counters.cancelled, counters.excludedRows,
        )
    }
}

private data class RegularOccurrence(val subject: UUID, val session: UUID, val date: LocalDate)
private class WeekCounters(
    var present: Long = 0,
    var absent: Long = 0,
    var unmarked: Long = 0,
    var cancelled: Long = 0,
    var excludedRows: Long = 0,
) {
    fun count(status: AttendanceStatus?) {
        when (status) {
            AttendanceStatus.PRESENT -> present++
            AttendanceStatus.ABSENT -> absent++
            AttendanceStatus.CANCELLED -> cancelled++
            null -> unmarked++
        }
    }
}
