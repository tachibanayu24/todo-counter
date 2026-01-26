package com.tachibanayu24.todocounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tachibanayu24.todocounter.data.entity.DailyCompletion
import com.tachibanayu24.todocounter.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HeatmapCalendar(
    completions: List<DailyCompletion>,
    weeks: Int = 12,
    modifier: Modifier = Modifier,
    onDateClick: ((String) -> Unit)? = null
) {
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val completionMap = remember(completions) {
        completions.associateBy { it.date }
    }

    val today = LocalDate.now()
    // 今週の土曜日を終点として、そこから weeks 分遡った日曜日を起点とする
    val endOfWeek = today.with(DayOfWeek.SATURDAY)
    val startDate = endOfWeek.minusWeeks(weeks.toLong() - 1)
        .with(DayOfWeek.SUNDAY)

    val maxCount = (completions.maxOfOrNull { it.completedCount } ?: 1).coerceAtLeast(1)

    // 曜日ラベル（日曜始まり）
    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    Column(modifier = modifier) {
        // Month labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Spacer for day labels column
            Spacer(modifier = Modifier.width(28.dp))

            // Month labels
            var currentDate = startDate
            var lastMonth = -1
            repeat(weeks) { _ ->
                val month = currentDate.monthValue
                val showLabel = month != lastMonth
                lastMonth = month

                Box(
                    modifier = Modifier.width(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (showLabel) {
                        Text(
                            text = currentDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
                currentDate = currentDate.plusWeeks(1)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Main grid with day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Day of week labels column
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                dayLabels.forEach { label ->
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Calendar grid
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(weeks) { weekOffset ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(7) { dayOfWeek ->
                            val cellDate = startDate
                                .plusWeeks(weekOffset.toLong())
                                .plusDays(dayOfWeek.toLong())

                            val isInFuture = cellDate.isAfter(today)
                            val dateStr = cellDate.format(dateFormatter)
                            val completion = completionMap[dateStr]
                            val count = completion?.completedCount ?: 0

                            val color = when {
                                isInFuture -> Color.Transparent
                                count == 0 -> HeatmapEmpty
                                count <= maxCount * 0.25 -> HeatmapLevel1
                                count <= maxCount * 0.50 -> HeatmapLevel2
                                count <= maxCount * 0.75 -> HeatmapLevel3
                                else -> HeatmapLevel4
                            }

                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                                    .border(
                                        width = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .then(
                                        if (!isInFuture && onDateClick != null) {
                                            Modifier.clickable {
                                                onDateClick(dateStr)
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Less",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            listOf(HeatmapEmpty, HeatmapLevel1, HeatmapLevel2, HeatmapLevel3, HeatmapLevel4).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "More",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}
