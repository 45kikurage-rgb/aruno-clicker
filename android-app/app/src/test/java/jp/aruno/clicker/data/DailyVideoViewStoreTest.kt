package jp.aruno.clicker.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DailyVideoViewStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val url = "https://lite.tiktok.com/t/AbC123/"
    private var day = LocalDate.of(2026, 10, 29)
    private fun store() = DailyVideoViewStore(app) { day }

    @Before fun clear() {
        app.getSharedPreferences("aruno_clicker_daily_video_views", 0).edit().clear().commit()
    }

    @Test fun interruptedStartsPersistAndStopAtTwoAcrossInstances() {
        assertEquals(2, store().limit())
        assertTrue(store().recordStart(url, 2))
        // No completion is reported: a stop/restart still preserves the count.
        assertEquals(1, store().count(url))
        assertTrue(store().recordStart(url, 2))
        assertFalse(store().recordStart(url, 2))
        assertEquals(2, store().count(url))
    }

    @Test fun changingLimitToThreeRetainsExistingCountsAndRejectsOtherValues() {
        repeat(2) { assertTrue(store().recordStart(url, 2)) }
        assertTrue(store().setLimit(3))
        assertEquals(3, store().limit())
        assertEquals(2, store().count(url))
        assertTrue(store().recordStart(url, 3))
        assertFalse(store().recordStart(url, 3))
        for (invalid in listOf(-1, 0, 1, 4, 99)) {
            assertFalse(store().setLimit(invalid))
            assertFalse(store().recordStart(url, invalid))
        }
        assertEquals(3, store().limit())
        assertTrue(store().setLimit(2))
        assertEquals(3, store().count(url))
        assertFalse(store().recordStart(url, 2))
    }

    @Test fun japanMidnightStartsNewDayWithoutResettingPreferenceOrPreviousDay() {
        val zone = ZoneId.of("Asia/Tokyo")
        var now = Instant.parse("2026-10-29T14:59:59Z")
        val history = DailyVideoViewStore(app) { now.atZone(zone).toLocalDate() }
        assertTrue(history.setLimit(3))
        repeat(3) { assertTrue(history.recordStart(url, 3)) }
        now = Instant.parse("2026-10-29T15:00:00Z")
        assertEquals(0, history.count(url))
        assertEquals(3, history.limit())
        assertTrue(history.recordStart(url, 3))
        assertEquals(1, history.count(url))
        now = Instant.parse("2026-10-29T14:59:59Z")
        assertEquals(3, history.count(url))
    }

    @Test fun numbersAndTrackingDoNotResetCountsButDifferentVideosRemainSeparate() {
        assertTrue(store().recordStart(url, 2))
        assertEquals(1, store().count("https://lite.tiktok.com/t/AbC123/?share=changed#fragment"))
        assertEquals(0, store().count("https://lite.tiktok.com/t/abc123/"))
        assertTrue(store().recordStart("https://www.tiktok.com/@first/video/123456789?share=a", 2))
        assertEquals(1, store().count("https://m.tiktok.com/@second/video/123456789"))
        assertEquals(0, store().count("https://www.tiktok.com/@first/video/987654321"))
    }
}
