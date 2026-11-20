package com.jaguar.attendancetracker.ui.imExport

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaguar.attendancetracker.R
import java.time.LocalDate

@Composable
internal fun PlannerBackupScreen(viewModel: PlannerBackupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.pickerResult(PlannerPicker.OPEN, it)
    }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.pickerResult(PlannerPicker.CREATE, it)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.discardPreview() }
    }

    fun choose(kind: PlannerPicker, launch: () -> Unit) {
        if (!viewModel.beginPicker(kind)) return
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            viewModel.pickerFailed(kind)
        } catch (_: SecurityException) {
            viewModel.pickerFailed(kind)
        }
    }

    PlannerBackupContent(
        state = state,
        save = {
            choose(PlannerPicker.CREATE) { create.launch("track-at-planner-${LocalDate.now()}.json") }
        },
        open = { choose(PlannerPicker.OPEN) { open.launch(arrayOf("*/*")) } },
        discard = viewModel::discardPreview,
        apply = viewModel::apply,
    )
}

@Composable
internal fun PlannerBackupContent(
    state: PlannerBackupState,
    save: () -> Unit,
    open: () -> Unit,
    discard: () -> Unit,
    apply: () -> Unit,
) {
    val ready = state is PlannerBackupState.Idle || state is PlannerBackupState.Notice
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.planner_backup_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.planner_backup_scope))
            Text(stringResource(R.string.planner_backup_privacy))
            Button(onClick = save, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.planner_backup_save))
            }
            OutlinedButton(onClick = open, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.planner_backup_open))
            }
            if (state is PlannerBackupState.Busy) {
                CircularProgressIndicator()
                Text(
                    stringResource(state.message),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (state is PlannerBackupState.Notice) {
                Text(
                    stringResource(state.message),
                    color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }

    if (state is PlannerBackupState.Review) {
        val canApply = state.counts.any { it.insert > 0 }
        AlertDialog(
            onDismissRequest = discard,
            title = { Text(stringResource(R.string.planner_backup_preview)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.planner_backup_keep_local))
                    for (counts in state.counts) {
                        Text(stringResource(
                            R.string.planner_backup_row,
                            stringResource(counts.label),
                            counts.insert, counts.identical, counts.conflict, counts.blocked,
                        ))
                    }
                    if (state.counts.any { it.blocked > 0 }) {
                        Text(stringResource(R.string.planner_backup_block_explanation))
                    }
                    if (!canApply) Text(stringResource(R.string.planner_backup_no_changes))
                }
            },
            confirmButton = {
                if (canApply) {
                    TextButton(onClick = apply) {
                        Text(stringResource(R.string.planner_backup_apply))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = discard) {
                    Text(stringResource(
                        if (canApply) R.string.planner_backup_cancel else R.string.planner_backup_close,
                    ))
                }
            },
        )
    }
}
