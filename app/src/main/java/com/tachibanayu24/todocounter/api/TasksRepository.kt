package com.tachibanayu24.todocounter.api

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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

data class CompletedTask(
    val id: String,
    val title: String,
    val completedAt: LocalDate,
    val completedAtTimestamp: Long  // ミリ秒
)

data class TaskListInfo(
    val id: String,
    val title: String
)

class TasksRepository(private val context: Context) {

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

    suspend fun getTaskCount(): TaskCount? = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext null

        try {
            // ローカルタイムゾーンでの「今日」の日付文字列
            val localDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = localDateFormat.format(Date())
            val todayDate = localDateFormat.parse(todayStr)!!

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

                    val dueDate = parseDueDate(task.due) ?: continue
                    val dueDateStr = localDateFormat.format(dueDate)

                    when {
                        dueDate.before(todayDate) -> overdueCount++
                        dueDateStr == todayStr -> todayCount++
                    }
                }
            }

            TaskCount(overdue = overdueCount, today = todayCount)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 指定期間に完了したタスクを取得
     * @param completedAfter この日時以降に完了したタスクを取得（null の場合は過去365日）
     * @param completedBefore この日時以前に完了したタスクを取得（nullの場合は現在まで）
     */
    suspend fun getCompletedTasks(
        completedAfter: Instant? = null,
        completedBefore: Instant? = null
    ): List<CompletedTask> = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext emptyList()

        try {
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            val completedTasks = mutableListOf<CompletedTask>()

            // completedMin: 指定日時以降に完了したタスクのみ取得
            val minDate = completedAfter ?: Instant.now().minusSeconds(365L * 24 * 60 * 60) // デフォルト365日前
            val rfc3339Format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneId.of("UTC"))
            val completedMinStr = rfc3339Format.format(minDate)
            val completedMaxStr = completedBefore?.let { rfc3339Format.format(it) }

            for (taskList in taskLists) {
                var pageToken: String? = null

                do {
                    val request = service.tasks().list(taskList.id)
                        .setShowCompleted(true)
                        .setShowHidden(true)
                        .setCompletedMin(completedMinStr)
                        .setMaxResults(100)

                    // completedMax が指定されている場合は設定
                    if (completedMaxStr != null) {
                        request.setCompletedMax(completedMaxStr)
                    }

                    if (pageToken != null) {
                        request.pageToken = pageToken
                    }

                    val response = request.execute()
                    val tasks = response.items ?: emptyList()

                    for (task in tasks) {
                        // 完了済みタスクのみ処理
                        if (task.status == "completed" && task.completed != null) {
                            val completedInfo = parseCompletedDate(task.completed)
                            if (completedInfo != null) {
                                completedTasks.add(
                                    CompletedTask(
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
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseDueDate(dueString: String): Date? {
        // Google Tasks APIは RFC 3339 形式で返す (例: 2024-01-15T00:00:00.000Z)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            isoFormat.parse(dueString)
        } catch (e: Exception) {
            // フォールバック: yyyy-MM-dd 形式
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(dueString)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseCompletedDate(completedString: String): Pair<LocalDate, Long>? {
        return try {
            // RFC 3339 形式 (例: 2024-01-15T10:30:00.000Z)
            val instant = Instant.parse(completedString)
            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            Pair(localDate, instant.toEpochMilli())
        } catch (e: Exception) {
            try {
                // フォールバック: yyyy-MM-dd形式
                val localDate = LocalDate.parse(completedString.substring(0, 10))
                val timestamp = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                Pair(localDate, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 今日までが期日のタスク（期限切れ + 今日）を取得
     * 完了/未完了両方を含む
     */
    suspend fun getPendingTasksDueToday(): List<PendingTask> = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext emptyList()

        try {
            val today = LocalDate.now()
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            val pendingTasks = mutableListOf<PendingTask>()

            for (taskList in taskLists) {
                // 未完了タスクを取得
                val incompleteTasks = service.tasks().list(taskList.id)
                    .setShowCompleted(false)
                    .setShowHidden(false)
                    .execute()
                    .items ?: emptyList()

                // 完了タスクも取得（今日完了したもののみ）
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

                    val dueDate = parseDueDateToLocalDate(task.due) ?: continue

                    // 今日以前のタスクのみ対象
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

            // ソート: 未完了が先、その中で期日が古い順
            pendingTasks.sortedWith(
                compareBy<PendingTask> { it.status == TaskStatus.COMPLETED }
                    .thenBy { it.due }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * タスクの完了/未完了状態を更新
     */
    suspend fun updateTaskStatus(
        taskListId: String,
        taskId: String,
        completed: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext false

        try {
            val task = service.tasks().get(taskListId, taskId).execute()
            task.status = if (completed) "completed" else "needsAction"
            if (!completed) {
                task.completed = null
            }
            service.tasks().update(taskListId, taskId, task).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseDueDateToLocalDate(dueString: String): LocalDate? {
        return try {
            // RFC 3339 形式 (例: 2024-01-15T00:00:00.000Z)
            val instant = Instant.parse(dueString)
            instant.atZone(ZoneId.of("UTC")).toLocalDate()
        } catch (e: Exception) {
            try {
                // フォールバック: yyyy-MM-dd 形式
                LocalDate.parse(dueString.substring(0, 10))
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * タスクリスト一覧を取得
     */
    suspend fun getTaskLists(): List<TaskListInfo> = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext emptyList()

        try {
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            taskLists.map { TaskListInfo(id = it.id, title = it.title) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * タスクを追加
     */
    suspend fun addTask(
        taskListId: String,
        title: String,
        notes: String? = null,
        dueDate: LocalDate? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val service = getTasksService() ?: return@withContext false

        try {
            val task = com.google.api.services.tasks.model.Task().apply {
                this.title = title
                if (notes != null) {
                    this.notes = notes
                }
                if (dueDate != null) {
                    // Google Tasks APIはRFC 3339形式を期待
                    this.due = dueDate.atStartOfDay(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))
                }
            }
            service.tasks().insert(taskListId, task).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
