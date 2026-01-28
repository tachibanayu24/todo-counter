package com.tachibanayu24.todocounter.data

import com.tachibanayu24.todocounter.api.ITasksRepository
import com.tachibanayu24.todocounter.data.dao.CompletedTaskDao
import com.tachibanayu24.todocounter.data.entity.CompletedTask
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class SyncManager @Inject constructor(
    private val tasksRepository: ITasksRepository,
    private val completionRepository: CompletionRepository,
    private val completedTaskDao: CompletedTaskDao
) {
    /**
     * 指定日数分の完了タスクをGoogle Tasksから同期
     */
    suspend fun syncCompletedTasks(days: Int): SyncResult {
        val now = Instant.now()
        val completedAfter = now.minusSeconds(days.toLong() * 24 * 60 * 60)
        val startDate = java.time.LocalDate.now().minusDays(days.toLong())

        val result = tasksRepository.getCompletedTasks(completedAfter)

        val completedTasks = result.getOrNull()
        if (completedTasks == null) {
            Timber.e(result.exceptionOrNull(), "Failed to sync completed tasks")
            return SyncResult(synced = 0)
        }

        if (completedTasks.isEmpty()) {
            return SyncResult(synced = 0)
        }

        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        // completed_tasksテーブルにタスク詳細を保存（taskIdでupsert）
        val taskEntities = completedTasks.map { task ->
            CompletedTask(
                taskId = task.id,
                title = task.title,
                date = task.completedAt.format(dateFormatter),
                completedAt = task.completedAtTimestamp
            )
        }
        completedTaskDao.upsertAll(taskEntities)

        // DBから日付ごとの正確なカウントを再集計してdaily_completionsを更新
        val dailyCounts = completedTaskDao.getDailyCountsFrom(startDate.format(dateFormatter))
        for (dailyCount in dailyCounts) {
            completionRepository.setCompletion(dailyCount.date, dailyCount.count)
        }

        Timber.d("Synced ${completedTasks.size} tasks, updated ${dailyCounts.size} daily counts")
        return SyncResult(synced = completedTasks.size)
    }

    /**
     * 今日の完了タスクのみを軽量に同期
     */
    suspend fun syncTodayCompletedTasks(): SyncResult {
        return syncCompletedTasks(1)
    }
}

data class SyncResult(
    val synced: Int
)
