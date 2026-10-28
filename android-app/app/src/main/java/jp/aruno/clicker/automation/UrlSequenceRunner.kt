package jp.aruno.clicker.automation

import jp.aruno.clicker.data.LoopMode
import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.data.UrlRunnerLimits

/**
 * URL順次展開の実行器（純粋な状態遷移コア）。
 *
 * Android API には依存せず、「開く」「前面確認」「遅延」はインターフェース経由にする。
 * Android 側の実装（ArunoAccessibilityService 内のグルー）が、既存の
 * openTargetUrl（ACTION_VIEW＋setPackage＋resolve確認）と前面確認ポーリングを差し込む。
 *
 * この実行経路では、×認識・広告判定・スワイプ・定期再起動・Recents削除は一切呼ばない。
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
    HOLDING,
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
    private val loopMode: LoopMode? = null,
    private val canContinue: () -> Boolean = { true },
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

    fun isRunning(): Boolean = active

    /**
     * 実行を開始する。二重開始は [UrlRunnerStartResult.AlreadyRunning] で拒否する。
     * URL未登録・不正秒数など検証に失敗した場合は開始しない。
     */
    fun start(config: UrlRunnerConfig): UrlRunnerStartResult {
        if (active) return UrlRunnerStartResult.AlreadyRunning
        val issues = config.validate()
        if (issues.isNotEmpty()) {
            return UrlRunnerStartResult.Invalid(issues.map { it.message })
        }
        snapshot = config.snapshot()
        index = config.startIndex
        active = true
        skippedFailureCount = 0
        consecutiveFailures = 0
        generation += 1L
        openCurrent(generation)
        return UrlRunnerStartResult.Started
    }

    /** 停止する。以後の遅延処理・コールバックは世代不一致で無効になる。 */
    fun stop(reason: String = "停止しました") {
        if (!active) return
        active = false
        generation += 1L
        scheduler.cancelAll()
        phase = UrlRunnerPhase.STOPPED
        report(phase, index, waitTotalSeconds = 0, waitElapsedSeconds = 0, message = reason)
    }

    private fun openCurrent(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        // Preserve numbered positions while skipping blank URLs without a delay.
        while (index < config.urls.size && config.urls[index].isBlank()) {
            report(UrlRunnerPhase.OPENING, index, 0, 0, "${index + 1}番は空欄のためスキップ")
            index += 1
        }
        if (index >= config.urls.size) {
            if ((loopMode ?: config.loopMode) == LoopMode.LOOP) {
                index = 0
                openCurrent(gen) // validate() guarantees at least one nonblank URL.
            } else {
                finish("全${config.urls.size}枠のURLを開きました（起動・表示失敗${skippedFailureCount}件をスキップ・動画の再生は未確認）")
            }
            return
        }
        val url = config.urls[index]
        phase = UrlRunnerPhase.OPENING
        report(
            phase, index,
            waitTotalSeconds = config.secondsFor(index), waitElapsedSeconds = 0,
            message = "${index + 1}番目のURLを開いています",
        )
        openRequestedAtMs = clock.nowMs()
        val opened = runCatching { opener.open(url) }.getOrDefault(false)
        if (!isCurrent(gen)) return
        if (!opened) {
            skipFailedUrl(gen, "起動失敗")
            return
        }
        phase = UrlRunnerPhase.WAITING_FOREGROUND
        report(
            phase, index,
            waitTotalSeconds = config.secondsFor(index), waitElapsedSeconds = 0,
            message = "${index + 1}番目のURLを開きました。表示を確認しています",
        )
        foregroundChecker.awaitForeground(gen) { ready ->
            if (!isCurrent(gen)) return@awaitForeground
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
        val totalSeconds = config.secondsFor(index)
        val startedAtMs = if (config.waitFromForeground) clock.nowMs() else openRequestedAtMs
        consecutiveFailures = 0
        phase = UrlRunnerPhase.HOLDING
        reportHold(gen, startedAtMs, totalSeconds)
    }

    private fun reportHold(gen: Long, startedAtMs: Long, totalSeconds: Int) {
        if (!checkCanContinue(gen)) return
        val elapsedSeconds = ((clock.nowMs() - startedAtMs) / 1_000L)
            .coerceIn(0L, totalSeconds.toLong())
            .toInt()
        report(
            UrlRunnerPhase.HOLDING, index,
            waitTotalSeconds = totalSeconds,
            waitElapsedSeconds = elapsedSeconds,
            message = "${index + 1}番目のURLを開きました（動画の再生は未確認）" +
                " ${elapsedSeconds}秒経過 / あと${totalSeconds - elapsedSeconds}秒",
        )
        if (elapsedSeconds >= totalSeconds) {
            advance(gen)
            return
        }
        scheduler.schedule(TICK_MS) { reportHold(gen, startedAtMs, totalSeconds) }
    }

    private fun advance(gen: Long) {
        if (!checkCanContinue(gen)) return
        val config = snapshot ?: return
        index += 1
        openCurrent(gen)
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
        val nonBlankCount = config.urls.count { it.isNotBlank() }
        val noMoreInCycle = (loopMode ?: config.loopMode) == LoopMode.LOOP &&
            consecutiveFailures >= nonBlankCount
        val hasNextUrl = ((index + 1) until config.urls.size)
            .any { config.urls[it].isNotBlank() } ||
            (loopMode ?: config.loopMode) == LoopMode.LOOP

        report(
            UrlRunnerPhase.SKIPPED, index, 0, 0,
            "${index + 1}番：$reason。失敗として記録" +
                when {
                    noMoreInCycle -> "（全リンク失敗、連続再試行は中止）"
                    hasNextUrl -> "、TikTok Liteを起動し直して次へ"
                    else -> "、次のURLなし",
                },
        )
        if (!isCurrent(gen)) return
        if (noMoreInCycle) {
            finish("全${nonBlankCount}件のリンクで表示確認できませんでした（失敗${skippedFailureCount}件・安全停止）")
            return
        }
        if (!hasNextUrl) {
            finish("全${config.urls.size}枠のURLを開きました（起動・表示失敗${skippedFailureCount}件をスキップ・動画の再生は未確認）")
            return
        }

        // Keep a short minimum gap before attempting to start a new Activity.
        // The continuation is cancelled by stop() or any safety condition.
        scheduler.schedule(FAILED_LINK_SKIP_DELAY_MS) {
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
            scheduler.schedule(SPLASH_SETTLE_DELAY_MS) next@{
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
        scheduler.cancelAll()
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

    private fun report(
        phase: UrlRunnerPhase,
        index: Int,
        waitTotalSeconds: Int,
        waitElapsedSeconds: Int,
        message: String,
    ) {
        val config = snapshot
        statusSink.onStatus(
            UrlRunnerStatus(
                phase = phase,
                index = index,
                total = config?.urls?.size ?: 0,
                url = config?.urls?.getOrNull(index) ?: "",
                waitTotalSeconds = waitTotalSeconds,
                waitElapsedSeconds = waitElapsedSeconds,
                message = message,
                nextIndex = config?.let { current ->
                    val next = ((index + 1) until current.urls.size)
                        .firstOrNull { current.urls[it].isNotBlank() }
                    next ?: if ((loopMode ?: current.loopMode) == LoopMode.LOOP) {
                        current.urls.indexOfFirst(String::isNotBlank).takeIf { it >= 0 }
                    } else null
                },
            ),
        )
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
