package jp.aruno.clicker.data

import jp.aruno.clicker.BuildConfig

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val scrollIntervalSeconds: Int = 8,
    val swipeDurationMillis: Int = 300,
    val runMinutes: Int = 120,
    val randomInterval: Boolean = true,
    val fastContentEnabled: Boolean = true,
    val classificationConfirmationCount: Int = 2,
    val fastIntervalSeconds: Int = 2,
    val fastSwipeDurationMillis: Int = 150,
    val floatingController: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val autoResume: Boolean = true,
    val autoDismissPopups: Boolean = true,
    val scheduleEnabled: Boolean = false,
    val scheduleHour: Int = 2,
    val scheduleMinute: Int = 0,
    val swipeStartPercent: Int = 56,
    val swipeEndPercent: Int = 29,
    val actionRetryCount: Int = 3,
    val pageSettleMillis: Int = 1_200,
    val targetPackage: String = "com.ss.android.ugc.tiktok.lite",
    val startupName1: String = "",
    val startupUrl1: String = "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/",
    val startupName2: String = "",
    val startupUrl2: String = "https://lite.tiktok.com/t/ZS9rdoB6rsLHp-UHtJt/",
    val shareName: String = "",
    val shareUrl: String = "https://lite.tiktok.com/t/ZS9AJY6f4n6Sw-GQnDP/",
    val startupTestMode: Boolean = true,
    val remoteSyncEnabled: Boolean = true,
    val remoteAdminMode: Boolean = false,
    val remoteServerUrl: String = DEFAULT_REMOTE_SERVER_URL,
    val remoteAdminKey: String = "",
    val remoteLastSyncEpochMillis: Long = 0L,
    val remoteLastSyncSucceeded: Boolean = false,
    val remoteLastSyncMessage: String = "未同期",
    val remoteConfigVersion: Long = 0L,
    val remoteUpdatedAt: String = "",
) {
    companion object {
        val DEFAULT_REMOTE_SERVER_URL: String = BuildConfig.CONFIG_ENDPOINT
    }
}
