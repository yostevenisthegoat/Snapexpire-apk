package com.example.worker

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.ScreenshotStatus
import com.example.data.SnapFadeDatabase
import com.example.data.UserPreferencesRepository
import java.util.concurrent.TimeUnit

class ExpirationScheduler(private val context: Context) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun scheduleExpiration(
        screenshotId: Long,
        expiresAtMillis: Long,
        warningLeadMinutes: Int
    ) {
        val now = System.currentTimeMillis()
        val delayMillis = (expiresAtMillis - now).coerceAtLeast(0L)

        val expireInput = Data.Builder()
            .putLong(KEY_SCREENSHOT_ID, screenshotId)
            .build()

        val expireRequest = OneTimeWorkRequestBuilder<ExpireScreenshotWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(expireInput)
            .addTag(TAG_EXPIRATION)
            .build()

        workManager.enqueueUniqueWork(
            uniqueExpireWorkName(screenshotId),
            ExistingWorkPolicy.REPLACE,
            expireRequest
        )

        cancelWarning(screenshotId)
        if (warningLeadMinutes > 0) {
            val leadMillis = warningLeadMinutes * 60_000L
            val warnAtMillis = expiresAtMillis - leadMillis
            val warnDelay = warnAtMillis - now
            if (warnDelay > 5_000L) {
                val warnInput = Data.Builder()
                    .putLong(KEY_SCREENSHOT_ID, screenshotId)
                    .putLong(KEY_WARNING_LEAD_MINUTES, warningLeadMinutes.toLong())
                    .build()
                val warnRequest = OneTimeWorkRequestBuilder<PreExpiryWarningWorker>()
                    .setInitialDelay(warnDelay, TimeUnit.MILLISECONDS)
                    .setInputData(warnInput)
                    .addTag(TAG_WARNING)
                    .build()
                workManager.enqueueUniqueWork(
                    uniqueWarnWorkName(screenshotId),
                    ExistingWorkPolicy.REPLACE,
                    warnRequest
                )
            }
        }
    }

    fun cancelExpiration(screenshotId: Long) {
        workManager.cancelUniqueWork(uniqueExpireWorkName(screenshotId))
        cancelWarning(screenshotId)
    }

    private fun cancelWarning(screenshotId: Long) {
        workManager.cancelUniqueWork(uniqueWarnWorkName(screenshotId))
    }

    fun armMediaStoreObserver() {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(400L, TimeUnit.MILLISECONDS)
            .setTriggerContentMaxDelay(2_000L, TimeUnit.MILLISECONDS)
            .build()

        val request = OneTimeWorkRequestBuilder<ScreenshotMonitorWorker>()
            .setConstraints(constraints)
            .addTag(TAG_OBSERVER)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_MEDIA_OBSERVER,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun disarmMediaStoreObserver() {
        workManager.cancelUniqueWork(UNIQUE_MEDIA_OBSERVER)
    }

    suspend fun rescheduleAllPending() {
        val dao = SnapFadeDatabase.getInstance(context).screenshotDao()
        val prefs = UserPreferencesRepository(context).getSnapshot()
        val pendingItems = dao.getScheduledAndPending()
        for (item in pendingItems) {
            if (item.status == ScreenshotStatus.SCHEDULED && item.expiresAtMillis != null) {
                scheduleExpiration(
                    screenshotId = item.id,
                    expiresAtMillis = item.expiresAtMillis,
                    warningLeadMinutes = if (item.warningNotified) 0 else prefs.notifyBeforeExpireMinutes
                )
            }
        }
        if (prefs.autoDetectEnabled) {
            armMediaStoreObserver()
        } else {
            disarmMediaStoreObserver()
        }
    }

    companion object {
        const val KEY_SCREENSHOT_ID = "screenshot_id"
        const val KEY_WARNING_LEAD_MINUTES = "warning_lead_minutes"
        const val TAG_EXPIRATION = "snapfade_expiration"
        const val TAG_WARNING = "snapfade_warning"
        const val TAG_OBSERVER = "snapfade_observer"
        const val UNIQUE_MEDIA_OBSERVER = "snapfade_mediastore_observer"

        fun uniqueExpireWorkName(id: Long) = "expire_screenshot_$id"
        fun uniqueWarnWorkName(id: Long) = "warn_screenshot_$id"
    }
}
