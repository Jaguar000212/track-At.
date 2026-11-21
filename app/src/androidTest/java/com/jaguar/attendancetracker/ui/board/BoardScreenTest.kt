package com.jaguar.attendancetracker.ui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.AttendanceDatabase
import com.jaguar.attendancetracker.backend.entities.BoardItem
import com.jaguar.attendancetracker.backend.entities.BoardChecklistItem
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    @Test fun boardPinsAndEditsChecklistWithoutChangingTaskCompletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AttendanceDatabase::class.java).build()
        val task = BoardItem(title = "Briefing")
        val sooner = BoardItem(title = "Sooner", dueDate = LocalDate.of(2026, 12, 1))
        val imported = BoardChecklistItem(boardItemId = task.id, position = Int.MAX_VALUE, text = "Imported step")
        val model = BoardViewModel(db)
        try {
            runBlocking {
                db.plannerRestoreDao().insertBoard(listOf(task, sooner))
                db.plannerRestoreDao().insertBoardChecklist(listOf(imported))
            }
            val portable = BoardPortabilityActions(model::setPinned, model::observeChecklist,
                model::saveChecklist, model::setChecklistDone)
            compose.setContent {
                val board by model.state.collectAsStateWithLifecycle()
                MaterialTheme { BoardContent(board, model::save, model::setDone, portability = portable) }
            }
            val pin = context.getString(R.string.board_pin_named, task.title)
            compose.waitUntil(timeoutMillis = 5000) {
                compose.onAllNodesWithContentDescription(context.getString(R.string.board_pin_named, sooner.title))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("board-list").performScrollToNode(hasContentDescription(pin))
            compose.onNodeWithContentDescription(pin).performClick()
            compose.waitUntil(timeoutMillis = 5000) {
                runBlocking { db.plannerRestoreDao().board().single { it.id == task.id }.isPinned }
            }
            val openChecklist = context.getString(R.string.board_checklist_named, task.title)
            compose.onNodeWithTag("board-list").performScrollToNode(hasContentDescription(openChecklist))
            compose.onNodeWithContentDescription(openChecklist).performClick()
            compose.waitUntil(timeoutMillis = 5000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasSetTextAction()).performTextInput("Second step")
            compose.onNodeWithText(context.getString(R.string.board_checklist_add)).performClick()
            val edit = context.getString(R.string.board_checklist_edit_named, "Second step")
            compose.waitUntil(timeoutMillis = 5000) {
                compose.onAllNodesWithContentDescription(edit).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(edit).performClick()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput("Edited step")
            compose.onNodeWithText(context.getString(R.string.board_checklist_save)).performClick()
            val checked = context.getString(R.string.board_checklist_mark_named, "Edited step")
            compose.waitUntil(timeoutMillis = 5000) {
                compose.onAllNodesWithContentDescription(checked).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(checked).performClick()
            compose.waitUntil(timeoutMillis = 5000) {
                runBlocking { db.boardItemDao().checklistItems(task.id).last().isDone }
            }
            val rows = runBlocking { db.boardItemDao().checklistItems(task.id) }
            assertEquals(imported.copy(position = 0), rows.first())
            assertEquals(listOf(0, 1), rows.map { it.position })
            assertEquals("Edited step", rows.last().text)
            val persisted = runBlocking { db.plannerRestoreDao().board().single { it.id == task.id } }
            assertTrue(persisted.isPinned)
            assertFalse(persisted.isDone)
        } finally { db.close() }
    }
}
