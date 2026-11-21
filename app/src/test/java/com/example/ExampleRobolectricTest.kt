package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotStatus
import com.example.data.SnapFadeDatabase
import com.example.ui.DashboardMetrics
import com.example.ui.SnapFadeUiState
import com.example.ui.components.TimeFormatters
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.SnapFadeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read SnapFade app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SnapFade", appName)
    }

    @Test
    fun `countdown formatter formats minutes, hours, and days cleanly`() {
        val now = 1_000_000L
        assertEquals("05m 00s", TimeFormatters.formatCountdown(now + 300_000L, now))
        assertEquals("02h 30m", TimeFormatters.formatCountdown(now + 9_000_000L, now))
        assertEquals("3d 0h", TimeFormatters.formatCountdown(now + 3L * 86_400_000L, now))
        assertEquals("Expired", TimeFormatters.formatCountdown(now - 1000L, now))
    }

    @Test
    fun `room dao inserts updates expiration and verifies deleted status`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SnapFadeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.screenshotDao()
            val sample = ManagedScreenshotEntity(
                id = 42L,
                contentUri = "content://media/external/images/media/42",
                fileName = "Screenshot_20261121.png",
                relativePath = "Pictures/Screenshots",
                sizeBytes = 512_000L,
                width = 1080,
                height = 2400,
                capturedAtMillis = 1_700_000_000_000L,
                status = ScreenshotStatus.NEW_DETECTED
            )
            dao.upsert(sample)

            val expiry = 1_700_003_600_000L
            dao.updateExpiration(
                id = 42L,
                expiresAtMillis = expiry,
                presetLabel = ExpirationPreset.ONE_HOUR.label,
                status = ScreenshotStatus.SCHEDULED,
                warningNotified = false,
                updatedAt = 1_700_000_001_000L
            )

            val scheduled = dao.getById(42L)!!
            assertEquals(ScreenshotStatus.SCHEDULED, scheduled.status)
            assertEquals(expiry, scheduled.expiresAtMillis)

            // Verify cancellation clears expiration without deleting
            dao.updateExpiration(
                id = 42L,
                expiresAtMillis = null,
                presetLabel = null,
                status = ScreenshotStatus.NEW_DETECTED,
                warningNotified = false,
                updatedAt = 1_700_000_002_000L
            )
            val cancelled = dao.getById(42L)!!
            assertNull(cancelled.expiresAtMillis)
            assertEquals(ScreenshotStatus.NEW_DETECTED, cancelled.status)

            // Verify marking expired
            dao.markExpiredVerified(42L, 1_700_003_605_000L)
            val all = dao.observeAll().first()
            assertEquals(ScreenshotStatus.EXPIRED, all.first().status)
        } finally {
            db.close()
        }
    }

    @Test
    fun `dashboard renders metrics and screenshot countdown card`() {
        val now = 1_700_000_000_000L
        val item = ManagedScreenshotEntity(
            id = 99L,
            contentUri = "content://media/external/images/media/99",
            fileName = "Screenshot_Receipt.png",
            relativePath = "Pictures/Screenshots",
            sizeBytes = 204_800L,
            width = 1080,
            height = 2400,
            capturedAtMillis = now - 30_000L,
            expiresAtMillis = now + 300_000L,
            presetLabel = "5 Minutes",
            status = ScreenshotStatus.SCHEDULED
        )
        val state = SnapFadeUiState(
            allScreenshots = listOf(item),
            metrics = DashboardMetrics(totalManaged = 1, scheduledCount = 1, expiringSoonCount = 1),
            expiringSoon = listOf(item),
            recentlyCaptured = listOf(item),
            hasMediaPermission = true,
            hasNotificationPermission = true,
            nowMillis = now
        )

        composeTestRule.setContent {
            SnapFadeTheme {
                DashboardScreen(
                    uiState = state,
                    onScanDevice = {},
                    onPickPhotos = {},
                    onRequestMediaPermission = {},
                    onRequestNotificationPermission = {},
                    onConfirmPendingDeletes = {},
                    onScreenshotClick = {},
                    onQuickPreset = { _, _ -> },
                    onQuickKeep = {},
                    onDeleteScreenshot = {},
                    onViewAllScreenshots = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SnapFade").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your screenshots. Your time.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("expiring_soon_card_99"))
        composeTestRule.onNodeWithTag("expiring_soon_card_99").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_lazy_column")
            .performScrollToNode(hasTestTag("screenshot_item_99"))
        composeTestRule.onNodeWithTag("screenshot_item_99").assertIsDisplayed()
    }
}
