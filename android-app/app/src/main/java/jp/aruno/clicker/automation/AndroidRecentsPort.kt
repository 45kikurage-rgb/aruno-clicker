package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import jp.aruno.clicker.data.RecentsLayout
import java.util.ArrayDeque

/** Reads only the active launcher/SystemUI window. Two visible cards are acquired afresh after every swipe. */
internal class AndroidRecentsPort(
    private val service: AccessibilityService,
    private val handler: Handler,
    private val bounds: () -> Rect,
    private val ready: () -> Boolean,
) : RecentsPort {
    private val launchers by lazy {
        service.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .mapNotNull { it.activityInfo?.packageName }.toSet()
    }
    override fun open() = ready() && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    override fun home() = ready() && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    override fun screen(): RecentsScreen {
        val root = service.rootInActiveWindow ?: return RecentsScreen(RecentsSurface.UNKNOWN)
        val owner = root.packageName?.toString() ?: return RecentsScreen(RecentsSurface.UNKNOWN)
        if (owner !in launchers && owner != "com.android.systemui") return RecentsScreen(RecentsSurface.UNKNOWN)
        val screen = bounds()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val cards = mutableListOf<Pair<AccessibilityNodeInfo, Rect>>()
        var visited = 0
        var complete = true
        var overview = false
        var home = false
        var explicitEmpty = false
        while (queue.isNotEmpty() && visited++ < 2500) {
            val node = queue.removeFirst()
            if (node.packageName?.toString() != owner) continue
            val id = node.viewIdResourceName.orEmpty().substringAfter(":id/").lowercase()
            if (node.isVisibleToUser) {
                if (id in OVERVIEW_IDS) overview = true
                if (id in HOME_IDS) home = true
                val label = sequenceOf(node.text, node.contentDescription).mapNotNull { it?.toString()?.trim() }
                if (label.any { it.lowercase() in EMPTY_LABELS }) explicitEmpty = true
                val rect = Rect().also(node::getBoundsInScreen)
                if (id in CARD_IDS && rect.intersect(screen) && rect.width() >= screen.width() * .18f &&
                    rect.height() >= screen.height() * .10f && rect.height() < screen.height() * .96f) {
                    cards += node to rect
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child == null) complete = false else queue.add(child)
            }
        }
        if (queue.isNotEmpty()) complete = false
        if (!complete) return RecentsScreen(RecentsSurface.UNKNOWN)
        // Keep innermost task cards, excluding any parent grouping around a two-card row.
        val leaves = cards.filter { outer -> cards.none { inner ->
            inner !== outer && inner.second != outer.second && outer.second.contains(inner.second)
        } }.distinctBy { it.second }.sortedByDescending { it.first.drawingOrder }
        if (leaves.isNotEmpty()) return RecentsScreen(RecentsSurface.CARDS, leaves.map { (node, rect) ->
            val labels = mutableListOf<String>()
            val descendants = ArrayDeque<AccessibilityNodeInfo>().apply { add(node) }
            var n = 0
            while (descendants.isNotEmpty() && n++ < 150) {
                val child = descendants.removeFirst()
                labels += child.text?.toString().orEmpty()
                labels += child.contentDescription?.toString().orEmpty()
                for (i in 0 until child.childCount) child.getChild(i)?.let(descendants::addLast)
            }
            RecentCard(labels.joinToString("|").hashCode().toString(), rect.left, rect.top, rect.right, rect.bottom)
        })
        if (explicitEmpty && (overview || !home)) return RecentsScreen(RecentsSurface.EMPTY)
        if (home && !overview) return RecentsScreen(RecentsSurface.HOME)
        return RecentsScreen(RecentsSurface.UNKNOWN)
    }
    override fun dismiss(card: RecentCard, layout: RecentsLayout, alternate: Boolean, done: (Boolean) -> Unit) {
        if (!ready()) { done(false); return }
        // Re-read before every gesture; never swipe stale card positions after a layout change.
        val latest = screen()
        if (latest.surface != RecentsSurface.CARDS || card !in latest.cards) { done(false); return }
        val screen = bounds()
        val points = listOf(
            (if (alternate) card.left + (card.right - card.left) * .67f else card.centerX) to card.centerY,
            card.centerX to (card.top + (card.bottom - card.top) * .65f),
            (card.left + (card.right - card.left) * .33f) to card.centerY,
        )
        val above = latest.cards.takeWhile { it != card }
        val point = points.firstOrNull { (x, y) -> above.none { x > it.left && x < it.right && y > it.top && y < it.bottom } }
        if (point == null) { done(false); return }
        val (x, y) = point
        val path = Path().apply {
            moveTo(x, y)
            if (layout == RecentsLayout.VERTICAL) lineTo(screen.left + screen.width() * .02f, y)
            else lineTo(x, screen.top + screen.height() * .03f)
        }
        var delivered = false
        fun result(ok: Boolean) { if (!delivered) { delivered = true; done(ok) } }
        val accepted = service.dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = result(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = result(false)
            }, handler)
        if (!accepted) result(false)
    }
    companion object {
        private val CARD_IDS = setOf("task_view", "taskview", "task", "task_card", "task_container", "recent_task", "recent_app", "recents_card", "recents_task_view")
        private val OVERVIEW_IDS = setOf("recents_view", "recentsview", "overview_panel", "recents_container", "recents", "recent_apps", "task_stack_view", "recents_view_container")
        private val HOME_IDS = setOf("workspace", "hotseat", "launcher_workspace", "desktop", "cell_layout")
        private val EMPTY_LABELS = setOf("最近使ったアプリはありません", "最近使用したアプリはありません", "最近のアプリはありません", "最近使用したアプリなし", "最近使ったアプリなし", "履歴はありません", "履歴がありません", "履歴なし", "最近のアイテムはありません", "no recent items", "no recent apps", "no recent applications", "nothing here yet", "すべてクリアしました")
    }
}
