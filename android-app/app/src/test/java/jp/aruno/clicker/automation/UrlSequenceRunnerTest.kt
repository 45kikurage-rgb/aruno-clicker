package jp.aruno.clicker.automation

import jp.aruno.clicker.data.LoopMode
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
    ) {
        var allowed = true
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val foreground = FakeForeground(foregroundResult)
        val statuses = mutableListOf<UrlRunnerStatus>()
        val runner = UrlSequenceRunnerCore(
            opener = UrlOpener { url ->
                if (url in failUrls) {
                    false
                } else {
                    opened += url
                    true
                }
            },
            foregroundChecker = foreground,
            scheduler = scheduler,
            clock = clock,
            statusSink = UrlRunnerStatusSink { statuses += it },
            loopMode = loopMode,
            canContinue = { allowed },
        )
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
    fun openerFailureIsFailureNotSuccess() {
        val config = config(2)
        val harness = Harness(failUrls = setOf(config.urls[0]))
        harness.runner.start(config)
        assertEquals(UrlRunnerPhase.FAILED, harness.runner.phase)
        assertTrue(harness.opened.isEmpty())
        assertTrue(harness.statuses.last().message.contains("開けませんでした"))
    }

    @Test
    fun foregroundFailureIsFailureNotSuccess() {
        val harness = Harness(foregroundResult = false)
        harness.runner.start(config(2))
        assertEquals(UrlRunnerPhase.FAILED, harness.runner.phase)
        assertEquals(1, harness.opened.size)
        harness.scheduler.advanceBy(60_000)
        assertEquals(1, harness.opened.size)
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
        assertTrue(harness.statuses.last().message.contains("開きました"))
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
}
