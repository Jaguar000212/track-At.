package com.jaguar.attendancetracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class PickerCalendarDateComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unchangedSemesterStartAndEndStayOnTheirCalendarDaysInWesternZone() = west {
        val original = Semester(name = "Term", startDate = LocalDate.of(2026, 1, 20),
            endDate = LocalDate.of(2026, 7, 20))
        var saved: Semester? = null
        compose.setContent {
            MaterialTheme { EditSemesterBottomSheet(original, onDismiss = {}, onSave = { saved = it }) }
        }
        compose.onNodeWithText("Finish").performClick()
        compose.runOnIdle {
            assertEquals(original.startDate, saved?.startDate)
            assertEquals(original.endDate, saved?.endDate)
        }
    }

    @Test fun unchangedSubjectStartStaysOnItsCalendarDayInWesternZone() = west {
        val date = LocalDate.of(2026, 7, 20)
        val term = Semester(name = "Term", startDate = date.minusMonths(1), endDate = null)
        val original = Subject(name = "Math", semesterId = term.id, startDate = date, isEnded = false,
            color = "PINK", professor = null, roomNo = null, notes = null)
        var saved: Subject? = null
        compose.setContent {
            MaterialTheme {
                EditSubjectBottomSheet(original, onDismiss = {}, onSave = { saved = it }, availableSemesters = listOf(term))
            }
        }
        compose.onNodeWithText("Finish").performClick()
        compose.runOnIdle { assertEquals(original.startDate, saved?.startDate) }
    }

    private fun west(action: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("America/Los_Angeles", ZoneId.systemDefault().id)
            action()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
