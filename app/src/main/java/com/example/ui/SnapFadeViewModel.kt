package com.example.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeleteRequestOutcome
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotFilter
import com.example.data.ScreenshotRepository
import com.example.data.ScreenshotStatus
import com.example.data.ThemeMode
import com.example.data.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardMetrics(
    val totalManaged: Int = 0,
    val scheduledCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val keptCount: Int = 0,
    val pendingConfirmCount: Int = 0,
    val clearedCount: Int = 0,
    val freedBytes: Long = 0L
)

data class PendingSystemDeleteRequest(
    val intentSender: IntentSender,
    val targetIds: List<Long>
)

data class SnapFadeUiState(
    val allScreenshots: List<ManagedScreenshotEntity> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics(),
    val expiringSoon: List<ManagedScreenshotEntity> = emptyList(),
    val recentlyCaptured: List<ManagedScreenshotEntity> = emptyList(),
    val pendingConfirmationItems: List<ManagedScreenshotEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences(),
    val activeFilter: ScreenshotFilter = ScreenshotFilter.ALL,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val activeSheetScreenshot: ManagedScreenshotEntity? = null,
    val showBatchTimerSheet: Boolean = false,
    val confirmDeleteIds: List<Long>? = null,
    val hasMediaPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val canManageMedia: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
    val isScanning: Boolean = false
)

class SnapFadeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScreenshotRepository.getInstance(application)

    private val _activeFilter = MutableStateFlow(ScreenshotFilter.ALL)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _activeSheetScreenshotId = MutableStateFlow<Long?>(null)
    private val _showBatchTimerSheet = MutableStateFlow(false)
    private val _confirmDeleteIds = MutableStateFlow<List<Long>?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _permissionRefreshTrigger = MutableStateFlow(0)
    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())

    private val _systemDeleteRequests = MutableSharedFlow<PendingSystemDeleteRequest>(extraBufferCapacity = 4)
    val systemDeleteRequests = _systemDeleteRequests.asSharedFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private val internalState = combine(
        _activeFilter,
        _selectedIds,
        _isSelectionMode,
        _activeSheetScreenshotId,
        _showBatchTimerSheet
    ) { filter, selected, selectionMode, sheetId, showBatch ->
        InternalUiSlice(filter, selected, selectionMode, sheetId, showBatch)
    }

    private val auxState = combine(
        _confirmDeleteIds,
        _isScanning,
        _permissionRefreshTrigger,
        _nowMillis
    ) { confirmIds, scanning, permTick, now ->
        AuxUiSlice(confirmIds, scanning, permTick, now)
    }

    val uiState: StateFlow<SnapFadeUiState> = combine(
        repository.allScreenshotsFlow,
        repository.preferencesFlow,
        internalState,
        auxState
    ) { screenshots, prefs, internal, aux ->
        val now = aux.nowMillis
        val soonThreshold = now + EXPIRING_SOON_WINDOW_MS

        val activeItems = screenshots.filter { it.status != ScreenshotStatus.EXPIRED }
        val scheduled = activeItems.filter { it.status == ScreenshotStatus.SCHEDULED }
        val expiringSoon = scheduled
            .filter { (it.expiresAtMillis ?: Long.MAX_VALUE) <= soonThreshold }
            .sortedBy { it.expiresAtMillis }
        val pendingConfirm = activeItems.filter { it.status == ScreenshotStatus.PENDING_CONFIRMATION }
        val kept = activeItems.filter { it.status == ScreenshotStatus.KEPT }
        val expired = screenshots.filter { it.status == ScreenshotStatus.EXPIRED }

        val metrics = DashboardMetrics(
            totalManaged = activeItems.size,
            scheduledCount = scheduled.size,
            expiringSoonCount = expiringSoon.size,
            keptCount = kept.size,
            pendingConfirmCount = pendingConfirm.size,
            clearedCount = expired.size,
            freedBytes = expired.sumOf { it.sizeBytes }
        )

        val recent = activeItems.sortedByDescending { it.capturedAtMillis }.take(12)
        val sheetItem = internal.activeSheetId?.let { id -> screenshots.firstOrNull { it.id == id } }

        SnapFadeUiState(
            allScreenshots = screenshots,
            metrics = metrics,
            expiringSoon = expiringSoon,
            recentlyCaptured = recent,
            pendingConfirmationItems = pendingConfirm,
            preferences = prefs,
            activeFilter = internal.filter,
            selectedIds = internal.selectedIds,
            isSelectionMode = internal.isSelectionMode,
            activeSheetScreenshot = sheetItem,
            showBatchTimerSheet = internal.showBatchTimerSheet,
            confirmDeleteIds = aux.confirmDeleteIds,
            hasMediaPermission = repository.scanner.hasMediaReadPermission(),
            hasNotificationPermission = repository.scanner.hasNotificationPermission(),
            canManageMedia = repository.scanner.canManageMedia(),
            canDrawOverlays = repository.scanner.canDrawOverlays(),
            nowMillis = now,
            isScanning = aux.isScanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SnapFadeUiState(
            hasMediaPermission = repository.scanner.hasMediaReadPermission(),
            hasNotificationPermission = repository.scanner.hasNotificationPermission(),
            canManageMedia = repository.scanner.canManageMedia(),
            canDrawOverlays = repository.scanner.canDrawOverlays()
        )
    )

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                _nowMillis.value = System.currentTimeMillis()
            }
        }

        viewModelScope.launch {
            repository.liveDetectedScreenshot.collect { detected ->
                if (_activeSheetScreenshotId.value == null) {
                    _activeSheetScreenshotId.value = detected.id
                }
            }
        }
    }

    fun refreshPermissionsAndReconcile() {
        _permissionRefreshTrigger.update { it + 1 }
        viewModelScope.launch {
            repository.reconcileMissingFiles()
            if (repository.scanner.hasMediaReadPermission()) {
                val prefs = repository.getPreferencesSnapshot()
                if (prefs.autoDetectEnabled) {
                    repository.scheduler.armMediaStoreObserver()
                }
            }
            repository.syncMonitorService()
        }
    }

    fun scanDeviceScreenshots() {
        viewModelScope.launch {
            if (!repository.scanner.hasMediaReadPermission()) {
                _snackbarMessages.tryEmit("Grant photo access or pick screenshots manually")
                return@launch
            }
            _isScanning.value = true
            try {
                val added = repository.importExistingScreenshotsFromMediaStore(limit = 60)
                repository.reconcileMissingFiles()
                val msg = if (added > 0) {
                    "Imported $added screenshot${if (added == 1) "" else "s"} from MediaStore"
                } else {
                    "Screenshots are up to date"
                }
                _snackbarMessages.tryEmit(msg)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun importFromPhotoPicker(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importFromPhotoPicker(uris)
            if (imported.size == 1) {
                _activeSheetScreenshotId.value = imported.first().id
            } else if (imported.size > 1) {
                _snackbarMessages.tryEmit("Added ${imported.size} screenshots")
            }
        }
    }

    fun openScreenshotSheet(id: Long) {
        _activeSheetScreenshotId.value = id
    }

    fun dismissScreenshotSheet() {
        _activeSheetScreenshotId.value = null
    }

    fun setFilter(filter: ScreenshotFilter) {
        _activeFilter.value = filter
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: Long) {
        _isSelectionMode.value = true
        _selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun selectAllFiltered(ids: List<Long>) {
        _isSelectionMode.value = true
        _selectedIds.value = ids.toSet()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun openBatchTimerSheet() {
        if (_selectedIds.value.isNotEmpty()) {
            _showBatchTimerSheet.value = true
        }
    }

    fun dismissBatchTimerSheet() {
        _showBatchTimerSheet.value = false
    }

    fun schedulePreset(id: Long, preset: ExpirationPreset) {
        viewModelScope.launch {
            repository.schedulePreset(id, preset)
            _activeSheetScreenshotId.value = null
            _snackbarMessages.tryEmit("Scheduled deletion in ${preset.label}")
        }
    }

    fun scheduleCustom(id: Long, expiresAtMillis: Long) {
        viewModelScope.launch {
            if (expiresAtMillis <= System.currentTimeMillis() + 5_000L) {
                _snackbarMessages.tryEmit("Please select a future date and time")
                return@launch
            }
            repository.scheduleCustom(id, expiresAtMillis, "Custom")
            _activeSheetScreenshotId.value = null
            _snackbarMessages.tryEmit("Custom expiration timer set")
        }
    }

    fun batchSchedulePreset(preset: ExpirationPreset) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchSchedulePreset(ids, preset)
            _showBatchTimerSheet.value = false
            clearSelection()
            _snackbarMessages.tryEmit("Set ${preset.label} timer for ${ids.size} screenshots")
        }
    }

    fun batchScheduleCustom(expiresAtMillis: Long) {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            if (expiresAtMillis <= System.currentTimeMillis() + 5_000L) {
                _snackbarMessages.tryEmit("Please select a future date and time")
                return@launch
            }
            repository.batchScheduleCustom(ids, expiresAtMillis, "Custom")
            _showBatchTimerSheet.value = false
            clearSelection()
            _snackbarMessages.tryEmit("Custom timer set for ${ids.size} screenshots")
        }
    }

    fun keepPermanently(id: Long) {
        viewModelScope.launch {
            repository.keepPermanently(id)
            _activeSheetScreenshotId.value = null
            _snackbarMessages.tryEmit("Marked to keep permanently")
        }
    }

    fun batchKeepPermanently() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchKeepPermanently(ids)
            clearSelection()
            _snackbarMessages.tryEmit("Kept ${ids.size} screenshots permanently")
        }
    }

    fun cancelScheduledDeletion(id: Long) {
        viewModelScope.launch {
            repository.cancelScheduledDeletion(id)
            _snackbarMessages.tryEmit("Scheduled deletion cancelled")
        }
    }

    fun batchCancelScheduledDeletion() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchCancelScheduledDeletion(ids)
            clearSelection()
            _snackbarMessages.tryEmit("Cancelled timers for ${ids.size} screenshots")
        }
    }

    fun requestDeleteWithOptionalConfirm(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val prefs = repository.getPreferencesSnapshot()
            if (prefs.confirmBeforeImmediateDelete) {
                _confirmDeleteIds.value = ids
            } else {
                executeDeleteNow(ids)
            }
        }
    }

    fun dismissDeleteConfirmDialog() {
        _confirmDeleteIds.value = null
    }

    fun confirmAndExecuteDelete() {
        val ids = _confirmDeleteIds.value ?: return
        _confirmDeleteIds.value = null
        executeDeleteNow(ids)
    }

    fun executePendingExpiredDeletions() {
        val pendingIds = uiState.value.pendingConfirmationItems.map { it.id }
        if (pendingIds.isEmpty()) return
        executeDeleteNow(pendingIds)
    }

    fun executeDeleteNow(ids: List<Long>) {
        viewModelScope.launch {
            when (val outcome = repository.requestDeleteNow(ids)) {
                is DeleteRequestOutcome.DeletedVerified -> {
                    _activeSheetScreenshotId.value = null
                    clearSelection()
                    if (outcome.count > 0) {
                        _snackbarMessages.tryEmit(
                            "Verified deleted ${outcome.count} screenshot${if (outcome.count == 1) "" else "s"}"
                        )
                    }
                }
                is DeleteRequestOutcome.NeedsSystemConfirmation -> {
                    _systemDeleteRequests.tryEmit(
                        PendingSystemDeleteRequest(
                            intentSender = outcome.intentSender,
                            targetIds = outcome.pendingIds
                        )
                    )
                }
                is DeleteRequestOutcome.Error -> {
                    _snackbarMessages.tryEmit(outcome.message)
                }
            }
        }
    }

    fun onSystemDeleteConfirmationResult(ids: List<Long>, approved: Boolean) {
        viewModelScope.launch {
            val verified = repository.verifyAfterSystemDeleteConfirmation(ids)
            _activeSheetScreenshotId.value = null
            clearSelection()
            if (approved && verified > 0) {
                _snackbarMessages.tryEmit(
                    "Deleted $verified screenshot${if (verified == 1) "" else "s"}"
                )
            } else if (!approved) {
                _snackbarMessages.tryEmit("Deletion cancelled — files were kept")
            } else {
                _snackbarMessages.tryEmit("Could not verify file deletion")
            }
        }
    }

    fun setAutoDetectEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoDetectEnabled(enabled)
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !repository.scanner.canDrawOverlays()) {
                _snackbarMessages.tryEmit("Allow 'Display over other apps' to enable overlay")
                return@launch
            }
            repository.setOverlayEnabled(enabled)
            _permissionRefreshTrigger.update { it + 1 }
            if (enabled) {
                _snackbarMessages.tryEmit("Cross-app screenshot overlay enabled")
            }
        }
    }

    fun onOverlayPermissionResult(enableIfGranted: Boolean) {
        _permissionRefreshTrigger.update { it + 1 }
        viewModelScope.launch {
            val granted = repository.scanner.canDrawOverlays()
            if (granted && enableIfGranted) {
                repository.setOverlayEnabled(true)
                _snackbarMessages.tryEmit("Cross-app screenshot overlay enabled")
            } else if (!granted) {
                repository.setOverlayEnabled(false)
                _snackbarMessages.tryEmit("Overlay permission not granted")
            } else {
                repository.syncMonitorService()
            }
        }
    }

    fun setDefaultPresetId(presetId: String?) {
        viewModelScope.launch {
            repository.setDefaultPresetId(presetId)
        }
    }

    fun setNotifyBeforeExpireMinutes(minutes: Int) {
        viewModelScope.launch {
            repository.setNotifyBeforeExpireMinutes(minutes)
        }
    }

    fun setConfirmBeforeImmediateDelete(confirm: Boolean) {
        viewModelScope.launch {
            repository.setConfirmBeforeImmediateDelete(confirm)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun clearExpiredHistory() {
        viewModelScope.launch {
            repository.clearExpiredHistory()
            _snackbarMessages.tryEmit("Cleared deleted screenshot history")
        }
    }

    private data class InternalUiSlice(
        val filter: ScreenshotFilter,
        val selectedIds: Set<Long>,
        val isSelectionMode: Boolean,
        val activeSheetId: Long?,
        val showBatchTimerSheet: Boolean
    )

    private data class AuxUiSlice(
        val confirmDeleteIds: List<Long>?,
        val isScanning: Boolean,
        val permissionTick: Int,
        val nowMillis: Long
    )

    companion object {
        const val EXPIRING_SOON_WINDOW_MS = 60L * 60_000L
    }
}
