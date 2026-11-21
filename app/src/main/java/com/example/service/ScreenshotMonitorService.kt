package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.ScreenshotRepository
import com.example.worker.ExpirationScheduler
import com.example.worker.SnapFadeNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenshotMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null
    private lateinit var overlayManager: ScreenshotOverlayManager
    private var mediaObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        overlayManager = ScreenshotOverlayManager(this)
        SnapFadeNotificationHelper.ensureChannels(this)

        val notification = SnapFadeNotificationHelper.buildMonitorServiceNotification(this)
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            SnapFadeNotificationHelper.NOTIFICATION_MONITOR_SERVICE,
            notification,
            fgType
        )

        val repository = ScreenshotRepository.getInstance(this)
        mediaObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                debounceJob?.cancel()
                debounceJob = serviceScope.launch {
                    val delays = longArrayOf(350L, 1_100L, 1_500L)
                    for (waitMs in delays) {
                        delay(waitMs)
                        val prefs = repository.getPreferencesSnapshot()
                        if (!prefs.autoDetectEnabled) return@launch

                        val detected = repository.processNewMediaStoreScreenshots(
                            notifyUser = false,
                            emitLiveEvent = false
                        )
                        if (detected.isNotEmpty()) {
                            for (item in detected) {
                                if (prefs.overlayEnabled && repository.scanner.canDrawOverlays()) {
                                    val shown = overlayManager.showOverlay(item, prefs.themeMode)
                                    if (!shown) {
                                        SnapFadeNotificationHelper.showNewScreenshotNotification(
                                            this@ScreenshotMonitorService,
                                            item
                                        )
                                    }
                                } else {
                                    SnapFadeNotificationHelper.showNewScreenshotNotification(
                                        this@ScreenshotMonitorService,
                                        item
                                    )
                                }
                            }
                            break
                        }
                    }
                }
            }
        }.also { observer ->
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                ScreenshotRepository.getInstance(this@ScreenshotMonitorService)
                    .setOverlayEnabled(false)
            }
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        ExpirationScheduler(applicationContext).enqueueImmediateServiceRecovery()
    }

    override fun onDestroy() {
        isRunning = false
        mediaObserver?.let { contentResolver.unregisterContentObserver(it) }
        mediaObserver = null
        overlayManager.hideOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.example.snapfade.ACTION_STOP_MONITOR_SERVICE"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
