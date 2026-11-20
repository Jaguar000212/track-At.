package com.jaguar.attendancetracker.ui.insights

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.AttendanceRecord
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.enums.AttendanceStatus
import com.jaguar.attendancetracker.backend.enums.ClassType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat
import java.time.LocalDate
import java.util.UUID

class InsightsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 20)
    private val active = Semester(id(1), "Current", today.minusMonths(2), null)
    private val archived = Semester(id(2), "Prior", today.minusMonths(3), today.minusDays(1), isArchived = true)
    private val math = Subject(id(3), "Math", active.id, active.startDate, false, "PINK", null, null, null)
    private val art = math.copy(id = id(4), name = "Art", semesterId = archived.id)
    private val present = record(1, math.id, today, AttendanceStatus.PRESENT)
    private val absent = record(2, math.id, today, AttendanceStatus.ABSENT)
    private val cancelled = record(3, art.id, today.minusDays(1), AttendanceStatus.CANCELLED)

    @Test fun insightsRoomSnapshotIncludesArchivedButNeverMaterializesTimetable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        try {
            runBlocking {
                val dao = db.plannerRestoreDao()
                dao.insertSemesters(listOf(active, archived))
                dao.insertSubjects(listOf(math, art))
                // A present-week scheduled row without any marks must never be materialized.
                dao.insertSchedule(listOf(SessionRecord(id(15), math.id, 0, today.dayOfWeek.value, today.minusWeeks(1))))
                val oldest = record(4, math.id, attendanceTrendStart(today).minusDays(1), AttendanceStatus.PRESENT)
                val future = record(5, math.id, today.plusDays(1), AttendanceStatus.ABSENT)
                dao.insertAttendance(listOf(present, absent, cancelled, oldest, future))
                val before = dao.attendance()
                val snapshot = readInsightsSnapshot(db, today)
                assertEquals(setOf(present, absent, cancelled), snapshot.rows.toSet())
                assertEquals(listOf(InsightsSubject(math.id, "Math", "Current", false),
                    InsightsSubject(art.id, "Art", "Prior", true)), snapshot.subjects)
                val current = attendanceTrend(snapshot.rows, today).last()
                assertEquals(AttendanceRate(1, 2), current.rate)
                assertEquals(1L, current.cancelled)
                assertEquals(0L, current.unmarked)
                assertEquals(before, dao.attendance())
            }
        } finally { db.close() }
    }

    @Test fun insightsShowsMarkedDenominatorAndArchivedEmptyRateScope() {
        compose.setContent {
            MaterialTheme {
                InsightsContent(InsightsState.Ready(today, listOf(present, absent, cancelled),
                    listOf(InsightsSubject(math.id, "Math", "Current", false),
                        InsightsSubject(art.id, "Art", "Prior", true))))
            }
        }
        val r = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val list = compose.onNodeWithTag("insights-list")
        val rate = r.getString(R.string.insights_rate, NumberFormat.getPercentInstance().format(0.5), 1L, 2L)
        list.performScrollToNode(hasText(rate))
        compose.onNodeWithText(rate).assertIsDisplayed()

        val scope = r.getString(R.string.insights_scope, r.getString(R.string.insights_all_subjects))
        list.performScrollToNode(hasText(scope))
        compose.onNodeWithText(scope).performClick()
        val label = r.getString(R.string.insights_archived_subject, "Art", "Prior")
        compose.onNodeWithText(label).performClick()
        val empty = r.getString(R.string.insights_no_marked_scope)
        list.performScrollToNode(hasText(empty))
        compose.onNodeWithText(empty).assertIsDisplayed()
        compose.onNodeWithText(rate).assertDoesNotExist()
        val other = r.getString(R.string.insights_other_counts, 0L, 1L)
        list.performScrollToNode(hasText(other))
        compose.onNodeWithText(other).assertIsDisplayed()
    }

    private fun record(n: Int, subject: UUID, date: LocalDate, status: AttendanceStatus) =
        AttendanceRecord(id(n + 100), subject, id(n + 200), date, status, ClassType.REGULAR)
    private fun id(n: Int) = UUID(0, n.toLong())
}
