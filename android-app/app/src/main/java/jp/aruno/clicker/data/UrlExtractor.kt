package jp.aruno.clicker.data

/**
 * 一括貼り付け文字列からの URL 抽出（純粋ロジック）。
 *
 * 仕様:
 * - `http://` または `https://` で始まる部分文字列を、出現順に抽出する。
 *   スキームの大文字小文字は判定にだけ使い、抽出結果は原文のまま保持する
 *   （大文字小文字・クエリ文字列・ハイフンを書き換えない）。
 * - 重複URLは削除しない。
 * - URL でない文字列（スキームのない文章、`ftp://` 等）は抽出対象にしない。
 * - 候補は ASCII の表示可能文字だけで構成する。日本語の文字・句読点・括弧は
 *   区切りとして扱うため、文章に密着したURLでも日本語部分を吸着しない。
 * - 候補の末尾処理:
 *   - URL内で意味を持つ `.,;:!?` は末尾でも原文のまま保持する。
 *   - 末尾の閉じ括弧（`) ] }`）は、候補内に対応する開き括弧が
 *     足りない場合だけ取り除く（URL内の正当な括弧は保持する）。
 *   - ハイフンは意味を持つ文字として末尾でも保持する。
 * - 末尾処理の結果、URL として成り立たなくなった候補（`https://` だけ等）は
 *   黙って捨てず、[ExtractionReport.rejected] に理由付きで返す。
 */
object UrlExtractor {
    data class RejectedCandidate(
        val raw: String,
        val reason: String,
    )

    data class ExtractionReport(
        /** 抽出できたURL（出現順、重複保持）。 */
        val urls: List<String>,
        /** URLとして成り立たず除外した候補と、その理由。 */
        val rejected: List<RejectedCandidate>,
    )

    // スキームの後ろが0文字の候補（"https://" だけ）も拾い、不完全として報告する。
    private val CANDIDATE_PATTERN = Regex(
        "(?i)https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&()*+,;=%]*",
    )

    private val CLOSING_TO_OPENING = mapOf(
        ')' to '(',
        ']' to '[',
        '}' to '{',
    )

    fun extract(text: String): List<String> = extractDetailed(text).urls

    fun extractDetailed(text: String): ExtractionReport {
        if (text.isEmpty()) return ExtractionReport(emptyList(), emptyList())
        val urls = mutableListOf<String>()
        val rejected = mutableListOf<RejectedCandidate>()
        for (match in CANDIDATE_PATTERN.findAll(text)) {
            val trimmed = trimTrailing(match.value, text.getOrNull(match.range.first - 1))
            when {
                trimmed.isEmpty() || trimmed.equals("http://", ignoreCase = true) ||
                    trimmed.equals("https://", ignoreCase = true) -> {
                    rejected += RejectedCandidate(match.value, "URLが不完全です")
                }
                !UrlRunnerConfig.isWellFormedHttpUrl(trimmed) -> {
                    rejected += RejectedCandidate(match.value, "URLの形式が不正です")
                }
                else -> urls += trimmed
            }
        }
        return ExtractionReport(urls, rejected)
    }

    /** 末尾の句読点と、対応する開き括弧が不足する閉じ括弧だけを取り除く。 */
    internal fun trimTrailing(candidate: String, outerOpening: Char? = null): String {
        var result = candidate
        while (result.isNotEmpty()) {
            val last = result.last()
            when {
                last in CLOSING_TO_OPENING && CLOSING_TO_OPENING[last] == outerOpening -> {
                    val opening = CLOSING_TO_OPENING.getValue(last)
                    val openings = result.count { it == opening }
                    val closings = result.count { it == last }
                    if (closings > openings) {
                        result = result.dropLast(1)
                    } else {
                        return result
                    }
                }
                else -> return result
            }
        }
        return result
    }
}
