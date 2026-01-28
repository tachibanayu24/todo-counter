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
    suspend fun syncCompletedTasks(days: Int): SyncResult {
        val now = Instant.now()
        val completedAfter = now.minusSeconds(days.toLong() * 24 * 60 * 60)

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

        val taskEntities = completedTasks.map { task ->
            CompletedTask(
                taskId = task.id,
                title = task.title,
                date = task.completedAt.format(dateFormatter),
                completedAt = task.completedAtTimestamp
            )
        }
        completedTaskDao.upsertAll(taskEntities)

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
