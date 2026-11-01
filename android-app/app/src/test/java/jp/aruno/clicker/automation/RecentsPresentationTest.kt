package jp.aruno.clicker.automation
import org.junit.Assert.*
import org.junit.Test
class RecentsPresentationTest {
    @Test fun preparingShowsCleanupInsteadOfClaimingNextVideoIsOpening() {
        val text=UrlRunnerOverlayCountdown.render(true,"00:02:03","停止まで",null,true,true)
        assertTrue(text.contains("履歴カード")); assertFalse(text.contains("次の動画を開いて"))
    }
    @Test fun existingCountdownRemainsWhenNotPreparing() {
        assertTrue(UrlRunnerOverlayCountdown.render(true,"00:00:01","停止まで",10,true).contains("あと10秒"))
    }
}
