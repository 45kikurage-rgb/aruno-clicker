package jp.aruno.clicker.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterruptionClassifierTest {
    @Test
    fun contactsPermissionDialogRequiresCompleteContext() {
        assertTrue(
            InterruptionClassifier.isContactsPermissionDialog(
                listOf(
                    "連絡先へのアクセスを TikTok-Lite に許可しますか？",
                    "許可",
                    "許可しない",
                ),
            ),
        )
        assertFalse(
            InterruptionClassifier.isContactsPermissionDialog(
                listOf("通知を許可しますか？", "許可", "許可しない"),
            ),
        )
    }

    @Test
    fun onlyExactContactsDenyActionIsAccepted() {
        assertTrue(InterruptionClassifier.isContactsDenyAction("許可しない", null))
        assertTrue(InterruptionClassifier.isContactsDenyAction(null, "許可しない"))
        assertFalse(InterruptionClassifier.isContactsDenyAction("許可", null))
    }

    @Test
    fun coinStockFallbackRequiresAllPopupMarkers() {
        assertTrue(
            InterruptionClassifier.isCoinStockPopup(
                listOf("コインストックを割って", "ゲットしよう", "4,000円分", "始める"),
            ),
        )
        assertFalse(
            InterruptionClassifier.isCoinStockPopup(
                listOf("4,000円分", "始める", "×"),
            ),
        )
        assertFalse(
            InterruptionClassifier.isCoinStockPopup(
                listOf("おすすめ", "始める", "×"),
            ),
        )
    }

    @Test
    fun existingPopupClassifierReadsDescriptionAndResourceId() {
        assertTrue(PopupCloseClassifier.score(null, "×", null) > 0)
        assertTrue(PopupCloseClassifier.isExactCloseSymbol(null, "✕"))
        assertTrue(PopupCloseClassifier.score(null, null, "popup_close_button") > 0)
        assertFalse(PopupCloseClassifier.score("始める", null, "primary_button") > 0)
    }

    @Test
    fun recentsMissingTargetMeaningDependsOnPriorDismissal() {
        assertFalse(RecentsDismissPolicy.missingTargetIsSuccess(dismissedCount = 0))
        assertTrue(RecentsDismissPolicy.missingTargetIsSuccess(dismissedCount = 1))
    }

    @Test
    fun targetGoneAfterDismissGestureIsSuccess() {
        assertTrue(RecentsDismissPolicy.targetRemovalSucceeded(targetStillVisibleAfterGesture = false))
        assertFalse(RecentsDismissPolicy.targetRemovalSucceeded(targetStillVisibleAfterGesture = true))
    }

    @Test
    fun forceLaunchRequiresContinuousThirtySecondAbsence() {
        assertFalse(TargetForegroundPolicy.shouldForceLaunch(29_999L, 30_000L))
        assertTrue(TargetForegroundPolicy.shouldForceLaunch(30_000L, 30_000L))
    }
}
