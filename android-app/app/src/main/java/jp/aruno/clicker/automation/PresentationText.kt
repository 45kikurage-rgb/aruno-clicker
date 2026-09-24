package jp.aruno.clicker.automation

import jp.aruno.clicker.BuildConfig

/** Prevents private startup details from appearing in ver.S UI, overlay or notifications. */
object PresentationText {
    fun sanitize(text: String): String {
        if (!BuildConfig.IS_VER_S) return text
        return when {
            text.contains("URL", ignoreCase = true) ||
                text.contains("初回") ||
                text.contains("共通") -> "開始準備中"
            text.contains("アプリID") -> text.replace("アプリIDから起動", "TikTok Liteを準備しています")
            text.contains("2回目以降") -> text.replace("2回目以降", "通常")
            else -> text
        }
    }
}
