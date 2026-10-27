package jp.aruno.clicker.data

import android.content.Context

/**
 * URLランナー設定の端末内保存に対する最小インターフェース。
 * 純粋ロジック（[UrlRunnerStore]）を Android の SharedPreferences から切り離し、
 * 単体テストではインメモリ実装に差し替える。
 */
interface UrlRunnerStorage {
    fun read(key: String): String?

    /**
     * 複数キーを1回の編集として書き込む。
     * @return 書き込みが確定したら true（SharedPreferences の commit 結果など）。
     */
    fun write(entries: Map<String, String?>): Boolean
}

/** 読み込み結果。壊れた保存データは「無い」と誤認せず CORRUPT として区別する。 */
data class UrlRunnerLoadResult(
    val status: Status,
    val config: UrlRunnerConfig?,
    val detail: String = "",
) {
    enum class Status { EMPTY, OK, CORRUPT }
}

/** 保存結果。失敗時は既存の正常な保存済み設定が保持される。 */
data class UrlRunnerSaveResult(
    val success: Boolean,
    val message: String,
)

/**
 * URLランナー設定の保存コア（純粋ロジック）。
 *
 * 原子的な差し替え手順:
 * 1. 候補を検証する。不正なら何も書き込まない。
 * 2. 候補を一時キーへ書き、読み戻して候補と一致することを確認する。
 * 3. 本キーを一時キーの内容で1回の書き込みとして差し替え、一時キーを消す。
 * 4. 本キーを読み戻して一致を確認する。
 * いずれの段階で失敗しても、本キーにある正常な保存済み設定は上書きしない
 * （本キーへ触れるのは、手順2で検証済みの内容を手順3で移すときだけ）。
 */
class UrlRunnerStore(private val storage: UrlRunnerStorage) {
    fun load(): UrlRunnerLoadResult {
        val raw = storage.read(KEY_CURRENT) ?: return UrlRunnerLoadResult(
            UrlRunnerLoadResult.Status.EMPTY, null, "保存済み設定はありません",
        )
        val config = UrlRunnerJson.decodeConfig(raw)
            ?: return UrlRunnerLoadResult(
                UrlRunnerLoadResult.Status.CORRUPT, null, "保存済み設定の形式が不正です",
            )
        val issues = config.validate()
        if (issues.isNotEmpty()) {
            return UrlRunnerLoadResult(
                UrlRunnerLoadResult.Status.CORRUPT,
                null,
                "保存済み設定に問題があります: ${issues.first().message}",
            )
        }
        return UrlRunnerLoadResult(UrlRunnerLoadResult.Status.OK, config)
    }

    /** 動画スタート用: 正常に保存された設定だけを返す（壊れたデータでは開始しない）。 */
    fun loadForStart(): UrlRunnerConfig? = load().config

    fun save(candidate: UrlRunnerConfig): UrlRunnerSaveResult {
        val issues = candidate.validate()
        if (issues.isNotEmpty()) {
            return UrlRunnerSaveResult(false, "保存しませんでした: ${issues.first().message}")
        }
        val encoded = UrlRunnerJson.encodeConfig(candidate)
        // 手順2: 一時キーへ書いて読み戻し確認。
        if (!storage.write(mapOf(KEY_PENDING to encoded))) {
            return UrlRunnerSaveResult(false, "保存に失敗しました（一時書き込み）。保存済み設定は変更されていません")
        }
        val staged = storage.read(KEY_PENDING)
        if (staged != encoded || UrlRunnerJson.decodeConfig(staged) != candidate) {
            storage.write(mapOf(KEY_PENDING to null))
            return UrlRunnerSaveResult(false, "保存に失敗しました（検証不一致）。保存済み設定は変更されていません")
        }
        // 手順3: 本キーを差し替えて一時キーを消す（1回の書き込み）。
        if (!storage.write(mapOf(KEY_CURRENT to encoded, KEY_PENDING to null))) {
            return UrlRunnerSaveResult(false, "保存に失敗しました（確定書き込み）。保存済み設定は変更されていません")
        }
        // 手順4: 読み戻し確認。
        val confirmed = storage.read(KEY_CURRENT)
        if (confirmed != encoded || UrlRunnerJson.decodeConfig(confirmed) != candidate) {
            return UrlRunnerSaveResult(false, "保存の確認に失敗しました")
        }
        return UrlRunnerSaveResult(true, "保存しました")
    }

    /**
     * 旧3枠（url1 / url2 / shareUrl）から初期一覧を作る任意の移行手順用。
     * 空欄は飛ばし、順序と重複はそのまま保持する。値が無ければ null。
     * ここでは保存は行わない（呼び出し側が内容を確認してから [save] する）。
     */
    fun buildLegacyImport(url1: String, url2: String, shareUrl: String): UrlRunnerConfig? {
        val urls = listOf(url1, url2, shareUrl).map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isEmpty()) return null
        return UrlRunnerConfig(urls = urls)
    }

    companion object {
        const val KEY_CURRENT = "url_runner_config_json"
        const val KEY_PENDING = "url_runner_config_pending_json"
    }
}

/** AtomicFile keeps the previous disk image if final writing fails. No in-memory failed commit. */
private class AtomicUrlRunnerStorage(context: Context) : UrlRunnerStorage {
    private val directory = java.io.File(context.filesDir, "url-runner").apply { mkdirs() }
    private val legacy = context.getSharedPreferences("aruno_clicker_url_runner", Context.MODE_PRIVATE)
    private fun file(key: String) = android.util.AtomicFile(java.io.File(directory, key))

    override fun read(key: String): String? = synchronized(LOCK) {
        val target = file(key)
        if (target.baseFile.exists() || java.io.File(target.baseFile.path + ".bak").exists()) {
            return@synchronized runCatching { String(target.readFully(), Charsets.UTF_8) }.getOrNull()
        }
        // Keep any existing preference data until it has been successfully migrated.
        if (key == UrlRunnerStore.KEY_CURRENT) legacy.getString(key, null) else null
    }

    override fun write(entries: Map<String, String?>): Boolean = synchronized(LOCK) {
        for ((key, value) in entries) {
            val target = file(key)
            if (value == null) { target.delete(); continue }
            var stream: java.io.FileOutputStream? = null
            try {
                stream = target.startWrite()
                stream.write(value.toByteArray(Charsets.UTF_8))
                target.finishWrite(stream)
            } catch (_: Exception) {
                target.failWrite(stream)
                return@synchronized false
            }
        }
        true
    }

    companion object { private val LOCK = Any() }
}

/**
 * URLランナー設定の端末内保存（Android 側の入口）。
 * プロセス再起動後も [load] で同じ内容が戻る。
 */
class UrlRunnerRepository(context: Context) {
    private val store = UrlRunnerStore(AtomicUrlRunnerStorage(context.applicationContext))

    fun load(): UrlRunnerLoadResult = store.load()

    fun loadForStart(): UrlRunnerConfig? = store.loadForStart()

    fun save(candidate: UrlRunnerConfig): UrlRunnerSaveResult = store.save(candidate)

    fun buildLegacyImport(url1: String, url2: String, shareUrl: String): UrlRunnerConfig? =
        store.buildLegacyImport(url1, url2, shareUrl)
}
