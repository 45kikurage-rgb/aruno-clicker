package jp.aruno.clicker.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import jp.aruno.clicker.automation.AutomationConfigStore
import jp.aruno.clicker.automation.AutomationContract
import java.util.Calendar

object ScheduleManager {
    fun scheduleDaily(context: Context, hour: Int, minute: Int): Long {
        val store = AutomationConfigStore(context)
        store.save(store.load().copy(scheduleEnabled = true, scheduleHour = hour, scheduleMinute = minute))
        val triggerAt = nextDailyTrigger(hour, minute)
        scheduleAt(context, triggerAt)
        return triggerAt
    }

    fun scheduleOnce(context: Context, triggerAtMillis: Long) {
        scheduleAt(context, triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context))
        val store = AutomationConfigStore(context)
        store.save(store.load().copy(scheduleEnabled = false))
    }

    fun rearmFromSettings(context: Context): Long? {
        val config = AutomationConfigStore(context).load()
        if (!config.scheduleEnabled) return null
        val triggerAt = nextDailyTrigger(config.scheduleHour, config.scheduleMinute)
        scheduleAt(context, triggerAt)
        return triggerAt
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    }

    private fun scheduleAt(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context)
        when {
            Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms() ->
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            Build.VERSION.SDK_INT >= 23 ->
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ScheduleReceiver::class.java).setAction(AutomationContract.ACTION_RUN_SCHEDULE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextDailyTrigger(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private const val REQUEST_CODE = 2201
}
