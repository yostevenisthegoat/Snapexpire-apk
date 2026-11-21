package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.QuickExpireSheetActivity
import com.example.R
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.media.MediaStoreScreenshotScanner
import java.text.DateFormat
import java.util.Date

object SnapFadeNotificationHelper {
    const val CHANNEL_NEW_SCREENSHOT = "snapfade_new_screenshot"
    const val CHANNEL_WARNINGS = "snapfade_warnings"
    const val CHANNEL_EXPIRING = "snapfade_expiring"
    const val CHANNEL_MONITOR_SERVICE = "snapfade_monitor_service"

    const val EXTRA_SCREENSHOT_ID = "extra_screenshot_id"
    const val EXTRA_PRESET_ID = "extra_preset_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    private const val NOTIFICATION_BASE_NEW = 10_000
    private const val NOTIFICATION_BASE_WARN = 20_000
    private const val NOTIFICATION_PENDING_CONFIRM = 30_001
    const val NOTIFICATION_MONITOR_SERVICE = 40_001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val newChannel = NotificationChannel(
            CHANNEL_NEW_SCREENSHOT,
            context.getString(R.string.channel_new_screenshot_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_new_screenshot_desc)
        }

        val warnChannel = NotificationChannel(
            CHANNEL_WARNINGS,
            context.getString(R.string.channel_warnings_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_warnings_desc)
        }

        val expiringChannel = NotificationChannel(
            CHANNEL_EXPIRING,
            context.getString(R.string.channel_expiring_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_expiring_desc)
        }

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR_SERVICE,
            context.getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_monitor_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(newChannel, warnChannel, expiringChannel, monitorChannel)
        )
    }

    fun buildMonitorServiceNotification(context: Context): android.app.Notification {
        ensureChannels(context)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_MONITOR_SERVICE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, com.example.service.ScreenshotMonitorService::class.java).apply {
            action = com.example.service.ScreenshotMonitorService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            NOTIFICATION_MONITOR_SERVICE + 1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SnapFade overlay monitor active")
            .setContentText("Watching for new screenshots across other apps")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Pause Overlay", stopPendingIntent)
            .build()
    }

    fun showNewScreenshotNotification(context: Context, screenshot: ManagedScreenshotEntity) {
        if (!MediaStoreScreenshotScanner(context).hasNotificationPermission()) return
        ensureChannels(context)

        val notifId = NOTIFICATION_BASE_NEW + (screenshot.id.toInt() and 0x0FFF)
        val timeText = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(screenshot.capturedAtMillis))

        val popupIntent = Intent(context, QuickExpireSheetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val popupPendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val thumbnail = loadThumbnailBitmap(context, Uri.parse(screenshot.contentUri))

        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_SCREENSHOT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Screenshot captured • Set timer")
            .setContentText("${screenshot.fileName} • $timeText")
            .setContentIntent(popupPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(
                0,
                "5m",
                buildActionPendingIntent(
                    context,
                    NotificationActionReceiver.ACTION_SET_PRESET,
                    screenshot.id,
                    notifId,
                    ExpirationPreset.FIVE_MINUTES.id,
                    reqOffset = 1
                )
            )
            .addAction(
                0,
                "1h",
                buildActionPendingIntent(
                    context,
                    NotificationActionReceiver.ACTION_SET_PRESET,
                    screenshot.id,
                    notifId,
                    ExpirationPreset.ONE_HOUR.id,
                    reqOffset = 2
                )
            )
            .addAction(
                0,
                "Keep",
                buildActionPendingIntent(
                    context,
                    NotificationActionReceiver.ACTION_KEEP,
                    screenshot.id,
                    notifId,
                    null,
                    reqOffset = 3
                )
            )

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(thumbnail)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText("${screenshot.fileName} • Tap for all timers")
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }

    fun showPreExpiryWarning(context: Context, screenshot: ManagedScreenshotEntity, remainingMinutes: Long) {
        if (!MediaStoreScreenshotScanner(context).hasNotificationPermission()) return
        ensureChannels(context)

        val notifId = NOTIFICATION_BASE_WARN + (screenshot.id.toInt() and 0x0FFF)
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SCREENSHOT_ID, screenshot.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeLabel = if (remainingMinutes <= 1L) "in under 1 minute" else "in $remainingMinutes minutes"
        val builder = NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Expiring $timeLabel")
            .setContentText(screenshot.fileName)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                "+1 Hour",
                buildActionPendingIntent(
                    context,
                    NotificationActionReceiver.ACTION_EXTEND_1H,
                    screenshot.id,
                    notifId,
                    null,
                    reqOffset = 4
                )
            )
            .addAction(
                0,
                "Keep Forever",
                buildActionPendingIntent(
                    context,
                    NotificationActionReceiver.ACTION_KEEP,
                    screenshot.id,
                    notifId,
                    null,
                    reqOffset = 5
                )
            )

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        }
    }

    fun showPendingDeletionConfirmation(context: Context, pendingCount: Int, latestName: String) {
        if (!MediaStoreScreenshotScanner(context).hasNotificationPermission()) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_open_pending_confirm", true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_PENDING_CONFIRM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (pendingCount == 1) {
            "1 screenshot ready to expire"
        } else {
            "$pendingCount screenshots ready to expire"
        }
        val body = if (pendingCount == 1) {
            "Tap to confirm deletion of $latestName"
        } else {
            "Android requires confirmation to permanently delete expired files"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_EXPIRING)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_PENDING_CONFIRM, builder.build())
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    fun cancelPendingConfirmationNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_PENDING_CONFIRM)
    }

    private fun buildActionPendingIntent(
        context: Context,
        action: String,
        screenshotId: Long,
        notifId: Int,
        presetId: String?,
        reqOffset: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCREENSHOT_ID, screenshotId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
            if (presetId != null) putExtra(EXTRA_PRESET_ID, presetId)
        }
        val requestCode = ((screenshotId.toInt() and 0x0FFF) * 10) + reqOffset
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun loadThumbnailBitmap(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    val sample = (maxDim / 512).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }
}
