package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo

/** Visible, bottom-tab scoped selectors; the supplied ec6 ID is one hint. */
internal class AndroidGiftHomePort(
    private val service: AccessibilityService,
    private val tapper: (Float, Float, (Boolean) -> Unit) -> Unit,
) : GiftHomePort {
    private fun nodes(): Pair<Rect, List<AccessibilityNodeInfo>>? {
        val root = service.rootInActiveWindow ?: return null
        if (root.packageName?.toString() !in AutomationConfig.TARGET_PACKAGES) return null
        val bounds = Rect().also(root::getBoundsInScreen)
        val manager = service.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = manager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsets(
                WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            val display = metrics.bounds
            bounds.intersect(Rect(display.left + insets.left, display.top + insets.top,
                display.right - insets.right, display.bottom - insets.bottom))
        } else {
            // On older devices the visible system navigation window gives its
            // actual height for 3-button navigation, without assuming fixed px.
            service.windows.orEmpty().filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM }
                .map { Rect().also(it::getBoundsInScreen) }
                .filter { !it.isEmpty && it.width() >= bounds.width() * 0.8f &&
                    it.top >= bounds.top + bounds.height() * 0.85f }
                .minOfOrNull { it.top }?.let { bounds.bottom = minOf(bounds.bottom, it) }
        }
        if (bounds.isEmpty) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val visible = mutableListOf<AccessibilityNodeInfo>()
        var scanned = 0
        while (queue.isNotEmpty() && scanned++ < 2_000) {
            val node = queue.removeFirst()
            val rect = Rect().also(node::getBoundsInScreen)
            if (node.isVisibleToUser && !rect.isEmpty && Rect.intersects(bounds, rect)) visible += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return bounds to visible
    }

    private fun isTab(node: AccessibilityNodeInfo, target: GiftHomeTarget, bounds: Rect): Boolean {
        val rect = Rect().also(node::getBoundsInScreen)
        if (!bounds.contains(rect.centerX(), rect.centerY())) return false
        if (rect.isEmpty || rect.centerY() < bounds.bottom - bounds.height() * 0.14f) return false
        val x = (rect.centerX() - bounds.left).toFloat() / bounds.width()
        if (target == GiftHomeTarget.GIFT && x !in 0.20f..0.40f) return false
        if (target == GiftHomeTarget.HOME && x !in 0f..0.20f) return false
        val name = if (target == GiftHomeTarget.GIFT) "ギフト" else "ホーム"
        val matchesLabel = listOf(node.text, node.contentDescription).any { it?.toString()?.trim() == name }
        val giftId = target == GiftHomeTarget.GIFT && node.viewIdResourceName ==
            "${node.packageName}:id/ec6"
        return matchesLabel || giftId
    }

    override fun screen(): GiftHomeScreen? {
        val (bounds, visible) = nodes() ?: return null
        val home = visible.filter { isTab(it, GiftHomeTarget.HOME, bounds) }
        val gift = visible.filter { isTab(it, GiftHomeTarget.GIFT, bounds) }
        val labels = visible.flatMap { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()) }
        val markers = listOf("チェックイン", "ポイント残高", "報酬", "アンロック", "受け取る", "動画を見る",
            "動画を見て", "コインストック", "ルーレット", "友達招待", "視聴する")
            .count { keyword -> labels.any { keyword in it } }
        val feed = home.isNotEmpty() && gift.isNotEmpty() &&
            (home.any { it.isSelected || it.parent?.isSelected == true } ||
                labels.any { it.trim() == "おすすめ" })
        val surface = when {
            feed -> GiftHomeSurface.FEED
            markers >= 2 -> GiftHomeSurface.REWARDS
            else -> GiftHomeSurface.UNKNOWN
        }
        return GiftHomeScreen(true, surface, bounds.left, bounds.top, bounds.width(), bounds.height(),
            home.isNotEmpty() && gift.isNotEmpty(),
            (home + gift).map { Rect().also(it::getBoundsInScreen).centerY().toFloat() }
                .takeIf { it.isNotEmpty() }?.average()?.toFloat())
    }

    override fun click(target: GiftHomeTarget): Boolean {
        val (bounds, visible) = nodes() ?: return false
        for (node in visible.filter { isTab(it, target, bounds) }) {
            var candidate: AccessibilityNodeInfo? = node
            repeat(4) {
                val current = candidate ?: return@repeat
                val rect = Rect().also(current::getBoundsInScreen)
                if (current.isVisibleToUser && current.isEnabled && current.isClickable &&
                    bounds.contains(rect.centerX(), rect.centerY()) &&
                    rect.width() <= bounds.width() * 0.45f &&
                    rect.centerY() >= bounds.bottom - bounds.height() * 0.14f &&
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                candidate = current.parent
            }
        }
        return false
    }

    override fun tap(x: Float, y: Float, done: (Boolean) -> Unit) {
        if (nodes() == null) { done(false); return }
        // Do not hit our own floating controls if the user placed them over a tab.
        val covered = service.windows.orEmpty().any { window ->
            val root = window.root
            root?.packageName?.toString() == service.packageName &&
                Rect().also(window::getBoundsInScreen).contains(x.toInt(), y.toInt())
        }
        if (covered) done(false) else tapper(x, y, done)
    }

    override fun back(): Boolean = nodes() != null && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
}
