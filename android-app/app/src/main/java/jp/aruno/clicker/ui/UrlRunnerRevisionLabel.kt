package jp.aruno.clicker.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Human-readable version of the *server's last save time* on the home screen.
 *
 * configVersion is still used internally for optimistic concurrency; it must
 * never be shown as an unrelated numeric "版5" to a device user.
 * Android minSdk=26 supports java.time with no external dependency.
 * Always use Asia/Tokyo rather than the phone's device timezone.
 */
object UrlRunnerRevisionLabel {
    private val savedDateFormatter = DateTimeFormatter
        .ofPattern("M月d日H時mm分版", Locale.JAPAN)
        .withZone(ZoneId.of("Asia/Tokyo"))

    fun format(updatedAt: String?): String {
        if (updatedAt.isNullOrBlank()) return "更新日時未取得"
        return runCatching {
            savedDateFormatter.format(Instant.parse(updatedAt.trim()))
        }.getOrDefault("更新日時未取得")
    }
}
