package com.jaguar.attendancetracker.ui.leave

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class LeavePlannerTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 20)
    private val term = Semester(id(1), "Spring", today.minusMonths(1), today.plusDays(1))
    private val archived = term.copy(id = id(2), name = "Archived", isArchived = true)
    private val math = Subject(id(3), "Math", term.id, today.minusMonths(1), false, "PINK",
        null, null, null, totalClasses = 1, attendedClasses = 1)
    private val archivedSubject = math.copy(id = id(4), semesterId = archived.id)
    private val ended = math.copy(id = id(5), name = "Ended", isEnded = true)
    private val staleCache = math.copy(id = id(6), name = "Stale cache")
    private val mark = AttendanceRecord(id(10), math.id, null, today.minusDays(1), AttendanceStatus.PRESENT, ClassType.EXTRA)

    @Test fun leaveReadsRealRowsWithoutWritesAndUsesExactOccurrenceAndExamDates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking {
                val dao = db.plannerRestoreDao()
                dao.insertSemesters(listOf(term, archived))
                dao.insertSubjects(listOf(math, archivedSubject, ended, staleCache))
                val scheduled = SessionRecord(id(11), math.id, 0, today.dayOfWeek.value, today.minusWeeks(1))
                val cancelled = scheduled.copy(id = id(12))
                val afterTerm = scheduled.copy(id = id(13), dayOfWeek = today.plusDays(2).dayOfWeek.value)
                val futureStart = scheduled.copy(id = id(14), startDate = today.plusDays(1))
                dao.insertSchedule(listOf(scheduled, cancelled, afterTerm, futureStart))
                val bookedExtra = mark.copy(id = id(15), date = today, status = null)
                val cancelledMark = mark.copy(id = id(16), sessionId = cancelled.id, date = today,
                    status = AttendanceStatus.CANCELLED, classType = ClassType.REGULAR)
                val wrongSubject = cancelledMark.copy(id = id(17), sessionId = scheduled.id, subjectId = archivedSubject.id)
                dao.insertAttendance(listOf(mark, bookedExtra, cancelledMark, wrongSubject))
                val linked = Exam(id(20), "Archived linked date", today, archived.id)
                val unlinked = Exam(id(21), "Unlinked date", today.plusDays(2))
                val outside = Exam(id(22), "Outside range", today.plusDays(3))
                val undated = Exam(id(23), "No date", semesterId = archived.id)
                dao.insertExams(listOf(linked, unlinked, outside, undated))

                val before = readLeaveSnapshot(db)
                val range = LeaveDateRange(today, today.plusDays(2))
                val facts = leaveFacts(before, range, today)
                assertEquals(listOf(linked, unlinked), facts.overlappingExams)
                assertEquals(listOf(undated), facts.undatedExams)
                assertEquals(listOf(math.id, staleCache.id), facts.subjects.map { it.subject.id })
                val known = facts.subjects.first()
                assertEquals(1, known.knownTimetableMisses)
                assertEquals(1, known.knownUnmarkedExtras)
                assertEquals(LeaveCounts(1, 1), known.verifiedCounts)
                assertNull(facts.subjects.last().verifiedCounts)
                assertEquals(LeaveUnknownReason.INCOMPLETE_TIMETABLE,
                    (known.estimate(range, today, null) as LeaveProjection.Unknown).reason)
                assertEquals(LeaveCounts(1, 3), (known.estimate(range, today, 2) as LeaveProjection.Estimate).after)
                assertEquals(before, readLeaveSnapshot(db))
            }
        } finally { db.close() }
    }

    @Test fun leaveWithoutTimetableRequiresExplicitZeroAndShowsExamsSeparately() {
        val exam = Exam(id(20), "Exam today", today)
        val undated = Exam(id(21), "Exam date unknown")
        val snapshot = LeaveSnapshot(listOf(term), listOf(math), emptyList(), listOf(mark), listOf(exam, undated))
        compose.setContent {
            MaterialTheme { LeaveContent(LeaveScreenState.Ready(snapshot), today) }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val list = compose.onNodeWithTag("leave-list")
        val needsEstimate = resources.getString(R.string.leave_enter_manual)
        list.performScrollToNode(hasText(needsEstimate))
        compose.onNodeWithText(needsEstimate).assertIsDisplayed()
        list.performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("0")
        val projected = resources.getString(R.string.leave_projected, 1L, 1L,
            resources.getString(R.string.leave_meets_target))
        list.performScrollToNode(hasText(projected))
        compose.onNodeWithText(projected).assertIsDisplayed()
        val dated = resources.getString(R.string.leave_exams)
        list.performScrollToNode(hasText(dated))
        compose.onNodeWithText(dated).assertIsDisplayed()
        val caveat = resources.getString(R.string.leave_undated_caveat)
        list.performScrollToNode(hasText(caveat))
        compose.onNodeWithText(caveat).assertIsDisplayed()
    }

    private fun id(n: Int) = UUID(0, n.toLong())
}
