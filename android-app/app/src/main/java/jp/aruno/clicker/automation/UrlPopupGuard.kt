package jp.aruno.clicker.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/** Counters belong to an appearance, never to an animated close-button rectangle. */
internal class PopupAttemptBudget {
    var attempts = 0
        private set
    var totalAttempts = 0
        private set
    private var firstSeen = -1L
    private var absentSince = -1L
    private var lastAttempt = -1L
    var exhausted = false
        private set

    fun observe(present: Boolean, now: Long) {
        if (present) {
            absentSince = -1L
            if (firstSeen < 0L) firstSeen = now
        } else {
            if (absentSince < 0L) absentSince = now
            if (now - absentSince >= 1_000L) {
                firstSeen = -1L
                attempts = 0
                exhausted = false
            }
        }
    }

    fun reserve(now: Long): Boolean {
        if (!blocking(now) || (lastAttempt >= 0L && now - lastAttempt < 900L)) return false
        attempts++
        totalAttempts++
        lastAttempt = now
        return true
    }

    fun blocking(now: Long): Boolean {
        if (firstSeen < 0L || absentSince >= 0L) return false
        if (attempts >= 3 || totalAttempts >= 6 || now - firstSeen >= 8_000L) exhausted = true
        return !exhausted
    }
}

internal data class PopupWindowRoot(val root: AccessibilityNodeInfo, val type: Int, val layer: Int)
internal data class UrlPopupCandidate(val node: AccessibilityNodeInfo?, val bounds: Rect, val method: String)
internal data class UrlPopupScan(
    val known: Boolean,
    val complete: Boolean,
    val candidate: UrlPopupCandidate?,
    val diagnostic: JSONObject,
    val forbiddenBounds: List<Rect> = emptyList(),
)

/** A complete, package-filtered scan; unknown trees are never proof of dismissal. */
internal object PopupCloseScanner {
    private data class Node(val info: AccessibilityNodeInfo, val bounds: Rect, val parent: Int)
    private val forbidden = listOf("始める", "受け取る", "招待", "ゲット", "獲得", "応募", "購入", "start", "claim")

    fun scan(roots: List<PopupWindowRoot>, screen: Rect, targetPackage: String): UrlPopupScan {
        val windows = JSONArray()
        var complete = roots.isNotEmpty() && roots.size <= 8
        var known = false
        var candidate: UrlPopupCandidate? = null
        val forbiddenBounds = ArrayList<Rect>()
        var remaining = 4_000
        for (window in roots.take(8)) {
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            queue.add(window.root to -1)
            val nodes = ArrayList<Node>()
            val labels = ArrayList<String>()
            val jsonNodes = JSONArray()
            while (queue.isNotEmpty() && remaining > 0) {
                val (node, parent) = queue.removeFirst()
                remaining--
                val b = Rect().also(node::getBoundsInScreen)
                val position = nodes.size
                nodes.add(Node(node, b, parent))
                val text = node.text?.toString().orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                if (node.isVisibleToUser && !b.isEmpty && Rect.intersects(screen, b)) {
                    labels.add(text); labels.add(description)
                }
                jsonNodes.put(JSONObject().put("id", position).put("parent", parent)
                    .put("text", diagnosticText(text)).put("description", diagnosticText(description))
                    .put("viewId", node.viewIdResourceName).put("class", node.className?.toString())
                    .put("bounds", b.toShortString()).put("clickable", node.isClickable)
                    .put("visible", node.isVisibleToUser).put("enabled", node.isEnabled))
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child == null) complete = false else queue.add(child to position)
                }
            }
            if (queue.isNotEmpty()) complete = false
            windows.put(JSONObject().put("package", window.root.packageName?.toString())
                .put("type", window.type).put("layer", window.layer).put("nodes", jsonNodes))
            if (window.root.packageName?.toString() != targetPackage) continue
            val visible = nodes.filter { it.info.isVisibleToUser && it.info.isEnabled && !it.bounds.isEmpty }
            val ctas = visible.filter { n ->
                val label = (n.info.text?.toString().orEmpty() + " " + n.info.contentDescription?.toString().orEmpty()).lowercase()
                forbidden.any(label::contains)
            }.map { it.bounds }
            forbiddenBounds.addAll(ctas)
            val coinPopup = InterruptionClassifier.isCoinStockPopup(labels)
            // Explicit close controls are shared by all popup types. Unknown
            // screens get no coordinate guesses: only a small labelled close.
            val genericMarkers = nodes.filter { n ->
                n.info.isVisibleToUser && safeElementBounds(n.bounds, screen) &&
                    n.bounds.width() <= screen.width() * .18f && n.bounds.height() <= screen.height() * .10f &&
                    PopupCloseClassifier.score(n.info.text, n.info.contentDescription, n.info.viewIdResourceName) >= 70
            }
            val genericClose = visible.filter { n ->
                safeElementBounds(n.bounds, screen) &&
                    n.bounds.width() <= screen.width() * .18f && n.bounds.height() <= screen.height() * .10f &&
                    PopupCloseClassifier.score(n.info.text, n.info.contentDescription, n.info.viewIdResourceName) >= 70 &&
                    ctas.none { Rect.intersects(n.bounds, it) }
            }.maxByOrNull { PopupCloseClassifier.score(it.info.text, it.info.contentDescription, it.info.viewIdResourceName) }
            if (!coinPopup) {
                // A disabled close during a loading animation is still present;
                // lack of an actionable candidate is not dismissal evidence.
                if (genericMarkers.isNotEmpty()) known = true
                if (genericClose != null && candidate == null) {
                    candidate = UrlPopupCandidate(genericClose.info, Rect(genericClose.bounds), "要素")
                }
                continue
            }
            known = true
            if (candidate != null) continue
            val start = visible.firstOrNull {
                InterruptionClassifier.isCoinStockStartAction(it.info.text, it.info.contentDescription)
            } ?: continue
            val titles = visible.filter { (it.info.text?.toString().orEmpty() + it.info.contentDescription).contains("コインストック") }
            val exact = visible.filter { n ->
                safeCloseBounds(n.bounds, screen) &&
                    n.bounds.centerY() >= start.bounds.bottom &&
                    kotlin.math.abs(n.bounds.centerX() - start.bounds.centerX()) <= screen.width() * .18f &&
                    PopupCloseClassifier.score(n.info.text, n.info.contentDescription, n.info.viewIdResourceName) > 0 &&
                    ctas.none { Rect.intersects(n.bounds, it) }
            }.maxByOrNull { PopupCloseClassifier.score(it.info.text, it.info.contentDescription, it.info.viewIdResourceName) }
            if (exact != null) {
                candidate = UrlPopupCandidate(exact.info, Rect(exact.bounds), "要素")
                continue
            }
            // Only a bounded ancestor containing both the title and CTA is a card.
            // Accessibility exposes geometry, not the white background colour.
            var ancestor = start.parent
            var card: Rect? = null
            repeat(6) {
                val row = nodes.getOrNull(ancestor) ?: return@repeat
                val b = row.bounds
                if (card == null && b.contains(start.bounds) && titles.any { b.contains(it.bounds) } &&
                    b.width() in (screen.width() * .45f).toInt()..(screen.width() * .96f).toInt() &&
                    b.height() in (screen.height() * .18f).toInt()..(screen.height() * .65f).toInt() &&
                    b.bottom > start.bounds.bottom && b.bottom < screen.top + screen.height() * .82f) card = Rect(b)
                ancestor = row.parent
            }
            val x = card?.centerX() ?: start.bounds.centerX()
            val y = if (card != null) (card!!.bottom + screen.height() * .035f).toInt()
                else (start.bounds.bottom + screen.height() * .065f).toInt()
            val r = (screen.width() * .025f).toInt().coerceAtLeast(6)
            val b = Rect(x - r, y - r, x + r, y + r)
            if (safeCloseBounds(b, screen) && b.top > start.bounds.bottom && ctas.none { Rect.intersects(it, b) }) {
                candidate = UrlPopupCandidate(null, b, if (card != null) "カード下" else "ボタン下推定")
            }
        }
        val diagnostic = JSONObject().put("windows", windows).put("truncatedOrUnavailable", !complete)
            .put("popupVisible", known).put("candidateMethod", candidate?.method)
            .put("candidateBounds", candidate?.bounds?.toShortString())
        return UrlPopupScan(known, complete, candidate.takeIf { complete }, diagnostic, forbiddenBounds)
    }

    internal fun safeElementBounds(bounds: Rect, screen: Rect): Boolean = !screen.isEmpty &&
        screen.contains(bounds) && bounds.width() in 6..(screen.width() / 4) &&
        bounds.height() in 6..(screen.height() / 6) &&
        bounds.centerY() in (screen.top + screen.height() * .02f).toInt()..(screen.top + screen.height() * .94f).toInt()

    internal fun safeCloseBounds(bounds: Rect, screen: Rect): Boolean = !screen.isEmpty &&
        screen.contains(bounds) && bounds.width() in 6..(screen.width() / 4) &&
        bounds.height() in 6..(screen.height() / 6) &&
        bounds.centerX() in (screen.left + screen.width() * .25f).toInt()..(screen.left + screen.width() * .75f).toInt() &&
        bounds.centerY() in (screen.top + screen.height() * .35f).toInt()..(screen.top + screen.height() * .88f).toInt()

    private fun diagnosticText(value: String): String = value
        .replace(Regex("https?://\\S+"), "[URL]").take(240)
}
