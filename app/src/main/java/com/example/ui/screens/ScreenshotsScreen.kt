package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CancelScheduleSend
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotFilter
import com.example.data.ScreenshotStatus
import com.example.ui.SnapFadeUiState
import com.example.ui.components.EmptyScreenshotsState
import com.example.ui.components.ScreenshotListCard

@Composable
fun ScreenshotsScreen(
    uiState: SnapFadeUiState,
    onFilterSelected: (ScreenshotFilter) -> Unit,
    onToggleSelectionMode: (Boolean) -> Unit,
    onToggleItemSelected: (Long) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onOpenBatchTimer: () -> Unit,
    onBatchKeep: () -> Unit,
    onBatchCancelTimers: () -> Unit,
    onBatchDelete: () -> Unit,
    onScreenshotClick: (ManagedScreenshotEntity) -> Unit,
    onQuickPreset: (Long, ExpirationPreset) -> Unit,
    onQuickKeep: (Long) -> Unit,
    onDeleteScreenshot: (Long) -> Unit,
    onScanDevice: () -> Unit,
    onPickPhotos: () -> Unit,
    onRequestMediaPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredList = remember(uiState.allScreenshots, uiState.activeFilter, uiState.nowMillis) {
        val soonCutoff = uiState.nowMillis + 3_600_000L
        when (uiState.activeFilter) {
            ScreenshotFilter.ALL -> uiState.allScreenshots.filter { it.status != ScreenshotStatus.EXPIRED }
            ScreenshotFilter.SCHEDULED -> uiState.allScreenshots.filter { it.status == ScreenshotStatus.SCHEDULED }
            ScreenshotFilter.EXPIRING_SOON -> uiState.allScreenshots.filter {
                it.status == ScreenshotStatus.SCHEDULED && (it.expiresAtMillis ?: Long.MAX_VALUE) <= soonCutoff
            }
            ScreenshotFilter.PENDING_CONFIRM -> uiState.allScreenshots.filter {
                it.status == ScreenshotStatus.PENDING_CONFIRMATION
            }
            ScreenshotFilter.UNASSIGNED -> uiState.allScreenshots.filter {
                it.status == ScreenshotStatus.NEW_DETECTED
            }
            ScreenshotFilter.KEPT -> uiState.allScreenshots.filter { it.status == ScreenshotStatus.KEPT }
            ScreenshotFilter.EXPIRED -> uiState.allScreenshots.filter { it.status == ScreenshotStatus.EXPIRED }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.isSelectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = onClearSelection,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Exit batch selection")
                        }
                        Text(
                            text = "${uiState.selectedIds.size} selected",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val selectableIds = filteredList
                                    .filter { it.status != ScreenshotStatus.EXPIRED }
                                    .map { it.id }
                                onSelectAll(selectableIds)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("select_all_button")
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                    }
                } else {
                    Column {
                        Text(
                            text = "Screenshots",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "${filteredList.size} item${if (filteredList.size == 1) "" else "s"} in view",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = onPickPhotos,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = "Pick screenshot"
                            )
                        }
                        FilledTonalIconButton(
                            onClick = { onToggleSelectionMode(true) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("batch_select_mode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Checklist,
                                contentDescription = "Batch manage screenshots"
                            )
                        }
                    }
                }
            }

            // Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ScreenshotFilter.entries, key = { it.name }) { filter ->
                    FilterChip(
                        selected = uiState.activeFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 4.dp,
                    bottom = if (uiState.isSelectionMode) 110.dp else 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filteredList.isEmpty()) {
                    item {
                        EmptyScreenshotsState(
                            hasMediaPermission = uiState.hasMediaPermission,
                            onScanDevice = onScanDevice,
                            onPickPhotos = onPickPhotos,
                            onRequestPermission = onRequestMediaPermission
                        )
                    }
                } else {
                    items(filteredList, key = { it.id }) { screenshot ->
                        val isSelectable = screenshot.status != ScreenshotStatus.EXPIRED
                        ScreenshotListCard(
                            screenshot = screenshot,
                            nowMillis = uiState.nowMillis,
                            isSelectionMode = uiState.isSelectionMode && isSelectable,
                            isSelected = screenshot.id in uiState.selectedIds,
                            onClick = {
                                if (uiState.isSelectionMode && isSelectable) {
                                    onToggleItemSelected(screenshot.id)
                                } else {
                                    onScreenshotClick(screenshot)
                                }
                            },
                            onLongClick = {
                                if (isSelectable) {
                                    onToggleItemSelected(screenshot.id)
                                }
                            },
                            onQuickPreset = { preset -> onQuickPreset(screenshot.id, preset) },
                            onQuickKeep = { onQuickKeep(screenshot.id) },
                            onDeleteClick = { onDeleteScreenshot(screenshot.id) }
                        )
                    }
                }
            }
        }

        // Floating Batch Action Bar
        AnimatedVisibility(
            visible = uiState.isSelectionMode && uiState.selectedIds.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BatchBarAction(
                        icon = Icons.Filled.Schedule,
                        label = "Timer",
                        onClick = onOpenBatchTimer,
                        modifier = Modifier.testTag("batch_timer_button")
                    )
                    BatchBarAction(
                        icon = Icons.Filled.BookmarkAdded,
                        label = "Keep",
                        onClick = onBatchKeep,
                        modifier = Modifier.testTag("batch_keep_button")
                    )
                    BatchBarAction(
                        icon = Icons.Outlined.CancelScheduleSend,
                        label = "Cancel",
                        onClick = onBatchCancelTimers,
                        modifier = Modifier.testTag("batch_cancel_button")
                    )
                    BatchBarAction(
                        icon = Icons.Filled.DeleteForever,
                        label = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onBatchDelete,
                        modifier = Modifier.testTag("batch_delete_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}
