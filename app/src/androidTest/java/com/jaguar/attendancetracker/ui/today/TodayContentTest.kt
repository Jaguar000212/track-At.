package com.jaguar.attendancetracker.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.ui.agenda.AgendaContent
import com.jaguar.attendancetracker.ui.agenda.AgendaState
import com.jaguar.attendancetracker.ui.agenda.projectAgenda
import com.jaguar.attendancetracker.ui.theme.UITheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TodayContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyTodayOffersAttendanceExamsAndSubjectDashboardWithoutTimetable() {
        var attendanceOpens = 0
        var dashboardOpens = 0
        var examsOpens = 0
        var leaveOpens = 0
        var insightsOpens = 0
        var agendaOpens = 0
        compose.setContent {
            UITheme {
                TodayContent(
                    TodayState.Ready(LocalDate.of(2026, 11, 20), emptyList(), emptyList()),
                    openDayView = { attendanceOpens++ },
                    openDashboard = { dashboardOpens++ },
                    openExams = { examsOpens++ },
                    openLeave = { leaveOpens++ },
                    openInsights = { insightsOpens++ },
                    openAgenda = { agendaOpens++ },
                )
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val list = compose.onNodeWithTag("today-list")
        val empty = resources.getString(R.string.today_empty)
        list.performScrollToNode(hasText(empty))
        compose.onNodeWithText(empty).assertIsDisplayed()
        for (id in listOf(R.string.today_open_day_view, R.string.today_open_dashboard, R.string.exams_title,
            R.string.leave_title, R.string.insights_title, R.string.agenda_title)) {
            val text = resources.getString(id)
            list.performScrollToNode(hasText(text))
            compose.onNodeWithText(text).performClick()
        }
        compose.runOnIdle {
            assertEquals(1, attendanceOpens)
            assertEquals(1, dashboardOpens)
            assertEquals(1, examsOpens)
            assertEquals(1, leaveOpens)
            assertEquals(1, insightsOpens)
            assertEquals(1, agendaOpens)
        }
    }

    @Test
    fun agendaUsesSavedCalendarDatesAndKeepsUndatedItemsSeparate() {
        val today = LocalDate.of(2026, 11, 18)
        val tomorrow = today.plusDays(1)
        val agenda = projectAgenda(
            listOf(BoardItem(title = "Read chapter", dueDate = today),
                BoardItem(title = "This is complete", dueDate = today, isDone = true)),
            listOf(Exam(title = "Exam without date"), Exam(title = "Tomorrow’s exam", date = tomorrow)),
        )
        compose.setContent { UITheme { AgendaContent(AgendaState.Ready(agenda), today, onBack = {}) } }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val list = compose.onNodeWithTag("agenda-list")
        list.performScrollToNode(hasText("Read chapter"))
        compose.onNodeWithText("Read chapter").assertIsDisplayed()
        compose.onNodeWithText("This is complete").assertDoesNotExist()
        list.performScrollToNode(hasText("Exam without date"))
        compose.onNodeWithText("Exam without date").assertIsDisplayed()
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag("agenda-day-$tomorrow"))
        compose.onNodeWithTag("agenda-day-$tomorrow").performClick().assertIsSelected()
        list.performScrollToNode(hasText("Tomorrow’s exam"))
        compose.onNodeWithText("Tomorrow’s exam").assertIsDisplayed()
        list.performScrollToNode(hasText(resources.getString(R.string.agenda_next_week)))
        compose.onNodeWithText(resources.getString(R.string.agenda_next_week)).performClick()
        list.performScrollToNode(hasText(resources.getString(R.string.agenda_day_empty)))
        compose.onNodeWithText(resources.getString(R.string.agenda_day_empty)).assertIsDisplayed()
        list.performScrollToNode(hasText(resources.getString(R.string.agenda_jump_today)))
        compose.onNodeWithText(resources.getString(R.string.agenda_jump_today)).performClick()
        compose.onNodeWithTag("agenda-day-$today").assertIsSelected()
    }
}
