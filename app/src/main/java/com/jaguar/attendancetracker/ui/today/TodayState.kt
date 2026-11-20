package com.jaguar.attendancetracker.ui.today

import com.jaguar.attendancetracker.backend.entities.AttendanceWithSubject
import com.jaguar.attendancetracker.backend.entities.SessionsWithSubject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import java.time.LocalDate

internal sealed interface TodayState {
    val date: LocalDate

    data class Loading(override val date: LocalDate) : TodayState
    data class Error(override val date: LocalDate) : TodayState
    data class Ready(
        override val date: LocalDate,
        val records: List<AttendanceWithSubject>,
        val scheduled: List<SessionsWithSubject>,
    ) : TodayState {
        val pendingCount = records.count { it.record.status == null } + scheduled.size
        val markedCount = records.count {
            it.record.status == AttendanceStatus.PRESENT || it.record.status == AttendanceStatus.ABSENT
        }
    }
}

// Match Day View's eligibility. A saved record (even a cancelled one) supersedes the
// corresponding timetable slot. Extra classes have no session ID and remain independent.
internal fun todaySnapshot(
    date: LocalDate,
    records: List<AttendanceWithSubject>,
    timetable: List<SessionsWithSubject>,
): TodayState.Ready {
    val todayRecords = records.filter { it.record.date == date }
    val savedSessions = todayRecords.mapNotNull { it.record.sessionId }.toHashSet()
    val scheduled = timetable.filter {
        it.session.dayOfWeek == date.dayOfWeek.value &&
            !it.session.startDate.isAfter(date) && it.session.id !in savedSessions
    }.sortedBy { it.session.orderNo }
    return TodayState.Ready(date, todayRecords, scheduled)
}
