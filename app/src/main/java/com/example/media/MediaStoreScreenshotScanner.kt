package com.example.media

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeleteAttemptResult {
    data object DeletedVerified : DeleteAttemptResult()
    data object AlreadyMissing : DeleteAttemptResult()
    data class NeedsUserConsent(val intentSender: IntentSender?) : DeleteAttemptResult()
    data class Failed(val reason: String) : DeleteAttemptResult()
}

class MediaStoreScreenshotScanner(private val context: Context) {

    fun hasMediaReadPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canManageMedia(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaStore.canManageMedia(context)
        } else {
            false
        }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    suspend fun scanScreenshots(
        limit: Int = 50,
        capturedAfterMillis: Long = 0L
    ): List<ManagedScreenshotEntity> = withContext(Dispatchers.IO) {
        if (!hasMediaReadPermission()) return@withContext emptyList()

        val results = mutableListOf<ManagedScreenshotEntity>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            pathColumn
        )

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (capturedAfterMillis > 0L) {
            val secondsThreshold = (capturedAfterMillis / 1000L) - 2L
            selectionParts.add("${MediaStore.Images.Media.DATE_ADDED} >= ?")
            selectionArgs.add(secondsThreshold.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionParts.add("${MediaStore.Images.Media.IS_PENDING} = 0")
        }

        val selection = if (selectionParts.isEmpty()) null else selectionParts.joinToString(" AND ")
        val argsArray = if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray()
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                argsArray,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val dateAddedIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val dateTakenIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val bucketIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(pathColumn)

                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idIdx)
                    val displayName = cursor.getString(nameIdx) ?: "Screenshot_$id.png"
                    val bucket = cursor.getString(bucketIdx).orEmpty()
                    val relPath = cursor.getString(pathIdx).orEmpty()

                    if (!isScreenshotMedia(displayName, bucket, relPath)) {
                        continue
                    }

                    val dateAddedSec = cursor.getLong(dateAddedIdx)
                    val dateTakenMs = cursor.getLong(dateTakenIdx)
                    val capturedAt = when {
                        dateTakenMs > 0L -> dateTakenMs
                        dateAddedSec > 0L -> dateAddedSec * 1000L
                        else -> System.currentTimeMillis()
                    }

                    if (capturedAfterMillis > 0L && capturedAt < capturedAfterMillis) {
                        continue
                    }

                    val contentUri = ContentUris.withAppendedId(collection, id).toString()
                    results.add(
                        ManagedScreenshotEntity(
                            id = id,
                            contentUri = contentUri,
                            fileName = displayName,
                            relativePath = relPath.ifBlank { "Pictures/Screenshots" },
                            sizeBytes = cursor.getLong(sizeIdx),
                            width = cursor.getInt(widthIdx),
                            height = cursor.getInt(heightIdx),
                            capturedAtMillis = capturedAt,
                            status = ScreenshotStatus.NEW_DETECTED
                        )
                    )
                }
            }
        }
        results
    }

    suspend fun buildEntityFromPickedUri(uri: Uri): ManagedScreenshotEntity? = withContext(Dispatchers.IO) {
        var displayName = "Screenshot_${System.currentTimeMillis()}.png"
        var sizeBytes = 0L

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }

        val parsedId = runCatching { ContentUris.parseId(uri) }.getOrNull()
        val id = parsedId ?: -(uri.toString().hashCode().toLong().let { if (it == 0L) 1L else kotlin.math.abs(it) })
        val now = System.currentTimeMillis()

        ManagedScreenshotEntity(
            id = id,
            contentUri = uri.toString(),
            fileName = displayName,
            relativePath = "Picked Media",
            sizeBytes = sizeBytes,
            width = 0,
            height = 0,
            capturedAtMillis = now,
            status = ScreenshotStatus.NEW_DETECTED
        )
    }

    suspend fun verifyExists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val byQuery = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            }
        }.getOrNull()

        if (byQuery == true) return@withContext true

        // Fallback stream check for non-MediaStore document URIs
        if (uri.authority != MediaStore.AUTHORITY) {
            return@withContext runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }
        false
    }

    suspend fun attemptDirectDelete(uri: Uri): DeleteAttemptResult = withContext(Dispatchers.IO) {
        if (!verifyExists(uri)) {
            return@withContext DeleteAttemptResult.AlreadyMissing
        }

        try {
            val rows = context.contentResolver.delete(uri, null, null)
            val stillExists = verifyExists(uri)
            if (rows > 0 && !stillExists) {
                DeleteAttemptResult.DeletedVerified
            } else if (!stillExists) {
                DeleteAttemptResult.DeletedVerified
            } else {
                val sender = createBatchDeleteIntentSender(listOf(uri))
                DeleteAttemptResult.NeedsUserConsent(sender)
            }
        } catch (recoverable: RecoverableSecurityException) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                createBatchDeleteIntentSender(listOf(uri))
            } else {
                recoverable.userAction.actionIntent.intentSender
            }
            DeleteAttemptResult.NeedsUserConsent(sender)
        } catch (security: SecurityException) {
            val sender = createBatchDeleteIntentSender(listOf(uri))
            if (sender != null) {
                DeleteAttemptResult.NeedsUserConsent(sender)
            } else {
                DeleteAttemptResult.Failed(security.message ?: "Permission denied")
            }
        } catch (e: Exception) {
            DeleteAttemptResult.Failed(e.message ?: "Deletion failed")
        }
    }

    fun createBatchDeleteIntentSender(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
            }.getOrNull()
        } else {
            null
        }
    }

    private fun isScreenshotMedia(displayName: String, bucket: String, path: String): Boolean {
        return path.contains("screenshots", ignoreCase = true) ||
            bucket.equals("Screenshots", ignoreCase = true) ||
            displayName.startsWith("Screenshot", ignoreCase = true)
    }
}
