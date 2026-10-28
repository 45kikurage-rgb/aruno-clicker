package jp.aruno.clicker.data

import org.junit.Assert.*
import org.junit.Test

class UrlRunnerStartSelectionTest {
    private fun config() = UrlRunnerConfig(
        urls = (1..7).map { "https://lite.tiktok.com/t/slot$it/" },
        individualSeconds = listOf(1, 2, 3, 4, 5), commonSeconds = 12,
        configVersion = 2, updatedAt = "2026-10-28T00:00:00Z", startIndex = 2,
    )
    @Test fun sixthStartKeepsOriginalNumberingTimingAndSavedConfig() {
        val saved = config()
        val selected = UrlRunnerStartSelection.snapshot(saved, true)
        assertNull(UrlRunnerStartSelection.issue(saved, true))
        assertEquals(5, selected.startIndex)
        assertEquals(saved.urls, selected.urls)
        assertEquals("https://lite.tiktok.com/t/slot6/", selected.urls[selected.startIndex])
        assertEquals(12, selected.secondsFor(selected.startIndex))
        assertEquals(saved.individualSeconds, selected.individualSeconds)
        assertEquals(saved.configVersion, selected.configVersion)
        assertEquals(saved.updatedAt, selected.updatedAt)
        assertEquals(2, saved.startIndex)
    }
    @Test fun normalStartKeepsConfiguredStartPosition() {
        assertEquals(2, UrlRunnerStartSelection.snapshot(config(), false).startIndex)
    }
    @Test fun absentSixthRangeNeverFallsBackToFirstFive() {
        assertNotNull(UrlRunnerStartSelection.issue(config().copy(urls = config().urls.take(5)), true))
        assertNotNull(UrlRunnerStartSelection.issue(config().copy(urls = config().urls.take(5) + listOf("", "")), true))
    }
    @Test fun blankSixthCanSkipForwardWithoutRenumbering() {
        val saved = config().copy(urls = config().urls.toMutableList().apply { this[5] = "" })
        assertNull(UrlRunnerStartSelection.issue(saved, true))
        assertEquals(5, UrlRunnerStartSelection.snapshot(saved, true).startIndex)
    }
    @Test fun selectionIsADefensiveSnapshot() {
        val urls = config().urls.toMutableList()
        val selected = UrlRunnerStartSelection.snapshot(config().copy(urls = urls), true)
        urls[5] = "https://lite.tiktok.com/t/replaced/"
        assertEquals("https://lite.tiktok.com/t/slot6/", selected.urls[5])
    }
}
