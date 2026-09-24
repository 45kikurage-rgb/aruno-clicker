package jp.aruno.clicker.automation

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** Keeps ver.S daily-start state separate from the editable automation settings. */
class DailyStartModeStore(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun modeForToday(): String = if (preferences.getString(KEY_COMPLETED_DAY, null) == todayKey()) {
        AutomationContract.START_MODE_REPEAT
    } else {
        AutomationContract.START_MODE_FIRST
    }

    fun markTodayCompleted() {
        preferences.edit().putString(KEY_COMPLETED_DAY, todayKey()).apply()
    }

    internal fun todayKey(): String = Instant.ofEpochMilli(nowMillis())
        .let(::japanDayKey)

    companion object {
        private const val PREFS = "aruno_daily_start_state"
        private const val KEY_COMPLETED_DAY = "completed_japan_day"
    }
}

internal fun japanDayKey(instant: Instant): String = instant
    .atZone(ZoneId.of("Asia/Tokyo"))
    .toLocalDate()
    .toString()
