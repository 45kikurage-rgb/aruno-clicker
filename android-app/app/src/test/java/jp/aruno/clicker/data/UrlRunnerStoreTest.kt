package jp.aruno.clicker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRunnerStoreTest {
    /** SharedPreferences の代わりのインメモリ実装。書き込み失敗を再現できる。 */
    private class InMemoryStorage : UrlRunnerStorage {
        val values = mutableMapOf<String, String>()
        var failWrites = false

        override fun read(key: String): String? = values[key]

        override fun write(entries: Map<String, String?>): Boolean {
            if (failWrites) return false
            for ((key, value) in entries) {
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
    }

    private fun config(marker: String, version: Long = 1L) = UrlRunnerConfig(
        urls = listOf("https://lite.tiktok.com/t/$marker/"),
        individualSeconds = listOf(5, 6, 7, 8, 9),
        commonSeconds = 10,
        configVersion = version,
        updatedAt = "2026-10-27T00:00:00.000Z",
    )

    @Test
    fun saveAndLoadRoundTripAcrossStoreInstances() {
        // 別インスタンスからの読み直しが、プロセス再起動後の保持に相当する。
        val storage = InMemoryStorage()
        val saved = config("KEEP")
        assertTrue(UrlRunnerStore(storage).save(saved).success)
        val loaded = UrlRunnerStore(storage).load()
        assertEquals(UrlRunnerLoadResult.Status.OK, loaded.status)
        assertEquals(saved, loaded.config)
        assertEquals(saved, UrlRunnerStore(storage).loadForStart())
    }

    @Test
    fun failedSaveKeepsPreviouslySavedConfig() {
        val storage = InMemoryStorage()
        val store = UrlRunnerStore(storage)
        val good = config("GOOD")
        assertTrue(store.save(good).success)

        storage.failWrites = true
        val result = store.save(config("BAD", version = 2L))
        assertFalse(result.success)
        storage.failWrites = false

        assertEquals(good, store.load().config)
    }

    @Test
    fun invalidCandidateIsNeverSaved() {
        val storage = InMemoryStorage()
        val store = UrlRunnerStore(storage)
        val invalid = UrlRunnerConfig(urls = listOf("https://example.com/not-allowed"))
        assertFalse(store.save(invalid).success)
        assertEquals(UrlRunnerLoadResult.Status.EMPTY, store.load().status)
    }

    @Test
    fun corruptStoredDataIsReportedAndNotUsedForStart() {
        val storage = InMemoryStorage()
        storage.values[UrlRunnerStore.KEY_CURRENT] = "{broken json"
        val store = UrlRunnerStore(storage)
        assertEquals(UrlRunnerLoadResult.Status.CORRUPT, store.load().status)
        assertNull(store.loadForStart())
    }

    @Test
    fun legacyImportBuildsOrderedListSkippingBlanks() {
        val store = UrlRunnerStore(InMemoryStorage())
        val imported = store.buildLegacyImport(
            "https://lite.tiktok.com/t/1/",
            "",
            "https://lite.tiktok.com/t/3/",
        )
        assertEquals(
            listOf("https://lite.tiktok.com/t/1/", "https://lite.tiktok.com/t/3/"),
            imported?.urls,
        )
        assertNull(store.buildLegacyImport("", "", ""))
    }
}
