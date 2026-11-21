package com.example.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import com.example.media.DeleteAttemptResult
import com.example.media.MediaStoreScreenshotScanner
import com.example.worker.ExpirationScheduler
import com.example.worker.SnapFadeNotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class DeleteRequestOutcome {
    data class DeletedVerified(val count: Int) : DeleteRequestOutcome()
    data class NeedsSystemConfirmation(
        val intentSender: IntentSender,
        val pendingIds: List<Long>
    ) : DeleteRequestOutcome()
    data class Error(val message: String) : DeleteRequestOutcome()
}

class ScreenshotRepository private constructor(private val context: Context) {

    private val database = SnapFadeDatabase.getInstance(context)
    private val dao = database.screenshotDao()
    private val prefsRepo = UserPreferencesRepository(context)
    val scanner = MediaStoreScreenshotScanner(context)
    val scheduler = ExpirationScheduler(context)

    private val _liveDetectedScreenshot = MutableSharedFlow<ManagedScreenshotEntity>(
        extraBufferCapacity = 8
    )
    val liveDetectedScreenshot = _liveDetectedScreenshot.asSharedFlow()

    val allScreenshotsFlow: Flow<List<ManagedScreenshotEntity>> = dao.observeAll()
    val preferencesFlow: Flow<UserPreferences> = prefsRepo.preferencesFlow

    suspend fun getPreferencesSnapshot(): UserPreferences = prefsRepo.getSnapshot()

    fun observeScreenshot(id: Long): Flow<ManagedScreenshotEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ManagedScreenshotEntity? = dao.getById(id)

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        prefsRepo.setAutoDetectEnabled(enabled)
        if (enabled) {
            val prefs = prefsRepo.getSnapshot()
            if (prefs.lastMediaScanMillis == 0L) {
                prefsRepo.updateLastMediaScanMillis(System.currentTimeMillis())
            }
            scheduler.armMediaStoreObserver()
        } else {
            scheduler.disarmMediaStoreObserver()
        }
    }

    suspend fun setDefaultPresetId(presetId: String?) = prefsRepo.setDefaultPresetId(presetId)

    suspend fun setNotifyBeforeExpireMinutes(minutes: Int) =
        prefsRepo.setNotifyBeforeExpireMinutes(minutes)

    suspend fun setConfirmBeforeImmediateDelete(confirm: Boolean) =
        prefsRepo.setConfirmBeforeImmediateDelete(confirm)

    suspend fun setThemeMode(mode: ThemeMode) = prefsRepo.setThemeMode(mode)

    suspend fun initializeScanAnchorIfNeeded() {
        val prefs = prefsRepo.getSnapshot()
        if (prefs.lastMediaScanMillis == 0L) {
            prefsRepo.updateLastMediaScanMillis(System.currentTimeMillis())
        }
        if (prefs.autoDetectEnabled && scanner.hasMediaReadPermission()) {
            scheduler.armMediaStoreObserver()
        }
    }

    suspend fun importExistingScreenshotsFromMediaStore(limit: Int = 60): Int {
        if (!scanner.hasMediaReadPermission()) return 0
        val found = scanner.scanScreenshots(limit = limit, capturedAfterMillis = 0L)
        var addedCount = 0
        found.forEach { entity ->
            val existing = dao.getById(entity.id) ?: dao.getByUri(entity.contentUri)
            if (existing == null) {
                val insertedId = dao.insertIgnore(entity)
                if (insertedId != -1L) addedCount++
            }
        }
        prefsRepo.updateLastMediaScanMillis(System.currentTimeMillis())
        return addedCount
    }

    suspend fun importFromPhotoPicker(uris: List<Uri>): List<ManagedScreenshotEntity> {
        val imported = mutableListOf<ManagedScreenshotEntity>()
        for (uri in uris) {
            val existing = dao.getByUri(uri.toString())
            if (existing != null) {
                imported.add(existing)
                continue
            }
            val entity = scanner.buildEntityFromPickedUri(uri)
            if (entity != null) {
                dao.upsert(entity)
                imported.add(entity)
            }
        }
        return imported
    }

    suspend fun processNewMediaStoreScreenshots(
        notifyUser: Boolean,
        emitLiveEvent: Boolean = false
    ): List<ManagedScreenshotEntity> {
        if (!scanner.hasMediaReadPermission()) return emptyList()
        val prefs = prefsRepo.getSnapshot()
        val anchor = if (prefs.lastMediaScanMillis > 0L) {
            prefs.lastMediaScanMillis
        } else {
            System.currentTimeMillis() - 10_000L
        }

        val candidates = scanner.scanScreenshots(limit = 15, capturedAfterMillis = anchor)
        val newlyInserted = mutableListOf<ManagedScreenshotEntity>()
        var newestCapture = anchor

        for (candidate in candidates) {
            if (candidate.capturedAtMillis > newestCapture) {
                newestCapture = candidate.capturedAtMillis
            }
            val existing = dao.getById(candidate.id) ?: dao.getByUri(candidate.contentUri)
            if (existing == null) {
                val defaultPreset = ExpirationPreset.fromId(prefs.defaultPresetId)
                val initialEntity = if (defaultPreset != null) {
                    val expiresAt = System.currentTimeMillis() + defaultPreset.durationMillis
                    candidate.copy(
                        status = ScreenshotStatus.SCHEDULED,
                        expiresAtMillis = expiresAt,
                        presetLabel = defaultPreset.label
                    )
                } else {
                    candidate
                }
                dao.upsert(initialEntity)
                if (initialEntity.status == ScreenshotStatus.SCHEDULED && initialEntity.expiresAtMillis != null) {
                    scheduler.scheduleExpiration(
                        screenshotId = initialEntity.id,
                        expiresAtMillis = initialEntity.expiresAtMillis,
                        warningLeadMinutes = prefs.notifyBeforeExpireMinutes
                    )
                }
                newlyInserted.add(initialEntity)
            }
        }

        if (newlyInserted.isNotEmpty() || newestCapture > anchor) {
            prefsRepo.updateLastMediaScanMillis(maxOf(newestCapture, System.currentTimeMillis()))
        }

        for (entity in newlyInserted) {
            if (emitLiveEvent) {
                _liveDetectedScreenshot.tryEmit(entity)
            }
            if (notifyUser) {
                SnapFadeNotificationHelper.showNewScreenshotNotification(context, entity)
            }
        }
        return newlyInserted
    }

    suspend fun schedulePreset(id: Long, preset: ExpirationPreset) {
        val expiresAt = System.currentTimeMillis() + preset.durationMillis
        scheduleCustom(id, expiresAt, preset.label)
    }

    suspend fun scheduleCustom(id: Long, expiresAtMillis: Long, label: String = "Custom") {
        val prefs = prefsRepo.getSnapshot()
        val now = System.currentTimeMillis()
        dao.updateExpiration(
            id = id,
            expiresAtMillis = expiresAtMillis,
            presetLabel = label,
            status = ScreenshotStatus.SCHEDULED,
            warningNotified = false,
            updatedAt = now
        )
        scheduler.scheduleExpiration(
            screenshotId = id,
            expiresAtMillis = expiresAtMillis,
            warningLeadMinutes = prefs.notifyBeforeExpireMinutes
        )
        refreshPendingConfirmationNotification()
    }

    suspend fun batchSchedulePreset(ids: List<Long>, preset: ExpirationPreset) {
        val expiresAt = System.currentTimeMillis() + preset.durationMillis
        batchScheduleCustom(ids, expiresAt, preset.label)
    }

    suspend fun batchScheduleCustom(ids: List<Long>, expiresAtMillis: Long, label: String = "Custom") {
        ids.forEach { id ->
            scheduleCustom(id, expiresAtMillis, label)
        }
    }

    suspend fun extendByHours(id: Long, hours: Int) {
        val entity = dao.getById(id) ?: return
        val base = maxOf(entity.expiresAtMillis ?: System.currentTimeMillis(), System.currentTimeMillis())
        val updatedExpiry = base + hours * 3_600_000L
        scheduleCustom(id, updatedExpiry, "Extended")
    }

    suspend fun keepPermanently(id: Long) {
        scheduler.cancelExpiration(id)
        dao.updateExpiration(
            id = id,
            expiresAtMillis = null,
            presetLabel = "Kept Permanently",
            status = ScreenshotStatus.KEPT,
            warningNotified = false,
            updatedAt = System.currentTimeMillis()
        )
        refreshPendingConfirmationNotification()
    }

    suspend fun batchKeepPermanently(ids: List<Long>) {
        ids.forEach { keepPermanently(it) }
    }

    suspend fun cancelScheduledDeletion(id: Long) {
        scheduler.cancelExpiration(id)
        dao.updateExpiration(
            id = id,
            expiresAtMillis = null,
            presetLabel = null,
            status = ScreenshotStatus.NEW_DETECTED,
            warningNotified = false,
            updatedAt = System.currentTimeMillis()
        )
        refreshPendingConfirmationNotification()
    }

    suspend fun batchCancelScheduledDeletion(ids: List<Long>) {
        ids.forEach { cancelScheduledDeletion(it) }
    }

    suspend fun requestDeleteNow(ids: List<Long>): DeleteRequestOutcome {
        if (ids.isEmpty()) return DeleteRequestOutcome.DeletedVerified(0)
        val entities = ids.mapNotNull { dao.getById(it) }
        if (entities.isEmpty()) return DeleteRequestOutcome.DeletedVerified(0)

        var verifiedDeleted = 0
        val needsConsentEntities = mutableListOf<ManagedScreenshotEntity>()
        var fallbackConsentSender: IntentSender? = null

        for (entity in entities) {
            val uri = Uri.parse(entity.contentUri)
            when (val result = scanner.attemptDirectDelete(uri)) {
                DeleteAttemptResult.DeletedVerified,
                DeleteAttemptResult.AlreadyMissing -> {
                    scheduler.cancelExpiration(entity.id)
                    dao.markExpiredVerified(entity.id, System.currentTimeMillis())
                    verifiedDeleted++
                }
                is DeleteAttemptResult.NeedsUserConsent -> {
                    needsConsentEntities.add(entity)
                    if (fallbackConsentSender == null) {
                        fallbackConsentSender = result.intentSender
                    }
                }
                is DeleteAttemptResult.Failed -> {
                    val sender = scanner.createBatchDeleteIntentSender(listOf(uri))
                    if (sender != null) {
                        needsConsentEntities.add(entity)
                        if (fallbackConsentSender == null) fallbackConsentSender = sender
                    } else {
                        return DeleteRequestOutcome.Error(result.reason)
                    }
                }
            }
        }

        if (needsConsentEntities.isEmpty()) {
            refreshPendingConfirmationNotification()
            return DeleteRequestOutcome.DeletedVerified(verifiedDeleted)
        }

        val batchUris = needsConsentEntities.map { Uri.parse(it.contentUri) }
        val batchSender = scanner.createBatchDeleteIntentSender(batchUris) ?: fallbackConsentSender
        return if (batchSender != null) {
            DeleteRequestOutcome.NeedsSystemConfirmation(
                intentSender = batchSender,
                pendingIds = needsConsentEntities.map { it.id }
            )
        } else {
            DeleteRequestOutcome.Error(
                "Android requires file confirmation to delete these screenshots."
            )
        }
    }

    suspend fun verifyAfterSystemDeleteConfirmation(ids: List<Long>): Int {
        var verifiedCount = 0
        val now = System.currentTimeMillis()
        for (id in ids) {
            val entity = dao.getById(id) ?: continue
            val uri = Uri.parse(entity.contentUri)
            if (!scanner.verifyExists(uri)) {
                scheduler.cancelExpiration(id)
                dao.markExpiredVerified(id, now)
                verifiedCount++
            }
        }
        refreshPendingConfirmationNotification()
        return verifiedCount
    }

    suspend fun reconcileMissingFiles() {
        val active = dao.getAllNonExpired()
        val now = System.currentTimeMillis()
        for (item in active) {
            val uri = Uri.parse(item.contentUri)
            val exists = scanner.verifyExists(uri)
            if (!exists) {
                scheduler.cancelExpiration(item.id)
                dao.markExpiredVerified(item.id, now)
            } else if (
                item.status == ScreenshotStatus.SCHEDULED &&
                item.expiresAtMillis != null &&
                item.expiresAtMillis <= now
            ) {
                when (scanner.attemptDirectDelete(uri)) {
                    DeleteAttemptResult.DeletedVerified,
                    DeleteAttemptResult.AlreadyMissing -> {
                        scheduler.cancelExpiration(item.id)
                        dao.markExpiredVerified(item.id, now)
                    }
                    is DeleteAttemptResult.NeedsUserConsent,
                    is DeleteAttemptResult.Failed -> {
                        dao.updateStatus(item.id, ScreenshotStatus.PENDING_CONFIRMATION, now)
                    }
                }
            }
        }
        refreshPendingConfirmationNotification()
    }

    suspend fun removeTrackingRecord(id: Long) {
        scheduler.cancelExpiration(id)
        dao.deleteRecord(id)
        refreshPendingConfirmationNotification()
    }

    suspend fun clearExpiredHistory() {
        dao.clearExpiredHistory()
    }

    private suspend fun refreshPendingConfirmationNotification() {
        val pending = dao.getPendingConfirmation()
        if (pending.isEmpty()) {
            SnapFadeNotificationHelper.cancelPendingConfirmationNotification(context)
        }
    }

    companion object {
        @Volatile
        private var instance: ScreenshotRepository? = null

        fun getInstance(context: Context): ScreenshotRepository {
            return instance ?: synchronized(this) {
                instance ?: ScreenshotRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
