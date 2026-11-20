package com.jaguar.attendancetracker.ui.exams

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jaguar.attendancetracker.backend.reminders.ExamReminderPolicy
import com.jaguar.attendancetracker.backend.reminders.ReminderChoice
import com.jaguar.attendancetracker.backend.reminders.ReminderExam
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
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

    @Test fun datedCardReviewsReminderAndDenialNeverLooksScheduled() {
        val dated = Exam(title = "Dated fixture", date = LocalDate.of(2026, 11, 21))
        val undated = Exam(title = "Undated fixture")
        val zone = ZoneId.of("Asia/Kolkata")
        val item = ReminderExam(dated.id, dated.date)
        val time = (ExamReminderPolicy.suggest(item, zone, Instant.parse("2026-11-19T00:00:00Z"))
            as ReminderChoice.Available).time
        val editor = mutableStateOf<ReminderEditorState?>(null)
        var explicitClicks = 0
        compose.setContent {
            MaterialTheme {
                ExamsContent(ExamsState.Ready(listOf(dated, undated), emptyList()), null,
                    edit = {}, change = {}, cancel = {}, save = {},
                    openReminder = {
                        editor.value = ReminderEditorState(UUID(0, 2), item, zone, time.local.toLocalDate(),
                            time.local.toLocalTime(), ReminderChoice.Available(time), replacing = false)
                    },
                )
                editor.value?.let {
                    ExamReminderDialog(it, false, {}, {}, close = { editor.value = null }, disable = {},
                        enable = {
                            explicitClicks++
                            editor.value = it.copy(notice = ReminderFeedback.PERMISSION_DENIED)
                        },
                        settings = {},
                    )
                }
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val named = resources.getString(R.string.exam_reminder_named, dated.title)
        compose.onNodeWithTag("exam-list").performScrollToNode(hasContentDescription(named))
        compose.onNodeWithContentDescription(named).performClick()
        compose.onNodeWithText(resources.getString(R.string.exam_reminder_zone, zone.id)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, explicitClicks) }
        compose.onNodeWithText(resources.getString(R.string.exam_reminder_enable)).performClick()
        compose.onNodeWithText(resources.getString(R.string.exam_reminder_permission_denied)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.exam_reminder_scheduled)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, explicitClicks) }
        compose.onNodeWithText(resources.getString(android.R.string.cancel)).performClick()
        compose.onNodeWithContentDescription(resources.getString(R.string.exam_reminder_named, undated.title))
            .assertDoesNotExist()
    }
}
