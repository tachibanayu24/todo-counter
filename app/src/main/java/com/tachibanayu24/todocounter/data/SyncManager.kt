package com.tachibanayu24.todocounter.data

import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.entity.CompletedTask
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import java.time.Instant
import java.time.format.DateTimeFormatter

class SyncManager(
    private val tasksRepository: TasksRepository,
    private val completionRepository: CompletionRepository,
    private val completedTaskDao: CompletedTaskDao
) {
    /**
     * 過去N日分の完了タスクを同期
     * @param days 同期する日数（例: 30, 90, 365）
     */
    suspend fun syncCompletedTasks(days: Int): SyncResult {
        val now = Instant.now()
        val completedAfter = now.minusSeconds(days.toLong() * 24 * 60 * 60)

        val completedTasks = tasksRepository.getCompletedTasks(completedAfter)

        if (completedTasks.isEmpty()) {
            return SyncResult(synced = 0)
        }

        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // タスク詳細をDBに保存
        val taskEntities = completedTasks.map { task ->
            CompletedTask(
                taskId = task.id,
                title = task.title,
                date = task.completedAt.format(dateFormatter),
                completedAt = task.completedAtTimestamp
            )
        }
        completedTaskDao.upsertAll(taskEntities)

        // 日付ごとに完了数を集計してDailyCompletionに保存
        val dailyCounts = completedTasks
            .groupBy { it.completedAt }
            .mapValues { it.value.size }

        for ((date, count) in dailyCounts) {
            val dateStr = date.format(dateFormatter)
            completionRepository.setCompletion(dateStr, count)
        }

        return SyncResult(synced = completedTasks.size)
    }
}

data class SyncResult(
    val synced: Int
)
