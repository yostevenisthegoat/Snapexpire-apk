package com.example.service

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.QuickExpireSheetActivity
import com.example.data.ExpirationPreset
import com.example.data.ManagedScreenshotEntity
import com.example.data.ScreenshotRepository
import com.example.data.ThemeMode
import com.example.ui.components.TimeFormatters
import com.example.worker.SnapFadeNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenshotOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentOverlayView: View? = null

    fun showOverlay(
        screenshot: ManagedScreenshotEntity,
        themeMode: ThemeMode = ThemeMode.SYSTEM
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            return false
        }

        mainHandler.post {
            hideOverlayInternal()

            val useDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> {
                    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                }
            }

            val card = buildOverlayView(screenshot, useDark)
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(24)
            }

            runCatching {
                windowManager.addView(card, params)
                currentOverlayView = card
            }.onFailure {
                currentOverlayView = null
                SnapFadeNotificationHelper.showNewScreenshotNotification(context, screenshot)
            }
        }
        return true
    }

    fun hideOverlay() {
        mainHandler.post { hideOverlayInternal() }
    }

    private fun hideOverlayInternal() {
        currentOverlayView?.let { existing ->
            runCatching { windowManager.removeViewImmediate(existing) }
            currentOverlayView = null
        }
    }

    private fun buildOverlayView(
        screenshot: ManagedScreenshotEntity,
        dark: Boolean
    ): View {
        val bgSurface = if (dark) Color.parseColor("#111827") else Color.parseColor("#FFFFFF")
        val bgChip = if (dark) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9")
        val bgBadge = if (dark) Color.parseColor("#0C4A6E") else Color.parseColor("#E0F2FE")
        val textBadge = if (dark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7")
        val textPrimary = if (dark) Color.parseColor("#F1F5F9") else Color.parseColor("#0F172A")
        val textSecondary = if (dark) Color.parseColor("#94A3B8") else Color.parseColor("#475569")
        val borderStroke = if (dark) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        val accentKeepBg = if (dark) Color.parseColor("#115E59") else Color.parseColor("#CCFBF1")
        val accentKeepText = if (dark) Color.parseColor("#2DD4BF") else Color.parseColor("#0D9488")

        val outerWrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(bgSurface)
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), borderStroke)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top bar: SnapFade badge + Dismiss button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val brandBadge = TextView(context).apply {
            text = "SnapFade • New Screenshot"
            setTextColor(textBadge)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(bgBadge)
                cornerRadius = dp(10).toFloat()
            }
        }

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val dismissBtn = TextView(context).apply {
            text = "Dismiss"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(6), dp(6), dp(6))
            setOnClickListener { hideOverlay() }
        }

        headerRow.addView(brandBadge)
        headerRow.addView(spacer)
        headerRow.addView(dismissBtn)
        card.addView(headerRow)

        // Content row: Thumbnail + Filename + Capture time
        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }

        val thumbView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(bgChip)
                cornerRadius = dp(12).toFloat()
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(72)).apply {
                marginEnd = dp(12)
            }
        }

        scope.launch {
            val bitmap = loadThumbnailBitmap(Uri.parse(screenshot.contentUri))
            if (bitmap != null) {
                withContext(Dispatchers.Main) {
                    thumbView.setImageBitmap(bitmap)
                }
            }
        }

        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameText = TextView(context).apply {
            text = screenshot.fileName
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val timeText = TextView(context).apply {
            text = "${TimeFormatters.formatCaptureTime(screenshot.capturedAtMillis)} • ${
                TimeFormatters.formatFileSize(screenshot.sizeBytes)
            }"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(2), 0, 0)
        }

        val subtitleText = TextView(context).apply {
            text = "Choose how long to keep this screenshot:"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, 0)
        }

        textColumn.addView(nameText)
        textColumn.addView(timeText)
        textColumn.addView(subtitleText)
        infoRow.addView(thumbView)
        infoRow.addView(textColumn)
        card.addView(infoRow)

        // Row 1 presets: 5m, 1h, 10h, 1d
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        listOf(
            ExpirationPreset.FIVE_MINUTES,
            ExpirationPreset.ONE_HOUR,
            ExpirationPreset.TEN_HOURS,
            ExpirationPreset.ONE_DAY
        ).forEachIndexed { index, preset ->
            row1.addView(
                buildChipButton(
                    label = preset.shortLabel,
                    bgColor = bgChip,
                    textColor = textPrimary,
                    strokeColor = borderStroke,
                    marginEndPx = if (index < 3) dp(8) else 0
                ) {
                    hideOverlay()
                    scope.launch {
                        ScreenshotRepository.getInstance(context).schedulePreset(screenshot.id, preset)
                    }
                }
            )
        }
        card.addView(row1)

        // Row 2: 3d, 1w, Keep, Custom
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row2.addView(
            buildChipButton(
                label = ExpirationPreset.THREE_DAYS.shortLabel,
                bgColor = bgChip,
                textColor = textPrimary,
                strokeColor = borderStroke,
                marginEndPx = dp(8)
            ) {
                hideOverlay()
                scope.launch {
                    ScreenshotRepository.getInstance(context)
                        .schedulePreset(screenshot.id, ExpirationPreset.THREE_DAYS)
                }
            }
        )
        row2.addView(
            buildChipButton(
                label = ExpirationPreset.ONE_WEEK.shortLabel,
                bgColor = bgChip,
                textColor = textPrimary,
                strokeColor = borderStroke,
                marginEndPx = dp(8)
            ) {
                hideOverlay()
                scope.launch {
                    ScreenshotRepository.getInstance(context)
                        .schedulePreset(screenshot.id, ExpirationPreset.ONE_WEEK)
                }
            }
        )
        row2.addView(
            buildChipButton(
                label = "Keep",
                bgColor = accentKeepBg,
                textColor = accentKeepText,
                strokeColor = accentKeepBg,
                marginEndPx = dp(8)
            ) {
                hideOverlay()
                scope.launch {
                    ScreenshotRepository.getInstance(context).keepPermanently(screenshot.id)
                }
            }
        )
        row2.addView(
            buildChipButton(
                label = "Custom",
                bgColor = bgBadge,
                textColor = textBadge,
                strokeColor = bgBadge,
                marginEndPx = 0
            ) {
                hideOverlay()
                val intent = Intent(context, QuickExpireSheetActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(SnapFadeNotificationHelper.EXTRA_SCREENSHOT_ID, screenshot.id)
                }
                context.startActivity(intent)
            }
        )
        card.addView(row2)

        outerWrapper.addView(card)
        return outerWrapper
    }

    private fun buildChipButton(
        label: String,
        bgColor: Int,
        textColor: Int,
        strokeColor: Int,
        marginEndPx: Int,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(8), dp(11), dp(8), dp(11))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), strokeColor)
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = marginEndPx
            }
            setOnClickListener { onClick() }
        }
    }

    private fun loadThumbnailBitmap(uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                    val sample = (maxDim / 320).coerceAtLeast(1)
                    decoder.setTargetSampleSize(sample)
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
