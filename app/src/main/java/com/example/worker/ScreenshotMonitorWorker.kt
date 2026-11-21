package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ExpirationPreset
import com.example.data.ScreenshotRepository

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

        val newlyAdded = repository.processNewMediaStoreScreenshots(notifyUser = true)
        val defaultPreset = ExpirationPreset.fromId(prefs.defaultPresetId)
        if (defaultPreset != null) {
            newlyAdded.forEach { screenshot ->
                repository.schedulePreset(screenshot.id, defaultPreset)
            }
        }

        // Re-arm TriggerContentUri so background detection continues
        ExpirationScheduler(applicationContext).armMediaStoreObserver()
        return Result.success()
    }
}
