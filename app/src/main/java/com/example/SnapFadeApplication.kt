package com.example

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.Configuration
import com.example.data.ScreenshotRepository
import com.example.service.ScreenshotMonitorService
import com.example.worker.SnapFadeNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SnapFadeApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private lateinit var mediaObserver: ContentObserver

    override fun onCreate() {
        super.onCreate()
        SnapFadeNotificationHelper.ensureChannels(this)

        val repository = ScreenshotRepository.getInstance(this)
        appScope.launch {
            repository.initializeScanAnchorIfNeeded()
            repository.reconcileMissingFiles()
            repository.scheduler.rescheduleAllPending()
            repository.syncMonitorService()
        }

        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (ScreenshotMonitorService.isRunning) return
                debounceJob?.cancel()
                debounceJob = appScope.launch {
                    delay(450L)
                    val prefs = repository.getPreferencesSnapshot()
                    if (prefs.autoDetectEnabled) {
                        repository.processNewMediaStoreScreenshots(
                            notifyUser = true,
                            emitLiveEvent = true
                        )
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaObserver
        )
    }
}
