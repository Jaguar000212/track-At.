package com.jaguar.attendancetracker.ui.imExport

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import java.util.UUID

internal enum class PlannerMergeOutcome {
    INSERT, UNCHANGED, KEEP_LOCAL_CONFLICT, BLOCKED_DEPENDENCY,
}

internal enum class PlannerMergeBlockReason {
    SEMESTER_CONFLICT,
    SUBJECT_CONFLICT,
    SUBJECT_BLOCKED,
    SESSION_CONFLICT,
    SESSION_BLOCKED,
    SESSION_SUBJECT_MISMATCH,
    WOULD_MISLINK_LOCAL_ATTENDANCE,
    INCONSISTENT_LOCAL_TOTALS,
    INCONSISTENT_INCOMING_TOTALS,
}

internal data class PlannerMergeBlock(val reason: PlannerMergeBlockReason, val relatedId: UUID)

internal data class PlannerMergeRow<T>(
    val incoming: T,
    val outcome: PlannerMergeOutcome,
    val dependency: PlannerMergeBlock? = null,
)

internal data class PlannerSubjectTotals(val totalClasses: Int = 0, val attendedClasses: Int = 0) {
    fun plus(records: PlannerSubjectTotals) = PlannerSubjectTotals(
        Math.addExact(totalClasses, records.totalClasses),
        Math.addExact(attendedClasses, records.attendedClasses),
    )
}

internal data class PlannerMergePreview(
    val semesters: List<PlannerMergeRow<Semester>>,
    val subjects: List<PlannerMergeRow<Subject>>,
    val schedule: List<PlannerMergeRow<SessionRecord>>,
    val attendance: List<PlannerMergeRow<AttendanceRecord>>,
    val board: List<PlannerMergeRow<BoardItem>>,
    val exams: List<PlannerMergeRow<Exam>>,
    val totalsAfterAcceptedAttendance: Map<UUID, PlannerSubjectTotals>,
)

/**
 * Plans a conservative keep-local preview from two complete snapshots. Incoming has already
 * passed the planner decoder. This does not apply changes or authorize any later write.
 *
 * Subject user fields compare separately from cached aggregates. An inconsistent
 * stored baseline is never reset: it blocks affected attendance instead. The
 * write phase must re-plan inside a transaction and use only approved inserts
 * and these two derived columns.
 */
internal object PlannerMergePlanner {
    fun preview(local: PlannerBackup, incoming: PlannerBackup): PlannerMergePreview {
        val localData = local.attendance
        val incomingData = incoming.attendance
        val localSchedule = requireNotNull(localData.schedule).byId(SessionRecord::id)
        val localAttendance = requireNotNull(localData.attendance).byId(AttendanceRecord::id)
        val incomingAttendance = requireNotNull(incomingData.attendance)
        val localSubjects = requireNotNull(localData.subjects).byId(Subject::id)
        val incomingSubjects = requireNotNull(incomingData.subjects).byId(Subject::id)
        val localTotals = localAttendance.values.countedTotals()
        val incomingTotals = incomingAttendance.countedTotals()
        fun incomingTotalsBlock(id: UUID): PlannerMergeBlock? =
            if (incomingSubjects.getValue(id).storedTotals() != incomingTotals.totals(id)) {
                PlannerMergeBlock(PlannerMergeBlockReason.INCONSISTENT_INCOMING_TOTALS, id)
            } else null
        fun localTotalsBlock(id: UUID): PlannerMergeBlock? =
            if (localSubjects[id]?.let { it.storedTotals() != localTotals.totals(id) } == true) {
                PlannerMergeBlock(PlannerMergeBlockReason.INCONSISTENT_LOCAL_TOTALS, id)
            } else null
        val localAttendanceBySession = localAttendance.values.filter { it.sessionId != null }
            .groupBy { it.sessionId }
        val semesters = plan(
            requireNotNull(incomingData.semesters), requireNotNull(localData.semesters).byId(Semester::id),
            Semester::id,
        )
        val semesterOutcomes = semesters.associate { it.incoming.id to it.outcome }
        fun semesterBlock(id: UUID): PlannerMergeBlock? =
            if (semesterOutcomes.getValue(id) == PlannerMergeOutcome.KEEP_LOCAL_CONFLICT) {
                PlannerMergeBlock(PlannerMergeBlockReason.SEMESTER_CONFLICT, id)
            } else null
        val subjects = plan(
            incomingSubjects.values.toList(), localSubjects, Subject::id,
            same = { incomingItem, localItem -> incomingItem.authoredFields() == localItem.authoredFields() },
        ) { semesterBlock(it.semesterId) ?: incomingTotalsBlock(it.id) }
        val subjectDecisions = subjects.associateBy { it.incoming.id }
        fun subjectBlock(id: UUID): PlannerMergeBlock? = when (subjectDecisions.getValue(id).outcome) {
            PlannerMergeOutcome.KEEP_LOCAL_CONFLICT ->
                PlannerMergeBlock(PlannerMergeBlockReason.SUBJECT_CONFLICT, id)
            PlannerMergeOutcome.BLOCKED_DEPENDENCY ->
                PlannerMergeBlock(PlannerMergeBlockReason.SUBJECT_BLOCKED, id)
            // Preserve an identical local subject as unchanged, but don't attach new records to
            // its tree if the incoming and retained semester representations disagree.
            PlannerMergeOutcome.UNCHANGED -> semesterBlock(subjectDecisions.getValue(id).incoming.semesterId)
            PlannerMergeOutcome.INSERT -> null
        }

        val schedule = plan(requireNotNull(incomingData.schedule), localSchedule, SessionRecord::id) { item ->
            subjectBlock(item.subjectId)
                ?: localAttendanceBySession[item.id]?.takeIf { rows ->
                    rows.any { row -> row.subjectId != item.subjectId }
                }?.let { PlannerMergeBlock(PlannerMergeBlockReason.WOULD_MISLINK_LOCAL_ATTENDANCE, item.id) }
        }
        val scheduleOutcomes = schedule.associate { it.incoming.id to it.outcome }
        val effectiveSchedule = localSchedule + schedule.filter { it.outcome == PlannerMergeOutcome.INSERT }
            .associate { it.incoming.id to it.incoming }

        val attendance = plan(incomingAttendance, localAttendance, AttendanceRecord::id) { item ->
            subjectBlock(item.subjectId) ?: localTotalsBlock(item.subjectId)
                ?: incomingTotalsBlock(item.subjectId) ?: item.sessionId?.let { sessionId ->
                when (scheduleOutcomes[sessionId]) {
                    PlannerMergeOutcome.KEEP_LOCAL_CONFLICT ->
                        PlannerMergeBlock(PlannerMergeBlockReason.SESSION_CONFLICT, sessionId)
                    PlannerMergeOutcome.BLOCKED_DEPENDENCY ->
                        PlannerMergeBlock(PlannerMergeBlockReason.SESSION_BLOCKED, sessionId)
                    else -> {
                        val effectiveSession = effectiveSchedule[sessionId]
                        if (effectiveSession != null && effectiveSession.subjectId != item.subjectId) {
                            PlannerMergeBlock(PlannerMergeBlockReason.SESSION_SUBJECT_MISMATCH, sessionId)
                        } else null
                    }
                }
            }
        }
        val board = plan(incoming.board, local.board.byId(BoardItem::id), BoardItem::id)
        val exams = incoming.exams?.let { incomingExams ->
            val localExams = requireNotNull(local.exams).byId(Exam::id)
            plan(incomingExams, localExams, Exam::id) { item -> item.semesterId?.let(::semesterBlock) }
        }.orEmpty()
        val acceptedMarks = attendance.filter { it.outcome == PlannerMergeOutcome.INSERT }
            .map { it.incoming }.countedTotals()
        val updates = acceptedMarks.mapValues { (id, counts) -> localTotals.totals(id).plus(counts) }
        return PlannerMergePreview(semesters, subjects, schedule, attendance, board, exams, updates)
    }

    private fun Subject.authoredFields() = copy(totalClasses = 0, attendedClasses = 0)
    private fun Subject.storedTotals() = PlannerSubjectTotals(totalClasses, attendedClasses)
    private fun Map<UUID, PlannerSubjectTotals>.totals(id: UUID) = get(id) ?: PlannerSubjectTotals()

    private fun Iterable<AttendanceRecord>.countedTotals(): Map<UUID, PlannerSubjectTotals> {
        val result = mutableMapOf<UUID, PlannerSubjectTotals>()
        for (row in this) {
            val present = when (row.status) {
                AttendanceStatus.PRESENT -> 1
                AttendanceStatus.ABSENT -> 0
                else -> continue
            }
            result[row.subjectId] = result.totals(row.subjectId).plus(PlannerSubjectTotals(1, present))
        }
        return result
    }

    private fun <T> plan(
        incoming: List<T>,
        local: Map<UUID, T>,
        id: (T) -> UUID,
        same: (T, T) -> Boolean = { a, b -> a == b },
        dependency: (T) -> PlannerMergeBlock? = { null },
    ): List<PlannerMergeRow<T>> = incoming.byId(id).entries.sortedBy { it.key }.map { (key, item) ->
        val retained = local[key]
        if (retained != null) {
            val outcome = if (same(item, retained)) PlannerMergeOutcome.UNCHANGED else PlannerMergeOutcome.KEEP_LOCAL_CONFLICT
            PlannerMergeRow(item, outcome)
        } else {
            val block = dependency(item)
            if (block == null) PlannerMergeRow(item, PlannerMergeOutcome.INSERT)
            else PlannerMergeRow(item, PlannerMergeOutcome.BLOCKED_DEPENDENCY, block)
        }
    }

    private fun <T> List<T>.byId(id: (T) -> UUID): Map<UUID, T> = associateBy(id).also {
        require(it.size == size) { "Planner preview requires unique entity IDs" }
    }
}
