package com.jaguar.attendancetracker.ui.imExport

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
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
}

internal data class PlannerMergeBlock(val reason: PlannerMergeBlockReason, val relatedId: UUID)

internal data class PlannerMergeRow<T>(
    val incoming: T,
    val outcome: PlannerMergeOutcome,
    val dependency: PlannerMergeBlock? = null,
)

internal data class PlannerMergePreview(
    val semesters: List<PlannerMergeRow<Semester>>,
    val subjects: List<PlannerMergeRow<Subject>>,
    val schedule: List<PlannerMergeRow<SessionRecord>>,
    val attendance: List<PlannerMergeRow<AttendanceRecord>>,
    val board: List<PlannerMergeRow<BoardItem>>,
)

/**
 * Plans a conservative keep-local preview from two complete snapshots. Incoming has already
 * passed the planner decoder. This does not apply changes or authorize any later write.
 *
 * Same-ID rows use exact stored field equality, including attendance totals on Subject.
 * The future write phase must separately decide derived-stat recomputation and re-plan inside
 * its own transaction; this preview has no side effects and never rewrites a reference.
 */
internal object PlannerMergePlanner {
    fun preview(local: PlannerBackup, incoming: PlannerBackup): PlannerMergePreview {
        val localData = local.attendance
        val incomingData = incoming.attendance
        val localSchedule = requireNotNull(localData.schedule).byId(SessionRecord::id)
        val localAttendance = requireNotNull(localData.attendance).byId(AttendanceRecord::id)
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
            requireNotNull(incomingData.subjects), requireNotNull(localData.subjects).byId(Subject::id),
            Subject::id,
        ) { semesterBlock(it.semesterId) }
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

        val attendance = plan(requireNotNull(incomingData.attendance), localAttendance, AttendanceRecord::id) { item ->
            subjectBlock(item.subjectId) ?: item.sessionId?.let { sessionId ->
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
        return PlannerMergePreview(semesters, subjects, schedule, attendance, board)
    }

    private fun <T> plan(
        incoming: List<T>,
        local: Map<UUID, T>,
        id: (T) -> UUID,
        dependency: (T) -> PlannerMergeBlock? = { null },
    ): List<PlannerMergeRow<T>> = incoming.byId(id).entries.sortedBy { it.key }.map { (key, item) ->
        val retained = local[key]
        if (retained != null) {
            val outcome = if (item == retained) PlannerMergeOutcome.UNCHANGED else PlannerMergeOutcome.KEEP_LOCAL_CONFLICT
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
