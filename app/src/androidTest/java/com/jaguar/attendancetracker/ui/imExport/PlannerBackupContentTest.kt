package com.jaguar.attendancetracker.ui.imExport

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.jaguar.attendancetracker.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlannerBackupContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewRequiresApplyAndNoChangePreviewCannotApply() {
        val withChanges = PlannerBackupState.Review(listOf(PlannerBackupCounts(
            R.string.planner_backup_board, insert = 1, identical = 0, conflict = 0, blocked = 0,
        )))
        val state = mutableStateOf<PlannerBackupState>(withChanges)
        var applies = 0
        var discards = 0
        compose.setContent {
            MaterialTheme {
                PlannerBackupContent(
                    state.value, save = {}, open = {},
                    discard = { discards++; state.value = PlannerBackupState.Idle },
                    apply = { applies++; state.value = PlannerBackupState.Idle },
                )
            }
        }
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val apply = resources.getString(R.string.planner_backup_apply)
        val cancel = resources.getString(R.string.planner_backup_cancel)
        val preview = resources.getString(R.string.planner_backup_preview)
        compose.onNodeWithText(preview).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, applies) }
        compose.onNodeWithText(cancel).performClick()
        compose.runOnIdle {
            assertEquals(1, discards)
            assertEquals(0, applies)
            state.value = withChanges
        }
        compose.onNodeWithText(apply).performClick()
        compose.runOnIdle {
            assertEquals(1, applies)
            state.value = PlannerBackupState.Review(emptyList())
        }
        compose.onNodeWithText(resources.getString(R.string.planner_backup_close)).assertIsDisplayed()
        compose.onNodeWithText(apply).assertDoesNotExist()
    }
}
