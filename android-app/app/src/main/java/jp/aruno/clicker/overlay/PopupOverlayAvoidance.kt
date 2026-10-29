package jp.aruno.clicker.overlay

import android.graphics.Rect
import android.view.View
import android.view.WindowManager
import java.util.WeakHashMap

/** Move a covering controller; keep stop/pause available and do not persist this temporary position. */
internal object PopupOverlayAvoidance {
    private val overlays = WeakHashMap<View, WindowManager.LayoutParams>()
    fun register(view: View, params: WindowManager.LayoutParams) { overlays[view] = params }
    fun unregister(view: View) { overlays.remove(view) }
    fun bounds(): List<Rect> = overlays.keys.mapNotNull(::boundsOf)

    fun clear(target: Rect, screen: Rect): Boolean {
        for ((view, params) in overlays.toMap()) {
            val b = boundsOf(view) ?: return false
            if (!Rect.intersects(b, target)) continue
            val positions = listOf(screen.left to screen.top, (screen.right - b.width()) to screen.top,
                screen.left to (screen.bottom - b.height()), (screen.right - b.width()) to (screen.bottom - b.height()))
            val next = positions.firstOrNull { (x, y) ->
                x >= screen.left && y >= screen.top &&
                    !Rect.intersects(Rect(x, y, x + b.width(), y + b.height()), target)
            } ?: return false
            params.x += next.first - b.left
            params.y += next.second - b.top
            if (runCatching { view.context.getSystemService(WindowManager::class.java).updateViewLayout(view, params) }.isFailure) return false
            // A fresh scan after layout is required. No gesture in this frame.
            return false
        }
        return true
    }

    private fun boundsOf(view: View): Rect? {
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return null
        val xy = IntArray(2); view.getLocationOnScreen(xy)
        return Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
    }
}
