package jp.aruno.clicker.automation

import jp.aruno.clicker.data.GiftHomeMode
import org.junit.Assert.*
import org.junit.Test

class GiftHomeNavigatorTest {
    private class Scheduler : DelayScheduler {
        data class Entry(val at: Long, val task: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val entries = mutableListOf<Entry>()
        override fun schedule(delayMs: Long, task: () -> Unit): ScheduledTask {
            val e = Entry(now + delayMs, task); entries += e
            return ScheduledTask { e.cancelled = true }
        }
        override fun cancelAll() { entries.clear() }
        fun drain() {
            var steps = 0
            while (entries.any { !it.cancelled }) {
                check(steps++ < 1_000) { "Unbounded navigator" }
                val e = entries.filterNot { it.cancelled }.minBy { it.at }
                entries.remove(e); now = e.at; e.task()
            }
        }
    }
    private class Port : GiftHomePort {
        var surface = GiftHomeSurface.FEED
        var target = true
        var elementWorks = true
        var elementMoves = true
        var coordinateMoves = true
        var hideRewardTabs = false
        var width = 684
        var height = 1496
        var reportedTabY: Float? = null
        var initialLoadingPolls = 0
        var transientNullPolls = 0
        var nullAfterEachClick = 0
        val actions = mutableListOf<String>()
        val points = mutableListOf<Pair<Float, Float>>()
        override fun screen(): GiftHomeScreen? {
            if (transientNullPolls > 0) { transientNullPolls--; return null }
            if (initialLoadingPolls > 0) {
                initialLoadingPolls--
                return GiftHomeScreen(target, GiftHomeSurface.UNKNOWN, 0, 24, width, height - 24, false)
            }
            return GiftHomeScreen(target, surface, 0, 24, width, height - 24,
                !(hideRewardTabs && surface == GiftHomeSurface.REWARDS), reportedTabY ?: height - 40f)
        }
        override fun click(target: GiftHomeTarget): Boolean {
            actions += "element:$target"
            if (elementWorks && elementMoves) surface = if (target == GiftHomeTarget.GIFT) GiftHomeSurface.REWARDS else GiftHomeSurface.FEED
            transientNullPolls = nullAfterEachClick
            return elementWorks
        }
        override fun tap(x: Float, y: Float, done: (Boolean) -> Unit) {
            actions += "coordinate"; points += x to y
            if (coordinateMoves) surface = if (x > width * 0.2f) GiftHomeSurface.REWARDS else GiftHomeSurface.FEED
            done(true)
        }
        override fun back(): Boolean { actions += "back"; surface = GiftHomeSurface.FEED; return true }
    }
    private fun run(port: Port, mode: GiftHomeMode): Pair<Boolean?, Scheduler> {
        val queue = Scheduler();var result: Boolean? = null
        GiftHomeNavigator(port, mode).prepare(queue, { true }) { result = it }
        queue.drain()
        return result to queue
    }

    @Test fun elementsVisitGiftThenHomeAndVerifyFeedWithoutCoordinates() {
        val port = Port()
        assertEquals(true, run(port, GiftHomeMode.ELEMENTS).first)
        assertEquals(listOf("element:GIFT", "element:HOME"), port.actions)
    }

    @Test fun autoFallsBackToCoordinatesWhenElementIsMissingOrAcceptedButDidNotMove() {
        for (missing in listOf(true, false)) {
            val port = Port().apply { elementWorks = !missing; elementMoves = false }
            assertEquals(true, run(port, GiftHomeMode.AUTO).first)
            assertTrue(port.actions.contains("coordinate"))
            assertTrue(port.points.all { it.second < port.height })
        }
    }

    @Test fun fullScreenRewardsUsesOneBackAndVerifiesFeed() {
        val port = Port().apply { hideRewardTabs = true }
        assertEquals(true, run(port, GiftHomeMode.ELEMENTS).first)
        assertEquals(listOf("element:GIFT", "back"), port.actions)
    }

    @Test fun coordinateModeUsesAppTabCenterAboveThreeButtonOrGestureNavigation() {
        for (bottom in listOf(1450, 1496)) {
            val port = Port().apply { height = bottom }
            assertEquals(true, run(port, GiftHomeMode.COORDINATES).first)
            assertTrue(port.actions.all { it == "coordinate" })
            assertEquals(port.width * 0.30f, port.points[0].first, 0.01f)
            assertEquals(port.width * 0.10f, port.points[1].first, 0.01f)
            assertTrue(port.points.all { it.second == bottom - 40f && it.second < bottom })
        }
    }

    @Test fun noTapForForeignAppUnknownScreenOrLandscape() {
        for (port in listOf(Port().apply { target = false },
            Port().apply { surface = GiftHomeSurface.UNKNOWN }, Port().apply { width = 1600; height = 900 })) {
            assertEquals(false, run(port, GiftHomeMode.COORDINATES).first)
            assertTrue(port.points.isEmpty())
        }
    }

    @Test fun refusesReportedTabCenterInsideSystemNavigationArea() {
        val port = Port().apply { height = 1450; reportedTabY = 1490f }
        assertEquals(false, run(port, GiftHomeMode.COORDINATES).first)
        assertTrue(port.points.isEmpty())
    }

    @Test fun completedGestureWithoutScreenTransitionIsFailureAndBounded() {
        val port = Port().apply { coordinateMoves = false }
        val (ok, scheduler) = run(port, GiftHomeMode.COORDINATES)
        assertEquals(false, ok)
        assertTrue(scheduler.now <= GiftHomeNavigator.MAX_POLLS * GiftHomeNavigator.POLL_MS)
        assertEquals(1, port.points.size)
    }

    @Test fun cancellationPreventsHomeOrBackAfterGiftAndNeverCallsCompletion() {
        val port = Port();val queue = Scheduler();var active = true;var result: Boolean? = null
        GiftHomeNavigator(port, GiftHomeMode.AUTO).prepare(queue, { active }) { result = it }
        active = false;queue.drain()
        assertEquals(listOf("element:GIFT"), port.actions)
        assertEquals(null, result)
    }

    @Test fun offDoesNotOperateAnyScreen() {
        val port = Port()
        assertEquals(true, run(port, GiftHomeMode.OFF).first)
        assertTrue(port.actions.isEmpty())
    }

    @Test fun waitsForInitialLoadingAndTemporaryMissingRootsAcrossBothTransitions() {
        val port = Port().apply { initialLoadingPolls = 4; nullAfterEachClick = 2 }
        assertEquals(true, run(port, GiftHomeMode.ELEMENTS).first)
        assertEquals(listOf("element:GIFT", "element:HOME"), port.actions)
    }
}
