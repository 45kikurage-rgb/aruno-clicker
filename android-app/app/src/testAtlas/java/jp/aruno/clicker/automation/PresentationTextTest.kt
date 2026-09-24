package jp.aruno.clicker.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationTextTest {
    @Test
    fun ownerBuildKeepsDetailedStatus() {
        assertEquals("初回URL完了", PresentationText.sanitize("初回URL完了"))
    }
}
