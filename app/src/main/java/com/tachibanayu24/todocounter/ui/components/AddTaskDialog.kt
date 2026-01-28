package com.tachibanayu24.todocounter.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tachibanayu24.todocounter.R
import com.tachibanayu24.todocounter.api.TaskListInfo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    taskLists: List<TaskListInfo>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAddTask: (taskListId: String, title: String, notes: String?, dueDate: LocalDate?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedTaskList by remember(taskLists) {
        mutableStateOf(taskLists.firstOrNull())
    }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.add_task)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title input (required)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.task_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )

                // Notes input (optional)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.task_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading
                )

                // Task list selector
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isLoading) expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTaskList?.title ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.task_list)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        taskLists.forEach { taskList ->
                            DropdownMenuItem(
                                text = { Text(taskList.title) },
                                onClick = {
                                    selectedTaskList = taskList
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Due date selector (optional)
                OutlinedTextField(
                    value = dueDate?.format(dateFormatter) ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.due_date)) },
                    placeholder = { Text(stringResource(R.string.due_date_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.select_date))
                    }
                    if (dueDate != null) {
                        OutlinedButton(
                            onClick = { dueDate = null },
                            enabled = !isLoading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedTaskList?.let { taskList ->
                        onAddTask(
                            taskList.id,
                            title.trim(),
                            notes.trim().takeIf { it.isNotEmpty() },
                            dueDate
                        )
                    }
                },
                enabled = !isLoading && title.isNotBlank() && selectedTaskList != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.add))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = dueDate?.toEpochDay()?.times(86400000L)
                ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dueDate = LocalDate.ofEpochDay(millis / 86400000L)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
