package jp.aruno.clicker.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class PresentationTextTest {

    @Test
    fun floatingTimerUsesNextVideoAndNeverShowsStopForeverInRunnerMode() {
        assertEquals(
            "経過 00:00:19\n次の動画まであと11秒",
            UrlRunnerOverlayCountdown.render(true, "00:00:19", "停止まで", 11, true),
        )
        assertEquals(
            "経過 00:00:30\n終了まであと5秒",
            UrlRunnerOverlayCountdown.render(true, "00:00:30", "停止まで", 5, false),
        )
        assertEquals(
            "経過 00:00:20\n次の動画を開いています",
            UrlRunnerOverlayCountdown.render(true, "00:00:20", "停止まで", null, true),
        )
        assertEquals(
            "経過 00:00:20  /  残り 00:10:00",
            UrlRunnerOverlayCountdown.render(false, "00:00:20", "00:10:00", null, false),
        )
    }

    @Test
    fun ownerBuildKeepsDetailedStatus() {
        assertEquals("初回URL完了", PresentationText.sanitize("初回URL完了"))
    }
}
