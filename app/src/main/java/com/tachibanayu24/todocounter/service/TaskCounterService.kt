package com.tachibanayu24.todocounter.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import com.tachibanayu24.todocounter.api.ITasksRepository
import com.tachibanayu24.todocounter.data.repository.CompletionRepository
import com.tachibanayu24.todocounter.overlay.TaskCounterOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class TaskCounterService : Service() {

    @Inject
    lateinit var repository: ITasksRepository

    @Inject
    lateinit var completionRepository: CompletionRepository

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var overlay: TaskCounterOverlay? = null
    private var periodicJob: Job? = null
    private var lastTaskCount: Int? = null

    override fun onCreate() {
        super.onCreate()
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
            val result = repository.getTaskCount()
            val count = result.getOrNull()

            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Failed to get task count in service")
            }

            if (overlay == null && Settings.canDrawOverlays(this@TaskCounterService)) {
                overlay = TaskCounterOverlay(this@TaskCounterService) { onOverlayTap() }
            }

            overlay?.let {
                // countがnullの場合（エラーまたは未サインイン）はオーバーレイを非表示
                if (count == null) {
                    it.hide()
                    return@launch
                }

                val total = count.total
                val previousCount = lastTaskCount

                if (total == 0) {
                    it.hide()
                } else if (it.isShowing()) {
                    it.updateCount(count)
                } else {
                    it.show(count)
                }

                if (vibrateOnSuccess) {
                    it.vibrate()

                    if (previousCount != null && total < previousCount) {
                        val completedCount = previousCount - total
                        it.animateSuccess()
                        scope.launch(Dispatchers.IO) {
                            completionRepository.recordCompletion(completedCount)
                        }
                    }
                }

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
