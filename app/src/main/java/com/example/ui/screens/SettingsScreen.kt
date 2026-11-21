package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ExpirationPreset
import com.example.data.ThemeMode
import com.example.ui.SnapFadeUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SnapFadeUiState,
    onToggleAutoDetect: (Boolean) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectDefaultPreset: (String?) -> Unit,
    onSelectWarningLeadMinutes: (Int) -> Unit,
    onToggleConfirmBeforeDelete: (Boolean) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onRequestMediaPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenManageMediaSettings: () -> Unit,
    onScanDeviceNow: () -> Unit,
    onClearExpiredHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
                Text(
                    text = "Control automatic detection, timers, and storage permissions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Detection & Automation Card
        item {
            SettingsSectionCard(title = "Screenshot Detection", icon = Icons.Filled.Radar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Automatic Screenshot Detection",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Watch MediaStore for newly captured screenshots and show expiration options",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.autoDetectEnabled,
                        onCheckedChange = onToggleAutoDetect,
                        modifier = Modifier.testTag("auto_detect_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Overlay Over Other Apps",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (uiState.canDrawOverlays) {
                                "Show a floating expiration card over any app when you take a screenshot"
                            } else {
                                "Requires 'Display over other apps' access to pop up while using other apps"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.overlayEnabled && uiState.canDrawOverlays,
                        onCheckedChange = { checked ->
                            if (checked && !uiState.canDrawOverlays) {
                                onOpenOverlaySettings()
                            } else {
                                onToggleOverlay(checked)
                            }
                        },
                        enabled = uiState.preferences.autoDetectEnabled,
                        modifier = Modifier.testTag("overlay_over_apps_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Default Behavior on Capture",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = uiState.preferences.defaultPresetId == null,
                        onClick = { onSelectDefaultPreset(null) },
                        label = { Text("Ask Every Time") }
                    )
                    ExpirationPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = uiState.preferences.defaultPresetId == preset.id,
                            onClick = { onSelectDefaultPreset(preset.id) },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }
        }

        // Notifications & Safety Card
        item {
            SettingsSectionCard(title = "Alerts & Safety", icon = Icons.Filled.Notifications) {
                Text(
                    text = "Pre-Expiration Reminder",
                    style = MaterialTheme.typography.titleSmall
                )
                val leadOptions = listOf(0 to "Off", 5 to "5 min", 15 to "15 min", 60 to "1 hour")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    leadOptions.forEach { (minutes, label) ->
                        FilterChip(
                            selected = uiState.preferences.notifyBeforeExpireMinutes == minutes,
                            onClick = { onSelectWarningLeadMinutes(minutes) },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Confirm Before Immediate Delete",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ask for confirmation in-app before permanently deleting right now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.confirmBeforeImmediateDelete,
                        onCheckedChange = onToggleConfirmBeforeDelete,
                        modifier = Modifier.testTag("confirm_delete_switch")
                    )
                }
            }
        }

        // Scoped Storage & Permissions Card
        item {
            SettingsSectionCard(title = "Permissions & Scoped Storage", icon = Icons.Filled.Security) {
                PermissionRow(
                    title = "MediaStore Screenshot Access",
                    subtitle = if (uiState.hasMediaPermission) {
                        "Granted — able to detect new screenshots"
                    } else {
                        "Required for automatic screenshot detection"
                    },
                    granted = uiState.hasMediaPermission,
                    buttonLabel = "Grant",
                    onClick = onRequestMediaPermission,
                    buttonTag = "settings_media_permission_button"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PermissionRow(
                    title = "Notifications",
                    subtitle = if (uiState.hasNotificationPermission) {
                        "Granted — capture popups and expiry alerts enabled"
                    } else {
                        "Allow notifications for instant expiration prompts"
                    },
                    granted = uiState.hasNotificationPermission,
                    buttonLabel = "Allow",
                    onClick = onRequestNotificationPermission,
                    buttonTag = "settings_notif_permission_button"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PermissionRow(
                    title = "Display Over Other Apps",
                    subtitle = if (uiState.canDrawOverlays) {
                        "Granted — floating overlay popup can appear over other apps"
                    } else {
                        "Optional — lets SnapFade detect screenshots in real time and show a floating popup over other apps"
                    },
                    granted = uiState.canDrawOverlays,
                    buttonLabel = if (uiState.canDrawOverlays) "Manage" else "Allow",
                    onClick = onOpenOverlaySettings,
                    buttonTag = "settings_overlay_permission_button",
                    alwaysShowButton = true
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionRow(
                        title = "Prompt-Free Auto-Delete (Media Management)",
                        subtitle = if (uiState.canManageMedia) {
                            "Enabled — expired screenshots delete automatically in the background"
                        } else {
                            "Optional Android 12+ access. Without it, Android shows a system confirmation notification when timers finish."
                        },
                        granted = uiState.canManageMedia,
                        buttonLabel = if (uiState.canManageMedia) "Manage" else "Configure",
                        onClick = onOpenManageMediaSettings,
                        buttonTag = "settings_manage_media_button",
                        alwaysShowButton = true
                    )
                }
            }
        }

        // Appearance Card
        item {
            SettingsSectionCard(title = "Appearance", icon = Icons.Filled.DarkMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.preferences.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Maintenance Card
        item {
            SettingsSectionCard(title = "Storage Maintenance", icon = Icons.Filled.FolderOpen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onScanDeviceNow,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("settings_scan_now_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Scan Screenshots")
                    }
                    OutlinedButton(
                        onClick = onClearExpiredHistory,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("settings_clear_history_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clear History")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
    buttonTag: String,
    alwaysShowButton: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Text(text = title, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!granted || alwaysShowButton) {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(buttonTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}
