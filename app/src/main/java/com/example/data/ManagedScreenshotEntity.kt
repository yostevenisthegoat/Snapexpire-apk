package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(
    tableName = "managed_screenshots",
    indices = [Index(value = ["contentUri"], unique = true)]
)
data class ManagedScreenshotEntity(
    @PrimaryKey val id: Long,
    val contentUri: String,
    val fileName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val capturedAtMillis: Long,
    val expiresAtMillis: Long? = null,
    val presetLabel: String? = null,
    val status: ScreenshotStatus = ScreenshotStatus.NEW_DETECTED,
    val warningNotified: Boolean = false,
    val expiredAtMillis: Long? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

class ScreenshotConverters {
    @TypeConverter
    fun fromStatus(status: ScreenshotStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): ScreenshotStatus =
        runCatching { ScreenshotStatus.valueOf(value) }.getOrDefault(ScreenshotStatus.NEW_DETECTED)
}
