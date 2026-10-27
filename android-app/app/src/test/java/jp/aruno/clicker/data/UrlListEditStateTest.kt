package jp.aruno.clicker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlListEditStateTest {
    private fun pasteText(count: Int): String =
        (1..count).joinToString("\n") { "https://lite.tiktok.com/t/P$it/" }

    @Test
    fun pasteBoundaries10And11() {
        val state = UrlListEditState()
        val report10 = state.applyPaste(pasteText(10), PasteMode.REPLACE)
        assertEquals(10, report10.appliedCount)
        assertEquals(0, report10.excessCount)
        assertEquals(10, state.filledCount())
        assertEquals("https://lite.tiktok.com/t/P10/", state.slotAt(9))
        assertEquals("", state.slotAt(10))

        val report11 = state.applyPaste(pasteText(11), PasteMode.REPLACE)
        assertEquals(11, report11.appliedCount)
        // 11件目は次の範囲（011-020）の先頭スロットに入る。
        assertEquals("https://lite.tiktok.com/t/P11/", state.slotAt(10))
    }

    @Test
    fun pasteBoundaries20And21() {
        val state = UrlListEditState()
        state.applyPaste(pasteText(21), PasteMode.REPLACE)
        assertEquals("https://lite.tiktok.com/t/P20/", state.slotAt(19))
        assertEquals("https://lite.tiktok.com/t/P21/", state.slotAt(20))
        assertEquals(21, state.filledCount())
    }

    @Test
    fun paste120FillsExactlyAnd121ReportsExcess() {
        val state = UrlListEditState()
        val report120 = state.applyPaste(pasteText(120), PasteMode.REPLACE)
        assertEquals(120, report120.appliedCount)
        assertEquals(0, report120.excessCount)

        val report121 = state.applyPaste(pasteText(121), PasteMode.REPLACE)
        assertEquals(120, report121.appliedCount)
        assertEquals(1, report121.excessCount)
        assertEquals(listOf("https://lite.tiktok.com/t/P121/"), report121.excessUrls)
        assertNotNull(report121.excessReason)
    }

    @Test
    fun appendKeepsExistingAndReplaceClears() {
        val state = UrlListEditState()
        state.applyPaste(pasteText(3), PasteMode.REPLACE)
        val appendReport = state.applyPaste("https://lite.tiktok.com/t/ADD/", PasteMode.APPEND)
        assertEquals(1, appendReport.appliedCount)
        assertEquals("https://lite.tiktok.com/t/ADD/", state.slotAt(3))
        assertEquals(4, state.filledCount())

        state.applyPaste("https://lite.tiktok.com/t/NEW/", PasteMode.REPLACE)
        assertEquals(1, state.filledCount())
        assertEquals("https://lite.tiktok.com/t/NEW/", state.slotAt(0))
    }

    @Test
    fun editsSurviveRangeSwitching() {
        // 範囲切替は「表示するスロットを変える」だけであり、
        // ホルダーに即時保存された編集は他の範囲を編集しても保持される。
        val state = UrlListEditState()
        state.setSlot(0, "https://lite.tiktok.com/t/RANGE1/")
        state.setSlot(9, "https://lite.tiktok.com/t/RANGE1-LAST/")
        // 別範囲（011-020）を編集中にする操作に相当。
        state.setSlot(10, "https://lite.tiktok.com/t/RANGE2/")
        state.setSlot(19, "https://lite.tiktok.com/t/RANGE2-LAST/")
        assertEquals("https://lite.tiktok.com/t/RANGE1/", state.slotAt(0))
        assertEquals("https://lite.tiktok.com/t/RANGE1-LAST/", state.slotAt(9))
        assertEquals("https://lite.tiktok.com/t/RANGE2-LAST/", state.slotAt(19))
    }

    @Test
    fun middleBlankIsKeptAndFailsBuild() {
        val state = UrlListEditState()
        state.setSlot(0, "https://lite.tiktok.com/t/A/")
        state.setSlot(2, "https://lite.tiktok.com/t/C/")
        assertEquals(
            listOf("https://lite.tiktok.com/t/A/", "", "https://lite.tiktok.com/t/C/"),
            state.effectiveUrls(),
        )
        val issues = mutableListOf<String>()
        assertNull(state.buildConfig(issues = issues))
        assertTrue(issues.any { it.contains("2番") })
    }

    @Test
    fun secondsParsingReportsEveryBadField() {
        val state = UrlListEditState()
        state.individualSecondsText[0] = "abc"
        state.individualSecondsText[1] = "0"
        state.commonSecondsText = "3601"
        val result = state.parseSeconds()
        assertEquals(false, result.isValid)
        assertEquals(3, result.errors.size)
    }

    @Test
    fun buildConfigSucceedsWithValidState() {
        val state = UrlListEditState()
        state.applyPaste(pasteText(7), PasteMode.REPLACE)
        state.individualSecondsText[0] = "3"
        state.commonSecondsText = "44"
        val issues = mutableListOf<String>()
        val config = state.buildConfig(issues = issues)
        assertNotNull(config)
        assertTrue(issues.isEmpty())
        assertEquals(7, config!!.urls.size)
        assertEquals(3, config.secondsFor(0))
        assertEquals(44, config.secondsFor(6))
    }

    @Test
    fun loadFromRestoresSavedConfig() {
        val saved = UrlRunnerConfig(
            urls = listOf("https://lite.tiktok.com/t/S1/", "https://lite.tiktok.com/t/S2/"),
            individualSeconds = listOf(1, 2, 3, 4, 5),
            commonSeconds = 6,
        )
        val state = UrlListEditState()
        state.loadFrom(saved)
        assertEquals(saved.urls, state.effectiveUrls())
        assertEquals("3", state.individualSecondsText[2])
        assertEquals("6", state.commonSecondsText)
    }
}
