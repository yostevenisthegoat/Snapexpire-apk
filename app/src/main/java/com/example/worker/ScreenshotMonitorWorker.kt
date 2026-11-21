package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ScreenshotRepository
import com.example.service.ScreenshotOverlayManager

class ScreenshotMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = ScreenshotRepository.getInstance(applicationContext)
        val prefs = repository.getPreferencesSnapshot()
        if (!prefs.autoDetectEnabled) {
            return Result.success()
        }

        // Ensure foreground monitor service stays alive when overlay is enabled
        repository.syncMonitorService()

        val showOverlay = prefs.overlayEnabled && repository.scanner.canDrawOverlays()
        val newlyAdded = repository.processNewMediaStoreScreenshots(
            notifyUser = !showOverlay,
            emitLiveEvent = false
        )

        if (showOverlay && newlyAdded.isNotEmpty()) {
            val overlayManager = ScreenshotOverlayManager(applicationContext)
            newlyAdded.forEach { item ->
                val shown = overlayManager.showOverlay(item, prefs.themeMode)
                if (!shown) {
                    SnapFadeNotificationHelper.showNewScreenshotNotification(applicationContext, item)
                }
            }
        }

        // Re-arm TriggerContentUri so background detection continues even if service is stopped
        ExpirationScheduler(applicationContext).armMediaStoreObserver()
        return Result.success()
    }
}
