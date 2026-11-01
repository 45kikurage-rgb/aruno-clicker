package jp.aruno.clicker.data

import org.junit.Assert.*
import org.junit.Test

class UrlRunnerStartSelectionTest {
    private fun config() = UrlRunnerConfig(
        urls = (1..7).map { "https://lite.tiktok.com/t/slot$it/" }, commonSeconds = 12,
        configVersion = 2, updatedAt = "2026-10-28T00:00:00Z", startIndex = 2,
    )
    @Test fun legacySixthFlagNowStartsAtFirstAndPreservesSavedData() {
        val saved = config()
        val selected = UrlRunnerStartSelection.snapshot(saved, true)
        assertNull(UrlRunnerStartSelection.issue(saved, true))
        assertEquals(0, selected.startIndex)
        assertEquals(saved.urls, selected.urls)
        assertEquals("https://lite.tiktok.com/t/slot1/", selected.urls[selected.startIndex])
        assertEquals(12, selected.secondsFor(0))
        assertEquals(saved.configVersion, selected.configVersion)
        assertEquals(saved.updatedAt, selected.updatedAt)
        assertEquals(2, saved.startIndex)
    }
    @Test fun normalPlayAndReplayAlwaysStartAtOne() {
        assertEquals(0, UrlRunnerStartSelection.snapshot(config(), false).startIndex)
    }
    @Test fun shortListsCanStartAndEmptyListsCannot() {
        assertNull(UrlRunnerStartSelection.issue(config().copy(urls = config().urls.take(1)), true))
        assertNotNull(UrlRunnerStartSelection.issue(config().copy(urls = listOf("", "")), true))
    }
    @Test fun blankFirstCanSkipForwardWithoutRenumbering() {
        val saved = config().copy(urls = config().urls.toMutableList().apply { this[0] = "" })
        assertNull(UrlRunnerStartSelection.issue(saved, true))
        assertEquals(0, UrlRunnerStartSelection.snapshot(saved, true).startIndex)
    }
    @Test fun selectionIsADefensiveSnapshot() {
        val urls = config().urls.toMutableList()
        val selected = UrlRunnerStartSelection.snapshot(config().copy(urls = urls), true)
        urls[0] = "https://lite.tiktok.com/t/replaced/"
        assertEquals("https://lite.tiktok.com/t/slot1/", selected.urls[0])
    }
}
