package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.snapFadeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "snapfade_preferences"
)

data class UserPreferences(
    val autoDetectEnabled: Boolean = true,
    val overlayEnabled: Boolean = false,
    val defaultPresetId: String? = null,
    val notifyBeforeExpireMinutes: Int = 15,
    val confirmBeforeImmediateDelete: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastMediaScanMillis: Long = 0L
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val AUTO_DETECT_ENABLED = booleanPreferencesKey("auto_detect_enabled")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val DEFAULT_PRESET_ID = stringPreferencesKey("default_preset_id")
        val NOTIFY_BEFORE_MINUTES = intPreferencesKey("notify_before_minutes")
        val CONFIRM_BEFORE_DELETE = booleanPreferencesKey("confirm_before_delete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_MEDIA_SCAN_MILLIS = longPreferencesKey("last_media_scan_millis")
    }

    val preferencesFlow: Flow<UserPreferences> = context.snapFadeDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        UserPreferences(
            autoDetectEnabled = prefs[Keys.AUTO_DETECT_ENABLED] ?: true,
            overlayEnabled = prefs[Keys.OVERLAY_ENABLED] ?: false,
            defaultPresetId = prefs[Keys.DEFAULT_PRESET_ID]?.takeIf { it.isNotBlank() },
            notifyBeforeExpireMinutes = prefs[Keys.NOTIFY_BEFORE_MINUTES] ?: 15,
            confirmBeforeImmediateDelete = prefs[Keys.CONFIRM_BEFORE_DELETE] ?: true,
            themeMode = themeMode,
            lastMediaScanMillis = prefs[Keys.LAST_MEDIA_SCAN_MILLIS] ?: 0L
        )
    }

    suspend fun getSnapshot(): UserPreferences = preferencesFlow.first()

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.AUTO_DETECT_ENABLED] = enabled
        }
    }

    suspend fun setOverlayEnabled(enabled: Boolean) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ENABLED] = enabled
        }
    }

    suspend fun setDefaultPresetId(presetId: String?) {
        context.snapFadeDataStore.edit { prefs ->
            if (presetId.isNullOrBlank()) {
                prefs.remove(Keys.DEFAULT_PRESET_ID)
            } else {
                prefs[Keys.DEFAULT_PRESET_ID] = presetId
            }
        }
    }

    suspend fun setNotifyBeforeExpireMinutes(minutes: Int) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.NOTIFY_BEFORE_MINUTES] = minutes
        }
    }

    suspend fun setConfirmBeforeImmediateDelete(confirm: Boolean) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.CONFIRM_BEFORE_DELETE] = confirm
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun updateLastMediaScanMillis(millis: Long) {
        context.snapFadeDataStore.edit { prefs ->
            prefs[Keys.LAST_MEDIA_SCAN_MILLIS] = millis
        }
    }
}
