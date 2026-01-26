package com.tachibanayu24.todocounter.data.repository

import com.tachibanayu24.todocounter.data.dao.DailyCompletionDao
import com.tachibanayu24.todocounter.data.entity.DailyCompletion
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CompletionRepository(private val dao: DailyCompletionDao) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun recordCompletion(count: Int) {
        val today = LocalDate.now().format(dateFormatter)
        val existing = dao.getByDate(today)
        val newCount = (existing?.completedCount ?: 0) + count
        dao.upsert(DailyCompletion(date = today, completedCount = newCount))
    }

    suspend fun getTodayCompletion(): DailyCompletion? {
        val today = LocalDate.now().format(dateFormatter)
        return dao.getByDate(today)
    }

    suspend fun setCompletion(date: String, count: Int) {
        dao.upsert(DailyCompletion(date = date, completedCount = count))
    }

    suspend fun getWeeklyData(): List<DailyCompletion> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6)
        return fillMissingDates(startDate, endDate)
    }

    suspend fun getMonthlyData(): List<DailyCompletion> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(29)
        return fillMissingDates(startDate, endDate)
    }

    /**
     * 指定期間のすべての日付を含むリストを返す（データがない日は0で埋める）
     */
    private suspend fun fillMissingDates(startDate: LocalDate, endDate: LocalDate): List<DailyCompletion> {
        val existingData = dao.getRange(
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        ).associateBy { it.date }

        val result = mutableListOf<DailyCompletion>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dateStr = currentDate.format(dateFormatter)
            val completion = existingData[dateStr] ?: DailyCompletion(date = dateStr, completedCount = 0)
            result.add(completion)
            currentDate = currentDate.plusDays(1)
        }
        return result
    }

    suspend fun getHeatmapData(weeks: Int = 12): List<DailyCompletion> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusWeeks(weeks.toLong()).plusDays(1)
        return dao.getRange(
            startDate.format(dateFormatter),
            endDate.format(dateFormatter)
        )
    }

    suspend fun getStatistics(): CompletionStats {
        val total = dao.getTotalCompleted() ?: 0
        val firstDateStr = dao.getFirstRecordDate()

        // 平均: 最初の記録日から今日までの全日数で計算
        val average = if (firstDateStr != null && total > 0) {
            val firstDate = LocalDate.parse(firstDateStr, dateFormatter)
            val today = LocalDate.now()
            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(firstDate, today) + 1
            total.toDouble() / totalDays.toDouble()
        } else {
            0.0
        }

        return CompletionStats(
            total = total,
            average = average,
            max = dao.getMaxCompleted() ?: 0,
            activeDays = dao.getActiveDaysCount()
        )
    }

    suspend fun getCurrentStreak(): Int {
        val completions = dao.getFromDate(
            LocalDate.now().minusDays(365).format(dateFormatter)
        ).associateBy { it.date }

        var streak = 0
        var currentDate = LocalDate.now()

        // 今日の記録を確認
        val todayStr = currentDate.format(dateFormatter)
        val todayCompletion = completions[todayStr]

        // 今日に記録がなければ昨日から開始
        if (todayCompletion == null || todayCompletion.completedCount == 0) {
            currentDate = currentDate.minusDays(1)
        }

        while (true) {
            val dateStr = currentDate.format(dateFormatter)
            val completion = completions[dateStr]
            if (completion != null && completion.completedCount > 0) {
                streak++
                currentDate = currentDate.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }
}

data class CompletionStats(
    val total: Int,
    val average: Double,
    val max: Int,
    val activeDays: Int
)
