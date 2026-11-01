package jp.aruno.clicker.data

/** Every play action starts at new URL 1. The old sixth flag is ignored for intent compatibility. */
object UrlRunnerStartSelection {
    @Suppress("UNUSED_PARAMETER")
    fun snapshot(saved: UrlRunnerConfig, fromSixth: Boolean = false): UrlRunnerConfig =
        saved.copy(startIndex = 0).snapshot()

    fun issue(saved: UrlRunnerConfig, fromSixth: Boolean = false): String? =
        snapshot(saved, fromSixth).validate().firstOrNull()?.message
}
