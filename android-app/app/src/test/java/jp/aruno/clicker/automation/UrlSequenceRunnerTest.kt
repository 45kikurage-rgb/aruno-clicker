package jp.aruno.clicker.automation

import jp.aruno.clicker.data.LoopMode
import jp.aruno.clicker.data.DailyVideoViews
import jp.aruno.clicker.data.UrlRunnerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSequenceRunnerTest {
    private class FakeClock(var now: Long = 0L) : UrlRunnerClock {
        override fun nowMs(): Long = now
    }

    private class FakeScheduler(private val clock: FakeClock) : DelayScheduler {
        private class Entry(val dueAt: Long, val task: () -> Unit) {
            var cancelled = false
        }

        private val entries = mutableListOf<Entry>()

        override fun schedule(delayMs: Long, task: () -> Unit): ScheduledTask {
            val entry = Entry(clock.now + delayMs, task)
            entries += entry
            return ScheduledTask { entry.cancelled = true }
        }

        override fun cancelAll() {
            entries.forEach { it.cancelled = true }
            entries.clear()
        }

        fun advanceBy(ms: Long) {
            val target = clock.now + ms
            while (true) {
                val next = entries
                    .filter { !it.cancelled && it.dueAt <= target }
                    .minByOrNull { it.dueAt } ?: break
                clock.now = next.dueAt
                entries.remove(next)
                next.task()
            }
            clock.now = target
        }
    }

    private class FakeForeground(var result: Boolean = true) : ForegroundChecker {
        var pendingCallback: ((Boolean) -> Unit)? = null
        var manual = false

        override fun awaitForeground(generation: Long, callback: (Boolean) -> Unit) {
            if (manual) {
                pendingCallback = callback
            } else {
                callback(result)
            }
        }
    }

    private class Harness(
        val opened: MutableList<String> = mutableListOf(),
        var failUrls: Set<String> = emptySet(),
        foregroundResult: Boolean = true,
        loopMode: LoopMode = LoopMode.STOP,
        splashEveryLinks: Int = 0,
        dailyViews: DailyVideoViews? = null,
        dailyLimit: Int = 2,
    ) {
        var allowed = true
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val foreground = FakeForeground(foregroundResult)
        val statuses = mutableListOf<UrlRunnerStatus>()
        val splashCalls = mutableListOf<String>()
        val order = mutableListOf<String>()
        val runner = UrlSequenceRunnerCore(
            opener = UrlOpener { url ->
                if (url in failUrls) {
                    false
                } else {
                    opened += url
                    order += "url:$url"
                    true
                }
            },
            splashLauncher = UrlRunnerSplashLauncher {
                splashCalls += "splash"
                order += "splash"
                true
            },
            foregroundChecker = foreground,
            scheduler = scheduler,
            clock = clock,
            statusSink = UrlRunnerStatusSink { statuses += it },
            splashEveryLinks = splashEveryLinks,
            loopMode = loopMode,
            canContinue = { allowed },
            dailyViews = dailyViews,
            dailyLimit = dailyLimit,
        )
    }

    private class MemoryViews : DailyVideoViews {
        val counts = mutableMapOf<String, Int>()
        var writable = true
        override fun count(url: String) = counts[url] ?: 0
        override fun recordStart(url: String, limit: Int): Boolean {
            if (!writable || count(url) >= limit) return false
            counts[url] = count(url) + 1
            return true
        }
    }

    @Test fun partialHoldCountsImmediatelyAndRestartSkipsCappedVideo() {
        val c = config(2)
        val history = MemoryViews()
        repeat(2) {
            val h = Harness(dailyViews = history)
            h.runner.start(c)
            h.scheduler.advanceBy(400)
            h.runner.stop()
            h.scheduler.advanceBy(10_000)
            assertEquals(listOf(c.urls[0]), h.opened)
        }
        assertEquals(2, history.count(c.urls[0]))
        val restarted = Harness(dailyViews = history)
        restarted.runner.start(c)
        assertEquals(listOf(c.urls[1]), restarted.opened)
        assertTrue(restarted.statuses.any { it.index == 0 && it.message.contains("スキップ") })
        assertEquals(1, history.count(c.urls[1]))
    }

    @Test fun loopsStopAfterTwoOrThreeStartsWithoutExtraSplash() {
        for (limit in listOf(2, 3)) {
            val c = config(2)
            val history = MemoryViews()
            val h = Harness(loopMode = LoopMode.LOOP, splashEveryLinks = 1,
                dailyViews = history, dailyLimit = limit)
            h.runner.start(c)
            h.scheduler.advanceBy(100_000)
            assertEquals(limit * 2, h.opened.size)
            c.urls.forEach { assertEquals(limit, history.count(it)) }
            assertEquals(limit * 2 - 1, h.splashCalls.size)
            assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
            assertTrue(h.statuses.last().message.contains("上限${limit}回"))
            assertEquals(null, h.statuses.last().nextIndex)
        }
    }

    @Test fun allCappedSparseListFinishesWithoutOpeningOrRecursion() {
        val history = MemoryViews()
        val c = config(1).copy(urls = listOf("", config(1).urls[0], ""), startIndex = 2)
        history.counts[c.urls[1]] = 2
        val h = Harness(loopMode = LoopMode.LOOP, dailyViews = history)
        h.runner.start(c)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        h.scheduler.advanceBy(100_000)
        assertTrue(h.opened.isEmpty())
    }

    @Test fun reorderedUrlsShareCountsAndDuplicateSlotsDoNotExceedLimit() {
        val history = MemoryViews()
        val c = config(2)
        history.counts[c.urls[0]] = 2
        val h = Harness(dailyViews = history)
        h.runner.start(c.copy(urls = listOf(c.urls[1], c.urls[0], c.urls[1], c.urls[1])))
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf(c.urls[1], c.urls[1]), h.opened)
        assertEquals(2, history.count(c.urls[1]))
        assertEquals(2, history.count(c.urls[0]))
    }

    @Test fun failedOpenAndFailedForegroundDoNotCountAndCappedLinksAreNotRetried() {
        val history = MemoryViews()
        val c = config(3)
        history.counts[c.urls[1]] = 2
        val h = Harness(failUrls = setOf(c.urls[0]), foregroundResult = false,
            loopMode = LoopMode.LOOP, dailyViews = history)
        h.runner.start(c)
        h.scheduler.advanceBy(30_000)
        assertEquals(0, history.count(c.urls[0]))
        assertEquals(2, history.count(c.urls[1]))
        assertEquals(0, history.count(c.urls[2]))
        assertEquals(listOf(c.urls[2]), h.opened)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
    }

    @Test fun duplicateAndLateForegroundCallbacksCannotChargeAnotherAttempt() {
        val history = MemoryViews()
        val h = Harness(dailyViews = history)
        h.foreground.manual = true
        val c = config(2)
        h.runner.start(c)
        val firstCallback = h.foreground.pendingCallback!!
        assertEquals(0, history.count(c.urls[0]))
        firstCallback(true)
        firstCallback(true)
        assertEquals(1, history.count(c.urls[0]))
        h.scheduler.advanceBy(3_000)
        firstCallback(true)
        assertEquals(0, history.count(c.urls[1]))
        h.runner.stop()
        h.foreground.pendingCallback!!(true)
        assertEquals(0, history.count(c.urls[1]))
    }

    @Test fun persistenceFailureStopsWithoutHoldingOrOpeningNextUrl() {
        val history = MemoryViews().also { it.writable = false }
        val h = Harness(dailyViews = history)
        h.runner.start(config(2))
        h.scheduler.advanceBy(30_000)
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
        assertEquals(1, h.opened.size)
        assertTrue(h.statuses.none { it.phase == UrlRunnerPhase.HOLDING })
    }

    @Test fun dateChangeDuringHoldDoesNotChargeNewDayUntilNextOpening() {
        val history = MemoryViews()
        val h = Harness(loopMode = LoopMode.LOOP, dailyViews = history)
        val c = config(1)
        h.runner.start(c)
        assertEquals(1, history.count(c.urls[0]))
        history.counts.clear() // Japan midnight while the existing hold continues.
        h.scheduler.advanceBy(2_000)
        assertEquals(0, history.count(c.urls[0]))
        h.scheduler.advanceBy(1_000)
        assertEquals(1, history.count(c.urls[0]))
    }

    private fun config(
        count: Int,
        individual: List<Int> = listOf(3, 3, 3, 3, 3),
        common: Int = 5,
    ) = UrlRunnerConfig(
        urls = (1..count).map { "https://lite.tiktok.com/t/U$it/" },
        individualSeconds = individual,
        commonSeconds = common,
    )

    @Test
    fun opensInOrderWithIndividualThenCommonSeconds() {
        val harness = Harness()
        val config = config(7)
        assertEquals(UrlRunnerStartResult.Started, harness.runner.start(config))
        assertEquals(listOf(config.urls[0]), harness.opened)

        // 1番目（個別3秒）: 3秒経過で次へ。
        harness.scheduler.advanceBy(2_999)
        assertEquals(1, harness.opened.size)
        harness.scheduler.advanceBy(1)
        assertEquals(2, harness.opened.size)

        // 2〜5番目（各3秒）を進める。
        repeat(3) { harness.scheduler.advanceBy(3_000) }
        assertEquals(5, harness.opened.size)

        // 5番目（個別3秒）が終わると6番目が開く。
        harness.scheduler.advanceBy(3_000)
        assertEquals(6, harness.opened.size)

        // 6番目（0始まり5）は共通5秒。3秒では進まず、5秒で次へ。
        harness.scheduler.advanceBy(3_000)
        assertEquals(6, harness.opened.size)
        harness.scheduler.advanceBy(2_000)
        assertEquals(7, harness.opened.size)

        // 最終（共通5秒）が終わると停止し、先頭へは戻らない（既定 STOP）。
        harness.scheduler.advanceBy(5_000)
        assertEquals(UrlRunnerPhase.FINISHED, harness.runner.phase)
        assertEquals(7, harness.opened.size)
        assertEquals(config.urls, harness.opened)
    }

    @Test
    fun blankPositionsAreSkippedWithoutDelayAndOriginalNumbersAreRetained() {
        val h = Harness()
        val config = UrlRunnerConfig(
            urls = listOf(
                "", "https://lite.tiktok.com/t/second/", "", 
                "https://lite.tiktok.com/t/fourth/", "",
            ),
            individualSeconds = listOf(1, 1, 1, 1, 1),
        )
        assertEquals(UrlRunnerStartResult.Started, h.runner.start(config))
        assertEquals(listOf(config.urls[1]), h.opened)
        assertTrue(h.statuses.any { it.index == 0 && it.message.contains("スキップ") })
        h.scheduler.advanceBy(1_000)
        assertEquals(listOf(config.urls[1], config.urls[3]), h.opened)
        assertTrue(h.statuses.any { it.index == 2 && it.message.contains("スキップ") })
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertEquals(2, h.opened.size)
        assertTrue(h.statuses.any { it.index == 4 && it.message.contains("スキップ") })
    }

    @Test
    fun startOnBlankAtEndStopsWithoutOpeningAnInvalidUrl() {
        val h = Harness()
        val config = UrlRunnerConfig(
            urls = listOf("https://lite.tiktok.com/t/first/", "", ""),
            startIndex = 1,
        )
        assertEquals(UrlRunnerStartResult.Started, h.runner.start(config))
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertTrue(h.opened.isEmpty())
    }


    @Test
    fun nextUrlIndexSkipsBlankSlotsAndLastSlotHasNoNext() {
        val h = Harness()
        val config = UrlRunnerConfig(
            urls = listOf(
                "https://lite.tiktok.com/t/first/", "", "",
                "https://lite.tiktok.com/t/fourth/",
            ),
            individualSeconds = listOf(1, 1, 1, 1, 1),
        )
        h.runner.start(config)
        assertEquals(3, h.statuses.last { it.phase == UrlRunnerPhase.HOLDING }.nextIndex)
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.HOLDING, h.runner.phase)
        assertEquals(null, h.statuses.last { it.phase == UrlRunnerPhase.HOLDING }.nextIndex)
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
    }

    @Test
    fun loopingLastUrlCountsDownToFirstNonblank() {
        val h = Harness(loopMode = LoopMode.LOOP)
        val config = UrlRunnerConfig(
            urls = listOf("", "https://lite.tiktok.com/t/only/", ""),
            individualSeconds = listOf(1, 1, 1, 1, 1),
        )
        h.runner.start(config)
        assertEquals(1, h.statuses.last { it.phase == UrlRunnerPhase.HOLDING }.nextIndex)
        h.runner.stop()
    }

    @Test
    fun doubleStartIsRejected() {
        val harness = Harness()
        harness.runner.start(config(3))
        assertEquals(UrlRunnerStartResult.AlreadyRunning, harness.runner.start(config(3)))
        assertEquals(1, harness.opened.size)
    }

    @Test
    fun stopDuringHoldPreventsNextOpen() {
        val harness = Harness()
        harness.runner.start(config(3))
        harness.scheduler.advanceBy(1_000)
        harness.runner.stop()
        assertEquals(UrlRunnerPhase.STOPPED, harness.runner.phase)
        harness.scheduler.advanceBy(60_000)
        assertEquals(1, harness.opened.size)
    }

    @Test
    fun stopDuringForegroundWaitIgnoresLateCallback() {
        val harness = Harness()
        harness.foreground.manual = true
        harness.runner.start(config(3))
        assertEquals(1, harness.opened.size)
        harness.runner.stop()
        // 停止後に届いた前面確認コールバックでは待機も次URLも始まらない。
        harness.foreground.pendingCallback?.invoke(true)
        harness.scheduler.advanceBy(60_000)
        assertEquals(1, harness.opened.size)
        assertEquals(UrlRunnerPhase.STOPPED, harness.runner.phase)
    }

    @Test
    fun openerFailureSkipsToNextUrlAndDoesNotClaimVideoSuccess() {
        val config = config(3)
        val harness = Harness(failUrls = setOf(config.urls[0]))
        harness.runner.start(config)
        assertEquals(UrlRunnerPhase.SKIPPED, harness.runner.phase)
        assertTrue(harness.opened.isEmpty())
        assertTrue(harness.statuses.any { it.index == 0 && it.message.contains("起動失敗") })
        harness.scheduler.advanceBy(649)
        assertTrue(harness.opened.isEmpty())
        harness.scheduler.advanceBy(1)
        assertEquals(listOf("splash"), harness.splashCalls)
        assertTrue(harness.opened.isEmpty())
        assertEquals(UrlRunnerPhase.RECOVERING, harness.runner.phase)
        harness.scheduler.advanceBy(1_799)
        assertTrue(harness.opened.isEmpty())
        harness.scheduler.advanceBy(1)
        assertEquals(listOf(config.urls[1]), harness.opened)
        assertEquals("splash", harness.order.first())
        assertEquals(UrlRunnerPhase.HOLDING, harness.runner.phase)
        harness.scheduler.advanceBy(6_000)
        assertEquals(listOf(config.urls[1], config.urls[2]), harness.opened)
        harness.scheduler.advanceBy(3_000)
        assertEquals(UrlRunnerPhase.FINISHED, harness.runner.phase)
        assertTrue(harness.statuses.last().message.contains("失敗1件"))
        assertTrue(harness.statuses.last().message.contains("再生は未確認"))
    }

    @Test
    fun foregroundFailureMovesPastAllLinksThenFinishesInStopMode() {
        val harness = Harness(foregroundResult = false)
        harness.runner.start(config(2))
        assertEquals(UrlRunnerPhase.SKIPPED, harness.runner.phase)
        assertEquals(1, harness.opened.size)
        harness.scheduler.advanceBy(650)
        assertEquals(listOf("splash"), harness.splashCalls)
        assertEquals(UrlRunnerPhase.RECOVERING, harness.runner.phase)
        harness.scheduler.advanceBy(1_800)
        assertEquals(2, harness.opened.size)
        // The last URL has no successor, so its failure finishes immediately.
        assertEquals(UrlRunnerPhase.FINISHED, harness.runner.phase)
        // The failed final URL has no successor: do not launch again.
        harness.scheduler.advanceBy(10_000)
        assertEquals(UrlRunnerPhase.FINISHED, harness.runner.phase)
        assertEquals(1, harness.splashCalls.size)
        assertEquals(2, harness.opened.size)
        assertTrue(harness.statuses.last().message.contains("失敗2件"))
    }

    @Test
    fun manualStopWhileSkipPendingNeverOpensAnotherLink() {
        val config = config(3)
        val h = Harness(failUrls = setOf(config.urls[0]))
        h.runner.start(config)
        assertEquals(UrlRunnerPhase.SKIPPED, h.runner.phase)
        h.runner.stop()
        h.scheduler.advanceBy(50_000)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.STOPPED, h.runner.phase)
    }

    @Test
    fun deviceBecomingLockedWhileSkipPendingNeverOpensAnotherLink() {
        val config = config(3)
        val h = Harness(failUrls = setOf(config.urls[0]))
        h.runner.start(config)
        h.allowed = false
        h.scheduler.advanceBy(650)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
    }

    @Test
    fun loopWithAllBrokenLinksStopsAfterOneCycle() {
        val config = config(3)
        val h = Harness(failUrls = config.urls.toSet(), loopMode = LoopMode.LOOP)
        h.runner.start(config)
        h.scheduler.advanceBy(10_000)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertEquals(3, h.statuses.count { it.phase == UrlRunnerPhase.SKIPPED })
        assertTrue(h.statuses.last().message.contains("安全停止"))
    }

    @Test
    fun failedLinkAndEmptyPositionsKeepOriginalNumbering() {
        val c = UrlRunnerConfig(
            urls = listOf("https://lite.tiktok.com/t/first/", "",
                "https://lite.tiktok.com/t/third/"),
        )
        val h = Harness(failUrls = setOf(c.urls[0]))
        h.runner.start(c)
        h.scheduler.advanceBy(2_450)
        assertEquals(listOf("splash"), h.splashCalls)
        assertEquals(listOf(c.urls[2]), h.opened)
        assertTrue(h.statuses.any { it.index == 1 && it.message.contains("空欄") })
        assertEquals(2, h.statuses.last { it.phase == UrlRunnerPhase.HOLDING }.index)
    }

    @Test
    fun explicitStopAfterSplashPreventsOpeningNextVideo() {
        val config = config(3)
        val h = Harness(failUrls = setOf(config.urls[0]))
        h.runner.start(config)
        h.scheduler.advanceBy(650)
        assertEquals(listOf("splash"), h.splashCalls)
        assertTrue(h.opened.isEmpty())
        h.runner.stop()
        h.scheduler.advanceBy(20_000)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.STOPPED, h.runner.phase)
    }

    @Test
    fun lockedDuringSplashDelayDoesNotOpenTheNextVideo() {
        val config = config(2)
        val h = Harness(failUrls = setOf(config.urls[0]))
        h.runner.start(config)
        h.scheduler.advanceBy(650)
        assertEquals(1, h.splashCalls.size)
        h.allowed = false
        h.scheduler.advanceBy(1_800)
        assertTrue(h.opened.isEmpty())
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
    }

    @Test
    fun failureOnLastUrlFinishesWithoutUnneededSplash() {
        val config = config(2).copy(startIndex = 1)
        val h = Harness(failUrls = setOf(config.urls[1]))
        h.runner.start(config)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertTrue(h.splashCalls.isEmpty())
        assertTrue(h.statuses.last().message.contains("失敗1件"))
    }

    @Test
    fun invalidConfigDoesNotStart() {
        val harness = Harness()
        val empty = harness.runner.start(UrlRunnerConfig(urls = emptyList()))
        assertTrue(empty is UrlRunnerStartResult.Invalid)
        val badSeconds = harness.runner.start(
            config(1).copy(individualSeconds = listOf(0, 3, 3, 3, 3)),
        )
        assertTrue(badSeconds is UrlRunnerStartResult.Invalid)
        assertTrue(harness.opened.isEmpty())
        assertEquals(UrlRunnerPhase.IDLE, harness.runner.phase)
    }

    @Test
    fun snapshotIsFixedAtStart() {
        val mutableUrls = mutableListOf(
            "https://lite.tiktok.com/t/FIRST/",
            "https://lite.tiktok.com/t/SECOND/",
        )
        val harness = Harness()
        harness.runner.start(
            UrlRunnerConfig(urls = mutableUrls, individualSeconds = listOf(1, 1, 1, 1, 1), commonSeconds = 1),
        )
        // 開始後に元のリストを書き換えても、実行中の順序は固定されたまま。
        mutableUrls[1] = "https://lite.tiktok.com/t/CHANGED/"
        harness.scheduler.advanceBy(1_000)
        assertEquals(
            listOf("https://lite.tiktok.com/t/FIRST/", "https://lite.tiktok.com/t/SECOND/"),
            harness.opened,
        )
    }

    @Test
    fun loopModeLoopsOnlyWhenExplicitlySelected() {
        val harness = Harness(loopMode = LoopMode.LOOP)
        val config = config(1, individual = listOf(1, 1, 1, 1, 1))
        harness.runner.start(config)
        harness.scheduler.advanceBy(1_000)
        // LOOP では先頭へ戻る（既定の STOP との切替確認）。
        assertEquals(2, harness.opened.size)
        harness.runner.stop()
    }

    @Test
    fun finishedStatusDistinguishesOpenedFromPlayed() {
        val harness = Harness()
        harness.runner.start(config(1, individual = listOf(1, 1, 1, 1, 1)))
        harness.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.FINISHED, harness.runner.phase)
        assertTrue(harness.statuses.last().message.contains("処理を完了"))
        assertTrue(harness.statuses.last().message.contains("再生は未確認"))
    }
    @Test fun lockedDuringHoldStopsWithoutNextOpen() {
        val h = Harness()
        h.runner.start(config(3))
        h.allowed = false
        h.scheduler.advanceBy(10_000)
        assertEquals(1, h.opened.size)
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
    }

    @Test fun lockedBeforeStartDoesNotOpenAnything() {
        val h = Harness()
        h.allowed = false
        h.runner.start(config(3))
        assertEquals(0, h.opened.size)
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
    }

    @Test fun configurableStartNumberAndRequestClockWork() {
        val h = Harness()
        h.foreground.manual = true
        h.runner.start(config(3).copy(startIndex = 1, waitFromForeground = false))
        assertEquals(listOf(config(3).urls[1]), h.opened)
        h.scheduler.advanceBy(3_000)
        h.foreground.pendingCallback!!.invoke(true)
        assertEquals(2, h.opened.size)
        assertEquals(config(3).urls[2], h.opened.last())
    }

    private fun sixthModeConfig(): UrlRunnerConfig =
        config(8, individual = List(5) { 1 }, common = 1).copy(startIndex = 5)

    @Test
    fun sixthSplashModeOpensSixDirectlyThenSplashBeforeSevenAndEight() {
        val h = Harness(splashEveryLinks = 1)
        val config = sixthModeConfig()
        h.runner.start(config)
        assertEquals(listOf(config.urls[5]), h.opened)
        assertTrue(h.splashCalls.isEmpty())
        h.scheduler.advanceBy(999)
        assertTrue(h.splashCalls.isEmpty())
        h.scheduler.advanceBy(1)
        assertEquals(listOf("splash"), h.splashCalls)
        assertEquals(UrlRunnerPhase.RECOVERING, h.runner.phase)
        assertEquals(listOf(config.urls[5]), h.opened)
        h.scheduler.advanceBy(1_799)
        assertEquals(1, h.opened.size)
        h.scheduler.advanceBy(1)
        assertEquals(listOf(config.urls[5], config.urls[6]), h.opened)
        assertEquals(
            listOf("url:${config.urls[5]}", "splash", "url:${config.urls[6]}"),
            h.order,
        )
        h.scheduler.advanceBy(1_000)
        assertEquals(2, h.splashCalls.size)
        assertEquals(2, h.opened.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(config.urls.slice(5..7), h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertEquals(2, h.splashCalls.size)
    }

    @Test
    fun sixthNoSplashModeOpensSixSevenEightImmediatelyAfterEachWait() {
        val h = Harness(splashEveryLinks = 0)
        val config = sixthModeConfig()
        h.runner.start(config)
        assertEquals(listOf(config.urls[5]), h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(listOf(config.urls[5], config.urls[6]), h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(config.urls.slice(5..7), h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.FINISHED, h.runner.phase)
        assertTrue("No intentional splash in normal transitions", h.splashCalls.isEmpty())
    }

    @Test
    fun splashModeSkipsBlankSevenAndSplashesOnlyBeforeEight() {
        val config = sixthModeConfig().copy(urls = sixthModeConfig().urls.toMutableList().apply { this[6] = "" })
        val h = Harness(splashEveryLinks = 1)
        h.runner.start(config)
        assertEquals(listOf(config.urls[5]), h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(1, h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(listOf(config.urls[5], config.urls[7]), h.opened)
        assertTrue(h.statuses.any { it.index == 6 && it.message.contains("空欄") })
        assertEquals(7, h.statuses.last { it.phase == UrlRunnerPhase.HOLDING }.index)
    }

    @Test
    fun explicitStopDuringNormalSplashPreventsNextUrl() {
        val config = sixthModeConfig()
        val h = Harness(splashEveryLinks = 1)
        h.runner.start(config)
        h.scheduler.advanceBy(1_000)
        assertEquals(1, h.splashCalls.size)
        h.runner.stop()
        h.scheduler.advanceBy(30_000)
        assertEquals(listOf(config.urls[5]), h.opened)
        assertEquals(UrlRunnerPhase.STOPPED, h.runner.phase)
    }

    @Test
    fun lockDuringNormalSplashPreventsNextUrl() {
        val config = sixthModeConfig()
        val h = Harness(splashEveryLinks = 1)
        h.runner.start(config)
        h.scheduler.advanceBy(1_000)
        h.allowed = false
        h.scheduler.advanceBy(1_800)
        assertEquals(listOf(config.urls[5]), h.opened)
        assertEquals(UrlRunnerPhase.FAILED, h.runner.phase)
    }

    @Test
    fun intentionalSplashModeDoesNotDoubleSplashAfterFailedUrlRecovery() {
        val config = sixthModeConfig()
        val h = Harness(
            splashEveryLinks = 1,
            failUrls = setOf(config.urls[5]),
        )
        h.runner.start(config)
        assertEquals(UrlRunnerPhase.SKIPPED, h.runner.phase)
        h.scheduler.advanceBy(650)
        assertEquals(1, h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(listOf(config.urls[6]), h.opened)
        assertEquals(1, h.splashCalls.size)
        h.scheduler.advanceBy(1_000)
        assertEquals(2, h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(listOf(config.urls[6], config.urls[7]), h.opened)
    }

    @Test
    fun oneRunSplashModeLoopsThroughExistingConfiguredOrder() {
        val config = sixthModeConfig().copy(loopMode = LoopMode.LOOP)
        val h = Harness(loopMode = LoopMode.LOOP, splashEveryLinks = 1)
        h.runner.start(config)
        h.scheduler.advanceBy(1_000 + 1_800)
        h.scheduler.advanceBy(1_000 + 1_800)
        h.scheduler.advanceBy(1_000)
        assertEquals(3, h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(config.urls.first(), h.opened.last())
        h.runner.stop()
    }


    @Test
    fun splashFrequencyTwoRunsEveryTwoSuccessfulVideosNotBeforeSecond() {
        val config = sixthModeConfig().copy(urls=(1..10).map { "https://lite.tiktok.com/t/slot$it/" })
        val h = Harness(splashEveryLinks = 2)
        h.runner.start(config)
        assertEquals(listOf(config.urls[5]),h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(listOf(config.urls[5],config.urls[6]),h.opened)
        assertTrue(h.splashCalls.isEmpty())
        h.scheduler.advanceBy(1_000)
        assertEquals(1,h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(config.urls.slice(5..7),h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(config.urls.slice(5..8),h.opened)
        assertEquals(1,h.splashCalls.size)
        h.scheduler.advanceBy(1_000)
        assertEquals(2,h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(config.urls.slice(5..9),h.opened)
    }

    @Test
    fun splashFrequencyThreeStartsAtSixAndOpensNineAfterSingleSplash() {
        val config = sixthModeConfig().copy(urls=(1..10).map { "https://lite.tiktok.com/t/slot$it/" })
        val h = Harness(splashEveryLinks = 3)
        h.runner.start(config)
        h.scheduler.advanceBy(3_000)
        assertEquals(config.urls.slice(5..7),h.opened)
        assertEquals(listOf("splash"),h.splashCalls)
        h.scheduler.advanceBy(1_800)
        assertEquals(config.urls.slice(5..8),h.opened)
        h.scheduler.advanceBy(2_000)
        assertEquals(1,h.splashCalls.size)
    }

    @Test
    fun splashFrequencyFiveWithOnlyFourVideoSlotsNeverLaunchesSplash() {
        val config = sixthModeConfig().copy(urls=(1..9).map { "https://lite.tiktok.com/t/slot$it/" })
        val h = Harness(splashEveryLinks = 5)
        h.runner.start(config)
        h.scheduler.advanceBy(4_000)
        assertEquals(config.urls.slice(5..8),h.opened)
        assertEquals(UrlRunnerPhase.FINISHED,h.runner.phase)
        assertTrue(h.splashCalls.isEmpty())
    }

    @Test
    fun splashFrequencyTwoResetsCounterAfterFailedLinkRecovery() {
        val config = sixthModeConfig().copy(urls=(1..10).map { "https://lite.tiktok.com/t/slot$it/" })
        val h = Harness(splashEveryLinks = 2,failUrls=setOf(config.urls[6]))
        h.runner.start(config)
        h.scheduler.advanceBy(1_000)
        assertEquals(UrlRunnerPhase.SKIPPED,h.runner.phase)
        h.scheduler.advanceBy(650)
        assertEquals(1,h.splashCalls.size)
        h.scheduler.advanceBy(1_800)
        assertEquals(listOf(config.urls[5],config.urls[7]),h.opened)
        h.scheduler.advanceBy(1_000)
        assertEquals(config.urls.slice(7..8).last(),h.opened.last())
        assertEquals(1,h.splashCalls.size)
        h.scheduler.advanceBy(1_000)
        assertEquals(2,h.splashCalls.size)
    }

}

