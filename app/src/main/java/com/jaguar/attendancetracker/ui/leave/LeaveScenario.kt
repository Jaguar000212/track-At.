package com.jaguar.attendancetracker.ui.leave

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal data class LeaveSnapshot(
    val semesters: List<Semester>,
    val subjects: List<Subject>,
    val timetable: List<SessionRecord>,
    val records: List<AttendanceRecord>,
    val exams: List<Exam>,
)

internal data class LeaveSubjectFacts(
    val subject: Subject,
    val verifiedCounts: LeaveCounts?,
    val knownTimetableMisses: Int,
    val knownUnmarkedExtras: Int,
) {
    fun estimate(range: LeaveDateRange, today: LocalDate, manualMisses: Int?) = projectLeave(
        subject.id, range, today, verifiedCounts, subject.minAttendance,
        coverage = LeaveTimetableCoverage.PARTIAL, manualMisses = manualMisses,
    )
}

internal data class LeaveFacts(
    val validRange: Boolean,
    val subjects: List<LeaveSubjectFacts> = emptyList(),
    val overlappingExams: List<Exam> = emptyList(),
    val undatedExams: List<Exam> = emptyList(),
)

internal fun validLeaveRange(range: LeaveDateRange, today: LocalDate): Boolean =
    !range.first.isBefore(today) && !range.last.isBefore(range.first) &&
        ChronoUnit.DAYS.between(range.first, range.last) < MAX_LEAVE_RANGE_DAYS

/** Weekly templates describe entries on record, not confirmed coverage or actual total misses. */
internal fun leaveFacts(snapshot: LeaveSnapshot, range: LeaveDateRange, today: LocalDate): LeaveFacts {
    if (!validLeaveRange(range, today)) return LeaveFacts(validRange = false)
    val terms = snapshot.semesters.associateBy { it.id }
    val sessions = snapshot.timetable.groupBy { it.subjectId }
    val attendance = snapshot.records.groupBy { it.subjectId }

    val studentsSubjects = snapshot.subjects.mapNotNull { subject ->
        val term = terms[subject.semesterId]?.takeIf { !it.isArchived } ?: return@mapNotNull null
        if (subject.isEnded) return@mapNotNull null
        val first = maxOf(range.first, term.startDate, subject.startDate)
        val last = minOf(range.last, term.endDate ?: range.last)
        if (first > last) return@mapNotNull null

        val records = attendance[subject.id].orEmpty()
        val present = records.count { it.status == AttendanceStatus.PRESENT }.toLong()
        val counted = present + records.count { it.status == AttendanceStatus.ABSENT }
        // Imported partial history can disagree with the stored cache; do not substitute
        // a zero or label either side as verified. Existing marked records are never edited.
        val attendanceBySessionAndDate = records.filter { it.sessionId != null && it.classType == ClassType.REGULAR }
            .groupBy { it.sessionId to it.date }
        val ambiguousHistory = attendanceBySessionAndDate.values.any { entries ->
            entries.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.ABSENT } > 1 ||
                entries.map { it.status }.distinct().size > 1
        }
        val counts = LeaveCounts(present, counted).takeIf {
            !ambiguousHistory && subject.attendedClasses.toLong() == present && subject.totalClasses.toLong() == counted
        }
        val weekly = sessions[subject.id].orEmpty().groupBy { it.dayOfWeek }
        val knownTimed = (0L..ChronoUnit.DAYS.between(first, last)).sumOf { offset ->
            val date = first.plusDays(offset)
            weekly[date.dayOfWeek.value].orEmpty().count { slot ->
                !slot.startDate.isAfter(date) &&
                    attendanceBySessionAndDate[slot.id to date].orEmpty().none { it.status != null }
            }
        }
        // An extra class has a stable AttendanceRecord UUID, not a synthetic timetable ID.
        val knownExtras = records.filter {
            it.classType == ClassType.EXTRA && it.sessionId == null && it.status == null && it.date in first..last
        }.distinctBy { it.id }.size
        LeaveSubjectFacts(subject, counts, knownTimed, knownExtras)
    }.sortedWith(compareBy<LeaveSubjectFacts> { it.subject.name }.thenBy { it.subject.id })

    return LeaveFacts(
        validRange = true,
        subjects = studentsSubjects,
        overlappingExams = snapshot.exams.filter { it.date?.let { date -> date in range.first..range.last } == true }
            .sortedWith(compareBy<Exam> { it.date }.thenBy { it.title }.thenBy { it.id }),
        undatedExams = snapshot.exams.filter { it.date == null }
            .sortedWith(compareBy<Exam> { it.title }.thenBy { it.id }),
    )
}
