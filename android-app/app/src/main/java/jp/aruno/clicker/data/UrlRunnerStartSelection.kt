package jp.aruno.clicker.data

/** One-run start selection. Never saves, downloads, renumbers or truncates URL slots. */
object UrlRunnerStartSelection {
    const val SIXTH_INDEX = 5

    fun snapshot(saved: UrlRunnerConfig, fromSixth: Boolean): UrlRunnerConfig =
        saved.copy(startIndex = if (fromSixth) SIXTH_INDEX else saved.startIndex).snapshot()

    fun issue(saved: UrlRunnerConfig, fromSixth: Boolean): String? {
        if (fromSixth && saved.urls.drop(SIXTH_INDEX).none { it.isNotBlank() }) {
            return "6番目以降にURLが登録されていません"
        }
        return snapshot(saved, fromSixth).validate().firstOrNull()?.message
    }
}
