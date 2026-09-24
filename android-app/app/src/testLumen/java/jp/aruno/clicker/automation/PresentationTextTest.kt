package jp.aruno.clicker.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationTextTest {
    @Test
    fun verSRemovesPrivateStartupTerms() {
        val output = PresentationText.sanitize("初回URL完了")
        assertEquals("開始準備中", output)
        assertFalse(output.contains("URL"))
        assertFalse(output.contains("初回"))
    }
}
