package jp.aruno.clicker.automation

/** Platform snapshots are flattened before classification, so overlay/window and OEM cases are testable. */
internal data class RecentNode(
    val key: String, val id: String = "", val className: String = "", val labels: List<String> = emptyList(),
    val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0,
    val visible: Boolean = true, val parent: Int = -1, val order: Int = 0, val dismissAction: Boolean = false,
)
internal data class RecentWindow(
    val owner: String, val layer: Int, val trusted: Boolean, val isHome: Boolean,
    val nodes: List<RecentNode>, val complete: Boolean = true, val title: String = "",
)
internal data class RecentsRead(val window: RecentWindow?, val screen: RecentsScreen, val clearKey: String? = null)
internal object RecentsTreeReader {
    private fun normalized(value: String) = value.substringAfter(":id/").lowercase().replace("_", "")
    private val cardIds = setOf("taskview", "task", "taskcard", "taskcontainer", "recenttask", "recentapp", "recentscard", "recentstaskview", "recenttaskview")
    private val overviewIds = setOf("recentsview", "overviewpanel", "recentscontainer", "recents", "recentapps", "taskstackview", "recentsviewcontainer", "recentsroot", "taskstack", "recentsrootview")
    private val homeIds = setOf("workspace", "hotseat", "launcherworkspace", "desktop", "celllayout")
    private val emptyIds = setOf("emptymessage", "emptytext", "emptyview", "recentsemptyview", "emptyrecentsview", "norecentapps", "emptystatemessage")
    private val clearIds = setOf("clearall", "clearallbutton", "closeall", "closeallbutton", "dismissall", "dismissallbutton", "recentsclearall", "clearanimview", "clearalltask")
    private val clearLabels = setOf("すべて閉じる", "全て閉じる", "すべて消去", "全て消去", "すべて削除", "全て削除", "全アプリ終了", "すべてクリア", "clear all", "close all", "dismiss all")
    private val emptyLabels = setOf("最近使ったアプリはありません", "最近使ったアプリがありません", "最近使用したアプリがありません", "最近使用したアプリはありません", "最近のアプリはありません", "最近使用したアプリなし", "最近使ったアプリなし", "履歴はありません", "履歴がありません", "履歴なし", "最近のアイテムはありません", "no recent items", "no recent apps", "no recent applications", "nothing here yet", "すべてクリアしました")
    fun read(windows: List<RecentWindow>, width: Int, height: Int): RecentsRead {
        val reads = windows.filter { it.trusted }.sortedByDescending { it.layer }.map { window -> classify(window, width, height) }
        // An own-app float or a stale application root must never hide a valid lower overview window.
        return reads.firstOrNull { it.screen.surface in listOf(RecentsSurface.CARDS, RecentsSurface.EMPTY) }
            ?: reads.firstOrNull { it.screen.surface == RecentsSurface.HOME }
            ?: RecentsRead(null, RecentsScreen(RecentsSurface.UNKNOWN, detail = "履歴画面の要素を確認できません（画面${windows.size}件）"))
    }
    private fun classify(w: RecentWindow, width: Int, height: Int): RecentsRead {
        val visible = w.nodes.withIndex().filter { it.value.visible }
        fun sized(n: RecentNode) = n.right - n.left >= width * .18f && n.bottom - n.top >= height * .10f &&
            n.bottom - n.top < height * .96f && n.right > 0 && n.bottom > 0 && n.left < width && n.top < height
        val classes = visible.map { it.value.className.substringAfterLast('.').lowercase() }
        var overview = visible.any { normalized(it.value.id) in overviewIds } ||
            classes.any { it in setOf("recentsview", "taskstackview", "overviewpanel", "recentappsview", "recentsactivity") } ||
            w.title.lowercase() in setOf("recent apps", "recents", "最近使ったアプリ", "最近使用したアプリ", "履歴")
        val candidates = linkedMapOf<Int, RecentNode>()
        for ((index, node) in visible) {
            val id = normalized(node.id)
            val cls = node.className.substringAfterLast('.').lowercase()
            if ((id in cardIds || cls in setOf("taskview", "taskcardview", "recenttaskview")) && sized(node)) candidates[index] = node
            // Some OEMs expose only thumbnail/header IDs or a dismiss action. Use an actual card-sized ancestor.
            if (id in setOf("thumbnail", "taskviewthumbnail", "taskthumbnail", "taskviewheader", "taskheader") || node.dismissAction) {
                var parent = node.parent; var depth = 0
                while (parent in w.nodes.indices && depth++ < 7) {
                    val ancestor = w.nodes[parent]
                    if (ancestor.visible && sized(ancestor)) { candidates[parent] = ancestor; break }
                    parent = ancestor.parent
                }
            }
        }
        if (candidates.isNotEmpty()) overview = true
        val leaves = candidates.values.filter { outer -> candidates.values.none { inner ->
            inner !== outer && inner.left >= outer.left && inner.top >= outer.top && inner.right <= outer.right && inner.bottom <= outer.bottom &&
                listOf(inner.left, inner.top, inner.right, inner.bottom) != listOf(outer.left, outer.top, outer.right, outer.bottom)
        } }.distinctBy { listOf(it.left, it.top, it.right, it.bottom) }.sortedByDescending { it.order }
        val clear = if (overview) visible.firstOrNull { (_, n) -> normalized(n.id) in clearIds || n.labels.any { it.trim().lowercase() in clearLabels } }?.value else null
        val empty = visible.filter { (_, n) -> n.labels.any { it.trim().lowercase() in emptyLabels } }
        val dedicatedEmpty = empty.any { normalized(it.value.id) in emptyIds }
        val detail = "履歴表示：${w.owner}・カード${leaves.size}枚" + if (!w.complete) "（一部取得）" else ""
        if (leaves.isNotEmpty()) return RecentsRead(w, RecentsScreen(RecentsSurface.CARDS, leaves.map {
            RecentCard(it.key, it.left.coerceAtLeast(0), it.top.coerceAtLeast(0), it.right.coerceAtMost(width), it.bottom.coerceAtMost(height))
        }, detail), clear?.key)
        if (RecentsEmptyEvidence.verified(empty.isNotEmpty(), overview, dedicatedEmpty && (w.isHome || w.owner == "com.android.systemui"), 0, w.complete))
            return RecentsRead(w, RecentsScreen(RecentsSurface.EMPTY, detail = "履歴なしの表示を確認中"))
        // Coordinate fallback is allowed only on positively recognized overview, never on an arbitrary launcher or app.
        if (overview && width > 0 && height > 0) return RecentsRead(w, RecentsScreen(RecentsSurface.CARDS,
            listOf(RecentCard("overview-position", (width * .20f).toInt(), (height * .22f).toInt(), (width * .80f).toInt(), (height * .78f).toInt())),
            "履歴画面を確認・カード位置で削除"), clear?.key)
        val home = visible.any { normalized(it.value.id) in homeIds }
        if ((home || w.isHome) && !overview) return RecentsRead(w, RecentsScreen(RecentsSurface.HOME, detail = "ホーム画面を確認"))
        return RecentsRead(w, RecentsScreen(RecentsSurface.UNKNOWN, detail = "履歴の要素が未対応：${w.owner}"))
    }
}
