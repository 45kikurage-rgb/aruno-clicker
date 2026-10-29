package jp.aruno.clicker.data

import android.content.Context

enum class GiftHomeMode(val label: String) {
    AUTO("自動（要素→位置）"), ELEMENTS("要素のみ"), COORDINATES("位置のみ"), OFF("なし（従来）")
}

class GiftHomeModeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aruno_clicker_gift_home", Context.MODE_PRIVATE)
    fun mode(): GiftHomeMode = runCatching {
        GiftHomeMode.valueOf(prefs.getString("mode", GiftHomeMode.AUTO.name)!!)
    }.getOrDefault(GiftHomeMode.AUTO)
    fun setMode(mode: GiftHomeMode): Boolean = prefs.edit().putString("mode", mode.name).commit()
}
