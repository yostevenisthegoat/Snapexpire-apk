package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ScreenshotRepository
import com.example.service.ScreenshotOverlayManager

class MonitorWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repository = ScreenshotRepository.getInstance(applicationContext)
        val prefs = repository.getPreferencesSnapshot()
        if (!prefs.autoDetectEnabled) {
            return Result.success()
        }

        repository.reconcileMissingFiles()
        repository.syncMonitorService()
        ExpirationScheduler(applicationContext).armMediaStoreObserver()

        val showOverlay = prefs.overlayEnabled && repository.scanner.canDrawOverlays()
        val missed = repository.processNewMediaStoreScreenshots(
            notifyUser = !showOverlay,
            emitLiveEvent = false
        )
        if (showOverlay && missed.isNotEmpty()) {
            val overlayManager = ScreenshotOverlayManager(applicationContext)
            missed.forEach { item ->
                if (!overlayManager.showOverlay(item, prefs.themeMode)) {
                    SnapFadeNotificationHelper.showNewScreenshotNotification(applicationContext, item)
                }
            }
        }
        return Result.success()
    }
}
