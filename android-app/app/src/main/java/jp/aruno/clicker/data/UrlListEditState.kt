package jp.aruno.clicker.data

/** 一括貼り付けの取り込み方法。既存一覧を無断で消さないため、必ず明示的に選ぶ。 */
enum class PasteMode {
    /** 既存一覧の末尾へ追加する。 */
    APPEND,

    /** 既存一覧を空にしてから取り込む。 */
    REPLACE,
}

/** 一括貼り付け1回分の結果報告。超過分は黙って捨てず、ここへ全件保持する。 */
data class PasteReport(
    val mode: PasteMode,
    /** 貼り付け文字列から抽出できたURLの件数。 */
    val foundCount: Int,
    /** 実際に一覧へ取り込めた件数。 */
    val appliedCount: Int,
    /** 上限超過で取り込めなかったURL（理由は [excessReason]）。 */
    val excessUrls: List<String>,
    /** 抽出段階でURLとして成り立たなかった候補と理由。 */
    val rejected: List<UrlExtractor.RejectedCandidate>,
) {
    val excessCount: Int get() = excessUrls.size
    val excessReason: String?
        get() = if (excessUrls.isEmpty()) {
            null
        } else {
            "上限${UrlRunnerLimits.MAX_URLS}件を超えたため${excessUrls.size}件は取り込めませんでした"
        }

    /** 画面表示用の要約文。 */
    fun summary(): String {
        val builder = StringBuilder("抽出${foundCount}件・取り込み${appliedCount}件")
        if (excessCount > 0) builder.append("・超過${excessCount}件（$excessReason）")
        if (rejected.isNotEmpty()) builder.append("・不正な候補${rejected.size}件を除外")
        return builder.toString()
    }
}

/** 秒数入力欄の文字列を数値へ変換した結果。 */
data class SecondsParseResult(
    /** 変換できた個別秒数（[UrlRunnerLimits.INDIVIDUAL_COUNT]件）。失敗時は null。 */
    val individualSeconds: List<Int>?,
    /** 変換できた共通秒数。失敗時は null。 */
    val commonSeconds: Int?,
    /** 欄ごとのエラーメッセージ（表示用）。 */
    val errors: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty() && individualSeconds != null && commonSeconds != null
}

/**
 * URL入力画面と設定画面で共有する編集状態ホルダー。
 *
 * - URLは常に [UrlRunnerLimits.MAX_URLS] 件分のスロットで保持するため、
 *   範囲（001–010 等）を切り替えても編集中の内容は失われない。
 * - 中間の空欄は保持し、確定時（送信・保存）の検証で明示する。末尾の空欄だけを一覧外とする。
 * - Compose に依存しない純粋クラスとして単体テスト可能にしている。
 */
class UrlListEditState(private val onChanged: () -> Unit = {}) {
    var loopMode: LoopMode = LoopMode.STOP
        set(value) { field = value; onChanged() }
    var waitFromForeground: Boolean = true
        set(value) { field = value; onChanged() }
    var startNumberText: String = "1"
        set(value) { field = value; onChanged() }
    fun setIndividualSeconds(index: Int, value: String) {
        individualSecondsText[index] = value
        onChanged()
    }
    private val slots = Array(UrlRunnerLimits.MAX_URLS) { "" }

    /** 範囲表示用の読み取り。範囲外は空文字。 */
    fun slotAt(index: Int): String = slots.getOrElse(index) { "" }

    /** 1件分の編集を即座に保持する（範囲切替で消えない）。 */
    fun setSlot(index: Int, value: String) {
        if (index in slots.indices) { slots[index] = value; onChanged() }
    }

    /** 現在のスロット全体のコピー（テスト・確認用）。 */
    fun slotsSnapshot(): List<String> = slots.toList()

    /** 末尾の空欄を除いた有効な一覧。中間の空欄は検証対象として残す。 */
    fun effectiveUrls(): List<String> {
        val lastNonBlank = slots.indexOfLast { it.isNotBlank() }
        if (lastNonBlank < 0) return emptyList()
        return (0..lastNonBlank).map { slots[it].trim() }
    }

    /** 登録済み（空欄でない）件数。 */
    fun filledCount(): Int = slots.count { it.isNotBlank() }

    // ---- 秒数入力欄の文字列状態 ----

    val individualSecondsText: Array<String> =
        Array(UrlRunnerLimits.INDIVIDUAL_COUNT) { UrlRunnerLimits.DEFAULT_INDIVIDUAL_SECONDS.toString() }

    var commonSecondsText: String = UrlRunnerLimits.DEFAULT_COMMON_SECONDS.toString()
        set(value) { field = value; onChanged() }

    // ---- 読み込み ----

    /** 保存済み設定を編集状態へ読み込む（既存の編集内容は置き換わるため、画面表示開始時にだけ呼ぶ）。 */
    fun loadFrom(config: UrlRunnerConfig) {
        slots.fill("")
        config.urls.take(UrlRunnerLimits.MAX_URLS).forEachIndexed { index, url -> slots[index] = url }
        config.individualSeconds.forEachIndexed { index, seconds ->
            if (index < individualSecondsText.size) individualSecondsText[index] = seconds.toString()
        }
        commonSecondsText = config.commonSeconds.toString()
        loopMode = config.loopMode
        waitFromForeground = config.waitFromForeground
        startNumberText = (config.startIndex + 1).toString()
        onChanged()
    }

    // ---- 一括貼り付け ----

    /**
     * 貼り付け文字列からURLを抽出して取り込む。
     * 上限を超えた分は切り捨てず [PasteReport.excessUrls] に保持して返す。
     */
    fun applyPaste(text: String, mode: PasteMode): PasteReport {
        val extraction = UrlExtractor.extractDetailed(text)
        if (mode == PasteMode.REPLACE) slots.fill("")
        val startIndex = if (mode == PasteMode.REPLACE) {
            0
        } else {
            val lastNonBlank = slots.indexOfLast { it.isNotBlank() }
            lastNonBlank + 1
        }
        var applied = 0
        val excess = mutableListOf<String>()
        extraction.urls.forEachIndexed { offset, url ->
            val target = startIndex + offset
            if (target < slots.size) {
                slots[target] = url
                applied++
            } else {
                excess += url
            }
        }
        onChanged()
        return PasteReport(
            mode = mode,
            foundCount = extraction.urls.size,
            appliedCount = applied,
            excessUrls = excess,
            rejected = extraction.rejected,
        )
    }

    // ---- 秒数と確定 ----

    /** 秒数入力欄を検証する。範囲外・数値でない欄はすべてエラーとして返す（黙って補正しない）。 */
    fun parseSeconds(): SecondsParseResult {
        val errors = mutableListOf<String>()
        val individual = individualSecondsText.mapIndexed { index, text ->
            parseSecondsField(text, "${index + 1}番の秒数", errors)
        }
        val common = parseSecondsField(commonSecondsText, "6番以降の共通秒数", errors)
        return SecondsParseResult(
            individualSeconds = if (errors.isEmpty()) individual.map { it!! } else null,
            commonSeconds = if (errors.isEmpty()) common else null,
            errors = errors,
        )
    }

    private fun parseSecondsField(text: String, label: String, errors: MutableList<String>): Int? {
        val value = text.trim().toIntOrNull()
        if (value == null) {
            errors += "$label は数値で入力してください"
            return null
        }
        if (value !in UrlRunnerLimits.MIN_SECONDS..UrlRunnerLimits.MAX_SECONDS) {
            errors += "$label は${UrlRunnerLimits.MIN_SECONDS}〜${UrlRunnerLimits.MAX_SECONDS}秒で入力してください"
            return null
        }
        return value
    }

    /**
     * 現在の編集内容から一式を組み立てる。
     * 秒数またはURL一覧に問題がある場合は null を返し、問題一覧を [issues] へ返す
     * （呼び出し側は送信・保存を行わず、理由を表示すること）。
     */
    fun buildConfig(
        configVersion: Long = 0L,
        updatedAt: String = "",
        issues: MutableList<String>,
    ): UrlRunnerConfig? {
        val seconds = parseSeconds()
        issues += seconds.errors
        if (!seconds.isValid) return null
        val startNumber = startNumberText.toIntOrNull()
        if (startNumber == null || startNumber !in 1..filledCount().coerceAtLeast(1)) {
            issues += "開始番号は登録済みURLの範囲で指定してください"
            return null
        }
        val config = UrlRunnerConfig(
            loopMode = loopMode, waitFromForeground = waitFromForeground, startIndex = startNumber - 1,
            urls = effectiveUrls(),
            individualSeconds = seconds.individualSeconds!!,
            commonSeconds = seconds.commonSeconds!!,
            configVersion = configVersion,
            updatedAt = updatedAt,
        )
        val validation = config.validate()
        issues += validation.map { it.message }
        return if (validation.isEmpty()) config else null
    }
}
