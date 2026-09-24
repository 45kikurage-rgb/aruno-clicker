package jp.aruno.clicker.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

enum class RemoteSyncAction { FETCH, PUBLISH, REGISTER_ADMIN_KEY }

data class RemoteSyncResult(
    val success: Boolean,
    val skipped: Boolean = false,
    val action: RemoteSyncAction = RemoteSyncAction.FETCH,
    val message: String,
    val completedAtEpochMillis: Long = System.currentTimeMillis(),
)

/**
 * Reads/writes the three startup destinations and their private management labels.
 * The administrator key is sent only as an
 * Authorization header and is never added to messages, exceptions or logs.
 */
class RemoteConfigClient(context: Context) {
    private val repository = SettingsRepository(context.applicationContext)

    suspend fun fetchAndCache(): RemoteSyncResult = withContext(Dispatchers.IO) {
        repository.refresh()
        val settings = repository.settings.value
        if (!settings.remoteSyncEnabled) {
            return@withContext RemoteSyncResult(
                success = true,
                skipped = true,
                message = "サーバー同期はOFFです（端末内URLを使用）",
            )
        }
        val endpoint = settings.remoteServerUrl.validSecureEndpoint()
            ?: return@withContext failure(RemoteSyncAction.FETCH, "同期先URLを確認してください")
        val requestJob = coroutineContext[Job]
        val requestedEndpoint = settings.remoteServerUrl.trim()

        runCatching {
            request(endpoint, "GET") { connection ->
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw RemoteRequestException(httpErrorMessage(responseCode))
                }
                val remote = parseConfig(connection.readSuccessBody())
                requestJob?.ensureActive()
                repository.refresh()
                val latest = repository.settings.value
                if (!latest.remoteSyncEnabled || latest.remoteServerUrl.trim() != requestedEndpoint) {
                    return@request RemoteSyncResult(
                        success = false,
                        skipped = true,
                        message = "同期設定が変更されたため取得結果を破棄しました",
                    )
                }
                val completedAt = System.currentTimeMillis()
                repository.applyRemoteSuccess(
                    name1 = remote.name1,
                    url1 = remote.url1,
                    name2 = remote.name2,
                    url2 = remote.url2,
                    shareName = remote.shareName,
                    shareUrl = remote.shareUrl,
                    completedAt = completedAt,
                    message = "共通URLを取得しました",
                    configVersion = remote.configVersion,
                    updatedAt = remote.updatedAt,
                )
                RemoteSyncResult(
                    success = true,
                    message = "共通URLを取得しました",
                    completedAtEpochMillis = completedAt,
                )
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            failure(RemoteSyncAction.FETCH, throwable.safeMessage("取得できませんでした。端末内URLを使用します"))
        }
    }

    suspend fun publishCurrentUrls(): RemoteSyncResult = withContext(Dispatchers.IO) {
        repository.refresh()
        val settings = repository.settings.value
        if (!settings.remoteAdminMode) {
            return@withContext failure(RemoteSyncAction.PUBLISH, "管理端末モードをONにしてください")
        }
        val endpoint = settings.remoteServerUrl.validSecureEndpoint()
            ?: return@withContext failure(RemoteSyncAction.PUBLISH, "同期先URLを確認してください")
        val adminKey = settings.remoteAdminKey.trim()
        if (!ADMIN_KEY_PATTERN.matches(adminKey)) {
            return@withContext failure(RemoteSyncAction.PUBLISH, "管理キーは英数字8文字で入力してください")
        }
        if (settings.remoteConfigVersion <= 0L) {
            return@withContext failure(RemoteSyncAction.PUBLISH, "先にサーバーから最新設定を取得してください")
        }
        val url1 = settings.startupUrl1.validSharedUrl()
            ?: return@withContext failure(RemoteSyncAction.PUBLISH, "URL 1を確認してください")
        val url2 = settings.startupUrl2.validSharedUrl()
            ?: return@withContext failure(RemoteSyncAction.PUBLISH, "URL 2を確認してください")
        val shareUrl = settings.shareUrl.validSharedUrl()
            ?: return@withContext failure(RemoteSyncAction.PUBLISH, "シェア用URLを確認してください")
        val payload = JSONObject()
            .put("name1", settings.startupName1.validName())
            .put("url1", url1)
            .put("name2", settings.startupName2.validName())
            .put("url2", url2)
            .put("shareName", settings.shareName.validName())
            .put("shareUrl", shareUrl)
            .apply {
                if (settings.remoteConfigVersion > 0L) {
                    put("expectedConfigVersion", settings.remoteConfigVersion)
                }
            }
            .toString()
        val requestJob = coroutineContext[Job]
        val requestedEndpoint = settings.remoteServerUrl.trim()

        runCatching {
            request(endpoint, "PUT") { connection ->
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $adminKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw RemoteRequestException(httpErrorMessage(responseCode))
                }

                val remote = parseConfig(connection.readSuccessBody())
                requestJob?.ensureActive()
                repository.refresh()
                if (repository.settings.value.remoteServerUrl.trim() != requestedEndpoint) {
                    return@request RemoteSyncResult(
                        success = false,
                        skipped = true,
                        action = RemoteSyncAction.PUBLISH,
                        message = "同期先が変更されたため応答を破棄しました",
                    )
                }
                val completedAt = System.currentTimeMillis()
                repository.applyRemoteSuccess(
                    name1 = remote.name1,
                    url1 = remote.url1,
                    name2 = remote.name2,
                    url2 = remote.url2,
                    shareName = remote.shareName,
                    shareUrl = remote.shareUrl,
                    completedAt = completedAt,
                    message = "共通URLを送信しました",
                    configVersion = remote.configVersion,
                    updatedAt = remote.updatedAt,
                )
                RemoteSyncResult(
                    success = true,
                    action = RemoteSyncAction.PUBLISH,
                    message = "共通URLを送信しました",
                    completedAtEpochMillis = completedAt,
                )
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            failure(RemoteSyncAction.PUBLISH, throwable.safeMessage("送信できませんでした"))
        }
    }

    suspend fun registerAdminKey(): RemoteSyncResult = withContext(Dispatchers.IO) {
        repository.refresh()
        val settings = repository.settings.value
        if (!settings.remoteAdminMode) {
            return@withContext failure(RemoteSyncAction.REGISTER_ADMIN_KEY, "管理端末モードをONにしてください")
        }
        val endpoint = settings.remoteServerUrl.validSecureEndpoint()
            ?: return@withContext failure(RemoteSyncAction.REGISTER_ADMIN_KEY, "同期先URLを確認してください")
        val adminKey = settings.remoteAdminKey.trim()
        if (!ADMIN_KEY_PATTERN.matches(adminKey)) {
            return@withContext failure(RemoteSyncAction.REGISTER_ADMIN_KEY, "管理キーは英数字8文字で入力してください")
        }
        val registrationEndpoint = URL(endpoint, "/v1/admin-key")
        val payload = JSONObject().put("newKey", adminKey).toString()

        runCatching {
            request(registrationEndpoint, "PUT") { connection ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
                val responseCode = connection.responseCode
                if (responseCode == 409) {
                    throw RemoteRequestException("管理キーは登録済みです")
                }
                if (responseCode !in 200..299) {
                    throw RemoteRequestException(httpErrorMessage(responseCode))
                }
                RemoteSyncResult(
                    success = true,
                    action = RemoteSyncAction.REGISTER_ADMIN_KEY,
                    message = "管理キーを登録しました",
                )
            }
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            failure(
                RemoteSyncAction.REGISTER_ADMIN_KEY,
                throwable.safeMessage("管理キーを登録できませんでした"),
            )
        }
    }

    private fun failure(action: RemoteSyncAction, message: String): RemoteSyncResult {
        val completedAt = System.currentTimeMillis()
        repository.applyRemoteFailure(message)
        return RemoteSyncResult(
            success = false,
            action = action,
            message = message,
            completedAtEpochMillis = completedAt,
        )
    }

    private inline fun <T> request(
        endpoint: URL,
        method: String,
        block: (HttpURLConnection) -> T,
    ): T {
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
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
                    throw RemoteRequestException("サーバー応答が大きすぎます")
                }
            }
            result.toString()
        }

    private fun parseConfig(body: String): SharedRemoteConfig {
        val root = runCatching { JSONObject(body) }
            .getOrElse { throw RemoteRequestException("サーバー応答の形式が不正です") }
        val source = root.optJSONObject("config") ?: root
        val url1 = (source.optString("url1").ifBlank { source.optString("startupUrl1") }).validSharedUrl()
            ?: throw RemoteRequestException("サーバーのURL 1が不正です")
        val url2 = (source.optString("url2").ifBlank { source.optString("startupUrl2") }).validSharedUrl()
            ?: throw RemoteRequestException("サーバーのURL 2が不正です")
        val shareUrl = source.optString("shareUrl").ifBlank { url1 }.validSharedUrl()
            ?: throw RemoteRequestException("サーバーのシェア用URLが不正です")
        val version = source.optLong("configVersion", 0L)
        if (version <= 0L) throw RemoteRequestException("サーバーの設定版が不正です")
        return SharedRemoteConfig(
            name1 = source.optString("name1").validName(),
            url1 = url1,
            name2 = source.optString("name2").validName(),
            url2 = url2,
            shareName = source.optString("shareName").validName(),
            shareUrl = shareUrl,
            configVersion = version,
            updatedAt = source.optString("updatedAt"),
        )
    }

    private fun String.validSecureEndpoint(): URL? = runCatching {
        val value = trim()
        if (value.length > MAX_ENDPOINT_CHARS) return@runCatching null
        URL(value).takeIf {
            it.protocol.equals("https", ignoreCase = true) &&
                it.host.isNotBlank() &&
                it.userInfo == null
        }
    }.getOrNull()

    private fun String.validSharedUrl(): String? {
        val value = trim()
        if (value.length > MAX_SHARED_URL_CHARS) return null
        val parsed = runCatching { URL(value) }.getOrNull() ?: return null
        return value.takeIf {
            parsed.protocol.equals("https", ignoreCase = true) &&
                parsed.host.lowercase() in ALLOWED_TIKTOK_HOSTS &&
                parsed.userInfo == null
        }
    }

    private fun String.validName(): String = trim().take(MAX_NAME_CHARS)

    private fun httpErrorMessage(code: Int): String = when (code) {
        401, 403 -> "認証できませんでした"
        404 -> "同期先が見つかりません"
        408, 504 -> "サーバーがタイムアウトしました"
        409 -> "設定が更新されています。取得してから再送してください"
        in 500..599 -> "サーバーでエラーが発生しました"
        else -> "通信エラー（HTTP $code）"
    }

    private fun Throwable.safeMessage(fallback: String): String =
        (this as? RemoteRequestException)?.message ?: fallback

    private class RemoteRequestException(message: String) : Exception(message)

    private data class SharedRemoteConfig(
        val name1: String,
        val url1: String,
        val name2: String,
        val url2: String,
        val shareName: String,
        val shareUrl: String,
        val configVersion: Long,
        val updatedAt: String,
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 3_000
        private const val MAX_RESPONSE_CHARS = 64 * 1024
        private const val MAX_ENDPOINT_CHARS = 2_048
        private const val MAX_SHARED_URL_CHARS = 2_048
        private const val MAX_NAME_CHARS = 80
        private val ADMIN_KEY_PATTERN = Regex("^[A-Za-z0-9]{8}$")
        private val ALLOWED_TIKTOK_HOSTS = setOf(
            "tiktok.com",
            "www.tiktok.com",
            "lite.tiktok.com",
            "m.tiktok.com",
            "vm.tiktok.com",
            "vt.tiktok.com",
        )
    }
}
