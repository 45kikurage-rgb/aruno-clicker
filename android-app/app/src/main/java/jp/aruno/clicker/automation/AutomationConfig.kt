package jp.aruno.clicker.automation

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

data class AutomationConfig(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val gestureDurationMs: Long = DEFAULT_GESTURE_MS,
    val timeLimitMs: Long = DEFAULT_TIME_LIMIT_MS,
    val randomInterval: Boolean = true,
    val fastContentEnabled: Boolean = true,
    val classificationConfirmationCount: Int = 2,
    val fastIntervalMs: Long = DEFAULT_FAST_INTERVAL_MS,
    val fastGestureDurationMs: Long = DEFAULT_FAST_GESTURE_MS,
    val keepScreenAwake: Boolean = true,
    val floatingController: Boolean = true,
    val autoResume: Boolean = true,
    val autoDismissPopups: Boolean = true,
    val pageSettleMs: Long = 1_200L,
    val actionRetryCount: Int = 3,
    val startXRatio: Float = DEFAULT_START_X,
    val startYRatio: Float = DEFAULT_START_Y,
    val endXRatio: Float = DEFAULT_END_X,
    val endYRatio: Float = DEFAULT_END_Y,
    val targetPackage: String = DEFAULT_TARGET_PACKAGE,
    val startupUrl1: String = DEFAULT_STARTUP_URL_1,
    val startupUrl2: String = DEFAULT_STARTUP_URL_2,
    val startupTestMode: Boolean = true,
    val scheduleEnabled: Boolean = false,
    val scheduleHour: Int = 2,
    val scheduleMinute: Int = 0,
    val overlayX: Int = 16,
    val overlayY: Int = 1_200,
    val overlayCompact: Boolean = true,
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 8_000L
        const val DEFAULT_GESTURE_MS = 300L
        const val DEFAULT_FAST_INTERVAL_MS = 2_000L
        const val DEFAULT_FAST_GESTURE_MS = 150L
        const val STARTUP_STEP_DELAY_MS = 10_000L
        const val DEFAULT_TIME_LIMIT_MS = 2L * 60L * 60L * 1_000L
        const val DEFAULT_START_X = 0.165f
        const val DEFAULT_START_Y = 0.5569f
        const val DEFAULT_END_X = 0.1604f
        const val DEFAULT_END_Y = 0.2862f
        const val DEFAULT_TARGET_PACKAGE = "com.zhiliaoapp.musically.go"
        const val DEFAULT_STARTUP_URL_1 = "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/"
        const val DEFAULT_STARTUP_URL_2 = "https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/"

        val TARGET_PACKAGES = listOf(
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.tiktok.lite",
            "com.tiktok.lite.go",
        )
    }

    fun sanitized(): AutomationConfig = copy(
        intervalMs = intervalMs.coerceIn(500L, 60_000L),
        gestureDurationMs = gestureDurationMs.coerceIn(50L, 2_000L),
        classificationConfirmationCount = classificationConfirmationCount.coerceIn(1, 3),
        fastIntervalMs = fastIntervalMs.coerceIn(1_000L, 7_000L),
        fastGestureDurationMs = fastGestureDurationMs.coerceIn(80L, 300L),
        timeLimitMs = if (timeLimitMs == 0L) 0L else timeLimitMs.coerceIn(60_000L, 12L * 60L * 60L * 1_000L),
        actionRetryCount = actionRetryCount.coerceIn(0, 5),
        pageSettleMs = pageSettleMs.coerceIn(300L, 5_000L),
        startXRatio = startXRatio.coerceIn(0.02f, 0.98f),
        startYRatio = startYRatio.coerceIn(0.10f, 0.98f),
        endXRatio = endXRatio.coerceIn(0.02f, 0.98f),
        endYRatio = endYRatio.coerceIn(0.02f, 0.90f),
        targetPackage = targetPackage.ifBlank { DEFAULT_TARGET_PACKAGE },
        startupUrl1 = startupUrl1.validWebUrlOr(DEFAULT_STARTUP_URL_1),
        startupUrl2 = startupUrl2.validWebUrlOr(DEFAULT_STARTUP_URL_2),
        scheduleHour = scheduleHour.coerceIn(0, 23),
        scheduleMinute = scheduleMinute.coerceIn(0, 59),
    )

    private fun String.validWebUrlOr(fallback: String): String {
        val value = trim()
        return if (value.startsWith("https://") || value.startsWith("http://")) value else fallback
    }
}

class AutomationConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_V020_DEFAULTS_APPLIED, false)) {
            prefs.edit()
                .putBoolean(KEY_SCHEDULE_ENABLED, false)
                .putBoolean(KEY_UI_RANDOM_INTERVAL, true)
                .putInt(KEY_OVERLAY_Y, 1_200)
                .putBoolean(KEY_V020_DEFAULTS_APPLIED, true)
                .apply()
        }
    }

    fun load(): AutomationConfig = AutomationConfig(
        intervalMs = when {
            prefs.contains(KEY_UI_INTERVAL_SECONDS) -> prefs.getInt(KEY_UI_INTERVAL_SECONDS, 8) * 1_000L
            else -> prefs.getLong(KEY_INTERVAL, AutomationConfig.DEFAULT_INTERVAL_MS)
        },
        gestureDurationMs = when {
            prefs.contains(KEY_UI_GESTURE) -> prefs.getInt(KEY_UI_GESTURE, 300).toLong()
            else -> prefs.getLong(KEY_GESTURE, AutomationConfig.DEFAULT_GESTURE_MS)
        },
        timeLimitMs = when {
            prefs.contains(KEY_UI_RUN_MINUTES) -> prefs.getInt(KEY_UI_RUN_MINUTES, defaultRunMinutes().toInt()).times(60_000L)
            prefs.contains(KEY_TIME_LIMIT) -> prefs.getLong(KEY_TIME_LIMIT, AutomationConfig.DEFAULT_TIME_LIMIT_MS)
            else -> defaultRunMinutes() * 60_000L
        },
        randomInterval = prefs.getBoolean(KEY_UI_RANDOM_INTERVAL, true),
        fastContentEnabled = prefs.getBoolean(KEY_FAST_CONTENT_ENABLED, true),
        classificationConfirmationCount = prefs.getInt(KEY_CLASSIFICATION_CONFIRMATION_COUNT, 2),
        fastIntervalMs = prefs.getInt(KEY_FAST_INTERVAL_SECONDS, 2) * 1_000L,
        fastGestureDurationMs = prefs.getInt(KEY_FAST_GESTURE_MS, 150).toLong(),
        keepScreenAwake = prefs.getBoolean(KEY_UI_KEEP_AWAKE, true),
        floatingController = prefs.getBoolean(KEY_UI_FLOATING_CONTROLLER, true),
        autoResume = prefs.getBoolean(KEY_UI_AUTO_RESUME, true),
        autoDismissPopups = prefs.getBoolean(KEY_AUTO_DISMISS_POPUPS, true),
        pageSettleMs = prefs.getInt(KEY_UI_PAGE_SETTLE, 1_200).toLong(),
        actionRetryCount = prefs.getInt(KEY_UI_RETRY_COUNT, 3),
        startXRatio = prefs.getFloat(KEY_START_X, AutomationConfig.DEFAULT_START_X),
        startYRatio = if (prefs.contains(KEY_UI_START_PERCENT)) {
            prefs.getInt(KEY_UI_START_PERCENT, 56) / 100f
        } else prefs.getFloat(KEY_START_Y, AutomationConfig.DEFAULT_START_Y),
        endXRatio = prefs.getFloat(KEY_END_X, AutomationConfig.DEFAULT_END_X),
        endYRatio = if (prefs.contains(KEY_UI_END_PERCENT)) {
            prefs.getInt(KEY_UI_END_PERCENT, 29) / 100f
        } else prefs.getFloat(KEY_END_Y, AutomationConfig.DEFAULT_END_Y),
        targetPackage = prefs.getString(KEY_TARGET, AutomationConfig.DEFAULT_TARGET_PACKAGE)
            ?: AutomationConfig.DEFAULT_TARGET_PACKAGE,
        startupUrl1 = prefs.getString(KEY_STARTUP_URL_1, AutomationConfig.DEFAULT_STARTUP_URL_1)
            ?: AutomationConfig.DEFAULT_STARTUP_URL_1,
        startupUrl2 = prefs.getString(KEY_STARTUP_URL_2, AutomationConfig.DEFAULT_STARTUP_URL_2)
            ?: AutomationConfig.DEFAULT_STARTUP_URL_2,
        startupTestMode = prefs.getBoolean(KEY_STARTUP_TEST_MODE, true),
        scheduleEnabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false),
        scheduleHour = prefs.getInt(KEY_SCHEDULE_HOUR, 2),
        scheduleMinute = prefs.getInt(KEY_SCHEDULE_MINUTE, 0),
        overlayX = prefs.getInt(KEY_OVERLAY_X, 16),
        overlayY = prefs.getInt(KEY_OVERLAY_Y, 1_200),
        overlayCompact = prefs.getBoolean(KEY_OVERLAY_COMPACT, true),
    ).sanitized()

    fun save(config: AutomationConfig) {
        val safe = config.sanitized()
        prefs.edit()
            .putLong(KEY_INTERVAL, safe.intervalMs)
            .putLong(KEY_GESTURE, safe.gestureDurationMs)
            .putLong(KEY_TIME_LIMIT, safe.timeLimitMs)
            .putFloat(KEY_START_X, safe.startXRatio)
            .putFloat(KEY_START_Y, safe.startYRatio)
            .putFloat(KEY_END_X, safe.endXRatio)
            .putFloat(KEY_END_Y, safe.endYRatio)
            .putString(KEY_TARGET, safe.targetPackage)
            .putString(KEY_STARTUP_URL_1, safe.startupUrl1)
            .putString(KEY_STARTUP_URL_2, safe.startupUrl2)
            .putBoolean(KEY_STARTUP_TEST_MODE, safe.startupTestMode)
            .putBoolean(KEY_SCHEDULE_ENABLED, safe.scheduleEnabled)
            .putInt(KEY_SCHEDULE_HOUR, safe.scheduleHour)
            .putInt(KEY_SCHEDULE_MINUTE, safe.scheduleMinute)
            .putInt(KEY_OVERLAY_X, safe.overlayX)
            .putInt(KEY_OVERLAY_Y, safe.overlayY)
            .putBoolean(KEY_OVERLAY_COMPACT, safe.overlayCompact)
            .putInt(KEY_UI_INTERVAL_SECONDS, (safe.intervalMs / 1_000L).toInt())
            .putInt(KEY_UI_GESTURE, safe.gestureDurationMs.toInt())
            .putInt(KEY_UI_RUN_MINUTES, (safe.timeLimitMs / 60_000L).toInt())
            .putInt(KEY_UI_START_PERCENT, (safe.startYRatio * 100).toInt())
            .putInt(KEY_UI_END_PERCENT, (safe.endYRatio * 100).toInt())
            .putBoolean(KEY_UI_RANDOM_INTERVAL, safe.randomInterval)
            .putBoolean(KEY_FAST_CONTENT_ENABLED, safe.fastContentEnabled)
            .putInt(KEY_CLASSIFICATION_CONFIRMATION_COUNT, safe.classificationConfirmationCount)
            .putInt(KEY_FAST_INTERVAL_SECONDS, (safe.fastIntervalMs / 1_000L).toInt())
            .putInt(KEY_FAST_GESTURE_MS, safe.fastGestureDurationMs.toInt())
            .putBoolean(KEY_UI_KEEP_AWAKE, safe.keepScreenAwake)
            .putBoolean(KEY_UI_FLOATING_CONTROLLER, safe.floatingController)
            .putBoolean(KEY_UI_AUTO_RESUME, safe.autoResume)
            .putBoolean(KEY_AUTO_DISMISS_POPUPS, safe.autoDismissPopups)
            .putInt(KEY_UI_PAGE_SETTLE, safe.pageSettleMs.toInt())
            .putInt(KEY_UI_RETRY_COUNT, safe.actionRetryCount)
            .apply()
    }

    fun saveOverlayPosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_OVERLAY_X, x).putInt(KEY_OVERLAY_Y, y).apply()
    }

    fun saveOverlayCompact(compact: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_COMPACT, compact).apply()
    }

    /** Returns true only for the first user/scheduled start in the device's current calendar day. */
    fun claimDailyStartup(): Boolean {
        val today = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val lastDate = prefs.getString(KEY_LAST_DAILY_STARTUP_DATE, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (lastDate != null && !today.isAfter(lastDate)) return false
        prefs.edit().putString(KEY_LAST_DAILY_STARTUP_DATE, today.toString()).apply()
        return true
    }

    private fun defaultRunMinutes(): Long {
        val firstInstallMs = prefs.getLong(KEY_FIRST_INSTALL_MS, 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis().also { prefs.edit().putLong(KEY_FIRST_INSTALL_MS, it).apply() }
        val installDay = ((System.currentTimeMillis() - firstInstallMs).coerceAtLeast(0L) / 86_400_000L) + 1L
        return if (installDay <= 5L) 120L else 180L
    }

    companion object {
        private const val PREFS = "aruno_clicker_settings"
        private const val KEY_INTERVAL = "interval_ms"
        private const val KEY_GESTURE = "gesture_duration_ms"
        private const val KEY_TIME_LIMIT = "time_limit_ms"
        private const val KEY_START_X = "swipe_start_x"
        private const val KEY_START_Y = "swipe_start_y"
        private const val KEY_END_X = "swipe_end_x"
        private const val KEY_END_Y = "swipe_end_y"
        private const val KEY_TARGET = "target_package"
        private const val KEY_STARTUP_URL_1 = "startup_url_1"
        private const val KEY_STARTUP_URL_2 = "startup_url_2"
        private const val KEY_STARTUP_TEST_MODE = "startup_test_mode"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_HOUR = "schedule_hour"
        private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_OVERLAY_COMPACT = "overlay_compact"
        private const val KEY_UI_INTERVAL_SECONDS = "scroll_interval_seconds"
        private const val KEY_UI_GESTURE = "swipe_duration_millis"
        private const val KEY_UI_RUN_MINUTES = "run_minutes"
        private const val KEY_UI_START_PERCENT = "swipe_start_percent"
        private const val KEY_UI_END_PERCENT = "swipe_end_percent"
        private const val KEY_UI_RANDOM_INTERVAL = "random_interval"
        private const val KEY_FAST_CONTENT_ENABLED = "fast_content_enabled"
        private const val KEY_CLASSIFICATION_CONFIRMATION_COUNT = "classification_confirmation_count"
        private const val KEY_FAST_INTERVAL_SECONDS = "fast_interval_seconds"
        private const val KEY_FAST_GESTURE_MS = "fast_swipe_duration_millis"
        private const val KEY_UI_KEEP_AWAKE = "keep_screen_awake"
        private const val KEY_UI_FLOATING_CONTROLLER = "floating_controller"
        private const val KEY_UI_AUTO_RESUME = "auto_resume"
        private const val KEY_AUTO_DISMISS_POPUPS = "auto_dismiss_popups"
        private const val KEY_UI_PAGE_SETTLE = "page_settle_millis"
        private const val KEY_FIRST_INSTALL_MS = "first_install_ms"
        private const val KEY_UI_RETRY_COUNT = "action_retry_count"
        private const val KEY_V020_DEFAULTS_APPLIED = "defaults_v020_applied"
        private const val KEY_LAST_DAILY_STARTUP_DATE = "last_daily_startup_date"
    }
}
