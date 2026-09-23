package jp.aruno.clicker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import jp.aruno.clicker.automation.AutomationContract
import jp.aruno.clicker.automation.AutomationConfigStore
import jp.aruno.clicker.wake.WakeActivity

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AutomationContract.ACTION_RUN_SCHEDULE) return
        if (!AutomationConfigStore(context).load().scheduleEnabled) return
        ScheduleManager.rearmFromSettings(context)

        val pendingResult = goAsync()
        val power = context.getSystemService(PowerManager::class.java)
        val wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ARUNO_CLICKER:schedule",
        ).apply {
            setReferenceCounted(false)
            acquire(15_000L)
        }

        runCatching {
            context.startActivity(
                Intent(context, WakeActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    ),
            )
        }.onFailure {
            WakeActivity.startAutomationService(context, AutomationContract.SOURCE_SCHEDULE)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (wakeLock.isHeld) wakeLock.release()
            pendingResult.finish()
        }, 5_000L)
    }
}
