package com.jaguar.attendancetracker.ui.exams

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.Exam
import com.jaguar.attendancetracker.backend.entities.Semester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ExamsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleOnlyExamCanEditAndChooseAnArchivedSemester() {
        val term = Semester(name = "Autumn", startDate = LocalDate.of(2024, 9, 1),
            endDate = null, isArchived = true)
        val records = mutableStateOf<List<Exam>>(emptyList())
        val draft = mutableStateOf<ExamDraft?>(null)
        compose.setContent {
            MaterialTheme {
                ExamsContent(
                    ExamsState.Ready(records.value, listOf(term)), draft.value,
                    edit = { draft.value = it?.let { ExamDraft(it.id, it.title, it.date, it.semesterId) } ?: ExamDraft() },
                    change = { draft.value = it }, cancel = { draft.value = null },
                    save = {
                        val editor = requireNotNull(draft.value)
                        records.value = editor.id?.let { id ->
                            listOf(Exam(id, editor.title, editor.date, editor.semesterId))
                        } ?: listOf(Exam(title = editor.title, date = editor.date, semesterId = editor.semesterId))
                        draft.value = null
                    },
                )
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val save = resources.getString(R.string.exams_save)
        val original = "Math final"
        val edited = "Math final review"
        compose.onNodeWithText(resources.getString(R.string.exams_empty)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.exams_add)).performClick()
        compose.onNodeWithText(save).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput(original)
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithText(original).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.exams_undated)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.exams_date_tbd)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.exams_no_semester)).assertIsDisplayed()

        compose.onNodeWithContentDescription(resources.getString(R.string.exams_edit_named, original)).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(edited)
        compose.onNodeWithText(resources.getString(R.string.exams_choose_semester)).performScrollTo().performClick()
        val archiveLabel = resources.getString(R.string.exams_archived_semester, term.name)
        compose.onNodeWithText(archiveLabel).performClick()
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithText(edited).assertIsDisplayed()
        compose.onNodeWithText(archiveLabel).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(term.id, records.value.single().semesterId)
            assertNull(records.value.single().date)
        }
    }
}
