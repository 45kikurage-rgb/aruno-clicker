package jp.aruno.clicker.data

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

/** A confirmed foreground opening counts once, including an interrupted hold.
 * This records local attempts, not verified playback or awarded points. */
interface DailyVideoViews {
    fun count(url: String): Int
    fun recordStart(url: String, limit: Int): Boolean
}

/** Device-local only. URL position/order and remote revisions are not identity. */
class DailyVideoViewStore(
    context: Context,
    private val today: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Tokyo")) },
) : DailyVideoViews {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun limit(): Int = prefs.getInt(LIMIT, DEFAULT_LIMIT).takeIf { it == 2 || it == 3 } ?: DEFAULT_LIMIT

    fun setLimit(value: Int): Boolean = synchronized(LOCK) {
        if (value != 2 && value != 3) return@synchronized false
        prefs.edit().putInt(LIMIT, value).commit()
    }

    override fun count(url: String): Int = synchronized(LOCK) {
        prefs.getInt(key(today(), url), 0).coerceAtLeast(0)
    }

    override fun recordStart(url: String, limit: Int): Boolean = synchronized(LOCK) {
        if (limit != 2 && limit != 3) return@synchronized false
        val day = today()
        val key = key(day, url)
        val count = prefs.getInt(key, 0).coerceAtLeast(0)
        if (count >= limit) return@synchronized false
        // Synchronous persistence before the hold: stopping/restarting cannot
        // forget this attempt. Keep a week of hashed daily keys; no raw URLs.
        val editor = prefs.edit().putInt(key, count + 1)
        val cutoff = day.minusDays(7).toString()
        prefs.all.keys.filter { it.startsWith("view:") && it.substring(5, 15) < cutoff }
            .forEach(editor::remove)
        editor.commit()
    }

    companion object {
        const val DEFAULT_LIMIT = 2
        private const val PREFS = "aruno_clicker_daily_video_views"
        private const val LIMIT = "daily_limit"
        private val LOCK = Any()

        private fun key(day: LocalDate, url: String): String {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(identity(url).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            return "view:$day:$hash"
        }

        /** Known video IDs survive tracking parameters and host/profile changes.
         * Distinct short-link tokens cannot be resolved offline to the same video. */
        internal fun identity(url: String): String {
            val uri = URI(url.trim())
            val host = uri.host.orEmpty().lowercase()
            val path = uri.rawPath.orEmpty().trimEnd('/')
            if (host in UrlRunnerLimits.ALLOWED_TIKTOK_HOSTS) {
                Regex("/(?:video|v)/(\\d+)(?:\\.html)?$").find(path)?.let {
                    return "tiktok-video:" + it.groupValues[1]
                }
                if (host in setOf("lite.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")) {
                    return "$host$path"
                }
            }
            return URI(uri.scheme?.lowercase(), uri.rawAuthority?.lowercase(), uri.rawPath,
                uri.rawQuery, null).toString()
        }
    }
}
