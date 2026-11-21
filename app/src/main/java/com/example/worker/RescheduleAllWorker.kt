package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ScreenshotRepository

class RescheduleAllWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val scheduler = ExpirationScheduler(applicationContext)
        scheduler.rescheduleAllPending()
        ScreenshotRepository.getInstance(applicationContext).reconcileMissingFiles()
        return Result.success()
    }
}
