package com.example.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object TimeFormatters {

    fun formatCountdown(expiresAtMillis: Long?, nowMillis: Long): String {
        if (expiresAtMillis == null) return "--:--"
        val diff = expiresAtMillis - nowMillis
        if (diff <= 0L) return "Expired"

        val totalSeconds = diff / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> String.format(Locale.US, "%02dh %02dm", hours, minutes)
            else -> String.format(Locale.US, "%02dm %02ds", minutes, seconds)
        }
    }

    fun formatProgressFraction(
        capturedAtMillis: Long,
        expiresAtMillis: Long?,
        nowMillis: Long,
        updatedAtMillis: Long
    ): Float {
        if (expiresAtMillis == null) return 0f
        val start = maxOf(capturedAtMillis, updatedAtMillis).coerceAtMost(expiresAtMillis - 1L)
        val total = (expiresAtMillis - start).coerceAtLeast(1L).toFloat()
        val remaining = (expiresAtMillis - nowMillis).coerceIn(0L, total.toLong()).toFloat()
        return (remaining / total).coerceIn(0f, 1f)
    }

    fun formatCaptureTime(millis: Long): String {
        val now = System.currentTimeMillis()
        val diff = abs(now - millis)
        return when {
            diff < 60_000L -> "Just now"
            diff < 3_600_000L -> "${(diff / 60_000L)}m ago"
            diff < 86_400_000L -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
        }
    }

    fun formatExactDateTime(millis: Long): String {
        return SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault()).format(Date(millis))
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
