package com.example.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ScreenshotStatus
import com.example.data.SnapFadeDatabase
import com.example.data.UserPreferencesRepository
import com.example.media.DeleteAttemptResult
import com.example.media.MediaStoreScreenshotScanner

class ExpireScreenshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val screenshotId = inputData.getLong(ExpirationScheduler.KEY_SCREENSHOT_ID, -1L)
        if (screenshotId == -1L) return Result.success()

        val dao = SnapFadeDatabase.getInstance(applicationContext).screenshotDao()
        val entity = dao.getById(screenshotId) ?: return Result.success()

        if (entity.status != ScreenshotStatus.SCHEDULED || entity.expiresAtMillis == null) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        if (now < entity.expiresAtMillis - 1500L) {
            val prefs = UserPreferencesRepository(applicationContext).getSnapshot()
            ExpirationScheduler(applicationContext).scheduleExpiration(
                screenshotId = entity.id,
                expiresAtMillis = entity.expiresAtMillis,
                warningLeadMinutes = if (entity.warningNotified) 0 else prefs.notifyBeforeExpireMinutes
            )
            return Result.success()
        }

        val scanner = MediaStoreScreenshotScanner(applicationContext)
        val uri = Uri.parse(entity.contentUri)

        when (scanner.attemptDirectDelete(uri)) {
            DeleteAttemptResult.DeletedVerified,
            DeleteAttemptResult.AlreadyMissing -> {
                dao.markExpiredVerified(entity.id, System.currentTimeMillis())
            }
            is DeleteAttemptResult.NeedsUserConsent,
            is DeleteAttemptResult.Failed -> {
                dao.updateStatus(
                    id = entity.id,
                    status = ScreenshotStatus.PENDING_CONFIRMATION,
                    updatedAt = System.currentTimeMillis()
                )
                val pending = dao.getPendingConfirmation()
                SnapFadeNotificationHelper.showPendingDeletionConfirmation(
                    context = applicationContext,
                    pendingCount = pending.size.coerceAtLeast(1),
                    latestName = entity.fileName
                )
            }
        }
        return Result.success()
    }
}
