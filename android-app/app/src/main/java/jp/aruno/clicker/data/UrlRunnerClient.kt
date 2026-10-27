package jp.aruno.clicker.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

enum class UrlRunnerSyncAction { DOWNLOAD, PUBLISH }

data class UrlRunnerSyncResult(
    val success: Boolean,
    val action: UrlRunnerSyncAction,
    val message: String,
    /** 成功時にサーバーから確定した一式。失敗時は null（呼び出し側は保存済み設定を保持すること）。 */
    val config: UrlRunnerConfig? = null,
)

/**
 * URLランナー一式の送受信（新エンドポイント /v1/url-runner、schemaVersion 3）。
 *
 * 既存 [RemoteConfigClient] の流儀を踏襲する:
 * - エンドポイントは https のみ・userinfo 不可で検証する。
 * - 管理キーは Authorization ヘッダでのみ送り、メッセージや例外に含めない。
 * - リダイレクトは追従しない。応答サイズには上限を設ける。
 * - 送信は管理端末モード（remoteAdminMode）＋暗号化保存された管理キーが必須。
 *   取得（ダウンロード）は管理キーを必要としない＝サブ端末へ書込資格情報を配らない。
 *
 * このクラスは保存を行わない。取得した一式は呼び出し側が
 * [UrlRunnerRepository.save] で検証してから原子的に差し替える。
 */
class UrlRunnerClient(
    context: Context,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    private val settingsRepository = SettingsRepository(context.applicationContext)

    /** 明示的な「データダウンロード」操作でのみ呼ぶ。起動時・開始直前の自動取得は行わない。 */
    suspend fun download(): UrlRunnerSyncResult = withContext(Dispatchers.IO) {
        settingsRepository.refresh()
        val settings = settingsRepository.settings.value
        val endpoint = UrlRunnerEndpoint.derive(settings.remoteServerUrl)
            ?: return@withContext failure(
                UrlRunnerSyncAction.DOWNLOAD, "同期先URLを確認してください",
            )
        runCatching {
            request(URL(endpoint), "GET") { connection ->
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw UrlRunnerRequestException(httpErrorMessage(responseCode))
                }
                val remote = UrlRunnerJson.decodeConfig(connection.readSuccessBody())
                    ?: throw UrlRunnerRequestException("サーバー応答の形式が不正です")
                if (remote.urls.isEmpty() && remote.configVersion <= 0L) {
                    throw UrlRunnerRequestException("サーバーにURL設定がまだ登録されていません")
                }
                val issues = remote.validate()
                if (issues.isNotEmpty()) {
                    throw UrlRunnerRequestException("サーバーの設定に問題があります: ${issues.first().message}")
                }
                settingsRepository.refresh()
                val latest = settingsRepository.settings.value
                if (latest.remoteServerUrl != settings.remoteServerUrl ||
                    latest.remoteAdminMode != settings.remoteAdminMode ||
                    latest.remoteAdminKey != settings.remoteAdminKey) {
                    throw UrlRunnerRequestException("通信中に接続設定が変わったため反映しませんでした。もう一度操作してください")
                }
                if (remote.validate().isNotEmpty()) throw UrlRunnerRequestException("サーバーの設定が不正です")
                UrlRunnerSyncResult(
                    success = true,
                    action = UrlRunnerSyncAction.DOWNLOAD,
                    message = "データを取得しました（${remote.urls.size}件）",
                    config = remote,
                )
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            failure(
                UrlRunnerSyncAction.DOWNLOAD,
                (throwable as? UrlRunnerRequestException)?.message
                    ?: "取得できませんでした。保存済み設定はそのまま使用します",
            )
        }
    }

    /**
     * メイン端末の「データ送信」。送信前にローカルで全件検証し、
     * 許可外ホスト等があれば一覧の位置を明示して送信自体を止める（黙って捨てない）。
     */
    suspend fun publish(config: UrlRunnerConfig): UrlRunnerSyncResult = withContext(Dispatchers.IO) {
        settingsRepository.refresh()
        val settings = settingsRepository.settings.value
        if (!settings.remoteAdminMode) {
            return@withContext failure(
                UrlRunnerSyncAction.PUBLISH, "管理端末モードをONにしてください",
            )
        }
        val adminKey = settings.remoteAdminKey.trim()
        if (!ADMIN_KEY_PATTERN.matches(adminKey)) {
            return@withContext failure(
                UrlRunnerSyncAction.PUBLISH, "管理キーは英数字8文字で入力してください",
            )
        }
        val issues = config.validate()
        if (issues.isNotEmpty()) {
            return@withContext failure(
                UrlRunnerSyncAction.PUBLISH,
                "送信しませんでした: " + issues.joinToString(" / ") { it.message },
            )
        }
        val endpoint = UrlRunnerEndpoint.derive(settings.remoteServerUrl)
            ?: return@withContext failure(
                UrlRunnerSyncAction.PUBLISH, "同期先URLを確認してください",
            )
        val payload = UrlRunnerJson.encodeRequest(
            config,
            expectedConfigVersion = config.configVersion,
        )
        runCatching {
            request(URL(endpoint), "PUT") { connection ->
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $adminKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw UrlRunnerRequestException(httpErrorMessage(responseCode))
                }
                val remote = UrlRunnerJson.decodeConfig(connection.readSuccessBody())
                    ?: throw UrlRunnerRequestException("サーバー応答の形式が不正です")
                settingsRepository.refresh()
                val latest = settingsRepository.settings.value
                if (latest.remoteServerUrl != settings.remoteServerUrl ||
                    latest.remoteAdminMode != settings.remoteAdminMode ||
                    latest.remoteAdminKey != settings.remoteAdminKey) {
                    throw UrlRunnerRequestException("通信中に接続設定が変わったため反映しませんでした。もう一度操作してください")
                }
                if (remote.validate().isNotEmpty()) throw UrlRunnerRequestException("サーバーの設定が不正です")
                UrlRunnerSyncResult(
                    success = true,
                    action = UrlRunnerSyncAction.PUBLISH,
                    message = "データを送信しました（${remote.urls.size}件）",
                    config = remote,
                )
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            failure(
                UrlRunnerSyncAction.PUBLISH,
                (throwable as? UrlRunnerRequestException)?.message ?: "送信できませんでした",
            )
        }
    }

    private fun failure(action: UrlRunnerSyncAction, message: String) = UrlRunnerSyncResult(
        success = false,
        action = action,
        message = message,
    )

    private fun <T> request(
        endpoint: URL,
        method: String,
        block: (HttpURLConnection) -> T,
    ): T {
        val connection = connectionFactory(endpoint).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            // 一式は最大で約250KBになり得るため、読込だけ既存（3秒）より長く取る。
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readSuccessBody(): String =
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (true) {
                val read = reader.read(buffer)
                if (read < 0) break
                result.append(buffer, 0, read)
                if (result.length > MAX_RESPONSE_CHARS) {
                    throw UrlRunnerRequestException("サーバー応答が大きすぎます")
                }
            }
            result.toString()
        }

    private fun httpErrorMessage(code: Int): String = when (code) {
        400 -> "サーバーが設定内容を拒否しました"
        401, 403 -> "認証できませんでした"
        404 -> "同期先が見つかりません（Worker の新エンドポイント未デプロイの可能性があります）"
        408, 504 -> "サーバーがタイムアウトしました"
        409 -> "設定が更新されています。ダウンロードしてから再送してください"
        in 500..599 -> "サーバーでエラーが発生しました"
        else -> "通信エラー（HTTP $code）"
    }

    private class UrlRunnerRequestException(message: String) : Exception(message)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 10_000

        /** 120件×2048文字の最悪ケース（約246KB）＋JSON構造分の余裕。 */
        private const val MAX_RESPONSE_CHARS = 512 * 1024
        private val ADMIN_KEY_PATTERN = Regex("^[A-Za-z0-9]{8}$")
    }
}
