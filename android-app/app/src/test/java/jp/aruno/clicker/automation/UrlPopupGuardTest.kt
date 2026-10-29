package jp.aruno.clicker.automation

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class UrlPopupGuardTest {
    private val screen = Rect(0, 0, 1000, 2000)
    private val pkg = "com.ss.android.ugc.tiktok.lite"
    private fun node(text: String, b: Rect, clickable: Boolean = false) = AccessibilityNodeInfo.obtain().apply {
        packageName = pkg; this.text = text; setBoundsInScreen(b)
        isVisibleToUser = true; isEnabled = true; isClickable = clickable
    }
    private fun root(children: List<AccessibilityNodeInfo>, packageName: String = pkg): AccessibilityNodeInfo =
        node("", screen).apply {
            this.packageName = packageName
            children.forEach { shadowOf(this).addChild(it) }
        }
    private fun popup(close: Boolean = true, extra: List<AccessibilityNodeInfo> = emptyList()) = root(listOf(
        node("コインストックを割って ゲットしよう", Rect(250, 750, 750, 950)),
        node("始める", Rect(300, 1150, 700, 1250), true),
    ) + (if (close) listOf(node("×", Rect(460, 1420, 520, 1480), true)) else emptyList()) + extra)
    private fun scan(root: AccessibilityNodeInfo) = PopupCloseScanner.scan(listOf(PopupWindowRoot(root, 1, 0)), screen, pkg)

    @Test fun fullContextFindsSmallCloseAndIgnoresUnrelatedTopRightClose() {
        val s = scan(popup(extra = listOf(node("×", Rect(900, 80, 960, 140), true))))
        assertTrue(s.known); assertTrue(s.complete)
        assertEquals(Rect(460, 1420, 520, 1480), s.candidate?.bounds)
        assertEquals("要素", s.candidate?.method)
    }
    @Test fun explicitCloseDoesNotRequireCoinContextButForeignPackageIsRejected() {
        assertNotNull(scan(root(listOf(node("×", Rect(460, 1420, 520, 1480), true)))).candidate)
        assertNull(scan(root(listOf(node("コインストック ゲットしよう", Rect(100, 700, 900, 900)),
            node("始める", Rect(300, 1150, 700, 1250), true)), "foreign.app")).candidate)
    }
    @Test fun nonCoinPopupsAcceptTopRightOrBottomCloseLabels() {
        for (label in listOf("×", "閉じる", "Close", "Dismiss")) {
            val b = Rect(880, 180, 950, 250)
            val s = scan(root(listOf(node("お知らせ", Rect(100, 500, 900, 800)), node(label, b, true))))
            assertTrue(s.known); assertEquals(b, s.candidate?.bounds)
        }
    }
    @Test fun unlabelledUnknownPopupOrOversizedCloseGetsNoCoordinateGuess() {
        assertNull(scan(root(listOf(node("お知らせ", Rect(100, 500, 900, 800))))).candidate)
        assertNull(scan(root(listOf(node("×", Rect(100, 100, 900, 1900), true)))).candidate)
        assertNull(scan(root(listOf(node("x", Rect(800, 200, 850, 250), true)))).candidate)
    }

    @Test fun disabledCloseDuringLoadingCannotCountAsDisappearance() {
        val close = node("閉じる", Rect(880, 180, 950, 250), true).apply { isEnabled = false }
        val pending = scan(root(listOf(close)))
        assertTrue(pending.complete); assertTrue(pending.known); assertNull(pending.candidate)
        val dismissed = scan(root(emptyList()))
        assertTrue(dismissed.complete); assertFalse(dismissed.known)
    }

    @Test fun noCloseNodeUsesBoundedFallbackAndNeverHitsStart() {
        val s = scan(popup(close = false))
        assertTrue(s.known)
        val b = requireNotNull(s.candidate).bounds
        assertTrue(b.top > 1250)
        assertFalse(Rect.intersects(b, Rect(300, 1150, 700, 1250)))
    }
    @Test fun fallbackOverClaimIsRejected() {
        val s = scan(popup(false, listOf(node("受け取る", Rect(400, 1330, 600, 1430), true))))
        assertTrue(s.known); assertNull(s.candidate)
    }
    @Test fun separateTargetDialogWindowIsScanned() {
        val s = PopupCloseScanner.scan(listOf(PopupWindowRoot(root(emptyList()), 1, 0),
            PopupWindowRoot(popup(), 1, 2)), screen, pkg)
        assertTrue(s.known); assertNotNull(s.candidate)
    }
    @Test fun truncatedOrMissingTreeNeverOffersTapOrDismissalProof() {
        assertFalse(PopupCloseScanner.scan(emptyList(), screen, pkg).complete)
        val tooMany = (0..4000).map { node("item", Rect(0, 0, 1, 1)) }
        val s = scan(popup(extra = tooMany))
        assertFalse(s.complete); assertNull(s.candidate)
    }
    @Test fun unsafeSizeEdgesAndNavigationAreRejectedAtBothResolutions() {
        for (scale in listOf(1, 2)) {
            val s = Rect(0, 0, 1000 * scale, 2000 * scale)
            assertTrue(PopupCloseScanner.safeCloseBounds(Rect(460*scale, 1420*scale, 520*scale, 1480*scale), s))
            assertFalse(PopupCloseScanner.safeCloseBounds(Rect(0, 0, s.width(), s.height()), s))
            assertFalse(PopupCloseScanner.safeCloseBounds(Rect(460*scale, 1900*scale, 520*scale, 1980*scale), s))
        }
    }
    @Test fun missingAmountAndCommaVariationsStillRequireAllThreeContextMarkers() {
        for (amount in listOf("500円分", "8000円分", "8,000円分", "")) {
            assertTrue(InterruptionClassifier.isCoinStockPopup(listOf("コインストック", "ゲットしよう", amount, "始める")))
        }
        assertFalse(InterruptionClassifier.isCoinStockPopup(listOf("コインストック", "500円分", "始める")))
    }
    @Test fun animationAndTransientAbsenceCannotResetThreeAttemptBudget() {
        val b = PopupAttemptBudget(); b.observe(true, 0)
        assertTrue(b.reserve(0)); assertFalse(b.reserve(899)); assertTrue(b.reserve(900))
        b.observe(false, 1000); b.observe(true, 1100)
        assertTrue(b.reserve(1800)); assertFalse(b.reserve(2700)); assertTrue(b.exhausted)
        assertEquals(3, b.totalAttempts)
    }
    @Test fun stableAbsenceResetsEpisodeButKeepsPerVideoCap() {
        val b = PopupAttemptBudget(); b.observe(true, 0)
        listOf(0L, 900L, 1800L).forEach { assertTrue(b.reserve(it)) }
        b.observe(false, 2000); b.observe(false, 3000); b.observe(true, 3100)
        listOf(3100L, 4000L, 4900L).forEach { assertTrue(b.reserve(it)) }
        b.observe(false, 5000); b.observe(false, 6000); b.observe(true, 6100)
        assertFalse(b.reserve(6100)); assertEquals(6, b.totalAttempts)
    }
    @Test fun popupWithoutCandidateHasBoundedEightSecondDeadline() {
        val b = PopupAttemptBudget(); b.observe(true, 0)
        assertTrue(b.blocking(7999)); assertFalse(b.blocking(8000)); assertTrue(b.exhausted)
        assertEquals(0, b.totalAttempts)
    }
}
