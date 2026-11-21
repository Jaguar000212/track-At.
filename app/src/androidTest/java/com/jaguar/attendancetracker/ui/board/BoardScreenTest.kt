package com.jaguar.attendancetracker.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.BoardItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BoardScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun titleOnlyTaskCanBeEditedCompletedAndReopened() {
        val items = mutableStateOf<List<BoardItem>>(emptyList())
        compose.setContent {
            MaterialTheme {
                BoardContent(BoardState.Ready(items.value), saveTask = { id, title, dueDate ->
                    items.value = if (id == null) listOf(BoardItem(title = title, dueDate = dueDate))
                    else items.value.map { if (it.id == id) it.copy(title = title, dueDate = dueDate) else it }
                    true
                }, changeDone = { id, done ->
                    items.value = items.value.map { if (it.id == id) it.copy(isDone = done) else it }
                    true
                })
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val add = resources.getString(R.string.board_add)
        val save = resources.getString(R.string.board_save)
        val original = "Submit assignment"
        val edited = "Submit history assignment"
        compose.onNodeWithText(resources.getString(R.string.board_open_empty)).assertIsDisplayed()
        compose.onNodeWithText(add).performClick()
        compose.onNodeWithText(save).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput(original)
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithText(original).assertIsDisplayed()

        compose.onNodeWithContentDescription(resources.getString(R.string.board_edit_named, original)).performClick()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput(edited)
        compose.onNodeWithText(save).performClick()
        compose.onNodeWithText(edited).assertIsDisplayed()

        compose.onNodeWithContentDescription(resources.getString(R.string.board_complete_named, edited)).performClick()
        compose.onNodeWithText(resources.getString(R.string.board_open_empty)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.board_done_tasks)).performClick()
        compose.onNodeWithContentDescription(resources.getString(R.string.board_reopen_named, edited)).performClick()
        compose.onNodeWithText(resources.getString(R.string.board_done_empty)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.board_open_tasks)).performClick()
        compose.onNodeWithText(edited).assertIsDisplayed()
    }
    @Test fun quickPinCancelDoesNotWriteAndExplicitSaveMakesOneUndatedBoardTask() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val model = BoardViewModel(db)
        val capture = mutableStateOf(true)
        try {
            compose.setContent {
                MaterialTheme {
                    BoardContent(
                        BoardState.Ready(emptyList()), model::save, model::setDone,
                        quickCapture = capture.value, onQuickCaptureHandled = { capture.value = false },
                    )
                }
            }
            val title = context.getString(R.string.quick_pin_composer_title)
            val save = context.getString(R.string.board_save)
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText(save).assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("Canceled capture")
            compose.onNodeWithText(context.getString(R.string.board_cancel)).performClick()
            compose.onNodeWithText(title).assertDoesNotExist()
            assertTrue(runBlocking { db.plannerRestoreDao().board() }.isEmpty())

            compose.runOnIdle { capture.value = true }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextInput("Saved capture")
            compose.onNodeWithText(save).performClick()
            compose.waitUntil(timeoutMillis = 5000) { runBlocking { db.plannerRestoreDao().board() }.isNotEmpty() }
            val stored = runBlocking { db.plannerRestoreDao().board() }.single()
            assertEquals("Saved capture", stored.title)
            assertNull(stored.dueDate)
        } finally { db.close() }
    }
}
