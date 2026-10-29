package jp.aruno.clicker.automation

/** Public contract shared by the activity, foreground overlay and accessibility service. */
object AutomationContract {
    const val ACTION_START_AUTOMATION = "jp.aruno.clicker.action.START_AUTOMATION"
    const val ACTION_STOP_AUTOMATION = "jp.aruno.clicker.action.STOP_AUTOMATION"
    const val ACTION_SHOW_OVERLAY = "jp.aruno.clicker.action.SHOW_OVERLAY"
    const val ACTION_CLOSE_OVERLAY = "jp.aruno.clicker.action.CLOSE_OVERLAY"
    const val ACTION_AUTOMATION_STATE_CHANGED = "jp.aruno.clicker.action.STATE_CHANGED"
    const val ACTION_RUN_SCHEDULE = "jp.aruno.clicker.action.RUN_SCHEDULE"

    const val EXTRA_DURATION_MS = "duration_ms"
    const val EXTRA_INTERVAL_MS = "interval_ms"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_RUNNING = "running"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_START_MODE = "start_mode"
    const val EXTRA_START_FROM_SIXTH = "url_start_from_sixth"
    const val EXTRA_SPLASH_BETWEEN_URLS = "url_splash_between_urls"

    const val SOURCE_MANUAL = "manual"
    const val SOURCE_SCHEDULE = "schedule"
    const val START_MODE_FIRST = "first"
    const val START_MODE_REPEAT = "repeat"
    const val START_MODE_AUTO = "auto"

    /** URL順次展開（URLランナー）の開始モード。従来モードとは開始経路を分離する。 */
    const val START_MODE_URL_RUNNER = "url-runner"
}
