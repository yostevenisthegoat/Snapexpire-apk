package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ScreenshotFilter
import com.example.ui.SnapFadeViewModel
import com.example.ui.components.BatchExpirationBottomSheet
import com.example.ui.components.ScreenshotExpirationBottomSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ScreenshotsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SnapFadeTheme
import com.example.worker.SnapFadeNotificationHelper

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_SCREENSHOTS = "screenshots"
const val ROUTE_SETTINGS = "settings"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: SnapFadeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleLaunchIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            SnapFadeTheme(themeMode = uiState.preferences.themeMode) {
                SnapFadeAppScaffold(
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionsAndReconcile()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        if (launchIntent == null) return
        val screenshotId = launchIntent.getLongExtra(SnapFadeNotificationHelper.EXTRA_SCREENSHOT_ID, -1L)
        if (screenshotId != -1L) {
            viewModel.openScreenshotSheet(screenshotId)
        }
        if (launchIntent.getBooleanExtra("extra_open_pending_confirm", false)) {
            viewModel.setFilter(ScreenshotFilter.PENDING_CONFIRM)
        }
    }
}

@Composable
private fun SnapFadeAppScaffold(viewModel: SnapFadeViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteTargetIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissionsAndReconcile()
        if (granted) {
            viewModel.scanDeviceScreenshots()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissionsAndReconcile()
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 15)
    ) { uris ->
        viewModel.importFromPhotoPicker(uris)
    }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        viewModel.onSystemDeleteConfirmationResult(pendingDeleteTargetIds, approved)
        pendingDeleteTargetIds = emptyList()
    }

    val manageMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndReconcile()
    }

    var enableOverlayAfterGrant by remember { mutableStateOf(false) }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onOverlayPermissionResult(enableOverlayAfterGrant)
        enableOverlayAfterGrant = false
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.systemDeleteRequests.collect { request ->
            pendingDeleteTargetIds = request.targetIds
            val senderRequest = IntentSenderRequest.Builder(request.intentSender).build()
            systemDeleteLauncher.launch(senderRequest)
        }
    }

    val launchMediaPermission: () -> Unit = {
        mediaPermissionLauncher.launch(mediaPermission)
    }

    val launchNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launchPhotoPicker: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val launchManageMediaSettings: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            manageMediaLauncher.launch(intent)
        }
    }

    val launchOverlayPermissionSettings: (Boolean) -> Unit = { autoEnable ->
        enableOverlayAfterGrant = autoEnable
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        overlayPermissionLauncher.launch(intent)
    }

    val navItems = remember {
        listOf(
            NavDestinationItem(
                route = ROUTE_DASHBOARD,
                labelRes = R.string.nav_dashboard,
                selectedIcon = Icons.Filled.Dashboard,
                unselectedIcon = Icons.Outlined.Dashboard,
                testTag = "nav_dashboard"
            ),
            NavDestinationItem(
                route = ROUTE_SCREENSHOTS,
                labelRes = R.string.nav_screenshots,
                selectedIcon = Icons.Filled.PhotoLibrary,
                unselectedIcon = Icons.Outlined.PhotoLibrary,
                testTag = "nav_screenshots"
            ),
            NavDestinationItem(
                route = ROUTE_SETTINGS,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Filled.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                uiState = uiState,
                                onScanDevice = { viewModel.scanDeviceScreenshots() },
                                onPickPhotos = launchPhotoPicker,
                                onRequestMediaPermission = launchMediaPermission,
                                onRequestNotificationPermission = launchNotificationPermission,
                                onConfirmPendingDeletes = { viewModel.executePendingExpiredDeletions() },
                                onScreenshotClick = { viewModel.openScreenshotSheet(it.id) },
                                onQuickPreset = { id, preset -> viewModel.schedulePreset(id, preset) },
                                onQuickKeep = { id -> viewModel.keepPermanently(id) },
                                onDeleteScreenshot = { id -> viewModel.requestDeleteWithOptionalConfirm(listOf(id)) },
                                onViewAllScreenshots = { navigateToTab(ROUTE_SCREENSHOTS) }
                            )
                        }

                        composable(ROUTE_SCREENSHOTS) {
                            ScreenshotsScreen(
                                uiState = uiState,
                                onFilterSelected = viewModel::setFilter,
                                onToggleSelectionMode = viewModel::toggleSelectionMode,
                                onToggleItemSelected = viewModel::toggleSelected,
                                onSelectAll = viewModel::selectAllFiltered,
                                onClearSelection = viewModel::clearSelection,
                                onOpenBatchTimer = viewModel::openBatchTimerSheet,
                                onBatchKeep = viewModel::batchKeepPermanently,
                                onBatchCancelTimers = viewModel::batchCancelScheduledDeletion,
                                onBatchDelete = {
                                    viewModel.requestDeleteWithOptionalConfirm(uiState.selectedIds.toList())
                                },
                                onScreenshotClick = { viewModel.openScreenshotSheet(it.id) },
                                onQuickPreset = { id, preset -> viewModel.schedulePreset(id, preset) },
                                onQuickKeep = { id -> viewModel.keepPermanently(id) },
                                onDeleteScreenshot = { id -> viewModel.requestDeleteWithOptionalConfirm(listOf(id)) },
                                onScanDevice = { viewModel.scanDeviceScreenshots() },
                                onPickPhotos = launchPhotoPicker,
                                onRequestMediaPermission = launchMediaPermission
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                uiState = uiState,
                                onToggleAutoDetect = viewModel::setAutoDetectEnabled,
                                onToggleOverlay = viewModel::setOverlayEnabled,
                                onSelectDefaultPreset = viewModel::setDefaultPresetId,
                                onSelectWarningLeadMinutes = viewModel::setNotifyBeforeExpireMinutes,
                                onToggleConfirmBeforeDelete = viewModel::setConfirmBeforeImmediateDelete,
                                onSelectThemeMode = viewModel::setThemeMode,
                                onRequestMediaPermission = launchMediaPermission,
                                onRequestNotificationPermission = launchNotificationPermission,
                                onOpenOverlaySettings = { launchOverlayPermissionSettings(true) },
                                onOpenManageMediaSettings = launchManageMediaSettings,
                                onScanDeviceNow = { viewModel.scanDeviceScreenshots() },
                                onClearExpiredHistory = viewModel::clearExpiredHistory
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Screenshot Expiration Bottom Sheet
    val sheetScreenshot = uiState.activeSheetScreenshot
    if (sheetScreenshot != null) {
        ScreenshotExpirationBottomSheet(
            screenshot = sheetScreenshot,
            nowMillis = uiState.nowMillis,
            onSelectPreset = { preset -> viewModel.schedulePreset(sheetScreenshot.id, preset) },
            onSelectCustomTimestamp = { millis -> viewModel.scheduleCustom(sheetScreenshot.id, millis) },
            onKeepPermanently = { viewModel.keepPermanently(sheetScreenshot.id) },
            onCancelTimer = { viewModel.cancelScheduledDeletion(sheetScreenshot.id) },
            onDeleteNow = { viewModel.requestDeleteWithOptionalConfirm(listOf(sheetScreenshot.id)) },
            onDismiss = viewModel::dismissScreenshotSheet
        )
    }

    // Batch Expiration Bottom Sheet
    if (uiState.showBatchTimerSheet) {
        BatchExpirationBottomSheet(
            selectedCount = uiState.selectedIds.size,
            onSelectPreset = viewModel::batchSchedulePreset,
            onSelectCustomTimestamp = viewModel::batchScheduleCustom,
            onDismiss = viewModel::dismissBatchTimerSheet
        )
    }

    // Optional Immediate Delete Confirmation Dialog
    val confirmIds = uiState.confirmDeleteIds
    if (confirmIds != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmDialog,
            title = {
                Text(
                    if (confirmIds.size == 1) "Delete screenshot permanently?"
                    else "Delete ${confirmIds.size} screenshots permanently?"
                )
            },
            text = {
                Text("This removes the selected file${if (confirmIds.size == 1) "" else "s"} from your device immediately and cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAndExecuteDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_dialog_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}
