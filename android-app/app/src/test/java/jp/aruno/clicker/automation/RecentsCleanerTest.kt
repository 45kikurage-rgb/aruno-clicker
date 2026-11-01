package jp.aruno.clicker.automation

import jp.aruno.clicker.data.RecentsLayout
import org.junit.Assert.*
import org.junit.Test

class RecentsCleanerTest {
    private class Queue : DelayScheduler {
        data class Entry(val task: () -> Unit, var cancelled: Boolean = false)
        val entries = mutableListOf<Entry>()
        override fun schedule(delayMs: Long, task: () -> Unit): ScheduledTask {
            val entry = Entry(task); entries += entry
            return ScheduledTask { entry.cancelled = true }
        }
        override fun cancelAll() = entries.clear()
        fun one() { if (entries.isNotEmpty()) entries.removeAt(0).let { if (!it.cancelled) it.task() } }
        fun drain() { var n = 0; while (entries.isNotEmpty()) { check(n++ < 1000); one() } }
    }
    private class Port(count: Int = 5) : RecentsPort {
        val cards = (1..count).map { RecentCard("app$it", 10, 100, 400, 700) }.toMutableList()
        val actions = mutableListOf<Pair<RecentCard, RecentsLayout>>()
        var opened = 0; var homes = 0; var reads = 0
        var surface = RecentsSurface.HOME
        var stuck = false; var accepted = true; var openWorks = true; var homeWorks = true
        var autoHome = false; var unknownAfterLast = false; var unknownReads = 0
        var pending: ((Boolean) -> Unit)? = null
        var delayGesture = false
        var visible = 2
        override fun open(): Boolean {
            opened++
            if (openWorks) surface = if (cards.isEmpty()) RecentsSurface.EMPTY else RecentsSurface.CARDS
            return openWorks
        }
        override fun home(): Boolean { homes++; if (homeWorks) surface = RecentsSurface.HOME; return homeWorks }
        override fun screen(): RecentsScreen {
            reads++
            if (unknownReads-- > 0) return RecentsScreen(RecentsSurface.UNKNOWN)
            return RecentsScreen(surface, if (surface == RecentsSurface.CARDS) cards.take(visible) else emptyList())
        }
        override fun dismiss(card: RecentCard, layout: RecentsLayout, alternate: Boolean, done: (Boolean) -> Unit) {
            assertEquals(cards.first(), card)
            actions += card to layout
            if (accepted && !stuck) {
                cards.removeAt(0)
                if (cards.isEmpty()) surface = when {
                    unknownAfterLast -> RecentsSurface.UNKNOWN
                    autoHome -> RecentsSurface.HOME
                    else -> RecentsSurface.EMPTY
                }
            }
            if (delayGesture) pending = done else done(accepted)
        }
    }
    private fun run(port: Port, layout: RecentsLayout = RecentsLayout.VERTICAL): Boolean? {
        val queue = Queue(); var result: Boolean? = null
        RecentsCleaner(port, layout).prepare(queue, { true }) { result = it }; queue.drain()
        return result
    }
    @Test fun twoFrontCardsAreReacquiredUntilAllHiddenCardsAreRemoved() {
        val port = Port(12)
        assertEquals(true, run(port))
        assertEquals(12, port.actions.size)
        assertTrue(port.actions.all { it.second == RecentsLayout.VERTICAL })
        assertEquals(0, port.cards.size); assertEquals(1, port.homes)
    }
    @Test fun horizontalLayoutUsesUpwardProfileForEveryCard() {
        val port = Port(6)
        assertEquals(true, run(port, RecentsLayout.HORIZONTAL))
        assertTrue(port.actions.all { it.second == RecentsLayout.HORIZONTAL })
    }
    @Test fun samePositionWithDifferentCardIdentityContinues() {
        val port = Port(8).apply { visible = 1 }
        assertEquals(true, run(port)); assertEquals(8, port.actions.size)
    }
    @Test fun successfulGestureWithoutRemovalFailsAndDoesNotReportEmpty() {
        val port = Port(2).apply { stuck = true }
        assertEquals(false, run(port)); assertEquals(3, port.actions.size); assertEquals(0, port.homes)
    }
    @Test fun missingTreeAfterLastSwipeIsNotSuccess() {
        val port = Port(1).apply { unknownAfterLast = true }
        assertEquals(false, run(port)); assertEquals(0, port.homes)
    }
    @Test fun lastSwipeReturningHomeRequiresReopeningEmptyOverview() {
        val port = Port(1).apply { autoHome = true }
        assertEquals(true, run(port)); assertEquals(2, port.opened)
    }
    @Test fun emptyOverviewNeedsThreeSeparateReadsAndVerifiedHome() {
        val port = Port(0)
        assertEquals(true, run(port)); assertTrue(port.reads >= 4); assertTrue(port.actions.isEmpty())
    }
    @Test fun temporaryUnknownScreenWaitsRatherThanSwipingBlindly() {
        val port = Port(2).apply { unknownReads = 4 }
        assertEquals(true, run(port)); assertEquals(2, port.actions.size)
    }
    @Test fun failedGestureOrOpenOrHomeNeverSucceeds() {
        for (port in listOf(Port().apply { accepted = false }, Port().apply { openWorks = false }, Port(0).apply { homeWorks = false })) {
            assertEquals(false, run(port))
        }
    }
    @Test fun stopDuringGestureDiscardsLateCallback() {
        val port = Port().apply { delayGesture = true }; val queue = Queue()
        var active = true; var result: Boolean? = null
        RecentsCleaner(port, RecentsLayout.VERTICAL).prepare(queue, { active }) { result = it }
        queue.one(); active = false; port.pending!!(true); queue.drain()
        assertNull(result); assertEquals(1, port.actions.size); assertEquals(0, port.homes)
    }
    @Test fun neverUsesMissingTikTokAsCompletionWhenOtherAppsRemain() {
        val port = Port(4).apply { cards[0] = cards[0].copy(id = "Chrome") }
        assertEquals(true, run(port)); assertEquals(4, port.actions.size)
    }
    @Test fun unstableEmptyReadCannotCompleteEarly() {
        val port = Port(0); val queue = Queue(); var result: Boolean? = null
        RecentsCleaner(port, RecentsLayout.VERTICAL).prepare(queue, { true }) { result = it }
        queue.one(); assertNull(result)
        port.surface = RecentsSurface.UNKNOWN; queue.one(); assertNull(result)
        port.surface = RecentsSurface.EMPTY; queue.one(); queue.one(); assertNull(result)
        queue.drain(); assertEquals(true, result)
    }

    @Test fun launcherHistoryTextOutsideOverviewIsNotEmptyRecentAppsEvidence() {
        assertFalse(RecentsEmptyEvidence.verified(true, false, false, 0, true))
    }
    @Test fun knownEmptyOverviewOrDedicatedEmptyMessageIsEvidence() {
        assertTrue(RecentsEmptyEvidence.verified(true, true, false, 0, true))
        assertTrue(RecentsEmptyEvidence.verified(true, false, true, 0, true))
    }
    @Test fun absentLabelsMissingTreeOrRemainingCardsNeverProveEmpty() {
        assertFalse(RecentsEmptyEvidence.verified(false, true, true, 0, true))
        assertFalse(RecentsEmptyEvidence.verified(true, true, true, 0, false))
        assertFalse(RecentsEmptyEvidence.verified(true, true, true, 2, true))
    }
}
