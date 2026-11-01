package jp.aruno.clicker.data

import org.junit.Assert.*
import org.junit.Test

class UrlRunnerLayoutMigrationTest {
    private class MemoryStorage : UrlRunnerStorage {
        val values = mutableMapOf<String, String>()
        var writes = 0
        var failCurrent = false
        override fun read(key: String): String? = values[key]
        override fun write(entries: Map<String, String?>): Boolean {
            writes++
            if (failCurrent && UrlRunnerStore.KEY_CURRENT in entries) return false
            entries.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }
    }
    private fun url(number: Int) = "https://lite.tiktok.com/t/Video-$number/?Case=Kept"
    private fun legacy(count: Int = 8): String = """
        {"schemaVersion":3,"urls":[${(1..count).joinToString(",") { "\"${url(it)}\"" }}],
         "individualSeconds":[11,12,13,14,15],"commonSeconds":27,
         "configVersion":98,"updatedAt":"2026-11-01T00:00:00Z","loopMode":"LOOP",
         "waitFromForeground":false,"startIndex":0}
    """.trimIndent()

    @Test fun legacySixBecomesOneExactlyOnceAndAllMetadataSurvives() {
        val storage = MemoryStorage().apply { values[UrlRunnerStore.KEY_CURRENT] = legacy() }
        val first = UrlRunnerStore(storage).loadForStart()!!
        assertEquals(listOf(url(6), url(7), url(8)), first.urls)
        assertEquals(emptyList<Int>(), first.individualSeconds)
        assertEquals(0, first.startIndex)
        assertEquals(27, first.secondsFor(0))
        assertEquals(98L, first.configVersion)
        assertEquals("2026-11-01T00:00:00Z", first.updatedAt)
        assertEquals(LoopMode.LOOP, first.loopMode)
        assertFalse(first.waitFromForeground)
        val raw = storage.values[UrlRunnerStore.KEY_CURRENT]!!
        assertTrue(raw.contains("\"layoutVersion\":2"))
        assertTrue(raw.contains("\"schemaVersion\":4"))
        val writes = storage.writes
        repeat(3) { assertEquals(first, UrlRunnerStore(storage).loadForStart()) }
        assertEquals(writes, storage.writes)
    }

    @Test fun migrationRetainsAll195PlayableSlotsFromAnOld200SlotFile() {
        val storage = MemoryStorage().apply { values[UrlRunnerStore.KEY_CURRENT] = legacy(200) }
        val config = UrlRunnerStore(storage).loadForStart()!!
        assertEquals(195, config.urls.size)
        assertEquals(url(6), config.urls.first())
        assertEquals(url(200), config.urls.last())
    }

    @Test fun migrationCommitFailureNeverRunsRetiredUrlsAndCanRetry() {
        val original = legacy()
        val storage = MemoryStorage().apply {
            values[UrlRunnerStore.KEY_CURRENT] = original
            failCurrent = true
        }
        assertNull(UrlRunnerStore(storage).loadForStart())
        assertEquals(original, storage.values[UrlRunnerStore.KEY_CURRENT])
        storage.failCurrent = false
        assertEquals(url(6), UrlRunnerStore(storage).loadForStart()!!.urls.first())
        assertEquals(3, UrlRunnerStore(storage).loadForStart()!!.urls.size)
    }

    @Test fun onlyRetiredUrlsMigrateToAnEmptyNonPlayableListWithoutRepeatedMigration() {
        val storage = MemoryStorage().apply { values[UrlRunnerStore.KEY_CURRENT] = legacy(5) }
        val load = UrlRunnerStore(storage).load()
        assertEquals(UrlRunnerLoadResult.Status.EMPTY, load.status)
        assertTrue(load.config!!.urls.isEmpty())
        val writes = storage.writes
        assertNull(UrlRunnerStore(storage).loadForStart())
        assertEquals(writes, storage.writes)
    }

    @Test fun v2DataIsNeverDroppedAgainAndPreservesSparseDuplicatePositions() {
        val remote = UrlRunnerConfig(
            urls = listOf(url(6), "", url(6), url(9), "", url(11)),
            commonSeconds = 23, configVersion = 99,
        )
        val wire = UrlRunnerJson.encodeConfig(remote).replace(",\"layoutVersion\":2", "")
        assertEquals(remote, UrlRunnerJson.decodeConfig(wire))
        val storage = MemoryStorage()
        assertTrue(UrlRunnerStore(storage).save(UrlRunnerJson.decodeConfig(wire)!!).success)
        repeat(3) { assertEquals(remote, UrlRunnerStore(storage).loadForStart()) }
    }

    @Test fun v1WireResponseAndUnknownLayoutsAreRejected() {
        assertNull(UrlRunnerJson.decodeConfig(legacy()))
        val wire = UrlRunnerJson.encodeConfig(UrlRunnerConfig(urls = listOf(url(6))))
        assertNull(UrlRunnerJson.decodeConfig(wire.replace("\"playbackLayout\":2", "\"playbackLayout\":3")))
        assertNull(UrlRunnerJson.decodeConfig(wire.replace("\"schemaVersion\":4", "\"schemaVersion\":3")))
        assertNull(UrlRunnerJson.decodeStoredConfig(wire.replace("\"layoutVersion\":2", "\"layoutVersion\":8")))
    }

    @Test fun emptyV2ReplacesCachedVideosButCannotStart() {
        val storage = MemoryStorage()
        val store = UrlRunnerStore(storage)
        assertTrue(store.save(UrlRunnerConfig(urls = listOf(url(6)), configVersion = 1)).success)
        assertTrue(store.save(UrlRunnerConfig(configVersion = 2)).success)
        assertNull(store.loadForStart())
        assertTrue(store.load().config!!.urls.isEmpty())
    }

    @Test fun savedNonzeroStartCannotReintroduceSixthStart() {
        val storage = MemoryStorage()
        assertFalse(UrlRunnerStore(storage).save(UrlRunnerConfig(
            urls = (1..6).map(::url), startIndex = 5,
        )).success)
        val first = UrlRunnerStartSelection.snapshot(UrlRunnerConfig(urls = (1..6).map(::url), startIndex = 5), true)
        assertEquals(0, first.startIndex)
        assertTrue(UrlRunnerStore(storage).save(first).success)
    }
}
