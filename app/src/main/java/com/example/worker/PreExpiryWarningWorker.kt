package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ScreenshotStatus
import com.example.data.SnapFadeDatabase
import kotlin.math.ceil

class PreExpiryWarningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val screenshotId = inputData.getLong(ExpirationScheduler.KEY_SCREENSHOT_ID, -1L)
        if (screenshotId == -1L) return Result.success()

        val dao = SnapFadeDatabase.getInstance(applicationContext).screenshotDao()
        val entity = dao.getById(screenshotId) ?: return Result.success()

        if (entity.status != ScreenshotStatus.SCHEDULED || entity.expiresAtMillis == null || entity.warningNotified) {
            return Result.success()
        }

        val remainingMs = (entity.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMinutes = ceil(remainingMs / 60_000.0).toLong().coerceAtLeast(1L)

        SnapFadeNotificationHelper.showPreExpiryWarning(
            context = applicationContext,
            screenshot = entity,
            remainingMinutes = remainingMinutes
        )
        dao.markWarningNotified(entity.id, System.currentTimeMillis())
        return Result.success()
    }
}
