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
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.BoardItem
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
        compose.onNode(hasSetTextAction()).performTextClearance().performTextInput(edited)
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
}
