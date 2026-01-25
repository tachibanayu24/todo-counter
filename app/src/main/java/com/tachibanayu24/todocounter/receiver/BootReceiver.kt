package com.tachibanayu24.todocounter.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tachibanayu24.todocounter.auth.GoogleAuthManager
import com.tachibanayu24.todocounter.service.TaskCounterService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val authManager = GoogleAuthManager(context)

            // ログイン済み & オーバーレイ権限があればサービスを起動
            if (authManager.isSignedIn() && Settings.canDrawOverlays(context)) {
                TaskCounterService.start(context)
            }
        }
    }
}
