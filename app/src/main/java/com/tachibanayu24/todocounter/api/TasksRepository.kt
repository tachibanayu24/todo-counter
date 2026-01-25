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
import java.util.*

data class TaskCount(
    val overdue: Int,
    val today: Int
) {
    val total: Int get() = overdue + today
}

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
}
