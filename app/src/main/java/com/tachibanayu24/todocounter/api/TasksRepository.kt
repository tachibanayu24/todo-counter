package com.tachibanayu24.todocounter.api

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.tachibanayu24.todocounter.util.DateTimeUtil
import com.tachibanayu24.todocounter.util.DateTimeUtil.toRfc3339
import com.tachibanayu24.todocounter.util.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

data class TaskCount(
    val overdue: Int,
    val today: Int
) {
    val total: Int get() = overdue + today
}

enum class TaskStatus {
    NEEDS_ACTION,
    COMPLETED
}

data class PendingTask(
    val id: String,
    val taskListId: String,
    val title: String,
    val notes: String?,
    val due: LocalDate,
    val status: TaskStatus,
    val isOverdue: Boolean
)

data class CompletedTaskDto(
    val id: String,
    val title: String,
    val completedAt: LocalDate,
    val completedAtTimestamp: Long
)

data class TaskListInfo(
    val id: String,
    val title: String
)

interface ITasksRepository {
    suspend fun getTaskCount(): Result<TaskCount>
    suspend fun getCompletedTasks(
        completedAfter: Instant? = null,
        completedBefore: Instant? = null
    ): Result<List<CompletedTaskDto>>
    suspend fun getPendingTasksDueToday(): Result<List<PendingTask>>
    suspend fun updateTaskStatus(taskListId: String, taskId: String, completed: Boolean): Result<Unit>
    suspend fun getTaskLists(): Result<List<TaskListInfo>>
    suspend fun addTask(taskListId: String, title: String, notes: String? = null, dueDate: LocalDate? = null): Result<Unit>
}

class TasksRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ITasksRepository {

    private fun getTasksService(): Tasks? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(TasksScopes.TASKS)
        ).apply {
            selectedAccount = account.account
        }

        return Tasks.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ToDo Counter")
            .build()
    }

    override suspend fun getTaskCount(): Result<TaskCount> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            // 今日の日付（ローカルタイムゾーン）
            val today = LocalDate.now()

            val taskLists = service.tasklists().list().execute().items ?: emptyList()

            var overdueCount = 0
            var todayCount = 0

            for (taskList in taskLists) {
                val tasks = service.tasks().list(taskList.id)
                    .setShowCompleted(false)
                    .setShowHidden(false)
                    .execute()
                    .items ?: continue

                for (task in tasks) {
                    if (task.due == null) continue

                    // 期限日をLocalDateとしてパース（UTCベースで日付のみ抽出）
                    val dueDate = DateTimeUtil.parseRfc3339ToLocalDate(task.due) ?: continue

                    when {
                        dueDate.isBefore(today) -> overdueCount++
                        dueDate.isEqual(today) -> todayCount++
                    }
                }
            }

            TaskCount(overdue = overdueCount, today = todayCount)
        }.onError { e ->
            Timber.e(e, "Failed to get task count")
        }
    }

    override suspend fun getCompletedTasks(
        completedAfter: Instant?,
        completedBefore: Instant?
    ): Result<List<CompletedTaskDto>> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            val completedTasks = mutableListOf<CompletedTaskDto>()

            val minDate = completedAfter ?: Instant.now().minusSeconds(365L * 24 * 60 * 60)
            val completedMinStr = DateTimeUtil.formatRfc3339(minDate)
            val completedMaxStr = completedBefore?.let { DateTimeUtil.formatRfc3339(it) }

            for (taskList in taskLists) {
                var pageToken: String? = null

                do {
                    val request = service.tasks().list(taskList.id)
                        .setShowCompleted(true)
                        .setShowHidden(true)
                        .setCompletedMin(completedMinStr)
                        .setMaxResults(100)

                    if (completedMaxStr != null) {
                        request.setCompletedMax(completedMaxStr)
                    }

                    if (pageToken != null) {
                        request.pageToken = pageToken
                    }

                    val response = request.execute()
                    val tasks = response.items ?: emptyList()

                    for (task in tasks) {
                        if (task.status == "completed" && task.completed != null) {
                            val completedInfo = DateTimeUtil.parseCompletedDate(task.completed)
                            if (completedInfo != null) {
                                completedTasks.add(
                                    CompletedTaskDto(
                                        id = task.id,
                                        title = task.title ?: "",
                                        completedAt = completedInfo.first,
                                        completedAtTimestamp = completedInfo.second
                                    )
                                )
                            }
                        }
                    }

                    pageToken = response.nextPageToken
                } while (pageToken != null)
            }

            completedTasks
        }.onError { e ->
            Timber.e(e, "Failed to get completed tasks")
        }
    }

    override suspend fun getPendingTasksDueToday(): Result<List<PendingTask>> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            val today = LocalDate.now()
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            val pendingTasks = mutableListOf<PendingTask>()

            for (taskList in taskLists) {
                val incompleteTasks = service.tasks().list(taskList.id)
                    .setShowCompleted(false)
                    .setShowHidden(false)
                    .execute()
                    .items ?: emptyList()

                val todayStartRfc3339 = today.atStartOfDay(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))
                val completedTasks = service.tasks().list(taskList.id)
                    .setShowCompleted(true)
                    .setShowHidden(true)
                    .setCompletedMin(todayStartRfc3339)
                    .execute()
                    .items?.filter { it.status == "completed" } ?: emptyList()

                val allTasks = incompleteTasks + completedTasks

                for (task in allTasks) {
                    if (task.due == null) continue

                    val dueDate = DateTimeUtil.parseRfc3339ToLocalDate(task.due) ?: continue

                    if (dueDate.isAfter(today)) continue

                    val isOverdue = dueDate.isBefore(today)
                    val status = if (task.status == "completed") TaskStatus.COMPLETED else TaskStatus.NEEDS_ACTION

                    pendingTasks.add(
                        PendingTask(
                            id = task.id,
                            taskListId = taskList.id,
                            title = task.title ?: "",
                            notes = task.notes,
                            due = dueDate,
                            status = status,
                            isOverdue = isOverdue
                        )
                    )
                }
            }

            pendingTasks.sortedWith(
                compareBy<PendingTask> { it.status == TaskStatus.COMPLETED }
                    .thenBy { it.due }
            )
        }.onError { e ->
            Timber.e(e, "Failed to get pending tasks due today")
        }
    }

    override suspend fun updateTaskStatus(
        taskListId: String,
        taskId: String,
        completed: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            val task = service.tasks().get(taskListId, taskId).execute()
            task.status = if (completed) "completed" else "needsAction"
            if (!completed) {
                task.completed = null
            }
            service.tasks().update(taskListId, taskId, task).execute()
            Unit
        }.onError { e ->
            Timber.e(e, "Failed to update task status")
        }
    }

    override suspend fun getTaskLists(): Result<List<TaskListInfo>> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            taskLists.map { TaskListInfo(id = it.id, title = it.title) }
        }.onError { e ->
            Timber.e(e, "Failed to get task lists")
        }
    }

    override suspend fun addTask(
        taskListId: String,
        title: String,
        notes: String?,
        dueDate: LocalDate?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val service = getTasksService()
            ?: return@withContext Result.Error(IllegalStateException("Not signed in"))

        Result.runCatching {
            val task = com.google.api.services.tasks.model.Task().apply {
                this.title = title
                if (notes != null) {
                    this.notes = notes
                }
                if (dueDate != null) {
                    this.due = dueDate.toRfc3339()
                }
            }
            service.tasks().insert(taskListId, task).execute()
            Unit
        }.onError { e ->
            Timber.e(e, "Failed to add task")
        }
    }
}
