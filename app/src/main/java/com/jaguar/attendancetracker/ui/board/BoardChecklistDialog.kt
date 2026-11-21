package com.jaguar.attendancetracker.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.BoardChecklistItem
import com.jaguar.attendancetracker.backend.entities.MAX_BOARD_CHECKLIST_TEXT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

internal data class BoardPortabilityActions(
    val setPinned: suspend (UUID, Boolean) -> Boolean,
    val checklist: (UUID) -> Flow<List<BoardChecklistItem>>,
    val saveChecklist: suspend (UUID, UUID?, String) -> Boolean,
    val setChecklistDone: suspend (UUID, UUID, Boolean) -> Boolean,
)

private sealed interface ChecklistLoad {
    data object Loading : ChecklistLoad
    data object Failed : ChecklistLoad
    data class Ready(val rows: List<BoardChecklistItem>) : ChecklistLoad
}

@Composable
internal fun BoardChecklistDialog(parent: UUID, close: () -> Unit, actions: BoardPortabilityActions) {
    val flow = remember(parent, actions) {
        actions.checklist(parent).map<List<BoardChecklistItem>, ChecklistLoad> { ChecklistLoad.Ready(it) }
            .catch { e -> if (e is CancellationException) throw e else emit(ChecklistLoad.Failed) }
    }
    val loaded by flow.collectAsStateWithLifecycle(ChecklistLoad.Loading)
    val scope = rememberCoroutineScope()
    var text by remember(parent) { mutableStateOf("") }
    var editing by remember(parent) { mutableStateOf<UUID?>(null) }
    var saving by remember(parent) { mutableStateOf(false) }
    var failed by remember(parent) { mutableStateOf(false) }

    fun write(action: suspend () -> Boolean, after: () -> Unit = {}) {
        if (saving) return
        saving = true
        failed = false
        scope.launch {
            try {
                if (action()) after() else failed = true
            } finally { saving = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) close() },
        title = { Text(stringResource(R.string.board_checklist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val current = loaded) {
                    ChecklistLoad.Failed -> Text(stringResource(R.string.board_load_error))
                    ChecklistLoad.Loading -> CircularProgressIndicator()
                    is ChecklistLoad.Ready -> {
                        if (current.rows.isEmpty()) Text(stringResource(R.string.board_checklist_empty))
                        else LazyColumn(Modifier.heightIn(max = 220.dp)) {
                            items(current.rows, key = { it.id.toString() }) { row ->
                                val mark = stringResource(R.string.board_checklist_mark_named, row.text)
                                val editLabel = stringResource(R.string.board_checklist_edit_named, row.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        row.isDone, onCheckedChange = { desired ->
                                            write({ actions.setChecklistDone(parent, row.id, desired) })
                                        }, enabled = !saving,
                                        modifier = Modifier.semantics { contentDescription = mark },
                                    )
                                    Text(row.text, Modifier.weight(1f))
                                    TextButton(enabled = !saving, onClick = { editing = row.id; text = row.text; failed = false },
                                        modifier = Modifier.semantics { contentDescription = editLabel }) {
                                        Text(stringResource(R.string.board_edit))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = text, onValueChange = { text = it; failed = false },
                            enabled = !saving, singleLine = true,
                            isError = text.trim().length > MAX_BOARD_CHECKLIST_TEXT,
                            supportingText = {
                                if (text.trim().length > MAX_BOARD_CHECKLIST_TEXT) {
                                    Text(stringResource(R.string.board_checklist_limit, MAX_BOARD_CHECKLIST_TEXT))
                                }
                            },
                            label = { Text(stringResource(if (editing == null) R.string.board_checklist_new else R.string.board_checklist_edit_text)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (editing != null) TextButton(enabled = !saving, onClick = { editing = null; text = ""; failed = false }) {
                            Text(stringResource(R.string.board_checklist_cancel_edit))
                        }
                    }
                }
                if (failed) BoardWriteError()
            }
        },
        confirmButton = {
            if (loaded is ChecklistLoad.Ready) {
                TextButton(enabled = !saving && text.isNotBlank() && text.trim().length <= MAX_BOARD_CHECKLIST_TEXT,
                    onClick = {
                        val id = editing
                        val draft = text
                        write({ actions.saveChecklist(parent, id, draft) }) { editing = null; text = "" }
                    }) {
                    Text(stringResource(if (editing == null) R.string.board_checklist_add else R.string.board_checklist_save))
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = close) { Text(stringResource(R.string.board_checklist_close)) }
        },
    )
}
