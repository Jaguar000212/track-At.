package com.jaguar.attendancetracker.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
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
        compose.setContent {
            UITheme {
                TodayContent(
                    TodayState.Ready(LocalDate.of(2026, 11, 20), emptyList(), emptyList()),
                    openDayView = { attendanceOpens++ },
                    openDashboard = { dashboardOpens++ },
                    openExams = { examsOpens++ },
                )
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.onNodeWithText(resources.getString(R.string.today_empty)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.today_open_day_view)).performClick()
        compose.onNodeWithText(resources.getString(R.string.today_open_dashboard)).performClick()
        compose.onNodeWithText(resources.getString(R.string.exams_title)).performClick()
        compose.runOnIdle {
            assertEquals(1, attendanceOpens)
            assertEquals(1, dashboardOpens)
            assertEquals(1, examsOpens)
        }
    }
}
