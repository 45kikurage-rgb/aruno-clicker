package jp.aruno.clicker.ui

import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.data.UrlRunnerJson
import org.junit.Assert.assertEquals
import org.junit.Test

class UrlRunnerRevisionLabelTest {
    @Test fun serverLastSavedUtcRendersAsJapaneseTokyoDate() {
        assertEquals(
            "10月29日7時35分版",
            UrlRunnerRevisionLabel.format("2026-10-28T22:35:00.000Z"),
        )
        assertEquals(
            "10月29日8時07分版",
            UrlRunnerRevisionLabel.format("2026-10-28T23:07:00Z"),
        )
    }

    @Test fun neverUsesPhoneTimeZoneOrCurrentTimeForDisplayedVersion() {
        assertEquals(
            "1月1日0時05分版",
            UrlRunnerRevisionLabel.format("2026-12-31T15:05:00Z"),
        )
        assertEquals(
            "10月29日7時35分版",
            UrlRunnerRevisionLabel.format("2026-10-29T07:35:00+09:00"),
        )
    }

    @Test fun unknownTimestampDoesNotPretendOldConfigVersionIsADate() {
        for (value in listOf(null, "", "   ", "版5", "2026-99-99", "not an instant")) {
            assertEquals("更新日時未取得", UrlRunnerRevisionLabel.format(value))
        }
    }

    @Test fun savedServerTimestampSurvivesLocalJsonEncodeDecode() {
        val config = UrlRunnerConfig(
            urls = listOf("https://lite.tiktok.com/t/ZS-example/"),
            configVersion = 5L,
            updatedAt = "2026-10-28T22:35:00.000Z",
        )
        val restored = UrlRunnerJson.decodeConfig(UrlRunnerJson.encodeConfig(config))
        assertEquals(config, restored)
        assertEquals("10月29日7時35分版", UrlRunnerRevisionLabel.format(restored?.updatedAt))
    }

    @Test fun versionNumberStillExistsForConflictDetectionButNotInDisplay() {
        val config = UrlRunnerConfig(
            urls = listOf("https://lite.tiktok.com/t/ZS-example/"),
            configVersion = 149L,
            updatedAt = "2026-10-28T22:35:00Z",
        )
        assertEquals(149L, config.configVersion)
        assertEquals("10月29日7時35分版", UrlRunnerRevisionLabel.format(config.updatedAt))
    }
}
