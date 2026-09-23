package jp.aruno.clicker.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import jp.aruno.clicker.automation.ArunoAccessibilityService
import jp.aruno.clicker.automation.AutomationConfigStore
import jp.aruno.clicker.automation.AutomationContract
import jp.aruno.clicker.automation.AutomationRuntime
import java.util.Locale

/** Foreground service plus a small draggable controller that remains above TikTok Lite. */
open class AutomationOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var configStore: AutomationConfigStore
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var statusTitleText: TextView? = null
    private var statusDetailText: TextView? = null
    private var timeText: TextView? = null
    private var countText: TextView? = null
    private var startButton: Button? = null
    private var sizeButton: Button? = null
    private var compactOverlay = true
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refreshOverlay()
            refreshNotification()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        configStore = AutomationConfigStore(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AutomationContract.ACTION_STOP_AUTOMATION -> requestStop()
            AutomationContract.ACTION_CLOSE_OVERLAY -> {
                requestStop()
                removeOverlay()
                stopSelf()
            }
            AutomationContract.ACTION_SHOW_OVERLAY -> showOverlay()
            AutomationContract.ACTION_START_AUTOMATION, null -> {
                if (configStore.load().floatingController) showOverlay()
                requestStart(
                    durationOverride = intent?.getLongExtra(AutomationContract.EXTRA_DURATION_MS, -1L)
                        ?.takeIf { it > 0L },
                    intervalOverride = intent?.getLongExtra(AutomationContract.EXTRA_INTERVAL_MS, -1L)
                        ?.takeIf { it > 0L },
                    source = intent?.getStringExtra(AutomationContract.EXTRA_SOURCE)
                        ?: AutomationContract.SOURCE_MANUAL,
                    startMode = intent?.getStringExtra(AutomationContract.EXTRA_START_MODE)
                        ?: AutomationContract.START_MODE_REPEAT,
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.post { clampOverlayToBounds() }
    }

    private fun requestStart(
        durationOverride: Long? = null,
        intervalOverride: Long? = null,
        source: String,
        startMode: String = AutomationContract.START_MODE_REPEAT,
    ) {
        val config = configStore.load()
        val duration = durationOverride ?: config.timeLimitMs
        val interval = intervalOverride ?: config.intervalMs
        AutomationRuntime.requestStart(this, duration, startMode)
        sendBroadcast(
            Intent(AutomationContract.ACTION_START_AUTOMATION)
                .setPackage(packageName)
                .putExtra(AutomationContract.EXTRA_DURATION_MS, duration)
                .putExtra(AutomationContract.EXTRA_INTERVAL_MS, interval)
                .putExtra(AutomationContract.EXTRA_SOURCE, source)
                .putExtra(AutomationContract.EXTRA_START_MODE, startMode),
        )
        if (ArunoAccessibilityService.instance == null) {
            AutomationRuntime.markWaiting(this, "アクセシビリティをONにしてください")
            Toast.makeText(this, "ARUNO CLICKERのアクセシビリティをONにしてください", Toast.LENGTH_LONG).show()
        }
        refreshOverlay()
    }

    private fun requestStop() {
        sendBroadcast(
            Intent(AutomationContract.ACTION_STOP_AUTOMATION).setPackage(packageName),
        )
        ArunoAccessibilityService.instance?.stopAutomation("停止しました")
            ?: AutomationRuntime.stop(this, "停止しました")
        refreshOverlay()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "フローティング表示の権限を許可してください", Toast.LENGTH_LONG).show()
            return
        }

        val config = configStore.load()
        compactOverlay = config.overlayCompact
        val root = LinearLayout(this).apply {
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
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = if (compactOverlay) "A  ARUNO" else "A  ARUNO CLICKER"
            setTextColor(Color.rgb(87, 255, 137))
            textSize = if (compactOverlay) 12f else 16f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(3), 0, dp(3), dp(if (compactOverlay) 4 else 8))
        }
        val sizeToggle = compactButton(if (compactOverlay) "大" else "小", Color.rgb(19, 92, 48)).apply {
            textSize = if (compactOverlay) 12f else 14f
            setOnClickListener { toggleOverlaySize() }
        }
        sizeButton = sizeToggle
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(sizeToggle, LinearLayout.LayoutParams(dp(if (compactOverlay) 38 else 44), dp(if (compactOverlay) 30 else 36)))
        val statusPanel = LinearLayout(this).apply {
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
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val actionButton = compactButton("2回目以降", Color.rgb(15, 116, 54)).apply {
            setOnClickListener {
                if (AutomationRuntime.snapshot().requested) {
                    requestStop()
                } else {
                    requestStart(
                        source = AutomationContract.SOURCE_MANUAL,
                        startMode = AutomationContract.START_MODE_REPEAT,
                    )
                }
            }
        }
        startButton = actionButton
        val closeButton = compactButton("閉じる", Color.rgb(83, 83, 83)).apply {
            setOnClickListener {
                requestStop()
                removeOverlay()
                stopSelf()
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
        root.addView(buttons, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(if (compactOverlay) 5 else 9)
        })

        val params = WindowManager.LayoutParams(
            desiredOverlayWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        windowManager.addView(root, params)
        overlayView = root
        overlayParams = params
        root.post { clampOverlayToBounds() }
        refreshOverlay()
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
                    overlayView?.let { windowManager.updateViewLayout(it, params) }
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

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        overlayParams = null
        statusTitleText = null
        statusDetailText = null
        timeText = null
        countText = null
        startButton = null
        sizeButton = null
    }

    private fun desiredOverlayWidth(): Int =
        dp(if (compactOverlay) 164 else 308)
            .coerceAtMost((resources.displayMetrics.widthPixels - dp(12)).coerceAtLeast(dp(1)))

    private fun toggleOverlaySize() {
        val params = overlayParams ?: return
        configStore.saveOverlayPosition(params.x, params.y)
        compactOverlay = !compactOverlay
        configStore.saveOverlayCompact(compactOverlay)
        removeOverlay()
        showOverlay()
    }

    private fun clampOverlayToBounds() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.width = desiredOverlayWidth()
        clampPosition(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun clampPosition(view: View, params: WindowManager.LayoutParams) {
        val width = if (params.width > 0) params.width else view.width
        val height = view.height
        val maxX = (resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - height).coerceAtLeast(0)
        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun refreshOverlay() {
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
        startButton?.text = if (state.requested) "停止" else "2回目以降"
        startButton?.background = roundedBackground(
            if (state.requested) Color.rgb(165, 39, 39) else Color.rgb(15, 116, 54),
            Color.TRANSPARENT,
        )
    }

    private fun label(size: Float, color: Int) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        setPadding(dp(2), dp(1), dp(2), dp(1))
    }

    private fun compactButton(textValue: String, color: Int) = Button(this).apply {
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1_000L).coerceAtLeast(0L)
        return String.format(Locale.JAPAN, "%02d:%02d:%02d", seconds / 3_600, (seconds / 60) % 60, seconds % 60)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ARUNO CLICKER 実行状態",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "自動スライド実行中に表示します"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, javaClass).setAction(AutomationContract.ACTION_STOP_AUTOMATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settingsIntent = PendingIntent.getActivity(
            this,
            3,
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val state = AutomationRuntime.snapshot()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ARUNO CLICKER")
            .setContentText(
                if (state.requested) {
                    "${state.message.replace('\n', '・')}・${state.swipeCount}回"
                } else {
                    "待機中"
                },
            )
            .setContentIntent(settingsIntent)
            .setOngoing(state.requested)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "停止", stopIntent).build())
            .build()
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        private const val CHANNEL_ID = "aruno_clicker_automation"
        private const val NOTIFICATION_ID = 1001
    }
}
