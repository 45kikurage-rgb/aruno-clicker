package jp.aruno.clicker.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

data class AccessibilityEventRecord(val at: Long, val category: String, val detail: String)

data class AccessibilityHealthSnapshot(
    val enabledInSettings: Boolean,
    val serviceConnected: Boolean,
    val lastStop: String,
    val recent: List<AccessibilityEventRecord>,
)

class AccessibilityDiagnostics(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("aruno_accessibility_diagnostics", Context.MODE_PRIVATE)

    fun snapshot(): AccessibilityHealthSnapshot = AccessibilityHealthSnapshot(
        enabledInSettings = isEnabledInSettings(app),
        serviceConnected = ArunoAccessibilityService.instance != null,
        lastStop = prefs.getString("last_stop", "").orEmpty(),
        recent = history(),
    )

    fun connected() = record("接続", "ユーザー補助サービス接続")
    fun disconnected() = record("切断", "ユーザー補助サービス切断")
    fun interrupted(active: Boolean) {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_interrupt_at", 0L) in 0 until 5000L) return
        prefs.edit().putLong("last_interrupt_at", now).apply()
        record("中断通知", if (active) "中断通知、動画処理は継続" else "停止中の中断通知")
    }

    fun popup(detail: String) = record("×解除", detail)

    fun stopped(reason: String) = record("停止", reason, true)

    fun history(): List<AccessibilityEventRecord> = runCatching {
        val a = JSONArray(prefs.getString("events", "[]").orEmpty())
        (0 until minOf(a.length(), 15)).mapNotNull { n ->
            val v = a.optJSONObject(n) ?: return@mapNotNull null
            AccessibilityEventRecord(v.optLong("at"), v.optString("category"), v.optString("detail"))
        }
    }.getOrDefault(emptyList())

    private fun record(category: String, detail: String, stop: Boolean = false) {
        val safe = detail.replace(Regex("https?://[^\\s]+"), "[URL]").replace("\n", " ").take(100)
        synchronized(LOCK) {
            val a = JSONArray()
            a.put(JSONObject().put("at", System.currentTimeMillis()).put("category", category).put("detail", safe))
            for (event in history().take(14)) {
                a.put(JSONObject().put("at", event.at).put("category", event.category).put("detail", event.detail))
            }
            val editor = prefs.edit().putString("events", a.toString())
            if (stop) editor.putString("last_stop", safe)
            editor.commit()
        }
        app.sendBroadcast(Intent(AutomationContract.ACTION_ACCESSIBILITY_DIAGNOSTICS_UPDATED)
            .setPackage(app.packageName))
    }

    companion object {
        private val LOCK = Any()
        fun isEnabledInSettings(context: Context): Boolean {
            val entries = Settings.Secure.getString(context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val expected = ComponentName(context.packageName, ArunoAccessibilityService::class.java.name)
            return entries.split(':').any { ComponentName.unflattenFromString(it.trim()) == expected }
        }
    }
}

