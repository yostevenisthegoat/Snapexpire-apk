package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DeleteRequestOutcome
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotRepository
import com.example.data.UserPreferences
import com.example.ui.components.ScreenshotExpirationBottomSheet
import com.example.ui.theme.SnapFadeTheme
import com.example.worker.SnapFadeNotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickExpireSheetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val screenshotId = intent.getLongExtra(SnapFadeNotificationHelper.EXTRA_SCREENSHOT_ID, -1L)
        val notifId = intent.getIntExtra(SnapFadeNotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notifId != -1) {
            SnapFadeNotificationHelper.cancelNotification(this, notifId)
        }
        if (screenshotId == -1L) {
            finish()
            return
        }

        val repository = ScreenshotRepository.getInstance(this)

        setContent {
            val prefs by repository.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            val screenshot by repository.observeScreenshot(screenshotId)
                .collectAsStateWithLifecycle(initialValue = null)
            var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var pendingDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }
            val scope = rememberCoroutineScope()

            val systemDeleteLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                scope.launch {
                    val approved = result.resultCode == Activity.RESULT_OK
                    if (approved) {
                        repository.verifyAfterSystemDeleteConfirmation(pendingDeleteIds)
                    }
                    pendingDeleteIds = emptyList()
                    finish()
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(1_000L)
                    nowMillis = System.currentTimeMillis()
                }
            }

            SnapFadeTheme(themeMode = prefs.themeMode, adjustSystemBars = false) {
                val item: ManagedScreenshotEntity? = screenshot
                if (item != null) {
                    ScreenshotExpirationBottomSheet(
                        screenshot = item,
                        nowMillis = nowMillis,
                        onSelectPreset = { preset ->
                            scope.launch {
                                repository.schedulePreset(item.id, preset)
                                finish()
                            }
                        },
                        onSelectCustomTimestamp = { millis ->
                            scope.launch {
                                if (millis > System.currentTimeMillis() + 5_000L) {
                                    repository.scheduleCustom(item.id, millis, "Custom")
                                    finish()
                                }
                            }
                        },
                        onKeepPermanently = {
                            scope.launch {
                                repository.keepPermanently(item.id)
                                finish()
                            }
                        },
                        onCancelTimer = {
                            scope.launch {
                                repository.cancelScheduledDeletion(item.id)
                                finish()
                            }
                        },
                        onDeleteNow = {
                            scope.launch {
                                when (val outcome = repository.requestDeleteNow(listOf(item.id))) {
                                    is DeleteRequestOutcome.DeletedVerified -> finish()
                                    is DeleteRequestOutcome.NeedsSystemConfirmation -> {
                                        pendingDeleteIds = outcome.pendingIds
                                        systemDeleteLauncher.launch(
                                            IntentSenderRequest.Builder(outcome.intentSender).build()
                                        )
                                    }
                                    is DeleteRequestOutcome.Error -> finish()
                                }
                            }
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}
