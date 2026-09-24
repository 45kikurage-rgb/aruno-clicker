package jp.aruno.clicker.automation

import android.content.Context
import android.content.Intent
import android.os.SystemClock

data class AutomationSnapshot(
    val requested: Boolean,
    val running: Boolean,
    val startedAtElapsed: Long,
    val endsAtElapsed: Long,
    val swipeCount: Int,
    val message: String,
) {
    val elapsedMs: Long
        get() = if (startedAtElapsed <= 0L) 0L else (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
    val remainingMs: Long
        get() = if (endsAtElapsed <= 0L) 0L else (endsAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
}

data class RestoredAutomationRequest(
    val durationMs: Long,
    val pendingStart: Boolean,
    val startMode: String,
)

/** In-process state with a persisted start request, allowing service binding after the user enables it. */
object AutomationRuntime {
    @Volatile private var requested = false
    @Volatile private var running = false
    @Volatile private var startedAtElapsed = 0L
    @Volatile private var endsAtElapsed = 0L
    @Volatile private var swipeCount = 0
    @Volatile private var message = "停止中"

    fun requestStart(
        context: Context,
        durationMs: Long,
        startMode: String = AutomationContract.START_MODE_REPEAT,
    ) {
        val now = SystemClock.elapsedRealtime()
        requested = true
        startedAtElapsed = now
        endsAtElapsed = if (durationMs <= 0L) Long.MAX_VALUE else now + durationMs
        swipeCount = 0
        message = "開始準備中"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_REQUESTED, true)
            .putBoolean(KEY_PENDING_START, true)
            .putLong(KEY_DURATION, durationMs)
            .putString(KEY_START_MODE, startMode)
            .putLong(
                KEY_END_WALL_CLOCK,
                if (durationMs <= 0L) Long.MAX_VALUE else System.currentTimeMillis() + durationMs,
            )
            .apply()
        notifyChanged(context)
    }

    fun restoreRequested(context: Context): RestoredAutomationRequest? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_REQUESTED, false)) return null
        val pendingStart = prefs.getBoolean(KEY_PENDING_START, false)
        val endWallClock = prefs.getLong(KEY_END_WALL_CLOCK, 0L)
        val startMode = prefs.getString(KEY_START_MODE, AutomationContract.START_MODE_REPEAT)
            ?: AutomationContract.START_MODE_REPEAT
        if (endWallClock == Long.MAX_VALUE) return RestoredAutomationRequest(0L, pendingStart, startMode)
        if (endWallClock > 0L) {
            val remaining = endWallClock - System.currentTimeMillis()
            if (remaining > 0L) return RestoredAutomationRequest(remaining, pendingStart, startMode)
            stop(context, "設定時間が終了しました")
            return null
        }
        return RestoredAutomationRequest(
            prefs.getLong(KEY_DURATION, AutomationConfig.DEFAULT_TIME_LIMIT_MS),
            pendingStart,
            startMode,
        )
    }

    fun markSessionActive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_START, false)
            .apply()
    }

    fun markRunning(context: Context, text: String = "自動スライド中") {
        running = true
        message = PresentationText.sanitize(text)
        notifyChanged(context)
    }

    fun markWaiting(context: Context, text: String) {
        running = false
        message = PresentationText.sanitize(text)
        notifyChanged(context)
    }

    fun incrementSwipe(context: Context) {
        swipeCount += 1
        notifyChanged(context)
    }

    fun stop(context: Context, text: String = "停止中") {
        requested = false
        running = false
        endsAtElapsed = 0L
        message = PresentationText.sanitize(text)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_REQUESTED, false)
            .putBoolean(KEY_PENDING_START, false)
            .apply()
        notifyChanged(context)
    }

    fun snapshot() = AutomationSnapshot(
        requested = requested,
        running = running,
        startedAtElapsed = startedAtElapsed,
        endsAtElapsed = endsAtElapsed,
        swipeCount = swipeCount,
        message = message,
    )

    private fun notifyChanged(context: Context) {
        val snapshot = snapshot()
        context.sendBroadcast(
            Intent(AutomationContract.ACTION_AUTOMATION_STATE_CHANGED)
                .setPackage(context.packageName)
                .putExtra(AutomationContract.EXTRA_RUNNING, snapshot.running)
                .putExtra(AutomationContract.EXTRA_MESSAGE, snapshot.message),
        )
    }

    private const val PREFS = "aruno_clicker_runtime"
    private const val KEY_REQUESTED = "start_requested"
    private const val KEY_DURATION = "requested_duration_ms"
    private const val KEY_PENDING_START = "pending_start"
    private const val KEY_END_WALL_CLOCK = "requested_end_wall_clock_ms"
    private const val KEY_START_MODE = "requested_start_mode"
}
