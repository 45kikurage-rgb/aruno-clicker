package jp.aruno.clicker.automation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStartModeStoreTest {
    @Test
    fun japanDayChangesAtTokyoMidnight() {
        assertEquals("2026-09-24", japanDayKey(Instant.parse("2026-09-24T14:59:59Z")))
        assertEquals("2026-09-25", japanDayKey(Instant.parse("2026-09-24T15:00:00Z")))
    }
}
