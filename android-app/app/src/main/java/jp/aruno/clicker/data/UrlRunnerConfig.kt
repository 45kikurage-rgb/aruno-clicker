package jp.aruno.clicker.data

import java.net.URI

/**
 * URL順次展開（URLランナー）のデータモデルと検証ロジック。
 *
 * このファイルは Android API に依存しない純粋ロジックのみで構成し、
 * JVM 単体テストから直接検証できるようにしている。
 * サーバー（Cloudflare Worker の /v1/url-runner、schemaVersion 3）と
 * 同じ検証規則をここに集約する。規則を変える場合は Worker 側と必ず揃えること。
 */

/** 最終URLを開き終えた後の挙動。未確定事項のため enum 1箇所で切り替える。 */
enum class LoopMode {
    /** 推奨案: 最終URLで停止する（暴走防止）。 */
    STOP,

    /** 先頭へ戻って繰り返す。既定にはしない。 */
    LOOP,
}

/** URLランナーに関する制限値・初期値の集約場所。秒数の初期値と上限もここだけを変える。 */
object UrlRunnerLimits {
    /** URL一覧の最大件数。 */
    const val MAX_URLS = 200

    /** 1件のURLの最大文字数（既存 RemoteConfigClient / Worker と同じ）。 */
    const val MAX_URL_LENGTH = 2048

    /** 個別秒数を指定できる件数（1〜5番）。 */
    const val INDIVIDUAL_COUNT = 5

    /** 秒数の最小値（推奨案）。 */
    const val MIN_SECONDS = 1

    /** 秒数の最大値（推奨案: 1時間）。 */
    const val MAX_SECONDS = 3600

    /** 個別秒数の初期値（推奨案: 現行の固定待機10秒と一致させる）。 */
    const val DEFAULT_INDIVIDUAL_SECONDS = 10

    /** 共通秒数の初期値（推奨案: 現行の固定待機10秒と一致させる）。 */
    const val DEFAULT_COMMON_SECONDS = 10

    /** 最終URL後の既定モード（推奨案: 停止）。 */
    val DEFAULT_LOOP_MODE: LoopMode = LoopMode.STOP

    /**
     * 送信を許可するホスト（既存 RemoteConfigClient / Worker の許可リストと同一）。
     * TikTok以外のURLの扱いは未確定事項のため、当面はこの既存リストを維持する（推奨案）。
     */
    val ALLOWED_TIKTOK_HOSTS: Set<String> = setOf(
        "tiktok.com",
        "www.tiktok.com",
        "lite.tiktok.com",
        "m.tiktok.com",
        "vm.tiktok.com",
        "vt.tiktok.com",
    )
}

/** 検証で見つかった問題。黙って切り捨てず、件数と理由を UI / 応答へ返すために使う。 */
data class UrlRunnerIssue(
    val code: String,
    val message: String,
    /** 対象URLの0始まり位置。URL以外の問題では null。 */
    val urlIndex: Int? = null,
) {
    companion object {
        const val URLS_EMPTY = "URLS_EMPTY"
        const val TOO_MANY_URLS = "TOO_MANY_URLS"
        const val BLANK_URL = "BLANK_URL"
        const val URL_TOO_LONG = "URL_TOO_LONG"
        const val INVALID_URL = "INVALID_URL"
        const val HOST_NOT_ALLOWED = "HOST_NOT_ALLOWED"
        const val INDIVIDUAL_COUNT_MISMATCH = "INDIVIDUAL_COUNT_MISMATCH"
        const val SECONDS_OUT_OF_RANGE = "SECONDS_OUT_OF_RANGE"
    }
}

/**
 * 順序付きURL一覧＋秒数の一式。
 *
 * @param urls 先頭から順に開くURL（重複は保持する。検証で勝手に削除しない）
 * @param individualSeconds 1〜5番それぞれの指定秒数（要素数は [UrlRunnerLimits.INDIVIDUAL_COUNT] 固定）
 * @param commonSeconds 6番以降の共通指定秒数
 * @param configVersion サーバー側の楽観ロック用バージョン。未保存・未取得は 0
 * @param updatedAt サーバーが付けた更新時刻（ISO 8601）。ローカルのみの場合は空文字
 */
data class UrlRunnerConfig(
    val urls: List<String> = emptyList(),
    val individualSeconds: List<Int> = List(UrlRunnerLimits.INDIVIDUAL_COUNT) {
        UrlRunnerLimits.DEFAULT_INDIVIDUAL_SECONDS
    },
    val commonSeconds: Int = UrlRunnerLimits.DEFAULT_COMMON_SECONDS,
    val configVersion: Long = 0L,
    val updatedAt: String = "",
    val loopMode: LoopMode = LoopMode.STOP,
    val waitFromForeground: Boolean = true,
    val startIndex: Int = 0,
) {
    /** 0始まりの位置に対する待機秒数。0〜4は個別秒数、5以降は共通秒数を返す。 */
    fun secondsFor(index: Int): Int {
        require(index >= 0) { "index must be >= 0" }
        return if (index < UrlRunnerLimits.INDIVIDUAL_COUNT) {
            individualSeconds.getOrElse(index) { commonSeconds }
        } else {
            commonSeconds
        }
    }

    /** 開始・送信・保存の前に必ず通す検証。問題を全件返す（最初の1件で止めない）。 */
    fun validate(): List<UrlRunnerIssue> {
        val issues = mutableListOf<UrlRunnerIssue>()
        if (urls.none { it.isNotBlank() }) {
            issues += UrlRunnerIssue(
                UrlRunnerIssue.URLS_EMPTY,
                "URLが1件も登録されていません",
            )
        }
        if (urls.size > UrlRunnerLimits.MAX_URLS) {
            issues += UrlRunnerIssue(
                UrlRunnerIssue.TOO_MANY_URLS,
                "URLは最大${UrlRunnerLimits.MAX_URLS}件までです（現在${urls.size}件）",
            )
        }
        if (startIndex < 0 || (urls.isNotEmpty() && startIndex >= urls.size)) {
            issues += UrlRunnerIssue("START_INDEX", "開始番号は1〜${urls.size.coerceAtLeast(1)}で指定してください")
        }
        urls.forEachIndexed { index, url ->
            // A blank position is an intentional skip; retain its index for synchronization.
            if (url.isNotBlank()) validateUrl(url, index)?.let(issues::add)
        }
        if (individualSeconds.size != UrlRunnerLimits.INDIVIDUAL_COUNT) {
            issues += UrlRunnerIssue(
                UrlRunnerIssue.INDIVIDUAL_COUNT_MISMATCH,
                "個別秒数は${UrlRunnerLimits.INDIVIDUAL_COUNT}件必要です（現在${individualSeconds.size}件）",
            )
        }
        individualSeconds.forEachIndexed { index, seconds ->
            if (seconds !in UrlRunnerLimits.MIN_SECONDS..UrlRunnerLimits.MAX_SECONDS) {
                issues += UrlRunnerIssue(
                    UrlRunnerIssue.SECONDS_OUT_OF_RANGE,
                    "${index + 1}番の秒数は${UrlRunnerLimits.MIN_SECONDS}〜${UrlRunnerLimits.MAX_SECONDS}秒で指定してください（現在${seconds}秒）",
                )
            }
        }
        if (commonSeconds !in UrlRunnerLimits.MIN_SECONDS..UrlRunnerLimits.MAX_SECONDS) {
            issues += UrlRunnerIssue(
                UrlRunnerIssue.SECONDS_OUT_OF_RANGE,
                "6番以降の共通秒数は${UrlRunnerLimits.MIN_SECONDS}〜${UrlRunnerLimits.MAX_SECONDS}秒で指定してください（現在${commonSeconds}秒）",
            )
        }
        return issues
    }

    fun isValid(): Boolean = validate().isEmpty()

    /** 実行開始時に固定するスナップショット（防御的コピー）。 */
    fun snapshot(): UrlRunnerConfig = copy(
        urls = urls.toList(),
        individualSeconds = individualSeconds.toList(),
    )

    companion object {
        /**
         * 1件のURLの検証。Worker の validateStartupUrl と同じ規則:
         * https のみ、userinfo 不可、許可ホストのみ、2048文字以内。
         * 大文字小文字・クエリ・ハイフンは保持し、正規化はしない。
         */
        fun validateUrl(url: String, index: Int? = null): UrlRunnerIssue? {
            val label = index?.let { "${it + 1}番のURL" } ?: "URL"
            if (url.isBlank()) {
                return UrlRunnerIssue(UrlRunnerIssue.BLANK_URL, "$label が空欄です", index)
            }
            if (url.length > UrlRunnerLimits.MAX_URL_LENGTH) {
                return UrlRunnerIssue(
                    UrlRunnerIssue.URL_TOO_LONG,
                    "$label が長すぎます（${url.length}文字、上限${UrlRunnerLimits.MAX_URL_LENGTH}文字）",
                    index,
                )
            }
            val uri = runCatching { URI(url) }.getOrNull()
                ?: return UrlRunnerIssue(UrlRunnerIssue.INVALID_URL, "$label の形式が不正です", index)
            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return UrlRunnerIssue(UrlRunnerIssue.INVALID_URL, "$label は https で始まる必要があります", index)
            }
            if (uri.userInfo != null) {
                return UrlRunnerIssue(UrlRunnerIssue.INVALID_URL, "$label に認証情報を含めることはできません", index)
            }
            val host = uri.host
                ?: return UrlRunnerIssue(UrlRunnerIssue.INVALID_URL, "$label のホストが不正です", index)
            if (host.lowercase() !in UrlRunnerLimits.ALLOWED_TIKTOK_HOSTS) {
                return UrlRunnerIssue(
                    UrlRunnerIssue.HOST_NOT_ALLOWED,
                    "$label のホスト（$host）は送信許可リストにありません",
                    index,
                )
            }
            return null
        }

        /** 抽出段階用: http/https のURLとして成り立つかだけを見る（許可ホストは見ない）。 */
        fun isWellFormedHttpUrl(url: String): Boolean {
            if (url.isBlank() || url.length > UrlRunnerLimits.MAX_URL_LENGTH) return false
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            val scheme = uri.scheme ?: return false
            if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) {
                return false
            }
            return !uri.host.isNullOrBlank() && uri.userInfo == null
        }
    }
}

/**
 * 既存の設定同期URL（…/v1/config）から URLランナー用エンドポイント（…/v1/url-runner）を導く。
 * 導けない形式は null を返し、呼び出し側は通信を行わない。
 */
object UrlRunnerEndpoint {
    const val PATH = "/v1/url-runner"
    private const val LEGACY_PATH = "/v1/config"

    fun derive(remoteServerUrl: String): String? {
        val value = remoteServerUrl.trim()
        if (value.isEmpty() || value.length > UrlRunnerLimits.MAX_URL_LENGTH) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank() || uri.userInfo != null) return null
        val path = uri.path.orEmpty().trimEnd('/')
        val basePath = when {
            path.endsWith(LEGACY_PATH) -> path.removeSuffix(LEGACY_PATH)
            path.endsWith(PATH) -> return value.trimEnd('/')
            path.isEmpty() -> ""
            else -> return null
        }
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "https://${uri.host}$port$basePath$PATH"
    }
}
