package com.jaguar.attendancetracker.ui.rollover

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.Semester
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.backend.repositories.TermRolloverRepository
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RolloverScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rolloverScreenReviewsSelectedCourseBeforeCreatingNewSemester() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val dao = db.plannerRestoreDao()
        val date = LocalDate.of(2025, 1, 1)
        val archived = Semester(name = "Origin fixture", startDate = date, endDate = null, isArchived = true)
        val selected = Subject(name = "Selected fixture", semesterId = archived.id, startDate = date,
            isEnded = true, color = "TEAL", professor = null, roomNo = null, notes = "Do not copy")
        val ignored = selected.copy(id = UUID.randomUUID(), name = "Excluded fixture")
        try {
            runBlocking {
                dao.insertSemesters(listOf(archived))
                dao.insertSubjects(listOf(selected, ignored))
            }
            lateinit var model: RolloverViewModel
            instrumentation.runOnMainSync {
                model = RolloverViewModel(TermRolloverRepository(db),
                    SavedStateHandle(mapOf("termId" to archived.id.toString())))
            }
            compose.setContent { RolloverScreen(onDone = {}, viewModel = model) }
            val destination = "Destination fixture"
            val review = context.getString(R.string.rollover_review_action)
            val reviewTitle = context.getString(R.string.rollover_review_title)
            val edit = context.getString(R.string.rollover_edit)
            val confirm = context.getString(R.string.rollover_create)
            val created = context.getString(R.string.rollover_created, destination)
            val origin = context.getString(R.string.rollover_from, archived.name)
            val list = compose.onNodeWithTag("rollover-list")
            fun click(text: String) {
                list.performScrollToNode(hasText(text))
                compose.onNodeWithText(text).performClick()
            }
            fun waitFor(text: String) {
                compose.waitUntil(timeoutMillis = 5000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            }
            waitFor(origin)
            list.performScrollToNode(hasSetTextAction())
            compose.onNode(hasSetTextAction()).performTextInput(destination)
            list.performScrollToNode(hasText(review))
            compose.onNodeWithText(review).assertIsNotEnabled()
            click(selected.name)
            click(review)
            waitFor(reviewTitle)
            val color = context.getString(R.string.rollover_course_color, selected.name,
                context.getString(R.string.rollover_teal))
            list.performScrollToNode(hasText(color))
            compose.onNodeWithText(color).assertIsDisplayed()
            compose.onNodeWithText(ignored.name, substring = true).assertDoesNotExist()
            assertEquals(listOf(archived), runBlocking { dao.semesters() })

            click(edit)
            waitFor(origin)
            click(review)
            waitFor(reviewTitle)
            click(confirm)
            waitFor(created)
            compose.onNodeWithText(created).assertIsDisplayed()
            runBlocking {
                val newTerm = dao.semesters().single { it.id != archived.id }
                assertEquals(destination, newTerm.name)
                assertEquals(listOf(selected.name), dao.subjects().filter { it.semesterId == newTerm.id }.map { it.name })
            }
        } finally { db.close() }
    }
}
