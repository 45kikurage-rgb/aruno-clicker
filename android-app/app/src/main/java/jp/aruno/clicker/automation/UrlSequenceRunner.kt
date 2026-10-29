package jp.aruno.clicker.automation

import jp.aruno.clicker.data.LoopMode
import jp.aruno.clicker.data.DailyVideoViews
import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.data.UrlRunnerLimits

/**
 * URL順次展開の実行器（純粋な状態遷移コア）。
 *
 * Android API には依存せず、「開く」「前面確認」「遅延」はインターフェース経由にする。
 * Android 側の実装（ArunoAccessibilityService 内のグルー）が、既存の
 * openTargetUrl（ACTION_VIEW＋setPackage＋resolve確認）と前面確認ポーリングを差し込む。
 *
 * このコアはURLの切替と待機を管理する。×の解除はAndroid側で独立して行う。
 * それらは既存の初回／スライド経路側にだけ残る。
 *
 * 停止の保証（既存の世代管理パターンの踏襲）:
 * - 開始ごとに世代トークンを進め、非同期コールバックは世代一致を確認してから進む。
 * - [stop] は世代を進め、予約済みの遅延処理をすべて取り消す。
 *   よって停止後に遅延処理が次のURLを開くことはない。
 *
 * 成功の区別:
 * - 成功扱いにするのは「URLを開き、対象アプリの前面表示を確認した」まで。
 * - 動画が再生されたかどうかは確認しない（状態表示でも未確認と明記する）。
 * - 起動失敗・前面未確認はスキップとして記録し、後続URLへ進む。成功とは扱わない。
 * - 画面OFF/ロック/ユーザー停止/サービス切断は従来どおり即時停止する。
 */

/** URLを開く処理。開く要求が受理されたら true、起動失敗は false。 */
fun interface UrlOpener {
    fun open(url: String): Boolean
}

/** 対象アプリが前面に安定表示されたかを確認する処理。結果はコールバックで返す。 */
interface ForegroundChecker {
    fun awaitForeground(generation: Long, callback: (Boolean) -> Unit)
}

/** 予約した遅延処理のハンドル。 */
fun interface ScheduledTask {
    fun cancel()
}

/** 遅延実行の抽象（Android 側は Handler、テストではフェイクに差し替える）。 */
interface DelayScheduler {
    fun schedule(delayMs: Long, task: () -> Unit): ScheduledTask
    fun cancelAll()
}

/** 時刻の抽象（待機計測の起点をテスト可能にする）。 */
fun interface UrlRunnerClock {
    fun nowMs(): Long
}

/**
 * Launch TikTok Lite from its normal home entrypoint before recovering a
 * failed URL. This is an Activity-task reset only, not app-data deletion.
 */
fun interface UrlRunnerSplashLauncher {
    fun launch(): Boolean
}

enum class UrlRunnerPhase {
    IDLE,
    OPENING,
    WAITING_FOREGROUND,
    PREPARING_ROUTE,
    HOLDING,
    PAUSED,
    SKIPPED,
    RECOVERING,
    FINISHED,
    STOPPED,
    FAILED,
}

/** 画面・フロート・通知へ出す実行状態。 */
data class UrlRunnerStatus(
    val phase: UrlRunnerPhase,
    /** 処理中のURLの0始まり位置。対象外は -1。 */
    val index: Int,
    val total: Int,
    val url: String,
    /** 現在のURLの待機秒数（個別／共通の解決結果）。 */
    val waitTotalSeconds: Int,
    /** 前面確認後からの経過秒数。 */
    val waitElapsedSeconds: Int,
    val message: String,
    /** Next non-blank slot in this run; null if the current URL is the last. */
    val nextIndex: Int? = null,
)

/** 状態の受け口（Android 側は AutomationRuntime へ流す）。 */
fun interface UrlRunnerStatusSink {
    fun onStatus(status: UrlRunnerStatus)
}

sealed interface UrlRunnerStartResult {
    data object Started : UrlRunnerStartResult
    data object AlreadyRunning : UrlRunnerStartResult
    data class Invalid(val messages: List<String>) : UrlRunnerStartResult
}

class UrlSequenceRunnerCore(
    private val opener: UrlOpener,
    private val foregroundChecker: ForegroundChecker,
    private val scheduler: DelayScheduler,
    private val clock: UrlRunnerClock,
    private val statusSink: UrlRunnerStatusSink,
    private val splashLauncher: UrlRunnerSplashLauncher = UrlRunnerSplashLauncher { false },
    /** 0=off, 1=every URL, 2..5=every N completed URL positions. Fixed per run. */
    private var splashEveryLinks: Int = 0,
    private val loopMode: LoopMode? = null,
    private val canContinue: () -> Boolean = { true },
    private val dailyViews: DailyVideoViews? = null,
    private val dailyLimit: Int = 2,
    private val routePreparer: UrlRoutePreparer? = null,
    private val dailyLimitStartIndex: Int = 5,
    private val holdBlocked: () -> Boolean = { false },
) {
    /** 世代トークン。開始・停止のたびに進む。 */
    var generation: Long = 0L
        private set

    var phase: UrlRunnerPhase = UrlRunnerPhase.IDLE
        private set

    /** 実行開始時に固定したスナップショット。実行中に外部から差し替わらない。 */
    private var snapshot: UrlRunnerConfig? = null
    private var index: Int = -1
    private var active: Boolean = false
    private var openRequestedAtMs = 0L
    private var skippedFailureCount = 0
    private var consecutiveFailures = 0
    private var completedLinksSinceSplash = 0
    private var visitToken = 0L
    private var currentDailyCount = 0
    private val tasks = PausableScheduler(scheduler, clock)
    private var pausedPhase = UrlRunnerPhase.IDLE
    private var pausedAt = 0L
    private var pausedTime = 0L
    private var paused = false
    private var lastStatus: UrlRunnerStatus? = null
    private var holdBlockedMs = 0L
    private var lastHoldTickMs = 0L
    private var holdWasBlocked = false
    private var hasOpenedUrl = false
    private var preparationToken = 0L

    fun isPaused(): Boolean = paused

    /** Apply user-edited splash controls at the next URL boundary. */
    fun updateSplashFrequency(value: Int) {
        if (value !in 0..5) return
        splashEveryLinks = value
        completedLinksSinceSplash = 0
    }

    fun togglePause() {
        if (!active) return
        if (paused) {
            if (!checkCanContinue(generation)) return
            pausedTime += clock.nowMs() - pausedAt
            paused = false
            phase = pausedPhase
            // Previous foreground callbacks are invalid. Start a fresh bounded
            // check without reopening or charging an already-started hold.
            if (phase == UrlRunnerPhase.WAITING_FOREGROUND) awaitCurrentForeground(generation, ++visitToken)
            else {
                lastStatus?.let { report(phase, index, it.waitTotalSeconds, it.waitElapsedSeconds, "再開しました") }
            }
            tasks.resume()
        } else {
            pausedAt = clock.nowMs()
            pausedPhase = phase
            paused = true
            visitToken += 1
            tasks.pause()
            phase = UrlRunnerPhase.PAUSED
            val previous = lastStatus
            report(phase, index, previous?.waitTotalSeconds ?: 0, previous?.waitElapsedSeconds ?: 0, "一時停止中")
        }
    }

    private fun nowMs(): Long = clock.nowMs() - pausedTime -
        (if (paused) clock.nowMs() - pausedAt else 0L)

    fun isRunning(): Boolean = active

    /**
     * 実行を開始する。二重開始は [UrlRunnerStartResult.AlreadyRunning] で拒否する。
     * URL未登録・不正秒数など検証に失敗した場合は開始しない。
     */
    fun start(config: UrlRunnerConfig): UrlRunnerStartResult {
        if (active) return UrlRunnerStartResult.AlreadyRunning
        val issues = config.validate()
        if (dailyViews != null && dailyLimit !in 2..3) {
            return UrlRunnerStartResult.Invalid(listOf("当日の上限は2回または3回にしてください"))
        }
        if (issues.isNotEmpty()) {
            return UrlRunnerStartResult.Invalid(issues.map { it.message })
        }
        snapshot = config.snapshot()
        index = config.startIndex
        active = true
        skippedFailureCount = 0
        consecutiveFailures = 0
        completedLinksSinceSplash = 0
        paused = false
        pausedTime = 0L
        hasOpenedUrl = false
        tasks.resume()
        generation += 1L
        openCurrent(generation)
        return UrlRunnerStartResult.Started
    }

    /** 停止する。以後の遅延処理・コールバックは世代不一致で無効になる。 */
    fun stop(reason: String = "停止しました") {
        if (!active) return
        active = false
        generation += 1L
        tasks.cancelAll()
        paused = false
        phase = UrlRunnerPhase.STOPPED
        report(phase, index, waitTotalSeconds = 0, waitElapsedSeconds = 0, message = reason)
    }

    private fun openCurrent(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        // A bounded scan avoids recursion/spinning when every URL is capped.
        var scanned = 0
        while (true) {
            if (!checkCanContinue(gen)) return
            if (index >= config.urls.size) {
                if ((loopMode ?: config.loopMode) != LoopMode.LOOP) {
                    finish("全${config.urls.size}枠の処理を完了しました（起動・表示失敗${skippedFailureCount}件をスキップ・動画の再生は未確認）")
                    return
                }
                if (dailyViews != null && config.urls.indices.any { it >= dailyLimitStartIndex && config.urls[it].isNotBlank() } &&
                    config.urls.indices.none { it >= dailyLimitStartIndex && isEligible(it) }) {
                    finish("本日の全動画が上限${dailyLimit}回に達したため終了しました。1〜5番は回数制限の対象外です")
                    return
                }
                index = 0
            }
            if (scanned >= config.urls.size) {
                finish("本日の全動画が上限${dailyLimit}回に達したため終了しました。次の日は開始操作で再開できます")
                return
            }
            val candidate = config.urls[index]
            val count = try { if (candidate.isBlank() || index < dailyLimitStartIndex) 0 else dailyViews?.count(candidate) ?: 0 }
                catch (_: Exception) { fail(gen, "当日の回数を読み取れないため停止しました"); return }
            if (candidate.isNotBlank() && (dailyViews == null || count < dailyLimit)) break
            report(UrlRunnerPhase.OPENING, index, 0, 0,
                if (candidate.isBlank()) "${index + 1}番は空欄のためスキップ"
                else "${index + 1}番は本日${count}/${dailyLimit}回のためスキップ")
            index += 1
            scanned += 1
        }
        val preparer = routePreparer
        if (preparer != null && hasOpenedUrl) {
            val preparation = ++preparationToken
            phase = UrlRunnerPhase.PREPARING_ROUTE
            report(phase, index, 0, 0, "次のURL前にギフト→ホームを確認しています")
            tasks.schedule(0) {
                if (!checkCanContinue(gen)) return@schedule
                runCatching {
                    preparer.prepare(tasks, { checkCanContinue(gen) }) { ready ->
                        tasks.schedule(0) prepared@{
                            if (!checkCanContinue(gen) || preparation != preparationToken || phase != UrlRunnerPhase.PREPARING_ROUTE) return@prepared
                            if (!ready) fail(gen, "ギフト→ホームを確認できないため停止しました。フロートの位置や切替方法を確認してください")
                            else launchCurrent(gen)
                        }
                    }
                }.onFailure { fail(gen, "ギフト→ホームの操作に失敗したため停止しました") }
            }
        } else launchCurrent(gen)
    }

    private fun launchCurrent(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        val url = config.urls[index]
        val token = ++visitToken
        currentDailyCount = 0
        phase = UrlRunnerPhase.OPENING
        report(
            phase, index,
            waitTotalSeconds = config.secondsFor(index), waitElapsedSeconds = 0,
            message = "${index + 1}番目のURLを開いています",
        )
        openRequestedAtMs = nowMs()
        val opened = runCatching { opener.open(url) }.getOrDefault(false)
        if (!isCurrent(gen)) return
        if (!opened) {
            skipFailedUrl(gen, "起動失敗")
            return
        }
        hasOpenedUrl = true
        phase = UrlRunnerPhase.WAITING_FOREGROUND
        report(
            phase, index,
            waitTotalSeconds = config.secondsFor(index), waitElapsedSeconds = 0,
            message = "${index + 1}番目のURLを開きました。表示を確認しています",
        )
        awaitCurrentForeground(gen, token)
    }

    private fun awaitCurrentForeground(gen: Long, token: Long) {
        foregroundChecker.awaitForeground(gen) { ready ->
            if (!isCurrent(gen) || paused || token != visitToken || phase != UrlRunnerPhase.WAITING_FOREGROUND) return@awaitForeground
            if (!ready) {
                skipFailedUrl(gen, "10秒以内に画面を確認できませんでした")
                return@awaitForeground
            }
            beginHold(gen)
        }
    }

    /**
     * 待機の計測は「対象アプリの前面確認が取れた時点」から始める（推奨案）。
     * 起動要求時点から測ると、起動の遅さで視聴時間が削られるため。
     */
    private fun beginHold(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        val history = dailyViews
        if (history != null && index >= dailyLimitStartIndex) {
            val recorded = runCatching { history.recordStart(config.urls[index], dailyLimit) }.getOrDefault(false)
            if (!recorded) {
                fail(gen, "当日の回数を保存できないか上限に達したため停止しました")
                return
            }
            currentDailyCount = runCatching { history.count(config.urls[index]) }.getOrDefault(0)
        }
        val totalSeconds = config.secondsFor(index)
        val startedAtMs = if (config.waitFromForeground) nowMs() else openRequestedAtMs
        consecutiveFailures = 0
        phase = UrlRunnerPhase.HOLDING
        holdBlockedMs = 0L
        lastHoldTickMs = nowMs()
        holdWasBlocked = false
        reportHold(gen, startedAtMs, totalSeconds)
    }

    private fun reportHold(gen: Long, startedAtMs: Long, totalSeconds: Int) {
        if (!checkCanContinue(gen)) return
        val tickNow = nowMs()
        val blocked = runCatching { holdBlocked() }.getOrDefault(false)
        if (!isCurrent(gen) || paused) return
        if (blocked || holdWasBlocked) holdBlockedMs += (tickNow - lastHoldTickMs).coerceAtLeast(0L)
        lastHoldTickMs = tickNow
        holdWasBlocked = blocked
        val elapsedSeconds = ((tickNow - startedAtMs - holdBlockedMs) / 1_000L)
            .coerceIn(0L, totalSeconds.toLong())
            .toInt()
        report(
            UrlRunnerPhase.HOLDING, index,
            waitTotalSeconds = totalSeconds,
            waitElapsedSeconds = elapsedSeconds,
            message = "${index + 1}番目のURLを開きました（動画の再生は未確認）" +
                (if (dailyViews != null && index >= dailyLimitStartIndex) " 本日${currentDailyCount}/${dailyLimit}回（途中停止も含む）" else "") +
                " ${elapsedSeconds}秒経過 / あと${totalSeconds - elapsedSeconds}秒",
        )
        if (elapsedSeconds >= totalSeconds) {
            advance(gen)
            return
        }
        tasks.schedule(TICK_MS) { reportHold(gen, startedAtMs, totalSeconds) }
    }

    private fun advance(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        completedLinksSinceSplash += 1
        if (splashEveryLinks !in 1..5 || completedLinksSinceSplash < splashEveryLinks) {
            index += 1
            openCurrent(gen)
            return
        }
        // First populated slot (normally #6) opens directly. Following slots
        // need a fresh normal TikTok Lite launch before their deep links.
        // Do not splash for an empty slot or after the final STOP-mode URL.
        val next = ((index + 1) until config.urls.size)
            .firstOrNull { isEligible(it) }
            ?: if ((loopMode ?: config.loopMode) == LoopMode.LOOP) {
                config.urls.indices.firstOrNull { isEligible(it) }
            } else null
        if (next == null) {
            index += 1
            openCurrent(gen)
            return
        }
        completedLinksSinceSplash = 0
        phase = UrlRunnerPhase.RECOVERING
        report(phase, index, 0, 0, "${next + 1}番のURL前にTikTok Liteを起動し直しています（${splashEveryLinks}件ごと）")
        val launched = runCatching { splashLauncher.launch() }.getOrDefault(false)
        if (!checkCanContinue(gen)) return
        if (!launched) {
            report(phase, index, 0, 0, "通常起動に失敗しました。次のURLを試します")
        }
        tasks.schedule(SPLASH_SETTLE_DELAY_MS) nextUrl@{
            if (!checkCanContinue(gen)) return@nextUrl
            index += 1
            openCurrent(gen)
        }
    }

    /**
     * URL opening/foreground failures are not evidence of video playback.
     * Record and skip the failed link rather than terminating all 200 slots.
     * Before the *next* nonblank URL, launch TikTok Lite's ordinary app entry
     * (splash/home) and give it time to settle. No app data is cleared.
     * User-stop, screen-off, lock and accessibility disconnect still stop.
     */
    private fun skipFailedUrl(gen: Long, reason: String) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        skippedFailureCount += 1
        consecutiveFailures += 1
        phase = UrlRunnerPhase.SKIPPED
        val nonBlankCount = config.urls.indices.count { isEligible(it) }.coerceAtLeast(1)
        val noMoreInCycle = (loopMode ?: config.loopMode) == LoopMode.LOOP &&
            consecutiveFailures >= nonBlankCount
        val hasNextUrl = ((index + 1) until config.urls.size)
            .any { isEligible(it) } ||
            ((loopMode ?: config.loopMode) == LoopMode.LOOP && config.urls.indices.any { isEligible(it) })

        report(
            UrlRunnerPhase.SKIPPED, index, 0, 0,
            "${index + 1}番：$reason。失敗として記録" +
                when {
                    noMoreInCycle -> "（全リンク失敗、連続再試行は中止）"
                    hasNextUrl -> "、TikTok Liteを起動し直して次へ"
                    else -> "、次のURLなし"
                },
        )
        if (!isCurrent(gen)) return
        if (noMoreInCycle) {
            finish("全${nonBlankCount}件のリンクで表示確認できませんでした（失敗${skippedFailureCount}件・安全停止）")
            return
        }
        if (!hasNextUrl) {
            finish("全${config.urls.size}枠の処理を完了しました（起動・表示失敗${skippedFailureCount}件をスキップ・動画の再生は未確認）")
            return
        }

        // Failure already requires splash recovery. Restart the normal
        // frequency counter so the next URL doesn't get two splash launches.
        completedLinksSinceSplash = 0

        // Keep a short minimum gap before attempting to start a new Activity.
        // The continuation is cancelled by stop() or any safety condition.
        tasks.schedule(FAILED_LINK_SKIP_DELAY_MS) {
            if (!checkCanContinue(gen)) return@schedule
            val splashStarted = runCatching { splashLauncher.launch() }.getOrDefault(false)
            if (!checkCanContinue(gen)) return@schedule
            phase = UrlRunnerPhase.RECOVERING
            report(
                phase, index, 0, 0,
                if (splashStarted) {
                    "TikTok Liteを通常起動しました。次のURLを準備しています"
                } else {
                    "TikTok Liteの通常起動に失敗。次のURLを試します"
                },
            )
            tasks.schedule(SPLASH_SETTLE_DELAY_MS) next@{
                if (!checkCanContinue(gen)) return@next
                index += 1
                openCurrent(gen)
            }
        }
    }

    private fun finish(message: String) {
        active = false
        phase = UrlRunnerPhase.FINISHED
        report(phase, (snapshot?.urls?.size ?: 1) - 1, 0, 0, message)
    }

    private fun fail(gen: Long, reason: String) {
        if (!isCurrent(gen)) return
        active = false
        generation += 1L
        tasks.cancelAll()
        paused = false
        phase = UrlRunnerPhase.FAILED
        report(phase, index, waitTotalSeconds = 0, waitElapsedSeconds = 0, message = reason)
    }

    private fun checkCanContinue(gen: Long): Boolean {
        if (!isCurrent(gen)) return false
        if (!runCatching { canContinue() }.getOrDefault(false)) {
            fail(gen, "画面ロック・画面OFF・サービス切断のため停止しました")
            return false
        }
        return true
    }

    private fun isCurrent(gen: Long): Boolean = active && gen == generation

    private fun isEligible(slot: Int): Boolean {
        val url = snapshot?.urls?.getOrNull(slot).orEmpty()
        return url.isNotBlank() && (slot < dailyLimitStartIndex ||
            (dailyViews?.let { history -> runCatching { history.count(url) < dailyLimit }.getOrDefault(false) } ?: true))
    }

    private fun report(
        phase: UrlRunnerPhase,
        index: Int,
        waitTotalSeconds: Int,
        waitElapsedSeconds: Int,
        message: String,
    ) {
        val config = snapshot
        val status = UrlRunnerStatus(
                phase = phase,
                index = index,
                total = config?.urls?.size ?: 0,
                url = config?.urls?.getOrNull(index) ?: "",
                waitTotalSeconds = waitTotalSeconds,
                waitElapsedSeconds = waitElapsedSeconds,
                message = message,
                nextIndex = config?.let { current ->
                    val next = ((index + 1) until current.urls.size)
                        .firstOrNull { isEligible(it) }
                    next ?: if ((loopMode ?: current.loopMode) == LoopMode.LOOP) {
                        current.urls.indices.firstOrNull { isEligible(it) }
                    } else null
                },
            )
        lastStatus = status
        statusSink.onStatus(status)
    }

    companion object {
        /** 待機中の状態更新間隔（既存 holdUrlWithCountdown の1秒刻み表示に合わせる）。 */
        const val TICK_MS = 200L
        const val FAILED_LINK_SKIP_DELAY_MS = 650L
        // Allow the app's normal launch/splash screen to settle before the
        // next deep link, without waiting indefinitely for a visual logo.
        const val SPLASH_SETTLE_DELAY_MS = 1_800L
    }
}


