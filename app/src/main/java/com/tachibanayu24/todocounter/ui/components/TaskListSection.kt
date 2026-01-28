package com.tachibanayu24.todocounter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tachibanayu24.todocounter.api.PendingTask
import com.tachibanayu24.todocounter.api.TaskStatus
import com.tachibanayu24.todocounter.ui.theme.Red
import com.tachibanayu24.todocounter.ui.theme.Yellow
import java.time.format.DateTimeFormatter

@Composable
fun TaskListSection(
    tasks: List<PendingTask>,
    expandedTaskId: String?,
    updatingTaskIds: Set<String>,
    onToggleStatus: (PendingTask) -> Unit,
    onToggleExpand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Tasks due today",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        tasks.forEach { task ->
            TaskItem(
                task = task,
                isExpanded = expandedTaskId == task.id,
                isUpdating = updatingTaskIds.contains(task.id),
                onToggleStatus = { onToggleStatus(task) },
                onToggleExpand = { onToggleExpand(task.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TaskItem(
    task: PendingTask,
    isExpanded: Boolean,
    isUpdating: Boolean,
    onToggleStatus: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val isCompleted = task.status == TaskStatus.COMPLETED
    val dueDateFormatter = DateTimeFormatter.ofPattern("M/d")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { onToggleStatus() }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Due date badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (task.isOverdue) Red.copy(alpha = 0.2f) else Yellow.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = task.due.format(dueDateFormatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.isOverdue) Red else Yellow,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Expand button (only if notes exist)
                if (!task.notes.isNullOrBlank()) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Notes section (expandable)
            AnimatedVisibility(
                visible = isExpanded && !task.notes.isNullOrBlank(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Text(
                    text = task.notes ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, top = 8.dp)
                )
            }
        }
    }
}
