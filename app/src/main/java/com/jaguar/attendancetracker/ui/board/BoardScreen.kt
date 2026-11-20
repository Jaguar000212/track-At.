package com.jaguar.attendancetracker.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import com.jaguar.attendancetracker.backend.entities.BoardItem
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private data class BoardEditor(val id: UUID? = null, val title: String = "", val dueDate: LocalDate? = null)

@Composable
internal fun BoardScreen(viewModel: BoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BoardContent(state, viewModel::save, viewModel::setDone)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoardContent(
    state: BoardState,
    saveTask: suspend (UUID?, String, LocalDate?) -> Boolean,
    changeDone: suspend (UUID, Boolean) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var showDone by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<BoardEditor?>(null) }
    var writeFailed by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf<UUID?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 620.dp).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.board_title),
                            style = MaterialTheme.typography.headlineLarge,
                            modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.board_description))
                    }
                }
            }
            if (state is BoardState.Ready) {
                item {
                    Button(onClick = { editor = BoardEditor() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.board_add))
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !showDone, onClick = { showDone = false },
                            label = { Text(stringResource(R.string.board_open_tasks)) })
                        FilterChip(selected = showDone, onClick = { showDone = true },
                            label = { Text(stringResource(R.string.board_done_tasks)) })
                    }
                }
                if (writeFailed) item { BoardWriteError() }
                val visible = state.items.filter { it.isDone == showDone }
                    .sortedWith(compareBy<BoardItem> { it.dueDate == null }.thenBy { it.dueDate }.thenBy { it.id })
                if (visible.isEmpty()) item {
                    Text(stringResource(if (showDone) R.string.board_done_empty else R.string.board_open_empty))
                }
                items(visible, key = { it.id.toString() }) { task ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleLarge)
                            task.dueDate?.let {
                                Text(stringResource(R.string.board_due_display, it.displayDate()))
                            }
                            val editDescription = stringResource(R.string.board_edit_named, task.title)
                            val toggleDescription = stringResource(
                                if (task.isDone) R.string.board_reopen_named else R.string.board_complete_named, task.title,
                            )
                            TextButton(
                                onClick = { editor = BoardEditor(task.id, task.title, task.dueDate) },
                                modifier = Modifier.semantics { contentDescription = editDescription },
                            ) {
                                Text(stringResource(R.string.board_edit))
                            }
                            TextButton(
                                enabled = changing == null,
                                modifier = Modifier.semantics { contentDescription = toggleDescription },
                                onClick = {
                                    if (changing == null) {
                                        changing = task.id
                                        writeFailed = false
                                        scope.launch {
                                            try { writeFailed = !changeDone(task.id, !task.isDone) }
                                            finally { changing = null }
                                        }
                                    }
                                },
                            ) {
                                Text(stringResource(if (task.isDone) R.string.board_reopen else R.string.board_complete))
                            }
                        }
                    }
                }
            } else if (state is BoardState.Loading) {
                item { CircularProgressIndicator() }
            } else {
                item { Text(stringResource(R.string.board_load_error), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    editor?.let { item ->
        BoardEditorDialog(item, close = { editor = null }, save = { title, due -> saveTask(item.id, title, due) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoardEditorDialog(
    initial: BoardEditor,
    close: () -> Unit,
    save: suspend (String, LocalDate?) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var dueDate by remember { mutableStateOf(initial.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val selectableYears = 1900..2100
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.takeIf { it.year in selectableYears }
                ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            yearRange = selectableYears,
        )
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                showDatePicker = false
            }) { Text(stringResource(R.string.board_set_date)) }
        }, dismissButton = {
            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.board_cancel)) }
        }) { DatePicker(state = picker) }
    } else {
        AlertDialog(
            onDismissRequest = { if (!saving) close() },
            title = { Text(stringResource(if (initial.id == null) R.string.board_add else R.string.board_edit)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it }, enabled = !saving, singleLine = true,
                        label = { Text(stringResource(R.string.board_task_title)) },
                    )
                    Text(dueDate?.let { stringResource(R.string.board_due_display, it.displayDate()) }
                        ?: stringResource(R.string.board_no_due))
                    TextButton(enabled = !saving, onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.board_pick_date))
                    }
                    if (dueDate != null) {
                        TextButton(enabled = !saving, onClick = { dueDate = null }) {
                            Text(stringResource(R.string.board_remove_date))
                        }
                    }
                    if (failed) BoardWriteError()
                    if (saving) CircularProgressIndicator()
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank() && !saving, onClick = {
                    if (!saving && title.isNotBlank()) {
                        saving = true
                        failed = false
                        scope.launch {
                            try {
                                if (save(title, dueDate)) close()
                                else failed = true
                            } finally { saving = false }
                        }
                    }
                }) { Text(stringResource(R.string.board_save)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = close) { Text(stringResource(R.string.board_cancel)) }
            },
        )
    }
}

@Composable
private fun BoardWriteError() {
    Text(stringResource(R.string.board_write_error), color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private fun LocalDate.displayDate(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
