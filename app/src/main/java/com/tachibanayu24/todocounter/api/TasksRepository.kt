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

data class CompletedTask(
    val id: String,
    val title: String,
    val completedAt: LocalDate,
    val completedAtTimestamp: Long  // ミリ秒
)

class TasksRepository(private val context: Context) {

    private fun getTasksService(): Tasks? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(TasksScopes.TASKS_READONLY)
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
}
