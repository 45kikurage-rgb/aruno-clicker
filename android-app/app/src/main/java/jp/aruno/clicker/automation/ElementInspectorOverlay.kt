package jp.aruno.clicker.automation

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Consumes inspection touches; never forwards a tap to TikTok. */
internal class ElementInspectorOverlay(
    private val service: AccessibilityService,
    private val snapshot: JSONObject,
    private val onDismiss: () -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val rows = snapshot.getJSONArray("nodes")
    private val candidates = snapshot.getJSONArray("candidates")
    private var root: FrameLayout? = null
    private var selected: JSONObject? = null
    private var region: Rect? = null
    private var regionMode = false
    private var matches = emptyList<JSONObject>()
    private var matchIndex = 0
    private var candidateIndex = -1
    private lateinit var details: TextView
    private lateinit var yes: Button
    private lateinit var no: Button
    private lateinit var drawing: View

    fun show() {
        val frame = FrameLayout(service)
        drawing = object : View(service) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat() }
            private var downX = 0
            private var downY = 0
            override fun onDraw(canvas: Canvas) {
                val location = IntArray(2).also { getLocationOnScreen(it) }
                canvas.save(); canvas.translate(-location[0].toFloat(), -location[1].toFloat())
                paint.color = Color.argb(155, 255, 85, 85); paint.strokeWidth = dp(1).toFloat()
                for (i in 0 until rows.length()) {
                    val node = rows.getJSONObject(i)
                    if (node.optBoolean("visible")) canvas.drawRect(ElementReferenceSelection.bounds(node), paint)
                }
                paint.color = Color.rgb(255, 180, 0); paint.strokeWidth = dp(2).toFloat()
                for (i in 0 until min(candidates.length(), 12)) {
                    canvas.drawRect(ElementReferenceSelection.bounds(rows.getJSONObject(candidates.getJSONObject(i).getInt("nodeId"))), paint)
                }
                paint.color = Color.GREEN; paint.strokeWidth = dp(3).toFloat()
                (region ?: selected?.let(ElementReferenceSelection::bounds))?.let { canvas.drawRect(it, paint) }
                canvas.restore()
            }
            override fun onTouchEvent(event: MotionEvent): Boolean {
                val x = event.rawX.toInt(); val y = event.rawY.toInt()
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = x; downY = y; return true }
                    MotionEvent.ACTION_MOVE -> if (regionMode) {
                        region = clippedRect(downX, downY, x, y); selected = null; update()
                    }
                    MotionEvent.ACTION_UP -> {
                        if (regionMode) {
                            region = clippedRect(downX, downY, x, y).takeUnless { it.isEmpty }
                            selected = null
                        } else {
                            matches = ElementReferenceSelection.atPoint(snapshot, x, y)
                            matchIndex = 0; selected = matches.firstOrNull(); region = null
                        }
                        performClick(); update()
                    }
                }
                return true
            }
            override fun performClick(): Boolean { super.performClick(); return true }
        }
        frame.addView(drawing, FrameLayout.LayoutParams(-1, -1))
        val top = panel()
        top.addView(label("×の要素確認：赤＝全要素／橙＝候補／緑＝選択"))
        top.addView(label("${snapshot.optInt("visibleCount")}要素・${if (snapshot.optBoolean("complete")) "取得完了" else "一部未取得"}。枠を選んで確認してください。"))
        top.addView(button("取消・戻る") { dismiss() })
        frame.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bottom = panel()
        details = label("候補は未確定です。×をタップして選択してください。")
        details.maxLines = 5
        bottom.addView(details)
        val choices = LinearLayout(service)
        choices.addView(button("候補次へ") { nextCandidate() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        choices.addView(button("重なり切替") {
            if (matches.isNotEmpty()) { matchIndex = (matchIndex + 1) % matches.size; selected = matches[matchIndex]; region = null; update() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        choices.addView(button("枠を指定") {
            regionMode = !regionMode; region = null; selected = null
            details.text = if (regionMode) "要素が取れない×を指で囲んでください。" else "×の要素をタップしてください。"
            drawing.invalidate(); yes.isEnabled = false; no.isEnabled = false
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(choices)
        bottom.addView(label("緑枠は×ですか？（端末に記録します）"))
        val answers = LinearLayout(service)
        yes = button("はい") { save(true) }
        no = button("いいえ") { save(false) }
        answers.addView(yes, LinearLayout.LayoutParams(0, dp(48), 1f))
        answers.addView(no, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(answers)
        frame.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        wm.addView(frame, params); root = frame; update()
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }; root = null; onDismiss()
    }
    private fun nextCandidate() {
        if (candidates.length() == 0) { details.text = "候補なし。×を選ぶか、枠を指定してください。"; return }
        candidateIndex = (candidateIndex + 1) % candidates.length()
        selected = rows.getJSONObject(candidates.getJSONObject(candidateIndex).getInt("nodeId"))
        regionMode = false; region = null
        val b = ElementReferenceSelection.bounds(selected!!)
        matches = ElementReferenceSelection.atPoint(snapshot, b.centerX(), b.centerY())
        matchIndex = matches.indexOfFirst { it.optInt("id") == selected!!.optInt("id") }.coerceAtLeast(0)
        update()
    }
    private fun update() {
        val b = region ?: selected?.let(ElementReferenceSelection::bounds)
        yes.isEnabled = b != null; no.isEnabled = b != null
        details.text = when {
            region != null -> "位置指定 ${b!!.width()}×${b.height()}px（要素として取得できない場所も記録可能）"
            selected != null -> {
                val n = selected!!
                "要素 #${n.optInt("id")} ${b!!.width()}×${b.height()}px\n" +
                    "文字：${display(n, "text")}／説明：${display(n, "description")}\nID：${display(n, "viewId")}\n" +
                    "親 #${n.optInt("parent")}・クリック${if (n.optBoolean("clickable")) "可" else "不可"}・重なり${matches.size}件"
            }
            regionMode -> "要素が取れない×を指で囲んでください。"
            else -> "候補は未確定です。×をタップして選択してください。"
        }
        drawing.invalidate()
    }
    private fun save(isClose: Boolean) {
        try {
            val record = region?.let { ElementReferenceSelection.labelledRegion(snapshot, it, isClose) }
                ?: selected?.let { ElementReferenceSelection.labelled(snapshot, it.getInt("id"), isClose) } ?: return
            ElementReferenceStore(service).save(record)
            Toast.makeText(service, "${if (isClose) "×の参考" else "×以外の参考"}として端末に保存しました。送信ボタンで中央へ送れます", Toast.LENGTH_LONG).show()
            dismiss()
        } catch (_: Exception) { Toast.makeText(service, "保存できませんでした。空き容量・保存上限を確認してください", Toast.LENGTH_LONG).show() }
    }
    private fun clippedRect(x1: Int, y1: Int, x2: Int, y2: Int): Rect {
        val a = snapshot.getJSONArray("screen")
        val screen = Rect(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
        return Rect(min(x1, x2), min(y1, y2), max(x1, x2) + 1, max(y1, y2) + 1).apply { if (!intersect(screen)) setEmpty() }
    }
    private fun display(n: JSONObject, key: String) = if (n.isNull(key) || n.optString(key).isBlank()) "空" else n.optString(key).take(80)
    private fun panel() = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(Color.argb(245, 8, 28, 18)) }
    private fun label(value: String) = TextView(service).apply { text = value; textSize = 13f; setTextColor(Color.WHITE) }
    private fun button(value: String, action: () -> Unit) = Button(service).apply { text = value; textSize = 12f; minHeight = dp(48); setOnClickListener { action() } }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
}
