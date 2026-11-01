package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import jp.aruno.clicker.data.RecentsLayout
import jp.aruno.clicker.overlay.PopupOverlayAvoidance
import org.json.JSONObject
import java.util.ArrayDeque

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
    // Separate queue: service stop clears its own handler, but float restoration must still run.
    private val gestureHandler = Handler(handler.looper)
    private val references = mutableMapOf<String, AccessibilityNodeInfo>()
    private var lastRead = RecentsRead(null, RecentsScreen(RecentsSurface.UNKNOWN))
    private var summary: List<JSONObject> = emptyList()
    override fun open() = ready() && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    override fun home() = ready() && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    private fun trusted(owner: String): Boolean = owner != service.packageName && (owner in launchers || owner == "com.android.systemui" ||
        runCatching { service.packageManager.getApplicationInfo(owner, 0).flags and
            (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }.getOrDefault(false))
    override fun screen(): RecentsScreen {
        references.clear()
        val snapshots = mutableListOf<RecentWindow>()
        val roots = mutableListOf<Triple<AccessibilityNodeInfo, Int, String>>()
        service.windows.orEmpty().filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
            it.type != AccessibilityWindowInfo.TYPE_INPUT_METHOD }.forEach { window ->
            window.root?.let { roots += Triple(it, window.layer, window.title?.toString().orEmpty()) }
        }
        service.rootInActiveWindow?.let { root ->
            if (roots.none { it.first.windowId == root.windowId }) roots += Triple(root, -1, "")
        }
        for ((root, layer, title) in roots) {
            val owner = root.packageName?.toString().orEmpty()
            if (!trusted(owner)) {
                snapshots += RecentWindow(owner, layer, false, false, emptyList(), title = title); continue
            }
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>().apply { add(root to -1) }
            val nodes = mutableListOf<RecentNode>(); var complete = true
            while (queue.isNotEmpty() && nodes.size < 2500) {
                val (node, parent) = queue.removeFirst()
                val index = nodes.size
                val rect = Rect().also(node::getBoundsInScreen)
                val key = "${root.windowId}:${node.hashCode()}"
                references[key] = node
                nodes += RecentNode(key, node.viewIdResourceName.orEmpty(), node.className?.toString().orEmpty(),
                    listOfNotNull(node.text?.toString(), node.contentDescription?.toString()), rect.left, rect.top, rect.right, rect.bottom,
                    node.isVisibleToUser, parent, node.drawingOrder,
                    node.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id })
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child == null) complete = false else queue.add(child to index)
                }
            }
            if (queue.isNotEmpty()) complete = false
            snapshots += RecentWindow(owner, layer, true, owner in launchers, nodes, complete, title)
        }
        summary = snapshots.take(8).map { window -> JSONObject().put("owner", window.owner).put("layer", window.layer)
            .put("trusted", window.trusted).put("complete", window.complete).put("nodes", window.nodes.size)
            .put("ids", org.json.JSONArray(window.nodes.map { it.id }.filter { it.isNotEmpty() }.distinct().take(50)))
            .put("classes", org.json.JSONArray(window.nodes.map { it.className }.distinct().take(20))) }
        val screen = bounds()
        lastRead = RecentsTreeReader.read(snapshots, screen.width(), screen.height())
        return lastRead.screen
    }
    override fun recordFailure(reason: String) {
        runCatching { PopupDiagnostics.save(service, JSONObject().put("kind", "RECENTS_CLEANUP")
            .put("reason", reason).put("windows", org.json.JSONArray(summary)).put("screen", bounds().toShortString())) }
    }
    override fun dismiss(card: RecentCard, layout: RecentsLayout, alternate: Boolean, done: (Boolean) -> Unit) {
        if (!ready()) { done(false); return }
        val latest = screen()
        if (latest.surface != RecentsSurface.CARDS) { done(latest.surface == RecentsSurface.EMPTY); return }
        // Read the actual current front card again; changing title/time labels must not invalidate all-card cleanup.
        val current = latest.cards.firstOrNull() ?: run { done(false); return }
        val clearNode = lastRead.clearKey?.let(references::get)
        if (clearNode != null) {
            var candidate: AccessibilityNodeInfo? = clearNode
            repeat(5) {
                val node = candidate
                if (node != null && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { done(true); return }
                candidate = node?.parent
            }
        }
        val screen = bounds()
        val x = if (alternate || current.id == "overview-position" && layout == RecentsLayout.VERTICAL)
            current.left + (current.right - current.left) * .67f else current.centerX
        val y = current.centerY
        val path = Path().apply {
            moveTo(x, y)
            if (layout == RecentsLayout.VERTICAL) lineTo(screen.left + screen.width() * .02f, y)
            else lineTo(x, screen.top + screen.height() * .03f)
        }
        val restore = PopupOverlayAvoidance.suspendTouches()
        if (restore == null) { done(false); return }
        var delivered = false
        lateinit var timeout: Runnable
        fun result(ok: Boolean) {
            if (!delivered) { delivered = true; gestureHandler.removeCallbacks(timeout); restore(); done(ok) }
        }
        timeout = Runnable { result(false) }
        gestureHandler.postDelayed(timeout, 1800L)
        // Allow WindowManager to remove the float's input region before injecting the swipe.
        gestureHandler.postDelayed({
            if (delivered) return@postDelayed
            if (!ready()) { result(false); return@postDelayed }
            val accepted = service.dispatchGesture(GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 280)).build(),
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = result(true)
                    override fun onCancelled(gestureDescription: GestureDescription?) = result(false)
                }, gestureHandler)
            if (!accepted) result(false)
        }, 120L)
    }
}
