package jp.aruno.clicker.automation

import org.junit.Assert.*
import org.junit.Test

class RecentsTreeReaderTest {
    private fun node(key: String, id: String = "", cls: String = "android.view.View", labels: List<String> = emptyList(),
                     left: Int = 30, top: Int = 200, right: Int = 500, bottom: Int = 1200, parent: Int = -1,
                     visible: Boolean = true, dismiss: Boolean = false) =
        RecentNode(key, id, cls, labels, left, top, right, bottom, visible, parent, dismissAction = dismiss)
    private fun window(nodes: List<RecentNode>, owner: String = "com.android.systemui", layer: Int = 1,
                       trusted: Boolean = true, home: Boolean = false, complete: Boolean = true) =
        RecentWindow(owner, layer, trusted, home, nodes, complete)
    private fun read(vararg windows: RecentWindow) = RecentsTreeReader.read(windows.toList(), 1080, 1920)
    @Test fun ownFloatAboveOverviewDoesNotHideTwoVisibleCards() {
        val float = window(listOf(node("float", "task_view")), "jp.aruno.clicker", 10, false)
        val recents = window(listOf(node("first", "task_view"), node("second", "task_view", left=550, right=1030)))
        val result = read(float, recents)
        assertEquals(RecentsSurface.CARDS, result.screen.surface); assertEquals(2, result.screen.cards.size)
        assertEquals("com.android.systemui", result.window!!.owner)
    }
    @Test fun foregroundHomeRootCannotMaskAnotherOverviewWindow() {
        val home = window(listOf(node("home", "workspace")), "aruno.home", 5, home=true)
        assertEquals(RecentsSurface.CARDS, read(home, window(listOf(node("card", "task_view")))).screen.surface)
    }
    @Test fun classOnlyCardsOnOldSystemUiDoNotRequireResourceIds() {
        val result = read(window(listOf(node("card", cls="com.android.systemui.recents.views.TaskView"))))
        assertEquals("card", result.screen.cards.single().id)
    }
    @Test fun headerAndThumbnailFindActualCardSizedAncestor() {
        for (id in listOf("task_view_header", "task_view_thumbnail", "thumbnail")) {
            val parent = node("frame", top=100, bottom=1400)
            val header = node("header", id, top=120, bottom=180, parent=0)
            assertEquals("frame", read(window(listOf(parent, header))).screen.cards.single().id)
        }
    }
    @Test fun dismissActionCanIdentifyAnOemCardAncestor() {
        assertEquals("frame", read(window(listOf(node("frame"), node("dismiss", top=100, bottom=150, parent=0, dismiss=true)))).screen.cards.single().id)
    }
    @Test fun partialTreeStillAllowsVisibleCardsButNeverProvesEmpty() {
        assertEquals(RecentsSurface.CARDS, read(window(listOf(node("card", "task_view")), complete=false)).screen.surface)
        val empty = read(window(listOf(node("overview", "recents_view"), node("empty", "empty_message", labels=listOf("最近のアイテムはありません"))), complete=false))
        assertNotEquals(RecentsSurface.EMPTY, empty.screen.surface)
    }
    @Test fun positivelyKnownOverviewCanUseCoordinatesWithoutCardMetadata() {
        val result = read(window(listOf(node("root", cls="com.android.quickstep.views.RecentsView"))))
        assertEquals(RecentsSurface.CARDS, result.screen.surface); assertEquals("overview-position", result.screen.cards.single().id)
    }
    @Test fun arbitrarySystemUiOrAppNeverAuthorizesBlindCoordinates() {
        assertEquals(RecentsSurface.UNKNOWN, read(window(listOf(node("root")))).screen.surface)
        assertEquals(RecentsSurface.UNKNOWN, read(window(listOf(node("root", "task_view")), "com.other.app", trusted=false)).screen.surface)
    }
    @Test fun clearAllIsOnlyAllowedWithinConfirmedOverview() {
        val button = node("clear", "clear_all", labels=listOf("すべて閉じる"))
        assertEquals("clear", read(window(listOf(node("card", "task_view"), button))).clearKey)
        assertNull(read(window(listOf(button))).clearKey)
    }
    @Test fun notificationClearAllDoesNotBecomeRecentAppsClearAll() {
        val result = read(window(listOf(node("notification", "clear_all", labels=listOf("clear all")))))
        assertEquals(RecentsSurface.UNKNOWN, result.screen.surface); assertNull(result.clearKey)
    }
    @Test fun explicitEmptyWithKnownContainerIsRequiredForCompletion() {
        for (label in listOf("最近使用したアプリはありません", "no recent apps", "履歴なし")) {
            assertEquals(RecentsSurface.EMPTY, read(window(listOf(node("overview", "recents_view"), node("empty", "empty_message", labels=listOf(label))))).screen.surface)
        }
        assertEquals(RecentsSurface.UNKNOWN, read(window(listOf(node("unrelated", "text", labels=listOf("履歴なし"))))).screen.surface)
    }
    @Test fun genericSystemSettingsEmptyTextCannotProveEmptyHistory() {
        assertEquals(RecentsSurface.UNKNOWN, read(window(listOf(node("empty", "empty_text", labels=listOf("履歴なし"))), "com.android.settings")).screen.surface)
    }
    @Test fun invisibleCardsAreNotSwipedAndFullScreenContainersAreNotCards() {
        val result=read(window(listOf(node("hidden", "task_view", visible=false), node("full", "task_view", top=0, bottom=1920, left=0, right=1080))))
        assertTrue(result.screen.cards.isEmpty())
    }
    @Test fun nestedCardGroupIsNotMistakenForOneFrontCard() {
        val nodes=listOf(node("group", "task_container", left=0, right=1080, top=100, bottom=1500), node("one", "task_view"), node("two", "task_view", left=550, right=1030))
        assertEquals(setOf("one","two"),read(window(nodes)).screen.cards.map { it.id }.toSet())
    }
}
