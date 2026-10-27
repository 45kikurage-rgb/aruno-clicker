package jp.aruno.clicker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {
    @Test
    fun extractsUrlsInOrderFromMixedTextAndNewlines() {
        val text = """
            今日の分です
            1つ目 https://lite.tiktok.com/t/AAA/ を見てね
            文章の途中にも https://vm.tiktok.com/BBB/ があります。
            最後は https://lite.tiktok.com/t/CCC/
        """.trimIndent()
        assertEquals(
            listOf(
                "https://lite.tiktok.com/t/AAA/",
                "https://vm.tiktok.com/BBB/",
                "https://lite.tiktok.com/t/CCC/",
            ),
            UrlExtractor.extract(text),
        )
    }

    @Test
    fun preservesCaseQueryAndHyphen() {
        val url = "HTTPS://Lite.TikTok.com/t/AbC-dEf/?Query=Val-ue_1&x=Y"
        assertEquals(listOf(url), UrlExtractor.extract("url: $url end"))
    }

    @Test
    fun keepsDuplicateUrls() {
        val url = "https://lite.tiktok.com/t/DUP/"
        assertEquals(listOf(url, url), UrlExtractor.extract("$url $url"))
    }

    @Test
    fun extractsHttpAndHttps() {
        assertEquals(
            listOf("http://example.com/a", "https://example.com/b"),
            UrlExtractor.extract("http://example.com/a と https://example.com/b"),
        )
    }

    @Test
    fun ignoresNonUrlText() {
        assertEquals(
            emptyList<String>(),
            UrlExtractor.extract("www.example.com スキームなし ftp://example.com/x メール a@b.com"),
        )
    }

    @Test
    fun stripsTrailingJapanesePunctuation() {
        assertEquals(
            listOf("https://lite.tiktok.com/t/ABC/"),
            UrlExtractor.extract("これを見て。https://lite.tiktok.com/t/ABC/。次へ"),
        )
    }

    @Test
    fun stripsUnbalancedClosingBracketButKeepsBalancedOnes() {
        assertEquals(
            listOf("https://example.com/a"),
            UrlExtractor.extract("(https://example.com/a)"),
        )
        assertEquals(
            listOf("https://example.com/a(b)"),
            UrlExtractor.extract("https://example.com/a(b) です"),
        )
    }

    @Test
    fun incompleteUrlIsReportedNotSilentlyDropped() {
        val report = UrlExtractor.extractDetailed("壊れた https:// と 正常 https://lite.tiktok.com/t/OK/")
        assertEquals(listOf("https://lite.tiktok.com/t/OK/"), report.urls)
        assertEquals(1, report.rejected.size)
        assertTrue(report.rejected[0].reason.isNotBlank())
    }
    @Test fun preservesAllValidTrailingPunctuation() {
        val values = listOf("!", "?", ".", ",", ";", ":", "!?").map { "https://lite.tiktok.com/t/AbC/?x=wow$it" }
        assertEquals(values, UrlExtractor.extract(values.joinToString("\n")))
    }
    @Test fun keepsLegitimateUnbalancedClosingParenthesisWithoutOuterWrapper() {
        val url = "https://lite.tiktok.com/t/ABC/?x=)"
        assertEquals(listOf(url), UrlExtractor.extract(url))
    }
}
