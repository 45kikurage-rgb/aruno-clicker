package jp.aruno.clicker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRunnerConfigTest {
    private fun url(i: Int) = "https://lite.tiktok.com/t/U$i/"

    private fun config(
        count: Int,
        individual: List<Int> = listOf(11, 12, 13, 14, 15),
        common: Int = 99,
    ) = UrlRunnerConfig(
        urls = (0 until count).map(::url),
        individualSeconds = individual,
        commonSeconds = common,
    )

    @Test
    fun secondsForUsesIndividualForFirstFiveAndCommonAfter() {
        val config = config(8)
        assertEquals(11, config.secondsFor(0))
        assertEquals(15, config.secondsFor(4))
        assertEquals(99, config.secondsFor(5))
        assertEquals(99, config.secondsFor(7))
    }

    @Test
    fun emptyListIsInvalid() {
        val issues = config(0).validate()
        assertTrue(issues.any { it.code == UrlRunnerIssue.URLS_EMPTY })
    }

    @Test
    fun boundary120IsValidAnd121IsTooMany() {
        assertTrue(config(120).validate().isEmpty())
        val issues = config(121).validate()
        assertTrue(issues.any { it.code == UrlRunnerIssue.TOO_MANY_URLS })
    }

    @Test
    fun duplicatesAreValid() {
        val config = UrlRunnerConfig(urls = listOf(url(1), url(1)))
        assertTrue(config.validate().isEmpty())
    }

    @Test
    fun disallowedHostIsReportedWithPosition() {
        val config = UrlRunnerConfig(
            urls = listOf(url(1), "https://example.com/x"),
        )
        val issue = config.validate().single { it.code == UrlRunnerIssue.HOST_NOT_ALLOWED }
        assertEquals(1, issue.urlIndex)
    }

    @Test
    fun httpAndUserinfoAreRejected() {
        assertEquals(
            UrlRunnerIssue.INVALID_URL,
            UrlRunnerConfig.validateUrl("http://lite.tiktok.com/t/a/")?.code,
        )
        assertEquals(
            UrlRunnerIssue.INVALID_URL,
            UrlRunnerConfig.validateUrl("https://user@lite.tiktok.com/t/a/")?.code,
        )
        assertNull(UrlRunnerConfig.validateUrl("https://lite.tiktok.com/t/a/"))
    }

    @Test
    fun secondsOutOfRangeAreInvalid() {
        assertTrue(
            config(1, individual = listOf(0, 10, 10, 10, 10)).validate()
                .any { it.code == UrlRunnerIssue.SECONDS_OUT_OF_RANGE },
        )
        assertTrue(
            config(1, common = 3601).validate()
                .any { it.code == UrlRunnerIssue.SECONDS_OUT_OF_RANGE },
        )
        assertTrue(config(1, individual = listOf(1, 3600, 10, 10, 10), common = 3600).validate().isEmpty())
    }

    @Test
    fun endpointDerivation() {
        assertEquals(
            "https://aruno-clicker-config.45kikurage.workers.dev/v1/url-runner",
            UrlRunnerEndpoint.derive("https://aruno-clicker-config.45kikurage.workers.dev/v1/config"),
        )
        assertEquals(
            "https://example.com/v1/url-runner",
            UrlRunnerEndpoint.derive("https://example.com/"),
        )
        assertNull(UrlRunnerEndpoint.derive("http://example.com/v1/config"))
        assertNull(UrlRunnerEndpoint.derive("https://example.com/other/path"))
    }

    @Test
    fun jsonRoundTripPreservesEverything() {
        val original = config(3).copy(configVersion = 7L, updatedAt = "2026-10-27T00:00:00.000Z")
        val decoded = UrlRunnerJson.decodeConfig(UrlRunnerJson.encodeConfig(original))
        assertEquals(original, decoded)
    }

    @Test
    fun jsonDecodeRejectsGarbage() {
        assertNull(UrlRunnerJson.decodeConfig("not json"))
        assertNull(UrlRunnerJson.decodeConfig("{\"urls\":[1]}"))
    }
}
