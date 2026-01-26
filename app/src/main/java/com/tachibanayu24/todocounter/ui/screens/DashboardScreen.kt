package com.tachibanayu24.todocounter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tachibanayu24.todocounter.data.entity.CompletedTask
import com.tachibanayu24.todocounter.ui.components.AnimatedCard
import com.tachibanayu24.todocounter.ui.components.HeatmapCalendar
import com.tachibanayu24.todocounter.ui.theme.Green
import com.tachibanayu24.todocounter.viewmodel.ChartPeriod
import com.tachibanayu24.todocounter.viewmodel.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.TaskAlt,
                    value = "${uiState.stats.total}",
                    label = "Total"
                )
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ShowChart,
                    value = String.format("%.1f", uiState.stats.average),
                    label = "Average"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.EmojiEvents,
                    value = "${uiState.stats.max}",
                    label = "Best Day"
                )
                StatsCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = "${uiState.currentStreak}",
                    label = "Streak"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Chart section
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                delayMillis = 100
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Completed Tasks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                onClick = { viewModel.setPeriod(ChartPeriod.WEEK) },
                                label = { Text("7D") },
                                selected = uiState.selectedPeriod == ChartPeriod.WEEK
                            )
                            FilterChip(
                                onClick = { viewModel.setPeriod(ChartPeriod.MONTH) },
                                label = { Text("30D") },
                                selected = uiState.selectedPeriod == ChartPeriod.MONTH
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.chartData.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No data yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val isMonthView = uiState.selectedPeriod == ChartPeriod.MONTH
                        SimpleBarChart(
                            data = uiState.chartData.map { it.completedCount },
                            labels = uiState.chartData.map {
                                val date = LocalDate.parse(it.date)
                                if (isMonthView) {
                                    date.format(DateTimeFormatter.ofPattern("d"))
                                } else {
                                    date.format(DateTimeFormatter.ofPattern("M/d"))
                                }
                            },
                            dates = uiState.chartData.map { it.date },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            labelInterval = if (isMonthView) 5 else 1,
                            onBarClick = { date ->
                                viewModel.selectDate(date)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Heatmap section
            AnimatedCard(
                modifier = Modifier.fillMaxWidth(),
                delayMillis = 200
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HeatmapCalendar(
                        completions = uiState.heatmapData,
                        weeks = 12,
                        modifier = Modifier.fillMaxWidth(),
                        onDateClick = { date ->
                            viewModel.selectDate(date)
                        }
                    )
                }
            }
        }
    }

    // タスク詳細ダイアログ
    if (uiState.selectedDate != null) {
        TaskDetailsDialog(
            date = uiState.selectedDate!!,
            tasks = uiState.selectedDateTasks,
            onDismiss = { viewModel.clearSelection() }
        )
    }
}

@Composable
private fun TaskDetailsDialog(
    date: String,
    tasks: List<CompletedTask>,
    onDismiss: () -> Unit
) {
    val displayFormatter = DateTimeFormatter.ofPattern("yyyy/M/d (E)", java.util.Locale.JAPANESE)
    val formattedDate = try {
        LocalDate.parse(date).format(displayFormatter)
    } catch (e: Exception) {
        date
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // ヘッダー
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${tasks.size} tasks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Green
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (tasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completed tasks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tasks) { task ->
                            TaskItem(task = task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskItem(task: CompletedTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.TaskAlt,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = task.title.ifEmpty { "(No title)" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SimpleBarChart(
    data: List<Int>,
    labels: List<String>,
    dates: List<String>,
    modifier: Modifier = Modifier,
    labelInterval: Int = 1,
    onBarClick: ((String) -> Unit)? = null
) {
    val maxValue = (data.maxOrNull() ?: 1).coerceAtLeast(1)

    Column(modifier = modifier) {
        // Chart area
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEachIndexed { index, value ->
                val heightFraction = value.toFloat() / maxValue.toFloat()
                val date = dates.getOrNull(index)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (date != null && onBarClick != null) {
                                Modifier.clickable { onBarClick(date) }
                            } else {
                                Modifier
                            }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Value label
                    if (value > 0) {
                        Text(
                            text = "$value",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    // Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .fillMaxHeight(heightFraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(Green)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Labels (show at intervals)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            labels.forEachIndexed { index, label ->
                val showLabel = index % labelInterval == 0
                Text(
                    text = if (showLabel) label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 8.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
