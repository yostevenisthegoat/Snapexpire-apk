package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.ui.SnapFadeUiState
import com.example.ui.components.EmptyScreenshotsState
import com.example.ui.components.ExpiringSoonCard
import com.example.ui.components.ScreenshotListCard
import com.example.ui.components.TimeFormatters

@Composable
fun DashboardScreen(
    uiState: SnapFadeUiState,
    onScanDevice: () -> Unit,
    onPickPhotos: () -> Unit,
    onRequestMediaPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onConfirmPendingDeletes: () -> Unit,
    onScreenshotClick: (ManagedScreenshotEntity) -> Unit,
    onQuickPreset: (Long, ExpirationPreset) -> Unit,
    onQuickKeep: (Long) -> Unit,
    onDeleteScreenshot: (Long) -> Unit,
    onViewAllScreenshots: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_lazy_column"),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Brand Hero Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onPickPhotos,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_pick_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "Add screenshot from gallery"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onScanDevice,
                        enabled = !uiState.isScanning,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_scan_button")
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Scan MediaStore for screenshots"
                            )
                        }
                    }
                }
            }
        }

        // Auto-detect status pill + notification quick banner
        item {
            val statusActive = uiState.preferences.autoDetectEnabled && uiState.hasMediaPermission
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (statusActive) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.tertiary
                                )
                        )
                        Column {
                            val overlayReady = statusActive && uiState.preferences.overlayEnabled && uiState.canDrawOverlays
                            Text(
                                text = when {
                                    !uiState.hasMediaPermission -> "Media permission required"
                                    overlayReady -> "Overlay detection active"
                                    uiState.preferences.autoDetectEnabled -> "Auto-detection active"
                                    else -> "Auto-detection paused"
                                },
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = when {
                                    !uiState.hasMediaPermission -> "Allow photo access to detect screenshots automatically"
                                    overlayReady -> "Floating popup enabled for screenshots in other apps"
                                    uiState.preferences.autoDetectEnabled -> "Watching MediaStore Screenshots folder"
                                    else -> "Enable automatic detection in Settings"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!uiState.hasMediaPermission) {
                        Button(
                            onClick = onRequestMediaPermission,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("grant_media_permission_button")
                        ) {
                            Text("Grant")
                        }
                    } else if (!uiState.hasNotificationPermission) {
                        Button(
                            onClick = onRequestNotificationPermission,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("grant_notif_permission_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Alerts")
                        }
                    }
                }
            }
        }

        // Pending System Delete Confirmation Banner (Scoped Storage honesty)
        if (uiState.metrics.pendingConfirmCount > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "${uiState.metrics.pendingConfirmCount} expired screenshot${
                                    if (uiState.metrics.pendingConfirmCount == 1) "" else "s"
                                } awaiting confirmation",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            text = "Android scoped storage requires a quick confirmation to permanently remove files you didn't grant background media management for.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = onConfirmPendingDeletes,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("confirm_pending_deletes_button")
                        ) {
                            Text("Confirm & Delete Expired Files")
                        }
                    }
                }
            }
        }

        // 2x2 Bento Stats Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBentoCard(
                        title = "Managed",
                        value = uiState.metrics.totalManaged.toString(),
                        subtitle = "${uiState.metrics.keptCount} kept safe",
                        icon = Icons.Outlined.PhotoLibrary,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBentoCard(
                        title = "Scheduled",
                        value = uiState.metrics.scheduledCount.toString(),
                        subtitle = "Active timers",
                        icon = Icons.Filled.Schedule,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricBentoCard(
                        title = "Expiring Soon",
                        value = uiState.metrics.expiringSoonCount.toString(),
                        subtitle = "Within 60 min",
                        icon = Icons.Outlined.HourglassBottom,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBentoCard(
                        title = "Cleared",
                        value = uiState.metrics.clearedCount.toString(),
                        subtitle = if (uiState.metrics.freedBytes > 0L) {
                            "${TimeFormatters.formatFileSize(uiState.metrics.freedBytes)} freed"
                        } else {
                            "Auto-expired"
                        },
                        icon = Icons.Filled.AutoDelete,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Expiring Soon Carousel
        if (uiState.expiringSoon.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Expiring Soon",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        TextButton(onClick = onViewAllScreenshots) {
                            Text("Manage all")
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.expiringSoon, key = { "soon_${it.id}" }) { item ->
                            ExpiringSoonCard(
                                screenshot = item,
                                nowMillis = uiState.nowMillis,
                                onClick = { onScreenshotClick(item) }
                            )
                        }
                    }
                }
            }
        }

        // Recently Captured Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recently Captured",
                    style = MaterialTheme.typography.titleLarge
                )
                if (uiState.recentlyCaptured.isNotEmpty()) {
                    TextButton(
                        onClick = onViewAllScreenshots,
                        modifier = Modifier.testTag("view_all_screenshots_button")
                    ) {
                        Text("See all")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        if (uiState.recentlyCaptured.isEmpty()) {
            item {
                EmptyScreenshotsState(
                    hasMediaPermission = uiState.hasMediaPermission,
                    onScanDevice = onScanDevice,
                    onPickPhotos = onPickPhotos,
                    onRequestPermission = onRequestMediaPermission
                )
            }
        } else {
            items(uiState.recentlyCaptured, key = { it.id }) { screenshot ->
                ScreenshotListCard(
                    screenshot = screenshot,
                    nowMillis = uiState.nowMillis,
                    isSelectionMode = false,
                    isSelected = false,
                    onClick = { onScreenshotClick(screenshot) },
                    onLongClick = { onScreenshotClick(screenshot) },
                    onQuickPreset = { preset -> onQuickPreset(screenshot.id, preset) },
                    onQuickKeep = { onQuickKeep(screenshot.id) },
                    onDeleteClick = { onDeleteScreenshot(screenshot.id) }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MetricBentoCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(6.dp).size(18.dp)
                        )
                    }
                }

                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
