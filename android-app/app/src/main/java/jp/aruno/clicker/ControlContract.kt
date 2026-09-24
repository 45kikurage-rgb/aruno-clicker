package jp.aruno.clicker

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * UI-to-service contract. The automation module owns the service implementation;
 * the UI only issues package-scoped commands.
 */
object ControlContract {
    const val ACTION_START = "jp.aruno.clicker.action.START_AUTOMATION"
    const val ACTION_STOP = "jp.aruno.clicker.action.STOP_AUTOMATION"
    const val ACTION_PAUSE = "jp.aruno.clicker.action.PAUSE_AUTOMATION"
    const val ACTION_RESUME = "jp.aruno.clicker.action.RESUME_AUTOMATION"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_SOURCE_UI = "ui"
    const val EXTRA_START_MODE = "start_mode"
    const val START_MODE_FIRST = "first"
    const val START_MODE_REPEAT = "repeat"
    const val START_MODE_AUTO = "auto"
    const val AUTOMATION_SERVICE = "jp.aruno.clicker.automation.AutomationService"

    fun startFirst(context: Context) = sendToService(context, ACTION_START, START_MODE_FIRST)
    fun startRepeat(context: Context) = sendToService(context, ACTION_START, START_MODE_REPEAT)
    fun startAutomatic(context: Context) = sendToService(context, ACTION_START, START_MODE_AUTO)
    fun stop(context: Context) = sendToService(context, ACTION_STOP)

    private fun sendToService(context: Context, action: String, startMode: String? = null) {
        val intent = Intent(action)
            .setClassName(context.packageName, AUTOMATION_SERVICE)
            .putExtra(EXTRA_SOURCE, EXTRA_SOURCE_UI)
        startMode?.let { intent.putExtra(EXTRA_START_MODE, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
