package com.tachibanayu24.todocounter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.tachibanayu24.todocounter.MainActivity
import com.tachibanayu24.todocounter.R
import com.tachibanayu24.todocounter.api.TaskCount
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.overlay.TaskCounterOverlay
import kotlinx.coroutines.*

class TaskCounterService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: TasksRepository
    private var overlay: TaskCounterOverlay? = null
    private var periodicJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = TasksRepository(this)
        createNotificationChannel()

        if (Settings.canDrawOverlays(this)) {
            overlay = TaskCounterOverlay(this) { onOverlayTap() }
        }
    }

    private fun onOverlayTap() {
        // タップで即更新（成功時バイブ）
        updateNotification(vibrateOnSuccess = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startService() // nullはシステム再起動時
            ACTION_STOP -> stopSelf()
            ACTION_UPDATE -> updateNotification()
        }
        return START_STICKY
    }

    private fun startService() {
        val notification = createNotification(null)
        startForeground(NOTIFICATION_ID, notification)
        updateNotification()
        startPeriodicUpdate()
    }

    private fun startPeriodicUpdate() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // 5分
                updateNotification()
            }
        }
    }

    private fun updateNotification(vibrateOnSuccess: Boolean = false) {
        scope.launch {
            val count = repository.getTaskCount()
            val notification = createNotification(count)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)

            // オーバーレイを初期化（権限が後から許可された場合）
            if (overlay == null && Settings.canDrawOverlays(this@TaskCounterService)) {
                overlay = TaskCounterOverlay(this@TaskCounterService) { onOverlayTap() }
            }

            // オーバーレイ：0なら非表示、1以上なら表示
            overlay?.let {
                val total = count?.total ?: 0
                if (total == 0) {
                    it.hide()
                } else if (it.isShowing()) {
                    it.updateCount(count)
                } else {
                    it.show(count)
                }

                // fetch成功時にバイブ
                if (vibrateOnSuccess && count != null) {
                    it.vibrate()
                }
            }
        }
    }

    private fun createNotification(count: TaskCount?): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val total = count?.total ?: 0
        val contentText = if (count != null) {
            "期限切れ: ${count.overdue} / 今日: ${count.today}"
        } else {
            "読み込み中..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("残りタスク: $total")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        periodicJob?.cancel()
        overlay?.hide()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "task_counter_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.tachibanayu24.todocounter.START"
        const val ACTION_STOP = "com.tachibanayu24.todocounter.STOP"
        const val ACTION_UPDATE = "com.tachibanayu24.todocounter.UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, TaskCounterService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TaskCounterService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun update(context: Context) {
            val intent = Intent(context, TaskCounterService::class.java).apply {
                action = ACTION_UPDATE
            }
            context.startService(intent)
        }
    }
}
