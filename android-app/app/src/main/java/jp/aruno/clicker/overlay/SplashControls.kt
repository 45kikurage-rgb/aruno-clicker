package jp.aruno.clicker.overlay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import jp.aruno.clicker.data.SplashFrequencyStore

/** In-window choices work with non-focusable overlay windows; no popup Spinner. */
internal class SplashControls(context: Context, private val onChanged: () -> Unit) : LinearLayout(context) {
    private val prefs = SplashFrequencyStore(context)
    private val toggle = Button(context)
    private val interval = Button(context)
    private val options = LinearLayout(context).apply { orientation = VERTICAL; visibility = GONE }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    init {
        orientation = VERTICAL
        val headings = LinearLayout(context)
        listOf("スプラッシュ", "間隔").forEach { value ->
            headings.addView(TextView(context).apply {
                text = value; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(headings)
        val row = LinearLayout(context)
        listOf(toggle, interval).forEach { button ->
            button.textSize = 12f
            button.setTextColor(Color.WHITE)
            button.setBackgroundColor(Color.rgb(25, 90, 58))
            button.minimumWidth = 0
            button.setPadding(0, 0, 0, 0)
            row.addView(button, LayoutParams(0, dp(48), 1f))
        }
        toggle.contentDescription = "スプラッシュ ON OFF"
        toggle.setOnClickListener { save { prefs.setEnabled(!prefs.isEnabled()) } }
        interval.contentDescription = "スプラッシュ間隔のリストを選択"
        interval.setOnClickListener { options.visibility = if (options.visibility == GONE) VISIBLE else GONE }
        addView(row)
        for (number in 1..5) {
            options.addView(Button(context).apply {
                text = "${number}件ごと"
                setOnClickListener {
                    if (save { prefs.setInterval(number) }) options.visibility = GONE
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        }
        addView(options)
        refresh()
    }

    private fun save(write: () -> Boolean): Boolean {
        val success = runCatching(write).getOrDefault(false)
        if (success) onChanged()
        else Toast.makeText(context, "設定を保存できませんでした", Toast.LENGTH_SHORT).show()
        refresh()
        return success
    }

    fun refresh() {
        toggle.text = if (prefs.isEnabled()) "ON" else "OFF"
        interval.text = "${prefs.interval()}件ごと ▾"
    }
}
