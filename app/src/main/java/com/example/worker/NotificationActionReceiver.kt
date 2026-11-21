package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.ExpirationPreset
import com.example.data.ScreenshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val screenshotId = intent.getLongExtra(SnapFadeNotificationHelper.EXTRA_SCREENSHOT_ID, -1L)
        val notifId = intent.getIntExtra(SnapFadeNotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notifId != -1) {
            SnapFadeNotificationHelper.cancelNotification(context, notifId)
        }
        if (screenshotId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ScreenshotRepository.getInstance(context)
                when (intent.action) {
                    ACTION_SET_PRESET -> {
                        val presetId = intent.getStringExtra(SnapFadeNotificationHelper.EXTRA_PRESET_ID)
                        val preset = ExpirationPreset.fromId(presetId)
                        if (preset != null) {
                            repo.schedulePreset(screenshotId, preset)
                        }
                    }
                    ACTION_KEEP -> {
                        repo.keepPermanently(screenshotId)
                    }
                    ACTION_EXTEND_1H -> {
                        repo.extendByHours(screenshotId, 1)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_PRESET = "com.example.snapfade.ACTION_SET_PRESET"
        const val ACTION_KEEP = "com.example.snapfade.ACTION_KEEP"
        const val ACTION_EXTEND_1H = "com.example.snapfade.ACTION_EXTEND_1H"
    }
}
