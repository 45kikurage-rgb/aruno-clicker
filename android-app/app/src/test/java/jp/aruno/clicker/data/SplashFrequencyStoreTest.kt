package jp.aruno.clicker.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SplashFrequencyStoreTest {
    @Test fun initialPreferenceIsEnabledWithOneSplashPerVideo() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("aruno_clicker_splash_frequency",0).edit().clear().commit()
        val store = SplashFrequencyStore(app)
        assertTrue(store.isEnabled())
        assertEquals(1,store.interval())
        assertEquals(1,store.selectedFrequency(true))
        assertEquals(0,store.selectedFrequency(false))
    }

    @Test fun fiveFrequenciesPersistLocallyAcrossStoreInstancesWithoutRemoteChanges() {
        val app = RuntimeEnvironment.getApplication()
        val store = SplashFrequencyStore(app)
        for (frequency in 1..5) {
            assertTrue(store.setInterval(frequency))
            assertEquals(frequency,SplashFrequencyStore(app).interval())
            assertEquals(frequency,store.selectedFrequency(true))
        }
        assertFalse(store.setInterval(0))
        assertFalse(store.setInterval(6))
        assertEquals(5,store.interval())
    }

    @Test fun splashOnOffOnlyAffectsOptionalCadenceNotSavedInterval() {
        val app = RuntimeEnvironment.getApplication()
        val store = SplashFrequencyStore(app)
        assertTrue(store.setInterval(3))
        assertTrue(store.setEnabled(false))
        assertFalse(SplashFrequencyStore(app).isEnabled())
        assertEquals(0,store.selectedFrequency(store.isEnabled()))
        assertTrue(store.setEnabled(true))
        assertEquals(3,store.selectedFrequency(store.isEnabled()))
    }
}
