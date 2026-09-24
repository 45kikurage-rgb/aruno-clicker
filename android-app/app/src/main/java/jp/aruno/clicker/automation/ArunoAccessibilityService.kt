package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import jp.aruno.clicker.BuildConfig
import jp.aruno.clicker.data.RemoteConfigClient
import java.util.ArrayDeque
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class ArunoAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val gaugeExecutor = Executors.newSingleThreadExecutor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startSyncJob: Job? = null
    private lateinit var configStore: AutomationConfigStore
    private var activeConfig = AutomationConfig()
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundPackage: String? = null
    private var foregroundPackageUpdatedAt = 0L
    private var wrongAppChecks = 0
    private var gestureFailures = 0
    private var automationGeneration = 0L
    private var lastSwipeDelayMs = 0L
    private var completedWarmupCycles = 0
    private var restartInProgress = false
    private var pendingSwipeReason: String? = null
    private var pendingSwipePageToken = -1L
    private var contentVisibleSinceMs = 0L
    private var contentPageToken = 0L
    private var classificationCandidate: String? = null
    private var classificationMatchCount = 0
    private var slidePhaseActive = false
    private var popupDismissInProgress = false
    private var popupCheckScheduled = false
    private var lastPopupScanAtMs = 0L
    private var lastPopupDismissAtMs = 0L
    private var pendingDailyStartCompletion = false

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AutomationContract.ACTION_START_AUTOMATION -> {
                    startWithLatestRemoteConfig(intent)
                }
                AutomationContract.ACTION_STOP_AUTOMATION -> stopAutomation("停止しました")
            }
        }
    }

    private val swipeRunnable = object : Runnable {
        override fun run() {
            val generation = automationGeneration
            val state = AutomationRuntime.snapshot()
            if (!isSessionActive(generation) || restartInProgress) return
            if (state.remainingMs <= 0L) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
                stopAutomation("設定時間が終了しました")
                return
            }

            if (!isTargetForeground()) {
                wrongAppChecks += 1
                AutomationRuntime.markWaiting(this@ArunoAccessibilityService, "TikTok Liteを開いています")
                if (wrongAppChecks == 1 || wrongAppChecks % 3 == 0) launchTargetApp(activeConfig.targetPackage)
                if (wrongAppChecks > activeConfig.actionRetryCount.coerceAtLeast(1) * 3) {
                    stopAutomation("TikTok Liteを開けませんでした")
                    return
                }
                handler.postDelayed(this, 1_500L)
                return
            }

            if (wrongAppChecks > 0) {
                wrongAppChecks = 0
                beginContentTiming(generation)
                return
            }
            if (pendingSwipePageToken != contentPageToken) {
                beginContentTiming(generation)
                return
            }
            performConfiguredSwipe(pendingSwipeReason, generation)
        }
    }

    private val popupCheckRunnable = Runnable {
        popupCheckScheduled = false
        lastPopupScanAtMs = android.os.SystemClock.elapsedRealtime()
        val generation = automationGeneration
        if (
            isSessionActive(generation) &&
            slidePhaseActive &&
            !restartInProgress &&
            activeConfig.autoDismissPopups &&
            isTargetForeground()
        ) {
            dismissBlockingPopupIfPresent(generation)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        configStore = AutomationConfigStore(this)
        val filter = IntentFilter().apply {
            addAction(AutomationContract.ACTION_START_AUTOMATION)
            addAction(AutomationContract.ACTION_STOP_AUTOMATION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(commandReceiver, filter)
        }

        val config = configStore.load()
        AutomationRuntime.restoreRequested(this)?.let { restored ->
            if (restored.pendingStart) {
                startWithLatestRemoteConfig(
                    Intent()
                        .putExtra(AutomationContract.EXTRA_DURATION_MS, restored.durationMs)
                        .putExtra(AutomationContract.EXTRA_START_MODE, restored.startMode),
                )
            } else if (config.autoResume) {
                startAutomation(
                    config.copy(timeLimitMs = restored.durationMs).sanitized(),
                    startMode = AutomationContract.START_MODE_REPEAT,
                )
            } else {
                AutomationRuntime.stop(this, "自動再開はOFFです")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.let {
            foregroundPackage = it
            foregroundPackageUpdatedAt = android.os.SystemClock.elapsedRealtime()
        }
        if (
            event != null &&
            event.packageName?.toString() in AutomationConfig.TARGET_PACKAGES &&
            slidePhaseActive &&
            activeConfig.autoDismissPopups
        ) {
            schedulePopupCheck()
        }
    }

    override fun onInterrupt() {
        AutomationRuntime.markWaiting(this, "アクセシビリティが中断されました")
    }

    override fun onDestroy() {
        automationGeneration += 1L
        handler.removeCallbacksAndMessages(null)
        gaugeExecutor.shutdownNow()
        startSyncJob?.cancel()
        serviceScope.cancel()
        runCatching { unregisterReceiver(commandReceiver) }
        releaseWakeLock()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun startAutomation(
        config: AutomationConfig = configStore.load(),
        startMode: String = AutomationContract.START_MODE_REPEAT,
    ) {
        automationGeneration += 1L
        val generation = automationGeneration
        activeConfig = config.sanitized()
        wrongAppChecks = 0
        gestureFailures = 0
        completedWarmupCycles = 0
        restartInProgress = false
        slidePhaseActive = false
        popupDismissInProgress = false
        popupCheckScheduled = false
        lastPopupScanAtMs = 0L
        lastPopupDismissAtMs = 0L
        pendingSwipeReason = null
        pendingSwipePageToken = -1L
        classificationCandidate = null
        classificationMatchCount = 0
        contentPageToken += 1L
        lastSwipeDelayMs = 0L
        handler.removeCallbacksAndMessages(null)
        val resolvedStartMode = if (startMode == AutomationContract.START_MODE_AUTO) {
            DailyStartModeStore(this).modeForToday()
        } else {
            startMode
        }
        pendingDailyStartCompletion = startMode == AutomationContract.START_MODE_AUTO &&
            resolvedStartMode == AutomationContract.START_MODE_FIRST
        AutomationRuntime.requestStart(this, activeConfig.timeLimitMs, resolvedStartMode)
        AutomationRuntime.markSessionActive(this)
        if (activeConfig.keepScreenAwake) acquireWakeLock(activeConfig.timeLimitMs)
        if (resolvedStartMode == AutomationContract.START_MODE_FIRST) {
            beginFirstStartSequence(activeConfig, generation)
        } else {
            beginRepeatStartSequence(activeConfig, generation)
        }
    }

    /** Every manual, overlay and scheduled start synchronizes shared URLs before choosing its mode. */
    private fun startWithLatestRemoteConfig(intent: Intent) {
        startSyncJob?.cancel()
        val durationOverride = intent.getLongExtra(AutomationContract.EXTRA_DURATION_MS, -1L)
            .takeIf { it >= 0L }
        val intervalOverride = intent.getLongExtra(AutomationContract.EXTRA_INTERVAL_MS, -1L)
            .takeIf { it > 0L }
        val startMode = intent.getStringExtra(AutomationContract.EXTRA_START_MODE)
            .takeIf {
                it == AutomationContract.START_MODE_FIRST ||
                    it == AutomationContract.START_MODE_REPEAT ||
                    it == AutomationContract.START_MODE_AUTO
            }
            ?: AutomationContract.START_MODE_REPEAT
        AutomationRuntime.markWaiting(this, "共通URLを確認しています")
        startSyncJob = serviceScope.launch {
            // On failure fetchAndCache leaves the last successfully cached URLs untouched.
            RemoteConfigClient(applicationContext).fetchAndCache()
            coroutineContext.ensureActive()
            val stored = configStore.load()
            activeConfig = stored.copy(
                timeLimitMs = durationOverride ?: stored.timeLimitMs,
                intervalMs = intervalOverride ?: stored.intervalMs,
            ).sanitized()
            startAutomation(activeConfig, startMode)
        }
    }

    fun stopAutomation(reason: String = "停止しました") {
        startSyncJob?.cancel()
        startSyncJob = null
        automationGeneration += 1L
        contentPageToken += 1L
        slidePhaseActive = false
        popupDismissInProgress = false
        popupCheckScheduled = false
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        AutomationRuntime.stop(this, reason)
    }

    private fun launchTargetApp(preferredPackage: String, resetTask: Boolean = false): Boolean {
        val candidates = listOf(preferredPackage) + AutomationConfig.TARGET_PACKAGES
        val launchIntent = candidates.distinct().firstNotNullOfOrNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        } ?: run {
            AutomationRuntime.markWaiting(this, "TikTok Liteが見つかりません")
            return false
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchIntent.addFlags(
            if (resetTask) {
                Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } else {
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
        )
        return runCatching { startActivity(launchIntent); true }.getOrDefault(false)
    }

    private data class InitialUrlStep(
        val url: String,
        val title: String,
        val label: String,
    )

    private fun beginFirstStartSequence(config: AutomationConfig, generation: Long) {
        slidePhaseActive = false
        AutomationRuntime.markWaiting(this, "初回起動\nアプリIDから起動")
        if (!launchTargetApp(config.targetPackage, resetTask = true)) {
            stopAutomation("TikTok Liteを起動できませんでした")
            return
        }
        waitForTargetForeground(generation) appReady@{ ready ->
            if (!isSessionActive(generation)) return@appReady
            if (!ready) {
                stopAutomation("TikTok Liteの表示を確認できませんでした")
                return@appReady
            }
            val steps = if (BuildConfig.IS_VER_S) {
                listOf(
                    InitialUrlStep(config.shareUrl, "開始準備中", "準備 1/2"),
                    InitialUrlStep(config.shareUrl, "開始準備中", "準備 2/2"),
                )
            } else {
                listOf(
                    InitialUrlStep(config.startupUrl1, "指定URL1を開く", "URL 1・1回目"),
                    InitialUrlStep(config.startupUrl1, "指定URL1を再度開く", "URL 1・2回目"),
                    InitialUrlStep(config.startupUrl2, "指定URL2を開く", "URL 2・1回目"),
                    InitialUrlStep(config.startupUrl2, "指定URL2を再度開く", "URL 2・2回目"),
                )
            }
            runInitialUrlStep(steps, index = 0, generation = generation) {
                closeTargetViaRecents(generation, "初回URL完了") initialClose@{ closed ->
                    if (!isSessionActive(generation)) return@initialClose
                    if (!closed) {
                        stopAutomation("TikTok Liteの履歴カードを終了できませんでした")
                    } else {
                        beginRepeatStartSequence(config, generation)
                    }
                }
            }
        }
    }

    private fun runInitialUrlStep(
        steps: List<InitialUrlStep>,
        index: Int,
        generation: Long,
        onFinished: () -> Unit,
    ) {
        if (!isSessionActive(generation)) return
        if (index >= steps.size) {
            onFinished()
            return
        }
        val step = steps[index]
        openUrlAndHold(step.url, step.title, step.label, generation) {
            runInitialUrlStep(steps, index + 1, generation, onFinished)
        }
    }

    private fun beginRepeatStartSequence(config: AutomationConfig, generation: Long) {
        launchTargetForSlidePhase(config, generation)
    }

    private fun launchTargetForSlidePhase(config: AutomationConfig, generation: Long) {
        if (!isSessionActive(generation)) return
        slidePhaseActive = false
        val phase = if (completedWarmupCycles < WARMUP_CYCLE_COUNT) {
            "1分動作 ${completedWarmupCycles + 1}/$WARMUP_CYCLE_COUNT"
        } else {
            "通常動作・10分ごとに再起動"
        }
        AutomationRuntime.markWaiting(this, "アプリIDから起動\n$phase")
        if (!launchTargetApp(config.targetPackage, resetTask = true)) {
            stopAutomation("TikTok Liteを起動できませんでした")
            return
        }
        waitForTargetForeground(generation) targetReady@{ ready ->
            if (!isSessionActive(generation)) return@targetReady
            if (!ready) {
                stopAutomation("TikTok Liteの表示を確認できませんでした")
                return@targetReady
            }
            restartInProgress = false
            slidePhaseActive = true
            if (pendingDailyStartCompletion) {
                DailyStartModeStore(this).markTodayCompleted()
                pendingDailyStartCompletion = false
            }
            beginContentTiming(generation, phase)
            val restartDelay = if (completedWarmupCycles < WARMUP_CYCLE_COUNT) {
                WARMUP_RESTART_INTERVAL_MS
            } else {
                PERIODIC_RESTART_INTERVAL_MS
            }
            handler.postDelayed({ restartTargetCycle(generation) }, restartDelay)
        }
    }

    private fun restartTargetCycle(generation: Long) {
        if (!isSessionActive(generation) || restartInProgress) return
        restartInProgress = true
        slidePhaseActive = false
        popupDismissInProgress = false
        popupCheckScheduled = false
        contentPageToken += 1L
        pendingSwipeReason = null
        pendingSwipePageToken = -1L
        classificationCandidate = null
        classificationMatchCount = 0
        handler.removeCallbacks(swipeRunnable)
        handler.removeCallbacks(popupCheckRunnable)
        val phaseLabel = if (completedWarmupCycles < WARMUP_CYCLE_COUNT) {
            "1分動作 ${completedWarmupCycles + 1}/$WARMUP_CYCLE_COUNT 完了"
        } else {
            "10分経過"
        }
        closeTargetViaRecents(generation, phaseLabel) restartDone@{ closed ->
            if (!isSessionActive(generation)) return@restartDone
            if (!closed) {
                stopAutomation("TikTok Liteの履歴カードを終了できませんでした")
                return@restartDone
            }
            if (completedWarmupCycles < WARMUP_CYCLE_COUNT) completedWarmupCycles += 1
            launchTargetForSlidePhase(activeConfig, generation)
        }
    }

    private fun openUrlAndHold(
        url: String,
        launchTitle: String,
        label: String,
        generation: Long,
        onHeld: () -> Unit,
    ) {
        AutomationRuntime.markWaiting(this, "$launchTitle\n$label・起動確認")
        if (!openTargetUrl(url)) {
            if (isSessionActive(generation)) stopAutomation("指定URL $label をTikTok Liteで開けませんでした")
            return
        }
        waitForTargetForeground(generation) launchReady@{ ready ->
            if (!isSessionActive(generation)) return@launchReady
            if (!ready) {
                stopAutomation("指定URL $label の表示を確認できませんでした")
                return@launchReady
            }
            holdUrlWithCountdown(launchTitle, label, generation, onHeld)
        }
    }

    private fun holdUrlWithCountdown(
        launchTitle: String,
        label: String,
        generation: Long,
        onHeld: () -> Unit,
    ) {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val totalSeconds = AutomationConfig.STARTUP_STEP_DELAY_MS / 1_000L
        val ticker = object : Runnable {
            override fun run() {
                if (!isSessionActive(generation)) return
                val elapsedMs = android.os.SystemClock.elapsedRealtime() - startedAt
                val elapsedSeconds = (elapsedMs / 1_000L).coerceAtMost(totalSeconds)
                val remainingSeconds = (totalSeconds - elapsedSeconds).coerceAtLeast(0L)
                AutomationRuntime.markWaiting(
                    this@ArunoAccessibilityService,
                    "$launchTitle\n$label・${elapsedSeconds.toString().padStart(2, '0')}秒経過 / " +
                        "${remainingSeconds.toString().padStart(2, '0')}秒待機",
                )
                if (elapsedMs >= AutomationConfig.STARTUP_STEP_DELAY_MS) {
                    onHeld()
                } else {
                    handler.postDelayed(this, 1_000L - (elapsedMs % 1_000L))
                }
            }
        }
        handler.post(ticker)
    }

    private fun waitForTargetForeground(
        generation: Long,
        onFinished: (Boolean) -> Unit,
    ) {
        val deadline = android.os.SystemClock.elapsedRealtime() + TARGET_LAUNCH_TIMEOUT_MS
        var chooserClicks = 0
        var lastChooserClickAt = 0L
        var targetStableSince = 0L
        val check = object : Runnable {
            override fun run() {
                if (!isSessionActive(generation)) return
                val now = android.os.SystemClock.elapsedRealtime()
                if (isTargetForeground()) {
                    if (targetStableSince == 0L) targetStableSince = now
                    if (now - targetStableSince >= TARGET_FOREGROUND_STABLE_MS) {
                        onFinished(true)
                    } else {
                        handler.postDelayed(this, FOREGROUND_CHECK_INTERVAL_MS)
                    }
                } else if (now >= deadline) {
                    onFinished(false)
                } else {
                    targetStableSince = 0L
                    if (chooserClicks < MAX_CHOOSER_CLICKS && now - lastChooserClickAt >= CHOOSER_CLICK_INTERVAL_MS) {
                        if (clickTikTokLiteChoice()) {
                            chooserClicks += 1
                            lastChooserClickAt = now
                            AutomationRuntime.markWaiting(
                                this@ArunoAccessibilityService,
                                "起動先を自動選択\nTikTok-Lite",
                            )
                        }
                    }
                    handler.postDelayed(this, FOREGROUND_CHECK_INTERVAL_MS)
                }
            }
        }
        handler.post(check)
    }

    private fun clickTikTokLiteChoice(): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        windows.mapNotNullTo(queue) { it.root }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited += 1
            val label = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }
            if (TARGET_CARD_LABELS.any { label.contains(it, ignoreCase = true) }) {
                var clickable: AccessibilityNodeInfo? = node
                repeat(MAX_CLICKABLE_PARENT_DEPTH) {
                    val candidate = clickable ?: return@repeat
                    if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    clickable = candidate.parent
                }
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return false
    }

    private fun closeTargetViaRecents(
        generation: Long,
        stepLabel: String,
        onClosed: (Boolean) -> Unit,
    ) {
        if (!isSessionActive(generation) || !isTargetForeground()) {
            onClosed(false)
            return
        }
        AutomationRuntime.markWaiting(this, "カードタスクキル\n$stepLabel")
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) {
            onClosed(false)
            return
        }
        waitForRecentsSurface(generation) recentsReady@{ ready ->
            if (!isSessionActive(generation)) return@recentsReady
            if (!ready) {
                onClosed(false)
                return@recentsReady
            }
            val windowManager = getSystemService(WindowManager::class.java)
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.maximumWindowMetrics.bounds
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.run {
                    android.graphics.Point().also(::getRealSize).let { android.graphics.Rect(0, 0, it.x, it.y) }
                }
            }
            findAndDismissTargetRecentsCards(
                generation = generation,
                bounds = bounds,
                searchIndex = 0,
                searchDirection = RECENTS_SEARCH_FORWARD,
                dismissedCount = 0,
            ) finish@{ succeeded ->
                if (!isSessionActive(generation)) return@finish
                performGlobalAction(GLOBAL_ACTION_HOME)
                handler.postDelayed({
                    if (isSessionActive(generation)) onClosed(succeeded)
                }, RECENTS_AFTER_DISMISS_MS)
            }
        }
    }

    private fun findAndDismissTargetRecentsCards(
        generation: Long,
        bounds: Rect,
        searchIndex: Int,
        searchDirection: Int,
        dismissedCount: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        if (!isSessionActive(generation)) return
        // HyperOS returns to the home screen as soon as the last recent-app card
        // is removed. The launcher can still expose a TikTok Lite home-screen
        // icon, so stop card discovery when the recents surface disappears.
        val targetCardBounds = findTargetRecentsCardBounds(bounds)
        if (
            targetCardBounds == null &&
            prefersHorizontalRecentsDismissal() &&
            !hasBottomRecentsClearIndicator(bounds)
        ) {
            AutomationRuntime.markWaiting(
                this,
                "カードタスクキル\nTikTokカード残り0件",
            )
            onFinished(true)
            return
        }
        if (targetCardBounds == null) {
            val searchLimit = if (searchDirection == RECENTS_SEARCH_FORWARD) {
                MAX_RECENTS_CARDS_TO_SEARCH
            } else {
                MAX_RECENTS_CARDS_TO_SEARCH * 2
            }
            if (searchIndex >= searchLimit) {
                if (searchDirection == RECENTS_SEARCH_FORWARD) {
                    AutomationRuntime.markWaiting(this, "カードを反対方向へ探索\n1/${MAX_RECENTS_CARDS_TO_SEARCH * 2}")
                    swipeRecentsForSearch(generation, bounds, RECENTS_SEARCH_REVERSE) { moved ->
                        if (!moved) {
                            onFinished(false)
                        } else {
                            handler.postDelayed({
                                findAndDismissTargetRecentsCards(
                                    generation,
                                    bounds,
                                    searchIndex = 1,
                                    searchDirection = RECENTS_SEARCH_REVERSE,
                                    dismissedCount = dismissedCount,
                                    onFinished = onFinished,
                                )
                            }, RECENTS_SEARCH_SETTLE_MS)
                        }
                    }
                    return
                }
                AutomationRuntime.markWaiting(
                    this,
                    "カードタスクキル\nTikTokカード残り0件",
                )
                onFinished(true)
            } else {
                AutomationRuntime.markWaiting(
                    this,
                    recentsSearchLabel(bounds, searchDirection, searchIndex + 1, searchLimit),
                )
                swipeRecentsForSearch(generation, bounds, searchDirection) { moved ->
                    if (!moved) {
                        onFinished(false)
                    } else {
                        handler.postDelayed({
                            findAndDismissTargetRecentsCards(
                                generation,
                                bounds,
                                searchIndex + 1,
                                searchDirection,
                                dismissedCount,
                                onFinished,
                            )
                        }, RECENTS_SEARCH_SETTLE_MS)
                    }
                }
            }
            return
        }
        if (dismissedCount >= MAX_TARGET_CARDS_TO_DISMISS) {
            onFinished(false)
            return
        }
        dismissVisibleTargetRecentsCard(
            generation = generation,
            bounds = bounds,
            targetCardBounds = targetCardBounds,
            attempt = 0,
        ) { dismissed ->
            if (!isSessionActive(generation)) return@dismissVisibleTargetRecentsCard
            if (!dismissed) {
                clearAllRecentsAsFallback(generation, bounds, onFinished)
            } else {
                AutomationRuntime.markWaiting(
                    this,
                    "カードタスクキル\n${dismissedCount + 1}枚終了・残り確認",
                )
                handler.postDelayed({
                    findAndDismissTargetRecentsCards(
                        generation,
                        bounds,
                        searchIndex = 0,
                        searchDirection = RECENTS_SEARCH_FORWARD,
                        dismissedCount = dismissedCount + 1,
                        onFinished = onFinished,
                    )
                }, RECENTS_RETRY_DELAY_MS)
            }
        }
    }

    private fun dismissVisibleTargetRecentsCard(
        generation: Long,
        bounds: Rect,
        targetCardBounds: Rect,
        attempt: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        val startX = targetCardBounds.centerX().toFloat()
            .coerceIn(bounds.width() * 0.15f, bounds.width() * 0.85f)
        val startY = targetCardBounds.centerY().toFloat()
            .coerceIn(bounds.height() * 0.35f, bounds.height() * 0.55f)
        val horizontalFirst = usesHorizontalRecentsDismissal(bounds)
        val gestureMode = when {
            horizontalFirst && attempt == 0 -> RECENTS_DISMISS_LEFT
            horizontalFirst && attempt == 1 -> RECENTS_DISMISS_RIGHT
            horizontalFirst -> RECENTS_DISMISS_UP
            attempt == 0 -> RECENTS_DISMISS_UP
            attempt == 1 -> RECENTS_DISMISS_LEFT
            else -> RECENTS_DISMISS_RIGHT
        }
        val path = Path().apply {
            moveTo(startX, startY)
            when (gestureMode) {
                RECENTS_DISMISS_LEFT -> lineTo(bounds.width() * 0.03f, startY)
                RECENTS_DISMISS_RIGHT -> lineTo(bounds.width() * 0.97f, startY)
                else -> lineTo(startX, bounds.height() * 0.04f)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, RECENTS_DISMISS_DURATION_MS))
            .build()
        val retryOrFinish: (Boolean) -> Unit = retry@{ gestureCompleted ->
            if (!isSessionActive(generation)) return@retry
            if (!gestureCompleted) {
                if (attempt >= RECENTS_DISMISS_RETRY_COUNT) {
                    onFinished(false)
                } else {
                    retryRecentsDismiss(generation, bounds, targetCardBounds, attempt, onFinished)
                }
                return@retry
            }
            val latestBounds = findTargetRecentsCardBounds(bounds)
            val originalCardGone = latestBounds == null || !sameRecentsCard(targetCardBounds, latestBounds)
            if (originalCardGone) {
                onFinished(true)
            } else if (attempt >= RECENTS_DISMISS_RETRY_COUNT) {
                onFinished(false)
            } else {
                retryRecentsDismiss(generation, bounds, requireNotNull(latestBounds), attempt, onFinished)
            }
        }
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({
                        if (isSessionActive(generation)) {
                            retryOrFinish(true)
                        }
                    }, RECENTS_DISMISS_VERIFY_MS)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    retryOrFinish(false)
                }
            },
            handler,
        )
        if (!accepted) retryOrFinish(false)
    }

    private fun retryRecentsDismiss(
        generation: Long,
        bounds: Rect,
        targetCardBounds: Rect,
        attempt: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        val nextAttempt = attempt + 1
        val nextDirection = when {
            usesHorizontalRecentsDismissal(bounds) && nextAttempt == 1 -> "反対方向へ横スワイプ"
            usesHorizontalRecentsDismissal(bounds) -> "上方向へスワイプ"
            nextAttempt == 1 -> "横方向へスワイプ"
            else -> "反対方向へ横スワイプ"
        }
        AutomationRuntime.markWaiting(this, "カードタスクキル\n$nextDirection")
        handler.postDelayed({
            if (isSessionActive(generation)) {
                dismissVisibleTargetRecentsCard(
                    generation,
                    bounds,
                    targetCardBounds,
                    nextAttempt,
                    onFinished,
                )
            }
        }, RECENTS_RETRY_DELAY_MS)
    }

    private fun prefersHorizontalRecentsDismissal(): Boolean {
        val deviceFamily = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return listOf("xiaomi", "redmi", "poco").any(deviceFamily::contains)
    }

    private fun usesHorizontalRecentsDismissal(screen: Rect): Boolean =
        prefersHorizontalRecentsDismissal() || hasBottomRecentsClearIndicator(screen)

    /**
     * HyperOS and similar launchers show a large clear-all X at the bottom center.
     * It is used only as a layout signal; the app never taps it because that would
     * also close unrelated LINE/Chrome cards.
     */
    private fun hasBottomRecentsClearIndicator(screen: Rect): Boolean =
        findRecentsClearAllNode(screen) != null

    private fun findRecentsClearAllNode(screen: Rect): AccessibilityNodeInfo? {
        val activePackage = rootInActiveWindow?.packageName?.toString() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        windows.mapNotNullTo(queue) { window ->
            window.root?.takeIf { it.packageName?.toString() == activePackage }
        }
        var visited = 0
        val minY = screen.top + (screen.height() * 0.68f).toInt()
        val minX = screen.left + (screen.width() * 0.22f).toInt()
        val maxX = screen.left + (screen.width() * 0.78f).toInt()
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited += 1
            if (node.isVisibleToUser) {
                val nodeBounds = Rect().also(node::getBoundsInScreen)
                val centerX = nodeBounds.centerX()
                val centerY = nodeBounds.centerY()
                if (!nodeBounds.isEmpty && centerX in minX..maxX && centerY >= minY) {
                    val label = buildString {
                        append(node.text?.toString().orEmpty())
                        append(' ')
                        append(node.contentDescription?.toString().orEmpty())
                    }.trim().lowercase()
                    val resourceId = node.viewIdResourceName.orEmpty().lowercase()
                    val exactX = label in RECENTS_CLEAR_LABELS
                    val clearAllHint = RECENTS_CLEAR_RESOURCE_HINTS.any(resourceId::contains) ||
                        RECENTS_CLEAR_WORD_HINTS.any(label::contains)
                    if (exactX || clearAllHint) return node
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun sameRecentsCard(before: Rect, after: Rect): Boolean {
        val intersection = Rect(before)
        if (!intersection.intersect(after)) return false
        val intersectionArea = intersection.width().toLong() * intersection.height().toLong()
        val smallerArea = minOf(
            before.width().toLong() * before.height().toLong(),
            after.width().toLong() * after.height().toLong(),
        ).coerceAtLeast(1L)
        return intersectionArea.toFloat() / smallerArea >= 0.45f
    }

    private fun swipeRecentsHorizontally(
        generation: Long,
        bounds: Rect,
        direction: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        if (!isSessionActive(generation)) return
        val y = bounds.height() * 0.40f
        val path = Path().apply {
            if (direction == RECENTS_SEARCH_FORWARD) {
                moveTo(bounds.width() * 0.78f, y)
                lineTo(bounds.width() * 0.22f, y)
            } else {
                moveTo(bounds.width() * 0.22f, y)
                lineTo(bounds.width() * 0.78f, y)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, RECENTS_SEARCH_DURATION_MS))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onFinished(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = onFinished(false)
            },
            handler,
        )
        if (!accepted) onFinished(false)
    }

    private fun swipeRecentsVertically(
        generation: Long,
        bounds: Rect,
        direction: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        if (!isSessionActive(generation)) return
        val x = bounds.width() * 0.50f
        val path = Path().apply {
            if (direction == RECENTS_SEARCH_FORWARD) {
                moveTo(x, bounds.height() * 0.72f)
                lineTo(x, bounds.height() * 0.28f)
            } else {
                moveTo(x, bounds.height() * 0.28f)
                lineTo(x, bounds.height() * 0.72f)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, RECENTS_SEARCH_DURATION_MS))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onFinished(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = onFinished(false)
            },
            handler,
        )
        if (!accepted) onFinished(false)
    }

    private fun swipeRecentsForSearch(
        generation: Long,
        bounds: Rect,
        direction: Int,
        onFinished: (Boolean) -> Unit,
    ) {
        if (usesHorizontalRecentsDismissal(bounds)) {
            swipeRecentsVertically(generation, bounds, direction, onFinished)
        } else {
            swipeRecentsHorizontally(generation, bounds, direction, onFinished)
        }
    }

    private fun recentsSearchLabel(bounds: Rect, direction: Int, index: Int, limit: Int): String {
        val directionLabel = if (usesHorizontalRecentsDismissal(bounds)) {
            if (direction == RECENTS_SEARCH_FORWARD) "下側" else "上側"
        } else {
            if (direction == RECENTS_SEARCH_FORWARD) "右方向" else "左方向"
        }
        return "カードを${directionLabel}へ探索\n$index/$limit"
    }

    private fun clearAllRecentsAsFallback(
        generation: Long,
        bounds: Rect,
        onFinished: (Boolean) -> Unit,
    ) {
        if (!isSessionActive(generation)) return
        val clearNode = findRecentsClearAllNode(bounds)
        if (clearNode == null) {
            onFinished(false)
            return
        }
        var clicked = false
        var current: AccessibilityNodeInfo? = clearNode
        repeat(MAX_CLICKABLE_PARENT_DEPTH) {
            val candidate = current ?: return@repeat
            if (!clicked && candidate.isClickable) {
                clicked = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = candidate.parent
        }
        if (!clicked) clicked = clearNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) {
            onFinished(false)
            return
        }
        AutomationRuntime.markWaiting(this, "カードタスクキル\n全カード消去で再確認")
        handler.postDelayed({
            if (isSessionActive(generation)) {
                onFinished(findTargetRecentsCardBounds(bounds) == null)
            }
        }, RECENTS_AFTER_DISMISS_MS)
    }

    private fun waitForRecentsSurface(generation: Long, onFinished: (Boolean) -> Unit) {
        val deadline = android.os.SystemClock.elapsedRealtime() + RECENTS_READY_TIMEOUT_MS
        val check = object : Runnable {
            override fun run() {
                if (!isSessionActive(generation)) return
                val rootPackage = rootInActiveWindow?.packageName?.toString()
                if (rootPackage != null && rootPackage !in AutomationConfig.TARGET_PACKAGES) {
                    onFinished(true)
                } else if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                    onFinished(false)
                } else {
                    handler.postDelayed(this, FOREGROUND_CHECK_INTERVAL_MS)
                }
            }
        }
        handler.postDelayed(check, RECENTS_SETTLE_MS)
    }

    private fun findTargetRecentsCardBounds(screen: Rect): Rect? {
        val activePackage = rootInActiveWindow?.packageName?.toString() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        windows.mapNotNullTo(queue) { window ->
            window.root?.takeIf { it.packageName?.toString() == activePackage }
        }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited += 1
            val labelBounds = Rect().also(node::getBoundsInScreen)
            val visibleOnScreen = node.isVisibleToUser &&
                !labelBounds.isEmpty &&
                screen.contains(labelBounds.centerX(), labelBounds.centerY())
            val label = buildString {
                append(node.text?.toString().orEmpty())
                append(' ')
                append(node.contentDescription?.toString().orEmpty())
            }
            if (visibleOnScreen && TARGET_CARD_LABELS.any { label.contains(it, ignoreCase = true) }) {
                var current: AccessibilityNodeInfo? = node
                var best: Rect? = null
                repeat(MAX_CARD_PARENT_DEPTH) {
                    val candidate = current ?: return@repeat
                    val rect = Rect().also(candidate::getBoundsInScreen)
                    val usable = rect.width() >= screen.width() * 0.25f &&
                        rect.height() >= screen.height() * 0.12f &&
                        rect.width() < screen.width() * 0.98f &&
                        rect.height() < screen.height() * 0.95f
                    if (usable && best == null) {
                        best = Rect(rect)
                    }
                    current = candidate.parent
                }
                // A launcher icon can carry exactly the same accessible label as
                // a recents card. Only a card-sized ancestor is a valid match.
                best?.let { return it }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return null
    }

    private fun openTargetUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val candidates = listOf(activeConfig.targetPackage) + AutomationConfig.TARGET_PACKAGES
        candidates.distinct().forEach { packageName ->
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (packageManager.resolveActivity(intent, 0) != null) {
                return runCatching { startActivity(intent); true }.getOrDefault(false)
            }
        }
        return false
    }

    private fun detectFastContent(): String? {
        val root = rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        val visibleLabels = ArrayList<String>()
        val screenBounds = Rect().also(root::getBoundsInScreen).takeUnless { it.isEmpty }
            ?: currentScreenBounds()
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited += 1
            val nodeBounds = Rect().also(node::getBoundsInScreen)
            val centerIsOnScreen = !nodeBounds.isEmpty &&
                screenBounds.contains(nodeBounds.centerX(), nodeBounds.centerY())
            if (node.isVisibleToUser && centerIsOnScreen) {
                node.text?.toString()?.takeIf(String::isNotBlank)?.let(visibleLabels::add)
                node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(visibleLabels::add)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return ContentClassifier.classify(visibleLabels)
    }

    private fun schedulePopupCheck() {
        if (popupCheckScheduled || popupDismissInProgress || !slidePhaseActive) return
        val now = android.os.SystemClock.elapsedRealtime()
        val throttleRemaining = (POPUP_SCAN_THROTTLE_MS - (now - lastPopupScanAtMs)).coerceAtLeast(0L)
        popupCheckScheduled = true
        handler.postDelayed(popupCheckRunnable, maxOf(POPUP_EVENT_DEBOUNCE_MS, throttleRemaining))
    }

    private data class PopupCloseCandidate(
        val node: AccessibilityNodeInfo,
        val bounds: Rect,
        val priority: Int,
    )

    private fun dismissBlockingPopupIfPresent(generation: Long): Boolean {
        if (
            !isSessionActive(generation) ||
            !slidePhaseActive ||
            restartInProgress ||
            popupDismissInProgress ||
            !activeConfig.autoDismissPopups ||
            !isTargetForeground()
        ) return false

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPopupDismissAtMs < POPUP_DISMISS_COOLDOWN_MS) return false
        val candidate = findPopupCloseCandidate() ?: return false
        popupDismissInProgress = true

        var clickable: AccessibilityNodeInfo? = candidate.node
        repeat(MAX_CLICKABLE_PARENT_DEPTH + 1) {
            val node = clickable ?: return@repeat
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                onPopupDismissed(generation)
                return true
            }
            clickable = node.parent
        }

        dispatchTap(candidate.bounds.centerX().toFloat(), candidate.bounds.centerY().toFloat()) { succeeded ->
            if (!isSessionActive(generation)) return@dispatchTap
            if (succeeded) {
                onPopupDismissed(generation)
            } else {
                popupDismissInProgress = false
            }
        }
        return true
    }

    private fun findPopupCloseCandidate(): PopupCloseCandidate? {
        val root = rootInActiveWindow ?: return null
        if (root.packageName?.toString() !in AutomationConfig.TARGET_PACKAGES) return null
        val screen = currentScreenBounds()
        if (screen.isEmpty) return null
        val minY = screen.top + (screen.height() * POPUP_MIN_Y_RATIO).toInt()
        val maxY = screen.top + (screen.height() * POPUP_MAX_Y_RATIO).toInt()
        val maxWidth = screen.width() * POPUP_MAX_SIZE_RATIO
        val maxHeight = screen.height() * POPUP_MAX_SIZE_RATIO
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        var best: PopupCloseCandidate? = null
        while (queue.isNotEmpty() && visited < MAX_NODES_TO_SCAN) {
            val node = queue.removeFirst()
            visited += 1
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                val onScreen = !bounds.isEmpty && screen.contains(centerX, centerY)
                val safeSize = bounds.width() in POPUP_MIN_SIZE_PX..maxWidth.toInt() &&
                    bounds.height() in POPUP_MIN_SIZE_PX..maxHeight.toInt()
                if (onScreen && safeSize && centerY in minY..maxY) {
                    val score = PopupCloseClassifier.score(
                        node.text,
                        node.contentDescription,
                        node.viewIdResourceName,
                    )
                    if (score > 0) {
                        val priority = score + if (node.isClickable) 10 else 0
                        if (best == null || priority > requireNotNull(best).priority) {
                            best = PopupCloseCandidate(node, Rect(bounds), priority)
                        }
                    }
                }
            }
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return best
    }

    private fun dispatchTap(x: Float, y: Float, result: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, POPUP_TAP_DURATION_MS))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = result(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = result(false)
            },
            handler,
        )
        if (!accepted) result(false)
    }

    private fun onPopupDismissed(generation: Long) {
        if (!isSessionActive(generation)) return
        lastPopupDismissAtMs = android.os.SystemClock.elapsedRealtime()
        popupCheckScheduled = false
        pendingSwipeReason = null
        pendingSwipePageToken = -1L
        contentPageToken += 1L
        handler.removeCallbacks(swipeRunnable)
        handler.removeCallbacks(popupCheckRunnable)
        AutomationRuntime.markWaiting(this, "ポップアップ解除\n×を閉じました")
        handler.postDelayed({
            if (isSessionActive(generation) && slidePhaseActive && !restartInProgress) {
                popupDismissInProgress = false
                beginContentTiming(generation)
            }
        }, POPUP_AFTER_DISMISS_MS)
    }

    private fun beginContentTiming(generation: Long, phaseLabel: String? = null) {
        if (!isSessionActive(generation) || restartInProgress) return
        pendingSwipeReason = null
        pendingSwipePageToken = -1L
        classificationCandidate = null
        classificationMatchCount = 0
        handler.removeCallbacks(swipeRunnable)
        contentVisibleSinceMs = android.os.SystemClock.elapsedRealtime()
        val pageToken = ++contentPageToken
        val regularDelay = nextRegularSwipeDelayMs(activeConfig)
        lastSwipeDelayMs = regularDelay
        pendingSwipePageToken = pageToken
        AutomationRuntime.markWaiting(
            this,
            phaseLabel?.let { "$it\n通常画面 ${regularDelay / 1_000L}秒待機" }
                ?: "通常画面\n${regularDelay / 1_000L}秒待機",
        )
        handler.postDelayed(swipeRunnable, regularDelay)
        if (!activeConfig.fastContentEnabled) return
        handler.postDelayed(
            { evaluateContentAndScheduleSwipe(generation, pageToken) },
            activeConfig.pageSettleMs,
        )
    }

    private fun evaluateContentAndScheduleSwipe(generation: Long, pageToken: Long) {
        if (!isCurrentContentPage(generation, pageToken)) return
        if (dismissBlockingPopupIfPresent(generation)) return
        val accessibilitySignal = detectFastContent()
        if (accessibilitySignal != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            handleClassificationResult(accessibilitySignal, generation, pageToken)
            return
        }
        runCatching { takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = runCatching {
                        Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }.getOrNull()
                    hardwareBuffer.close()
                    if (bitmap == null) {
                        if (isCurrentContentPage(generation, pageToken) && isTargetForeground()) {
                            handleClassificationResult(null, generation, pageToken)
                        }
                        return
                    }
                    runCatching {
                        gaugeExecutor.execute {
                            var analysisBitmap: Bitmap? = null
                            var gaugeFound: Boolean? = null
                            var pageDotsFound = false
                            try {
                                analysisBitmap = if (bitmap.width > GAUGE_ANALYSIS_WIDTH) {
                                    Bitmap.createScaledBitmap(
                                        bitmap,
                                        GAUGE_ANALYSIS_WIDTH,
                                        (bitmap.height * GAUGE_ANALYSIS_WIDTH.toFloat() / bitmap.width).toInt(),
                                        true,
                                    )
                                } else bitmap
                                val inspectedBitmap = requireNotNull(analysisBitmap)
                                pageDotsFound = hasCenteredPageDots(inspectedBitmap)
                                gaugeFound = hasCircleGauge(inspectedBitmap)
                            } catch (_: Throwable) {
                                gaugeFound = null
                            } finally {
                                if (analysisBitmap !== bitmap) runCatching { analysisBitmap?.recycle() }
                                runCatching { bitmap.recycle() }
                            }
                            handler.post {
                                if (isCurrentContentPage(generation, pageToken) && isTargetForeground()) {
                                    handleClassificationResult(
                                        when {
                                            pageDotsFound -> ContentClassifier.PAGE_DOTS
                                            gaugeFound == false -> ContentClassifier.NO_GAUGE
                                            else -> null
                                        },
                                        generation,
                                        pageToken,
                                    )
                                }
                            }
                        }
                    }.onFailure {
                        bitmap.recycle()
                        if (isCurrentContentPage(generation, pageToken) && isTargetForeground()) {
                            handleClassificationResult(null, generation, pageToken)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (isCurrentContentPage(generation, pageToken) && isTargetForeground()) {
                        handleClassificationResult(null, generation, pageToken)
                    }
                }
            },
        ) }.onFailure {
            if (isCurrentContentPage(generation, pageToken) && isTargetForeground()) {
                handleClassificationResult(null, generation, pageToken)
            }
        }
    }

    private fun handleClassificationResult(
        reason: String?,
        generation: Long,
        pageToken: Long,
    ) {
        if (!isCurrentContentPage(generation, pageToken) || !isTargetForeground()) return
        val requiredMatches = activeConfig.classificationConfirmationCount
        if (isForcedSwipeReason(reason)) {
            val confirmedReason = requireNotNull(reason)
            if (classificationCandidate == confirmedReason) {
                classificationMatchCount += 1
            } else {
                classificationCandidate = confirmedReason
                classificationMatchCount = 1
            }
            val label = classificationLabel(confirmedReason)
            if (classificationMatchCount >= requiredMatches) {
                scheduleConfirmedFastSwipe(confirmedReason, label, generation, pageToken)
                return
            }
            AutomationRuntime.markWaiting(
                this,
                "${label}を確認中\n$classificationMatchCount/$requiredMatches",
            )
        } else {
            classificationCandidate = null
            classificationMatchCount = 0
            markRegularWaiting()
        }

        val elapsed = android.os.SystemClock.elapsedRealtime() - contentVisibleSinceMs
        if (elapsed + CLASSIFICATION_RECHECK_MS < lastSwipeDelayMs) {
            handler.postDelayed(
                { evaluateContentAndScheduleSwipe(generation, pageToken) },
                CLASSIFICATION_RECHECK_MS,
            )
        }
    }

    private fun scheduleConfirmedFastSwipe(
        reason: String,
        label: String,
        generation: Long,
        pageToken: Long,
    ) {
        if (!isCurrentContentPage(generation, pageToken) || !isTargetForeground()) return
        val elapsed = android.os.SystemClock.elapsedRealtime() - contentVisibleSinceMs
        val regularRemaining = (lastSwipeDelayMs - elapsed).coerceAtLeast(MIN_SWIPE_SCHEDULE_DELAY_MS)
        val fastRemaining = (activeConfig.fastIntervalMs - elapsed).coerceAtLeast(MIN_SWIPE_SCHEDULE_DELAY_MS)
        if (fastRemaining >= regularRemaining) {
            pendingSwipeReason = null
            markRegularWaiting()
            return
        }
        pendingSwipeReason = reason
        pendingSwipePageToken = pageToken
        AutomationRuntime.markWaiting(
            this,
            "$label $classificationMatchCount/${activeConfig.classificationConfirmationCount}・確定\n" +
                "${(fastRemaining + 999L) / 1_000L}秒後にスライド",
        )
        handler.removeCallbacks(swipeRunnable)
        handler.postDelayed(swipeRunnable, fastRemaining)
    }

    private fun markRegularWaiting() {
        val elapsed = android.os.SystemClock.elapsedRealtime() - contentVisibleSinceMs
        val remaining = (lastSwipeDelayMs - elapsed).coerceAtLeast(0L)
        AutomationRuntime.markWaiting(
            this,
            "通常画面\n${(remaining + 999L) / 1_000L}秒待機",
        )
    }

    private fun classificationLabel(reason: String?): String = when (reason) {
        ContentClassifier.AD -> "広告"
        ContentClassifier.PHOTO -> "写真"
        ContentClassifier.TEXT_IMAGE -> "テキスト画像"
        ContentClassifier.PAGE_DOTS -> "ページ表示"
        ContentClassifier.NO_GAUGE -> "サークルゲージなし"
        else -> "高速対象"
    }

    private fun performConfiguredSwipe(fastReason: String?, generation: Long) {
        if (!isSessionActive(generation) || restartInProgress || !isTargetForeground()) return
        if (dismissBlockingPopupIfPresent(generation)) return
        val fastContent = isForcedSwipeReason(fastReason)
        val gestureDuration = if (fastContent) {
            activeConfig.fastGestureDurationMs
        } else {
            activeConfig.gestureDurationMs
        }
        val slideReason = when (fastReason) {
            ContentClassifier.AD -> "広告確認"
            ContentClassifier.PHOTO -> "写真確認"
            ContentClassifier.TEXT_IMAGE -> "テキスト画像確認"
            ContentClassifier.PAGE_DOTS -> "ページ表示確認"
            ContentClassifier.NO_GAUGE -> "サークルゲージなし"
            ContentClassifier.CLASSIFICATION_TIMEOUT -> "判定待機5秒"
            null -> "${(lastSwipeDelayMs / 1_000L).coerceAtLeast(0L)}秒経過"
            else -> fastReason
        }
        AutomationRuntime.markRunning(this, "スライド\n$slideReason")
        dispatchVerticalSwipe(activeConfig, gestureDuration) { succeeded ->
            if (!isSessionActive(generation)) return@dispatchVerticalSwipe
            if (succeeded) {
                gestureFailures = 0
                AutomationRuntime.incrementSwipe(this)
                beginContentTiming(generation)
            } else {
                gestureFailures += 1
                if (gestureFailures > activeConfig.actionRetryCount) {
                    stopAutomation("スライド操作に失敗しました")
                    return@dispatchVerticalSwipe
                }
                handler.postDelayed(swipeRunnable, GESTURE_RETRY_DELAY_MS)
            }
        }
    }

    private fun nextRegularSwipeDelayMs(config: AutomationConfig): Long =
        if (config.randomInterval) Random.nextInt(7, 10) * 1_000L else config.intervalMs

    private fun isForcedSwipeReason(reason: String?): Boolean = reason in setOf(
        ContentClassifier.AD,
        ContentClassifier.PHOTO,
        ContentClassifier.TEXT_IMAGE,
        ContentClassifier.PAGE_DOTS,
        ContentClassifier.NO_GAUGE,
        ContentClassifier.CLASSIFICATION_TIMEOUT,
    )

    private fun currentScreenBounds(): Rect {
        val windowManager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(windowManager.maximumWindowMetrics.bounds)
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.run { Rect(0, 0, widthPixels, heightPixels) }
        }
    }

    private fun isCurrentContentPage(generation: Long, pageToken: Long): Boolean =
        isSessionActive(generation) && !restartInProgress && pageToken == contentPageToken

    private fun hasCircleGauge(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 100 || height < 200) return true
        val xStart = (width * 0.02f).toInt()
        val xEnd = (width * 0.24f).toInt()
        val yStart = (height * 0.05f).toInt()
        val yEnd = (height * 0.34f).toInt()
        val step = (width / 120).coerceIn(6, 14)
        val radii = intArrayOf(
            (width * 0.018f).toInt(),
            (width * 0.024f).toInt(),
            (width * 0.032f).toInt(),
            (width * 0.042f).toInt(),
        ).filter { it >= 8 }
        for (centerY in yStart..yEnd step step) {
            for (centerX in xStart..xEnd step step) {
                for (radius in radii) {
                    var colorfulRingPoints = 0
                    for (sample in 0 until CIRCLE_SAMPLES) {
                        val angle = 2.0 * Math.PI * sample / CIRCLE_SAMPLES
                        val x = centerX + (kotlin.math.cos(angle) * radius).toInt()
                        val y = centerY + (kotlin.math.sin(angle) * radius).toInt()
                        if (x !in 0 until width || y !in 0 until height) continue
                        val pixel = bitmap.getPixel(x, y)
                        val red = Color.red(pixel)
                        val green = Color.green(pixel)
                        val blue = Color.blue(pixel)
                        val max = maxOf(red, green, blue)
                        val min = minOf(red, green, blue)
                        if (max >= 150 && max - min >= 55) colorfulRingPoints += 1
                    }
                    if (colorfulRingPoints >= MIN_CIRCLE_RING_POINTS) return true
                }
            }
        }
        return false
    }

    private data class DotCandidate(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Detects TikTok photo-page dots only inside the narrow lower-center region. */
    private fun hasCenteredPageDots(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 100 || height < 200) return false
        val left = (width * 0.37f).toInt()
        val right = (width * 0.63f).toInt()
        val top = (height * 0.60f).toInt()
        val bottom = (height * 0.80f).toInt()
        val roiWidth = right - left
        val roiHeight = bottom - top
        if (roiWidth <= 0 || roiHeight <= 0) return false

        val pixels = IntArray(roiWidth * roiHeight)
        bitmap.getPixels(pixels, 0, roiWidth, left, top, roiWidth, roiHeight)
        val bright = BooleanArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            val maximum = maxOf(red, green, blue)
            val minimum = minOf(red, green, blue)
            bright[index] = maximum >= 165 && maximum - minimum <= 45
        }

        val visited = BooleanArray(pixels.size)
        val candidates = ArrayList<DotCandidate>()
        val queue = ArrayDeque<Int>()
        for (start in pixels.indices) {
            if (!bright[start] || visited[start]) continue
            visited[start] = true
            queue.add(start)
            var minX = roiWidth
            var maxX = 0
            var minY = roiHeight
            var maxY = 0
            var area = 0
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val x = current % roiWidth
                val y = current / roiWidth
                minX = minOf(minX, x)
                maxX = maxOf(maxX, x)
                minY = minOf(minY, y)
                maxY = maxOf(maxY, y)
                area += 1
                for (offsetY in -1..1) {
                    for (offsetX in -1..1) {
                        if (offsetX == 0 && offsetY == 0) continue
                        val nextX = x + offsetX
                        val nextY = y + offsetY
                        if (nextX !in 0 until roiWidth || nextY !in 0 until roiHeight) continue
                        val next = nextY * roiWidth + nextX
                        if (bright[next] && !visited[next]) {
                            visited[next] = true
                            queue.add(next)
                        }
                    }
                }
            }
            val componentWidth = maxX - minX + 1
            val componentHeight = maxY - minY + 1
            val maxDotSize = (width * 0.030f).toInt().coerceAtLeast(8)
            val minDotSize = (width * 0.008f).toInt().coerceAtLeast(2)
            val fillRatio = area.toFloat() / (componentWidth * componentHeight).coerceAtLeast(1)
            val aspectRatio = componentWidth.toFloat() / componentHeight.coerceAtLeast(1)
            if (
                componentWidth in minDotSize..maxDotSize &&
                componentHeight in minDotSize..maxDotSize &&
                aspectRatio in 0.72f..1.38f &&
                fillRatio >= 0.48f
            ) {
                candidates += DotCandidate(
                    x = left + (minX + maxX) / 2,
                    y = top + (minY + maxY) / 2,
                    width = componentWidth,
                    height = componentHeight,
                )
            }
        }

        val maxRowDifference = (width * 0.014f).toInt().coerceAtLeast(4)
        val minSpacing = (width * 0.015f).toInt().coerceAtLeast(4)
        val maxSpacing = (width * 0.075f).toInt().coerceAtLeast(16)
        val centerMin = (width * 0.44f).toInt()
        val centerMax = (width * 0.56f).toInt()
        val sorted = candidates.sortedBy { it.x }
        for (firstIndex in sorted.indices) {
            var alignedCount = 1
            var previous = sorted[firstIndex]
            var groupMinX = previous.x
            var groupMaxX = previous.x
            for (nextIndex in firstIndex + 1 until sorted.size) {
                val next = sorted[nextIndex]
                val spacing = next.x - previous.x
                val similarWidth = maxOf(next.width, previous.width) <= minOf(next.width, previous.width) * 1.7f
                val similarHeight = maxOf(next.height, previous.height) <= minOf(next.height, previous.height) * 1.7f
                if (
                    kotlin.math.abs(next.y - previous.y) <= maxRowDifference &&
                    spacing in minSpacing..maxSpacing &&
                    similarWidth &&
                    similarHeight
                ) {
                    alignedCount += 1
                    previous = next
                    groupMaxX = next.x
                    val groupCenter = (groupMinX + groupMaxX) / 2
                    if (alignedCount >= 2 && groupCenter in centerMin..centerMax) return true
                }
            }
        }
        return false
    }

    private fun dispatchVerticalSwipe(
        config: AutomationConfig,
        durationMs: Long = config.gestureDurationMs,
        result: (Boolean) -> Unit,
    ) {
        val windowManager = getSystemService(WindowManager::class.java)
        val (width, height) = if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.run { widthPixels to heightPixels }
        }
        if (width <= 0 || height <= 0) {
            result(false)
            return
        }

        val path = Path().apply {
            moveTo(width * config.startXRatio, height * config.startYRatio)
            lineTo(width * config.endXRatio, height * config.endYRatio)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = result(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = result(false)
            },
            handler,
        )
        if (!accepted) result(false)
    }

    @Suppress("WakelockTimeout")
    private fun acquireWakeLock(durationMs: Long) {
        releaseWakeLock()
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ARUNO_CLICKER:automation").apply {
            setReferenceCounted(false)
            if (durationMs <= 0L) acquire() else acquire(durationMs + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun isTargetForeground(): Boolean {
        val rootPackage = rootInActiveWindow?.packageName?.toString()
        if (rootPackage != null) return rootPackage in AutomationConfig.TARGET_PACKAGES
        val eventIsRecent = android.os.SystemClock.elapsedRealtime() - foregroundPackageUpdatedAt <= EVENT_FALLBACK_MAX_AGE_MS
        return eventIsRecent && foregroundPackage in AutomationConfig.TARGET_PACKAGES
    }

    private fun isSessionActive(generation: Long): Boolean =
        generation == automationGeneration && AutomationRuntime.snapshot().requested

    companion object {
        private const val RECENTS_SETTLE_MS = 900L
        private const val RECENTS_READY_TIMEOUT_MS = 3_000L
        private const val RECENTS_AFTER_DISMISS_MS = 600L
        private const val RECENTS_DISMISS_DURATION_MS = 280L
        private const val RECENTS_DISMISS_VERIFY_MS = 450L
        private const val RECENTS_RETRY_DELAY_MS = 300L
        private const val RECENTS_DISMISS_RETRY_COUNT = 2
        private const val RECENTS_DISMISS_UP = 0
        private const val RECENTS_DISMISS_LEFT = 1
        private const val RECENTS_DISMISS_RIGHT = 2
        private const val MAX_RECENTS_CARDS_TO_SEARCH = 8
        private const val MAX_TARGET_CARDS_TO_DISMISS = 8
        private const val RECENTS_SEARCH_DURATION_MS = 260L
        private const val RECENTS_SEARCH_SETTLE_MS = 350L
        private const val RECENTS_SEARCH_FORWARD = 1
        private const val RECENTS_SEARCH_REVERSE = -1
        private const val WARMUP_CYCLE_COUNT = 2
        private const val WARMUP_RESTART_INTERVAL_MS = 60_000L
        private const val PERIODIC_RESTART_INTERVAL_MS = 10L * 60L * 1_000L
        private const val TARGET_LAUNCH_TIMEOUT_MS = 10_000L
        private const val TARGET_FOREGROUND_STABLE_MS = 600L
        private const val FOREGROUND_CHECK_INTERVAL_MS = 200L
        private const val CHOOSER_CLICK_INTERVAL_MS = 1_000L
        private const val MAX_CHOOSER_CLICKS = 3
        private const val MAX_CLICKABLE_PARENT_DEPTH = 5
        private const val EVENT_FALLBACK_MAX_AGE_MS = 1_000L
        private const val MAX_CARD_PARENT_DEPTH = 7
        private const val MAX_NODES_TO_SCAN = 500
        private const val MIN_SWIPE_SCHEDULE_DELAY_MS = 100L
        private const val CLASSIFICATION_RECHECK_MS = 400L
        private const val GESTURE_RETRY_DELAY_MS = 500L
        private const val POPUP_EVENT_DEBOUNCE_MS = 180L
        private const val POPUP_SCAN_THROTTLE_MS = 500L
        private const val POPUP_DISMISS_COOLDOWN_MS = 900L
        private const val POPUP_AFTER_DISMISS_MS = 650L
        private const val POPUP_TAP_DURATION_MS = 80L
        private const val POPUP_MIN_Y_RATIO = 0.03f
        private const val POPUP_MAX_Y_RATIO = 0.90f
        private const val POPUP_MAX_SIZE_RATIO = 0.25f
        private const val POPUP_MIN_SIZE_PX = 6
        private const val CIRCLE_SAMPLES = 24
        private const val MIN_CIRCLE_RING_POINTS = 12
        private const val GAUGE_ANALYSIS_WIDTH = 360
        private val TARGET_CARD_LABELS = listOf(
            "TikTok-Lite",
            "TikTok-Lit",
            "TikTok Lite",
            "TikTokLite",
            "TikTokライト",
        )
        private val RECENTS_CLEAR_LABELS = setOf("×", "✕", "✖", "x")
        private val RECENTS_CLEAR_WORD_HINTS = listOf(
            "すべて消去",
            "すべて閉じる",
            "全て消去",
            "クリア",
            "clear all",
            "close all",
        )
        private val RECENTS_CLEAR_RESOURCE_HINTS = listOf(
            "clear_all",
            "clearall",
            "clean_all",
            "cleanall",
            "dismiss_all",
            "clear_anim",
            "clearanimview",
        )

        @Volatile var instance: ArunoAccessibilityService? = null
            private set
    }
}
