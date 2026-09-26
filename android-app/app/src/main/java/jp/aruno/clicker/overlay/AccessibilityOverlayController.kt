package jp.aruno.clicker.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import jp.aruno.clicker.automation.AutomationConfigStore
import jp.aruno.clicker.automation.AutomationRuntime
import java.util.Locale

/**
 * Redmi A3 share-test controller owned by [AccessibilityService].
 *
 * TYPE_ACCESSIBILITY_OVERLAY must be added and removed by a connected accessibility service;
 * this controller is never instantiated by atlas or lumen builds.
 */
class AccessibilityOverlayController(
    private val service: AccessibilityService,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val onClose: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val configStore = AutomationConfigStore(service)
    private val handler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var statusTitleText: TextView? = null
    private var statusDetailText: TextView? = null
    private var timeText: TextView? = null
    private var countText: TextView? = null
    private var startButton: Button? = null
    private var sizeButton: Button? = null
    private var compactOverlay = true

    private val ticker = object : Runnable {
        override fun run() {
            if (overlayView == null) return
            refresh()
            handler.postDelayed(this, 1_000L)
        }
    }

    fun show(): Boolean {
        if (overlayView != null) {
            clampToBounds()
            return true
        }

        val config = configStore.load()
        compactOverlay = config.overlayCompact
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            keepScreenOn = config.keepScreenAwake
            setPadding(
                dp(if (compactOverlay) 8 else 16),
                dp(if (compactOverlay) 7 else 13),
                dp(if (compactOverlay) 8 else 16),
                dp(if (compactOverlay) 8 else 14),
            )
            background = roundedBackground(Color.argb(232, 5, 18, 12), Color.rgb(45, 255, 115))
            elevation = dp(8).toFloat()
        }
        val titleRow = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(service).apply {
            text = if (compactOverlay) "A  ARUNO" else "A  ARUNO CLICKER"
            setTextColor(Color.rgb(87, 255, 137))
            textSize = if (compactOverlay) 12f else 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(3), 0, dp(3), dp(if (compactOverlay) 4 else 8))
        }
        val sizeToggle = compactButton(if (compactOverlay) "大" else "小", Color.rgb(19, 92, 48)).apply {
            textSize = if (compactOverlay) 12f else 14f
            setOnClickListener { toggleSize() }
        }
        sizeButton = sizeToggle
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(
            sizeToggle,
            LinearLayout.LayoutParams(
                dp(if (compactOverlay) 38 else 44),
                dp(if (compactOverlay) 30 else 36),
            ),
        )

        val statusPanel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(if (compactOverlay) 7 else 12),
                dp(if (compactOverlay) 5 else 9),
                dp(if (compactOverlay) 7 else 12),
                dp(if (compactOverlay) 5 else 9),
            )
            background = roundedBackground(Color.argb(220, 9, 43, 24), Color.rgb(55, 220, 110))
        }
        statusTitleText = label(if (compactOverlay) 14f else 23f, Color.WHITE).apply {
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
        }
        statusDetailText = label(if (compactOverlay) 11f else 18f, Color.rgb(144, 255, 177)).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(2), dp(2), dp(2), dp(1))
            maxLines = 2
        }
        statusPanel.addView(statusTitleText)
        statusPanel.addView(statusDetailText)

        timeText = label(if (compactOverlay) 10f else 14f, Color.WHITE).apply {
            setPadding(dp(3), dp(if (compactOverlay) 4 else 8), dp(3), dp(1))
        }
        countText = label(if (compactOverlay) 11f else 15f, Color.rgb(144, 255, 177)).apply {
            setTypeface(typeface, Typeface.BOLD)
        }

        val buttons = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val actionButton = compactButton("スタート", Color.rgb(15, 116, 54)).apply {
            setOnClickListener {
                if (AutomationRuntime.snapshot().requested) onStop() else onStart()
                refresh()
            }
        }
        startButton = actionButton
        val closeButton = compactButton("閉じる", Color.rgb(83, 83, 83)).apply {
            // Stop and remove are separate operations: the Stop button keeps the controller,
            // while Close explicitly performs both so a later Start can recreate it.
            setOnClickListener {
                onStop()
                hide()
                onClose()
            }
        }
        val buttonHeight = dp(if (compactOverlay) 40 else 56)
        val buttonGap = dp(if (compactOverlay) 3 else 6)
        actionButton.textSize = if (compactOverlay) 13f else 17f
        closeButton.textSize = if (compactOverlay) 13f else 17f
        buttons.addView(actionButton, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply { marginEnd = buttonGap })
        buttons.addView(closeButton, LinearLayout.LayoutParams(0, buttonHeight, 1f).apply { marginStart = buttonGap })

        root.addView(titleRow)
        root.addView(statusPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(timeText)
        root.addView(countText)
        root.addView(
            buttons,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(if (compactOverlay) 5 else 9)
            },
        )

        val params = WindowManager.LayoutParams(
            desiredWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = config.overlayX
            y = config.overlayY
        }

        attachDrag(root, params)
        attachDrag(titleRow, params)
        attachDrag(title, params)
        attachDrag(statusPanel, params)
        statusTitleText?.let { attachDrag(it, params) }
        statusDetailText?.let { attachDrag(it, params) }
        timeText?.let { attachDrag(it, params) }
        countText?.let { attachDrag(it, params) }

        val added = runCatching { windowManager.addView(root, params) }.isSuccess
        if (!added) {
            clearReferences()
            Toast.makeText(service, "ARUNO操作枠を表示できませんでした", Toast.LENGTH_LONG).show()
            return false
        }
        overlayView = root
        overlayParams = params
        root.post { clampToBounds() }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        refresh()
        return true
    }

    fun hide() {
        handler.removeCallbacks(ticker)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        clearReferences()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        hide()
    }

    fun onConfigurationChanged() {
        overlayView?.post { clampToBounds() }
    }

    private fun attachDrag(handle: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = originX + (event.rawX - downX).toInt()
                    params.y = originY + (event.rawY - downY).toInt()
                    clampPosition(overlayView ?: handle, params)
                    overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    configStore.saveOverlayPosition(params.x, params.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleSize() {
        val params = overlayParams ?: return
        configStore.saveOverlayPosition(params.x, params.y)
        compactOverlay = !compactOverlay
        configStore.saveOverlayCompact(compactOverlay)
        hide()
        show()
    }

    private fun clampToBounds() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.width = desiredWidth()
        clampPosition(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clampPosition(view: View, params: WindowManager.LayoutParams) {
        val width = if (params.width > 0) params.width else view.width
        val maxX = (service.resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
        val maxY = (service.resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun refresh() {
        val state = AutomationRuntime.snapshot()
        val (statusTitle, statusDetail) = splitStatus(state.message)
        statusTitleText?.apply {
            text = statusTitle
            textSize = when {
                compactOverlay && statusTitle.length >= 15 -> 11f
                compactOverlay -> 14f
                statusTitle.length >= 15 -> 17f
                else -> 23f
            }
        }
        statusDetailText?.apply {
            text = statusDetail
            visibility = if (statusDetail.isBlank()) View.GONE else View.VISIBLE
        }
        timeText?.text = if (state.requested) {
            val remaining = if (state.endsAtElapsed == Long.MAX_VALUE) "停止まで" else formatDuration(state.remainingMs)
            "経過 ${formatDuration(state.elapsedMs)}  /  残り $remaining"
        } else {
            "経過 --:--:--  /  残り --:--:--"
        }
        countText?.text = "スライド ${state.swipeCount}回"
        startButton?.text = if (state.requested) "停止" else "スタート"
        startButton?.background = roundedBackground(
            if (state.requested) Color.rgb(165, 39, 39) else Color.rgb(15, 116, 54),
            Color.TRANSPARENT,
        )
        sizeButton?.text = if (compactOverlay) "大" else "小"
    }

    private fun clearReferences() {
        overlayView = null
        overlayParams = null
        statusTitleText = null
        statusDetailText = null
        timeText = null
        countText = null
        startButton = null
        sizeButton = null
    }

    private fun desiredWidth(): Int =
        dp(if (compactOverlay) 164 else 308)
            .coerceAtMost((service.resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(1)))

    private fun label(size: Float, color: Int) = TextView(service).apply {
        textSize = size
        setTextColor(color)
        setPadding(dp(2), dp(1), dp(2), dp(1))
    }

    private fun compactButton(textValue: String, color: Int) = Button(service).apply {
        text = textValue
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.WHITE)
        isAllCaps = false
        minHeight = 0
        minWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        background = roundedBackground(color, Color.TRANSPARENT)
    }

    private fun splitStatus(message: String): Pair<String, String> {
        val lines = message.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        return when {
            lines.isEmpty() -> "待機中" to ""
            lines.size == 1 -> lines.first() to ""
            else -> lines.first() to lines.drop(1).joinToString(" ")
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(11).toFloat()
        setColor(fill)
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density + 0.5f).toInt()

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1_000L).coerceAtLeast(0L)
        return String.format(
            Locale.JAPAN,
            "%02d:%02d:%02d",
            seconds / 3_600,
            (seconds / 60) % 60,
            seconds % 60,
        )
    }
}
