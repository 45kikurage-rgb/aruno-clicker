package jp.aruno.clicker.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Legacy alarms are retained as source only. URL runner starts explicitly. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ScheduleManager.cancelPending(context)
    }
}
