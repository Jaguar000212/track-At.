package com.jaguar.attendancetracker.ui.imExport

import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SEMESTER_CONFLICT
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SESSION_BLOCKED
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SESSION_CONFLICT
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SESSION_SUBJECT_MISMATCH
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SUBJECT_BLOCKED
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.SUBJECT_CONFLICT
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeBlockReason.WOULD_MISLINK_LOCAL_ATTENDANCE
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome.BLOCKED_DEPENDENCY
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome.INSERT
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome.KEEP_LOCAL_CONFLICT
import com.jaguar.attendancetracker.ui.imExport.PlannerMergeOutcome.UNCHANGED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class PlannerMergePreviewTest {
    private val date = LocalDate.of(2024, 2, 29)
    private val term = Semester(id(1), "Spring", date, null, 75, false)
    private val math = Subject(id(2), "Math", term.id, date, false, "PINK", null, null, null)
    private val art = math.copy(id = id(3), name = "Art")
    private val mathSlot = SessionRecord(id(4), math.id, 0, 4, date)
    private val artSlot = mathSlot.copy(subjectId = art.id)
    private val mathAttendance = AttendanceRecord(
        id(5), math.id, mathSlot.id, date, AttendanceStatus.PRESENT, ClassType.REGULAR,
    )
    private val artAttendance = mathAttendance.copy(id = id(6), subjectId = art.id)
    private val task = BoardItem(id(7), "Read chapter")
    private val extraTask = BoardItem(id(8), "Prepare slides", date, true)

    @Test fun freshMergeThenIdenticalRepeatIsIdempotentAtPlanLevel() {
        val incoming = snapshot(listOf(term), listOf(math), listOf(mathSlot), listOf(mathAttendance), listOf(task))
        val first = PlannerMergePlanner.preview(snapshot(), incoming)
        assertEquals(listOf(INSERT, INSERT, INSERT, INSERT, INSERT), first.outcomes())
        assertEquals(mathAttendance, first.attendance.single().incoming)

        val restored = incoming.copy(attendance = incoming.attendance.copy(timestamp = 99))
        val repeat = PlannerMergePlanner.preview(restored, incoming)
        assertEquals(List(5) { UNCHANGED }, repeat.outcomes())
    }

    @Test fun boardInsertAndConflictRemainIndependentAndRepeatHasNoNewInsert() {
        val local = snapshot(board = listOf(task))
        val incoming = snapshot(board = listOf(extraTask, task.copy(title = "Changed remotely")))
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertEquals(listOf(KEEP_LOCAL_CONFLICT, INSERT), plan.board.map { it.outcome })
        assertEquals(listOf(task.id, extraTask.id), plan.board.map { it.incoming.id })
        assertEquals(listOf(task), local.board)

        val repeat = PlannerMergePlanner.preview(local.copy(board = listOf(task, extraTask)), incoming)
        assertEquals(listOf(KEEP_LOCAL_CONFLICT, UNCHANGED), repeat.board.map { it.outcome })
    }

    @Test fun conflictingTermBlocksNewSubjectsAndTheirDependents() {
        val local = snapshot(terms = listOf(term.copy(name = "Kept local")))
        val incoming = snapshot(listOf(term), listOf(math), listOf(mathSlot), listOf(mathAttendance), listOf(task))
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertEquals(KEEP_LOCAL_CONFLICT, plan.semesters.single().outcome)
        assertBlock(plan.subjects.single(), SEMESTER_CONFLICT, term.id)
        assertBlock(plan.schedule.single(), SUBJECT_BLOCKED, math.id)
        assertBlock(plan.attendance.single(), SUBJECT_BLOCKED, math.id)
        assertEquals(INSERT, plan.board.single().outcome)
    }

    @Test fun unchangedStoredSubjectIsNotMutatedButItsConflictingTermBlocksNewChildren() {
        val local = snapshot(listOf(term.copy(name = "Kept local")), listOf(math), attendance = listOf(mathAttendance))
        val incoming = snapshot(listOf(term), listOf(math), listOf(mathSlot), listOf(mathAttendance))
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertEquals(UNCHANGED, plan.subjects.single().outcome)
        assertBlock(plan.schedule.single(), SEMESTER_CONFLICT, term.id)
        assertEquals(UNCHANGED, plan.attendance.single().outcome)
    }

    @Test fun subjectDifferenceIncludesStoredTotalsAndScheduleDifferenceBlocksNewDependents() {
        val incoming = snapshot(listOf(term), listOf(math), listOf(mathSlot), listOf(mathAttendance))
        val localChangedSubject = snapshot(listOf(term), listOf(math.copy(totalClasses = 10)))
        val subjectPlan = PlannerMergePlanner.preview(localChangedSubject, incoming)
        assertEquals(KEEP_LOCAL_CONFLICT, subjectPlan.subjects.single().outcome)
        assertBlock(subjectPlan.schedule.single(), SUBJECT_CONFLICT, math.id)
        assertBlock(subjectPlan.attendance.single(), SUBJECT_CONFLICT, math.id)

        val localChangedSlot = snapshot(listOf(term), listOf(math), listOf(mathSlot.copy(orderNo = 3)))
        val slotPlan = PlannerMergePlanner.preview(localChangedSlot, incoming)
        assertEquals(KEEP_LOCAL_CONFLICT, slotPlan.schedule.single().outcome)
        assertBlock(slotPlan.attendance.single(), SESSION_CONFLICT, mathSlot.id)
    }

    @Test fun incomingOrphanCannotResolveToAnotherSubjectsRetainedSlot() {
        val local = snapshot(listOf(term), listOf(art), listOf(artSlot))
        val incoming = snapshot(listOf(term), listOf(math), attendance = listOf(mathAttendance))
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertEquals(INSERT, plan.subjects.single().outcome)
        assertBlock(plan.attendance.single(), SESSION_SUBJECT_MISMATCH, mathSlot.id)
        assertEquals(mathSlot.id, plan.attendance.single().incoming.sessionId)
        assertEquals(listOf(artSlot), local.attendance.schedule)
    }

    @Test fun incomingSlotCannotResolveAnotherSubjectsRetainedOrphan() {
        val local = snapshot(listOf(term), listOf(math), attendance = listOf(mathAttendance))
        val incoming = snapshot(listOf(term), listOf(art), listOf(artSlot), listOf(artAttendance))
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertBlock(plan.schedule.single(), WOULD_MISLINK_LOCAL_ATTENDANCE, artSlot.id)
        assertBlock(plan.attendance.single(), SESSION_BLOCKED, artSlot.id)
        assertEquals(listOf(mathAttendance), local.attendance.attendance)
    }

    @Test fun genuineOrphanAndNullArePreservedWithoutInventedLinks() {
        val extra = mathAttendance.copy(id = id(10), sessionId = null, classType = ClassType.EXTRA)
        val incoming = snapshot(listOf(term), listOf(math), attendance = listOf(mathAttendance, extra))
        val local = snapshot()
        val plan = PlannerMergePlanner.preview(local, incoming)
        assertEquals(listOf(INSERT, INSERT), plan.attendance.map { it.outcome })
        assertEquals(listOf(mathAttendance, extra), plan.attendance.map { it.incoming })
        assertEquals(emptyList<PlannerMergeRow<SessionRecord>>(), plan.schedule)
    }

    private fun assertBlock(row: PlannerMergeRow<*>, reason: PlannerMergeBlockReason, id: UUID) {
        assertEquals(BLOCKED_DEPENDENCY, row.outcome)
        assertEquals(PlannerMergeBlock(reason, id), row.dependency)
    }

    private fun PlannerMergePreview.outcomes() =
        listOf(semesters, subjects, schedule, attendance, board).flatMap { rows -> rows.map { it.outcome } }

    private fun snapshot(
        terms: List<Semester> = emptyList(),
        subjects: List<Subject> = emptyList(),
        schedule: List<SessionRecord> = emptyList(),
        attendance: List<AttendanceRecord> = emptyList(),
        board: List<BoardItem> = emptyList(),
    ) = PlannerBackup(BackupData(timestamp = 42, semesters = terms, subjects = subjects,
        schedule = schedule, attendance = attendance), board)

    private fun id(n: Int): UUID = UUID(0, n.toLong())
}
