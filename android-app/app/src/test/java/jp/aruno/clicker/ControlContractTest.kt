package jp.aruno.clicker

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class ControlContractTest {
    @Test fun sixthButtonCommandCarriesOneRunSelectionToExistingService() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, true)
        val intent = shadowOf(app).nextStartedService
        assertEquals(ControlContract.ACTION_START, intent.action)
        assertEquals(app.packageName, intent.component!!.packageName)
        assertEquals(ControlContract.AUTOMATION_SERVICE, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, false))
    }
    @Test fun normalStartDoesNotInheritSixthSelection() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, true)
        shadowOf(app).nextStartedService
        ControlContract.startUrlRunner(app)
        assertFalse(shadowOf(app).nextStartedService.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
    }
}
