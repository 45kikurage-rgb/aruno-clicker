package jp.aruno.clicker.automation

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Suggestions for user labelling only. A score is not confidence or permission to tap. */
internal object ElementReferenceCandidates {
    fun analyse(snapshot: JSONObject): JSONArray {
        val screen = snapshot.getJSONArray("screen")
        val sw = (screen.getInt(2) - screen.getInt(0)).coerceAtLeast(1)
        val sh = (screen.getInt(3) - screen.getInt(1)).coerceAtLeast(1)
        val density = snapshot.optDouble("density", 1.0).coerceAtLeast(0.5)
        val nodes = snapshot.getJSONArray("nodes").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        val visible = nodes.filter { it.optBoolean("visible") }
        val ranked = visible.mapNotNull { node ->
            val b = ElementReferenceSelection.bounds(node)
            val text = node.optString("text", "").trim()
            val desc = node.optString("description", "").trim()
            val id = node.optString("viewId", "")
            val reasons = JSONArray()
            var score = 0
            if (listOf(text, desc).any { it in setOf("×", "✕", "✖", "╳", "关闭", "閉じる", "Close", "close") }) {
                score += 100; reasons.put("close-label")
            }
            if (Regex("(^|[/_:.-])(close|dismiss)([/_:.-]|$)", RegexOption.IGNORE_CASE).containsMatchIn(id)) {
                score += 70; reasons.put("close-id")
            }
            val small = b.width() / density in 8.0..64.0 && b.height() / density in 8.0..64.0
            val square = b.width().toDouble() / b.height().coerceAtLeast(1) in 0.5..2.0
            val empty = node.optBoolean("textMissing") && node.optBoolean("descriptionMissing") && node.optBoolean("viewIdMissing")
            if (small && square && empty) { score += 20; reasons.put("small-blank-element") }
            if (score == 0 || !node.optBoolean("enabled", true)) return@mapNotNull null
            val cx = (b.centerX() - screen.getInt(0)).toDouble() / sw
            val cy = (b.centerY() - screen.getInt(1)).toDouble() / sh
            if (small && cx in 0.1..0.9 && cy in 0.15..0.85) { score += 5; reasons.put("central-area") }
            if (node.optBoolean("clickable")) { score += 5; reasons.put("clickable") }
            // Look for a nearby wide text/button above the candidate, not a white-card node.
            val anchors = visible.filter { other ->
                val ob = ElementReferenceSelection.bounds(other)
                other.optInt("id") != node.optInt("id") && !other.optBoolean("textMissing") &&
                    ob.width() >= sw * 0.25 && ob.height() < sh * 0.25 &&
                    b.top - ob.bottom in 0..(sh * 0.25).toInt() && abs(ob.centerX() - b.centerX()) < sw * 0.2
            }.sortedBy { b.top - ElementReferenceSelection.bounds(it).bottom }.take(8)
            if (small && anchors.isNotEmpty()) { score += 15; reasons.put("below-wide-content") }
            JSONObject().put("nodeId", node.getInt("id")).put("score", score).put("reasons", reasons)
                .put("anchorNodeIds", JSONArray(anchors.map { it.getInt("id") }))
                .put("normalizedBounds", JSONArray(listOf(
                    (b.left - screen.getInt(0)).toDouble() / sw, (b.top - screen.getInt(1)).toDouble() / sh,
                    (b.right - screen.getInt(0)).toDouble() / sw, (b.bottom - screen.getInt(1)).toDouble() / sh)))
                .put("widthDp", b.width() / density).put("heightDp", b.height() / density)
        }.sortedByDescending { it.getInt("score") }
        return JSONArray(ranked)
    }
}
