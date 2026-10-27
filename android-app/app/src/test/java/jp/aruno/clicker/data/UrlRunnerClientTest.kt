package jp.aruno.clicker.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UrlRunnerClientTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val good = UrlRunnerConfig(urls = listOf("https://lite.tiktok.com/t/Case-Ab/?x=wow!?"), configVersion = 7)
    private class Reply(val status: Int, val body: String, val beforeResponse: () -> Unit = {}) : HttpURLConnection(URL("https://example.com")) {
        var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode(): Int { beforeResponse(); return status }
        override fun getInputStream() = body.byteInputStream()
    }
    private fun prepare() {
        SettingsRepository(app).update { it.copy(remoteServerUrl = "https://server.example/v1/config", remoteAdminMode = false) }
    }

    @Test fun downloadKeepsExactUrlsAndDoesNotSendAdminCredential() = runBlocking {
        prepare()
        val reply = Reply(200, UrlRunnerJson.encodeConfig(good))
        val client = UrlRunnerClient(app) { reply }
        val result = client.download()
        assertEquals(good, result.config)
        assertNull(reply.getRequestProperty("Authorization"))
        assertFalse(reply.instanceFollowRedirects)
        assertTrue(reply.disconnected)
    }

    @Test fun failuresDoNotReplaceCachedGoodData() = runBlocking {
        prepare()
        val repository = UrlRunnerRepository(app)
        assertTrue(repository.save(good).success)
        for ((status, body) in listOf(401 to "{}", 302 to "{}", 200 to "broken", 200 to "x".repeat(524289))) {
            assertFalse(UrlRunnerClient(app) { Reply(status, body) }.download().success)
            assertEquals(good, repository.loadForStart())
        }
    }

    @Test fun serverSwitchDuringDownloadRejectsLateResponse() = runBlocking {
        prepare()
        val reply = Reply(200, UrlRunnerJson.encodeConfig(good)) {
            SettingsRepository(app).update { it.copy(remoteServerUrl = "https://other.example/v1/config") }
        }
        assertFalse(UrlRunnerClient(app) { reply }.download().success)
    }

    @Test fun subDeviceCannotPublish() = runBlocking {
        prepare()
        var contacted = false
        val result = UrlRunnerClient(app) { contacted = true; Reply(200, "{}") }.publish(good)
        assertFalse(result.success)
        assertFalse(contacted)
    }
}
