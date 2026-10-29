package jp.aruno.clicker.data

import android.content.Context

/**
 * Local-only controls for URL6+ experiments. Never edit the central URL list.
 * The preference is read once for each new run; changes during playback apply
 * only to the next run. Existing installs default to splash every URL (1).
 */
class SplashFrequencyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun interval(): Int = prefs.getInt(KEY_FREQUENCY, DEFAULT_FREQUENCY).coerceIn(1, 5)

    fun setEnabled(value: Boolean): Boolean = prefs.edit().putBoolean(KEY_ENABLED, value).commit()

    fun setInterval(value: Int): Boolean {
        if (value !in 1..5) return false
        return prefs.edit().putInt(KEY_FREQUENCY, value).commit()
    }

    /** 0 means no optional splash. Recovery on URL-open failure remains active. */
    fun selectedFrequency(withSplash: Boolean): Int = if (withSplash) interval() else 0

    companion object {
        const val DEFAULT_FREQUENCY = 1
        private const val PREFS = "aruno_clicker_splash_frequency"
        private const val KEY_ENABLED = "splash_enabled"
        private const val KEY_FREQUENCY = "splash_interval"
    }
}
