package jp.aruno.clicker.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val adminKeyStore = AdminKeyStore(context.applicationContext)

    init {
        val legacyAdminKey = preferences.getString("remote_admin_key", null).orEmpty()
        if (legacyAdminKey.isNotBlank() && adminKeyStore.read().isBlank()) {
            adminKeyStore.write(legacyAdminKey)
        }
        if (preferences.contains("remote_admin_key")) {
            preferences.edit().remove("remote_admin_key").apply()
        }
        if (!preferences.getBoolean(KEY_V020_DEFAULTS_APPLIED, false)) {
            preferences.edit()
                .putBoolean("schedule_enabled", false)
                .putBoolean("random_interval", true)
                .putInt("overlay_y", 1_200)
                .putBoolean(KEY_V020_DEFAULTS_APPLIED, true)
                .apply()
        }
    }

    private val mutableSettings = MutableStateFlow(preferences.readSettings())
    val settings: StateFlow<AppSettings> = mutableSettings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        // Merge each UI edit onto the latest persisted snapshot so a concurrent remote sync
        // cannot be overwritten by an older in-memory copy of unrelated fields.
        val previous = preferences.readSettings().sanitized()
        val value = transform(previous).sanitized()
        mutableSettings.value = value
        if (value.remoteAdminKey != previous.remoteAdminKey) {
            adminKeyStore.write(value.remoteAdminKey)
        }
        preferences.edit().writeSettings(value).apply()
    }

    fun applyRemoteSuccess(
        name1: String,
        url1: String,
        name2: String,
        url2: String,
        shareName: String,
        shareUrl: String,
        completedAt: Long,
        message: String,
        configVersion: Long,
        updatedAt: String,
    ) {
        preferences.edit()
            .putString("startup_name_1", name1)
            .putString("startup_url_1", url1)
            .putString("startup_name_2", name2)
            .putString("startup_url_2", url2)
            .putString("share_name", shareName)
            .putString("share_url", shareUrl)
            .putLong("remote_last_sync_epoch_millis", completedAt)
            .putBoolean("remote_last_sync_succeeded", true)
            .putString("remote_last_sync_message", message)
            .putLong("remote_config_version", configVersion)
            .putString("remote_updated_at", updatedAt)
            .apply()
        refresh()
    }

    fun applyRemoteFailure(message: String) {
        preferences.edit()
            .putBoolean("remote_last_sync_succeeded", false)
            .putString("remote_last_sync_message", message)
            .apply()
        refresh()
    }

    fun refresh() {
        mutableSettings.value = preferences.readSettings().sanitized()
    }

    private fun AppSettings.sanitized() = copy(
        scrollIntervalSeconds = scrollIntervalSeconds.coerceIn(3, 120),
        swipeDurationMillis = swipeDurationMillis.coerceIn(80, 1_500),
        classificationConfirmationCount = classificationConfirmationCount.coerceIn(1, 3),
        fastIntervalSeconds = fastIntervalSeconds.coerceIn(1, 7),
        fastSwipeDurationMillis = fastSwipeDurationMillis.coerceIn(80, 300),
        runMinutes = runMinutes.coerceIn(0, 720),
        scheduleHour = scheduleHour.coerceIn(0, 23),
        scheduleMinute = scheduleMinute.coerceIn(0, 59),
        swipeStartPercent = swipeStartPercent.coerceIn(55, 95),
        swipeEndPercent = swipeEndPercent.coerceIn(5, 45),
        actionRetryCount = actionRetryCount.coerceIn(0, 5),
        pageSettleMillis = pageSettleMillis.coerceIn(300, 5_000),
        startupName1 = startupName1.trim().take(80),
        startupName2 = startupName2.trim().take(80),
        shareName = shareName.trim().take(80),
        remoteServerUrl = remoteServerUrl.trim(),
        remoteAdminKey = remoteAdminKey.trim(),
    )

    private fun SharedPreferences.readSettings(): AppSettings {
        val firstInstallMs = getLong("first_install_ms", 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis().also { edit().putLong("first_install_ms", it).apply() }
        val installDay = ((System.currentTimeMillis() - firstInstallMs).coerceAtLeast(0L) / 86_400_000L) + 1L
        val defaultRunMinutes = if (installDay <= 5L) 120 else 180
        return AppSettings(
        onboardingComplete = getBoolean("onboarding_complete", false),
        scrollIntervalSeconds = getInt("scroll_interval_seconds", 8),
        swipeDurationMillis = getInt("swipe_duration_millis", 300),
        runMinutes = getInt("run_minutes", defaultRunMinutes),
        randomInterval = getBoolean("random_interval", true),
        fastContentEnabled = getBoolean("fast_content_enabled", true),
        classificationConfirmationCount = getInt("classification_confirmation_count", 2),
        fastIntervalSeconds = getInt("fast_interval_seconds", 2),
        fastSwipeDurationMillis = getInt("fast_swipe_duration_millis", 150),
        floatingController = getBoolean("floating_controller", true),
        keepScreenAwake = getBoolean("keep_screen_awake", true),
        autoResume = getBoolean("auto_resume", true),
        autoDismissPopups = getBoolean("auto_dismiss_popups", true),
        scheduleEnabled = getBoolean("schedule_enabled", false),
        scheduleHour = getInt("schedule_hour", 2),
        scheduleMinute = getInt("schedule_minute", 0),
        swipeStartPercent = getInt("swipe_start_percent", 56),
        swipeEndPercent = getInt("swipe_end_percent", 29),
        actionRetryCount = getInt("action_retry_count", 3),
        pageSettleMillis = getInt("page_settle_millis", 1_200),
        targetPackage = getString("target_package", "com.ss.android.ugc.tiktok.lite")
            ?: "com.ss.android.ugc.tiktok.lite",
        startupName1 = getString("startup_name_1", "").orEmpty(),
        startupUrl1 = getString("startup_url_1", "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/")
            ?: "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/",
        startupName2 = getString("startup_name_2", "").orEmpty(),
        startupUrl2 = getString("startup_url_2", "https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/")
            ?: "https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/",
        shareName = getString("share_name", "").orEmpty(),
        shareUrl = getString("share_url", "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/")
            ?: "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/",
        startupTestMode = getBoolean("startup_test_mode", true),
        remoteSyncEnabled = getBoolean("remote_sync_enabled", true),
        remoteAdminMode = getBoolean("remote_admin_mode", false),
        remoteServerUrl = getString("remote_server_url", AppSettings.DEFAULT_REMOTE_SERVER_URL)
            ?: AppSettings.DEFAULT_REMOTE_SERVER_URL,
        remoteAdminKey = adminKeyStore.read(),
        remoteLastSyncEpochMillis = getLong("remote_last_sync_epoch_millis", 0L),
        remoteLastSyncSucceeded = getBoolean("remote_last_sync_succeeded", false),
        remoteLastSyncMessage = getString("remote_last_sync_message", "未同期") ?: "未同期",
        remoteConfigVersion = getLong("remote_config_version", 0L),
        remoteUpdatedAt = getString("remote_updated_at", "").orEmpty(),
        )
    }

    private fun SharedPreferences.Editor.writeSettings(value: AppSettings) = apply {
        putBoolean("onboarding_complete", value.onboardingComplete)
        putInt("scroll_interval_seconds", value.scrollIntervalSeconds)
        putInt("swipe_duration_millis", value.swipeDurationMillis)
        putInt("run_minutes", value.runMinutes)
        putBoolean("random_interval", value.randomInterval)
        putBoolean("fast_content_enabled", value.fastContentEnabled)
        putInt("classification_confirmation_count", value.classificationConfirmationCount)
        putInt("fast_interval_seconds", value.fastIntervalSeconds)
        putInt("fast_swipe_duration_millis", value.fastSwipeDurationMillis)
        putBoolean("floating_controller", value.floatingController)
        putBoolean("keep_screen_awake", value.keepScreenAwake)
        putBoolean("auto_resume", value.autoResume)
        putBoolean("auto_dismiss_popups", value.autoDismissPopups)
        putBoolean("schedule_enabled", value.scheduleEnabled)
        putInt("schedule_hour", value.scheduleHour)
        putInt("schedule_minute", value.scheduleMinute)
        putInt("swipe_start_percent", value.swipeStartPercent)
        putInt("swipe_end_percent", value.swipeEndPercent)
        putInt("action_retry_count", value.actionRetryCount)
        putInt("page_settle_millis", value.pageSettleMillis)
        putString("target_package", value.targetPackage)
        putString("startup_name_1", value.startupName1)
        putString("startup_url_1", value.startupUrl1.trim())
        putString("startup_name_2", value.startupName2)
        putString("startup_url_2", value.startupUrl2.trim())
        putString("share_name", value.shareName)
        putString("share_url", value.shareUrl.trim())
        putBoolean("startup_test_mode", value.startupTestMode)
        putBoolean("remote_sync_enabled", value.remoteSyncEnabled)
        putBoolean("remote_admin_mode", value.remoteAdminMode)
        putString("remote_server_url", value.remoteServerUrl)
        putLong("remote_last_sync_epoch_millis", value.remoteLastSyncEpochMillis)
        putBoolean("remote_last_sync_succeeded", value.remoteLastSyncSucceeded)
        putString("remote_last_sync_message", value.remoteLastSyncMessage)
        putLong("remote_config_version", value.remoteConfigVersion)
        putString("remote_updated_at", value.remoteUpdatedAt)
    }

    companion object {
        private const val PREFS_NAME = "aruno_clicker_settings"
        private const val KEY_V020_DEFAULTS_APPLIED = "defaults_v020_applied"
    }
}
