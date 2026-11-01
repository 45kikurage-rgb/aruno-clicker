package jp.aruno.clicker.data

import android.content.Context

enum class RecentsLayout(val label: String) { HORIZONTAL("横"), VERTICAL("縦") }
data class RecentsCleanupSettings(val enabled: Boolean = false, val layout: RecentsLayout = RecentsLayout.HORIZONTAL)
class RecentsCleanupStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aruno_recents_cleanup", Context.MODE_PRIVATE)
    fun load() = RecentsCleanupSettings(
        prefs.getBoolean("enabled", false),
        runCatching { RecentsLayout.valueOf(prefs.getString("layout", "HORIZONTAL")!!) }.getOrDefault(RecentsLayout.HORIZONTAL),
    )
    fun save(value: RecentsCleanupSettings): Boolean = prefs.edit()
        .putBoolean("enabled", value.enabled).putString("layout", value.layout.name).commit()
}
