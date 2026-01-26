package com.tachibanayu24.todocounter.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import com.tachibanayu24.todocounter.api.TasksRepository
import com.tachibanayu24.todocounter.data.AppDatabase
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import com.tachibanayu24.todocounter.overlay.TaskCounterOverlay
import kotlinx.coroutines.*

class TaskCounterService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: TasksRepository
    private lateinit var completionRepository: CompletionRepository
    private var overlay: TaskCounterOverlay? = null
    private var periodicJob: Job? = null
    private var lastTaskCount: Int? = null  // 前回のタスク数を保存

    override fun onCreate() {
        super.onCreate()
        repository = TasksRepository(this)
        completionRepository = CompletionRepository(
            AppDatabase.getInstance(this).dailyCompletionDao()
        )

        if (Settings.canDrawOverlays(this)) {
            overlay = TaskCounterOverlay(this) { onOverlayTap() }
        }
    }

    private fun onOverlayTap() {
        updateOverlay(vibrateOnSuccess = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, null -> startService()
            ACTION_STOP -> stopSelf()
            ACTION_UPDATE -> updateOverlay()
        }
        return START_STICKY
    }

    private fun startService() {
        updateOverlay()
        startPeriodicUpdate()
    }

    private fun startPeriodicUpdate() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000L) // 5分
                updateOverlay()
            }
        }
    }

    private fun updateOverlay(vibrateOnSuccess: Boolean = false) {
        scope.launch {
            val count = repository.getTaskCount()

            // オーバーレイを初期化（権限が後から許可された場合）
            if (overlay == null && Settings.canDrawOverlays(this@TaskCounterService)) {
                overlay = TaskCounterOverlay(this@TaskCounterService) { onOverlayTap() }
            }

            // オーバーレイ：0なら非表示、1以上なら表示
            overlay?.let {
                val total = count?.total ?: 0
                val previousCount = lastTaskCount

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

                    // タスクが減っていた場合のみ成功アニメーション & DB記録
                    if (previousCount != null && total < previousCount) {
                        val completedCount = previousCount - total
                        it.animateSuccess()
                        // DBに完了数を記録
                        scope.launch(Dispatchers.IO) {
                            completionRepository.recordCompletion(completedCount)
                        }
                    }
                }

                // 今回のタスク数を保存
                lastTaskCount = total
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        periodicJob?.cancel()
        overlay?.hide()
        scope.cancel()
    }

    companion object {
        const val ACTION_START = "com.tachibanayu24.todocounter.START"
        const val ACTION_STOP = "com.tachibanayu24.todocounter.STOP"
        const val ACTION_UPDATE = "com.tachibanayu24.todocounter.UPDATE"

        fun start(context: Context) {
            val intent = Intent(context, TaskCounterService::class.java).apply {
                action = ACTION_START
            }
            context.startService(intent)
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
