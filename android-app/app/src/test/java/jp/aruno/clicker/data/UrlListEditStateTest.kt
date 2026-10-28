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
        assertEquals("https://lite.tiktok.com/t/P10/", state.slotAt(14))
        assertEquals("", state.slotAt(15))

        val report11 = state.applyPaste(pasteText(11), PasteMode.REPLACE)
        assertEquals(11, report11.appliedCount)
        // 11件目は16番へ入る。
        assertEquals("https://lite.tiktok.com/t/P11/", state.slotAt(15))
    }

    @Test
    fun pasteBoundaries20And21() {
        val state = UrlListEditState()
        state.applyPaste(pasteText(21), PasteMode.REPLACE)
        assertEquals("https://lite.tiktok.com/t/P20/", state.slotAt(24))
        assertEquals("https://lite.tiktok.com/t/P21/", state.slotAt(25))
        assertEquals(21, state.filledCount())
    }

    @Test
    fun paste195FillsSlots6To200And196ReportsExcess() {
        val state = UrlListEditState()
        repeat(5) { state.setSlot(it, "https://lite.tiktok.com/t/FIXED${it + 1}/") }
        val report195 = state.applyPaste(pasteText(195), PasteMode.REPLACE)
        assertEquals(195, report195.appliedCount)
        assertEquals(0, report195.excessCount)
        assertEquals("https://lite.tiktok.com/t/FIXED1/", state.slotAt(0))
        assertEquals("https://lite.tiktok.com/t/P195/", state.slotAt(199))
        assertEquals(200, state.filledCount())

        val report196 = state.applyPaste(pasteText(196), PasteMode.REPLACE)
        assertEquals(195, report196.appliedCount)
        assertEquals(1, report196.excessCount)
        assertEquals(listOf("https://lite.tiktok.com/t/P196/"), report196.excessUrls)
        assertNotNull(report196.excessReason)
    }

    @Test
    fun appendKeepsExistingAndReplaceClears() {
        val state = UrlListEditState()
        state.setSlot(0, "https://lite.tiktok.com/t/FIXED/")
        state.applyPaste(pasteText(3), PasteMode.REPLACE)
        val appendReport = state.applyPaste("https://lite.tiktok.com/t/ADD/", PasteMode.APPEND)
        assertEquals(1, appendReport.appliedCount)
        assertEquals("https://lite.tiktok.com/t/ADD/", state.slotAt(8))
        assertEquals(5, state.filledCount())

        state.applyPaste("https://lite.tiktok.com/t/NEW/", PasteMode.REPLACE)
        assertEquals(2, state.filledCount())
        assertEquals("https://lite.tiktok.com/t/FIXED/", state.slotAt(0))
        assertEquals("https://lite.tiktok.com/t/NEW/", state.slotAt(5))
        assertEquals("", state.slotAt(6))
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
    fun middleBlankIsKeptAndAcceptedForSkipping() {
        val state = UrlListEditState()
        state.setSlot(0, "https://lite.tiktok.com/t/A/")
        state.setSlot(2, "https://lite.tiktok.com/t/C/")
        assertEquals(
            listOf("https://lite.tiktok.com/t/A/", "", "https://lite.tiktok.com/t/C/"),
            state.effectiveUrls(),
        )
        val issues = mutableListOf<String>()
        val config = state.buildConfig(issues = issues)
        assertNotNull(config)
        assertTrue(issues.isEmpty())
        assertEquals("", config!!.urls[1])
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
        assertEquals(12, config!!.urls.size)
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
