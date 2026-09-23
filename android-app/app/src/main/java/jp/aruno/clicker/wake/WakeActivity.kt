package jp.aruno.clicker.wake

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import jp.aruno.clicker.automation.AutomationContract
import jp.aruno.clicker.automation.AutomationService

/** Transparent lock-screen trampoline used only by the user's scheduled run. */
class WakeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        requestKeyguardDismissal()
        acquireWakeLock()

        handler.postDelayed({
            startAutomationService(this, AutomationContract.SOURCE_SCHEDULE)
            handler.postDelayed({ finishAndRemoveTask() }, 1_500L)
        }, 700L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    private fun requestKeyguardDismissal() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        val power = getSystemService(PowerManager::class.java)
        wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "ARUNO_CLICKER:wake-screen",
        ).apply {
            setReferenceCounted(false)
            acquire(12_000L)
        }
    }

    companion object {
        fun startAutomationService(context: Context, source: String) {
            val intent = Intent(context, AutomationService::class.java)
                .setAction(AutomationContract.ACTION_START_AUTOMATION)
                .putExtra(AutomationContract.EXTRA_SOURCE, source)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }
    }
}
