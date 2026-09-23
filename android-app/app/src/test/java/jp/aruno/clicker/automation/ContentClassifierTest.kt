package jp.aruno.clicker.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentClassifierTest {
    @Test
    fun genericCallToActionIsNotEnoughForAd() {
        assertNull(ContentClassifier.classify(listOf("詳しくはこちら", "今すぐ購入", "shop now")))
    }

    @Test
    fun explicitAdLabelIsDetected() {
        assertEquals(ContentClassifier.AD, ContentClassifier.classify(listOf("スポンサー")))
    }

    @Test
    fun photoCounterIsDetected() {
        assertEquals(ContentClassifier.PHOTO, ContentClassifier.classify(listOf("2 / 7")))
    }

    @Test
    fun regularVideoLabelsStayNormal() {
        assertNull(ContentClassifier.classify(listOf("おすすめ", "もっと見る", "1コメする")))
    }
}
