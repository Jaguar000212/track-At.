package com.jaguar.attendancetracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jaguar.attendancetracker.backend.entities.SessionRecord
import com.jaguar.attendancetracker.backend.entities.Subject
import com.jaguar.attendancetracker.ui.theme.AppTypography
import java.time.DayOfWeek
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionBottomSheet(
    subjects: List<Subject>, onDismiss: () -> Unit, onSave: (sessionRecord: SessionRecord) -> Unit
) {
    if (subjects.isNotEmpty()) {
        var subjectId: UUID by remember { mutableStateOf(subjects[0].id) }
        val daysOfWeek = remember { mutableStateListOf<Int>() }

        var subjectSelectionMenu: Boolean by remember { mutableStateOf(false) }

        ModalBottomSheet({ onDismiss() }) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "New Session",
                    style = AppTypography.titleLarge,
                    modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(8.dp))

                ExposedDropdownMenuBox(
                    expanded = subjectSelectionMenu,
                    onExpandedChange = { subjectSelectionMenu = it },
                ) {
                    TextField(
                        subjects.firstOrNull { it.id == subjectId }?.name ?: "No Subject",
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .padding(8.dp),
                        onValueChange = { },
                        readOnly = true,
                        label = {
                            Text(
                                "Subject",
                                style = AppTypography.titleSmall,
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectSelectionMenu) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors()

                    )
                    ExposedDropdownMenu(
                        expanded = subjectSelectionMenu,
                        onDismissRequest = { subjectSelectionMenu = false }) {
                        subjects.forEach { subject ->
                            DropdownMenuItem({
                                Text(
                                    subject.name,
                                    style = AppTypography.labelLarge,
                                )
                            }, onClick = {
                                subjectId = subject.id
                                subjectSelectionMenu = false
                            })
                        }
                    }
                }
                DayOfWeek.entries.forEach { day ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = daysOfWeek.contains(day.value), onCheckedChange = { bool ->
                                if (bool) daysOfWeek.add(day.value)
                                else daysOfWeek.remove(day.value)
                            })
                        Text(
                            day.name,
                            style = AppTypography.labelLarge,
                        )
                    }
                }
            }
            TextButton(
                {
                    if (daysOfWeek.isNotEmpty()) daysOfWeek.forEach { day ->
                        onSave(
                            SessionRecord(
                                subjectId = subjectId,
                                dayOfWeek = day,
                                startDate = subjects.first { it.id == subjectId }.startDate
                            )
                        )
                    }
                    onDismiss()
                }, modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp)
            ) {
                Text(
                    "Finish",
                    style = AppTypography.labelMedium,
                )
            }
        }
    } else {
        AlertDialog(onDismissRequest = { onDismiss() }, title = {
            Text(
                "No Subjects Available",
                style = AppTypography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }, text = {
            Text(
                "Please add a subject before adding a session.",
                style = AppTypography.bodyMedium,
            )
        }, confirmButton = {
            TextButton({ onDismiss() }) {
                Text(
                    "OK",
                    style = AppTypography.labelMedium,
                )
            }
        })
    }
}