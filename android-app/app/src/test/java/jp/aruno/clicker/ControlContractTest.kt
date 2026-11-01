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
    @Test fun giftHomeTestUsesDedicatedCommandWithoutUrlStartFlags() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.testGiftHome(app)
        val intent = shadowOf(app).nextStartedService
        assertEquals(jp.aruno.clicker.automation.AutomationContract.ACTION_TEST_GIFT_HOME, intent.action)
        assertEquals(ControlContract.AUTOMATION_SERVICE, intent.component!!.className)
        assertFalse(intent.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
        assertFalse(intent.getBooleanExtra(ControlContract.EXTRA_SPLASH_BETWEEN_URLS, true))
        assertNull(intent.getStringExtra(ControlContract.EXTRA_START_MODE))
    }
    @Test fun sixthButtonCommandCarriesOneRunSelectionToExistingService() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, true)
        val intent = shadowOf(app).nextStartedService
        assertEquals(ControlContract.ACTION_START, intent.action)
        assertEquals(app.packageName, intent.component!!.packageName)
        assertEquals(ControlContract.AUTOMATION_SERVICE, intent.component!!.className)
        assertFalse(intent.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
    }
    @Test fun explicitSixthSplashChoicesAreDistinctOneRunCommands() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, fromSixth = true, splashBetweenUrls = true)
        val withSplash = shadowOf(app).nextStartedService
        assertFalse(withSplash.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
        assertTrue(withSplash.getBooleanExtra(ControlContract.EXTRA_SPLASH_BETWEEN_URLS, false))
        ControlContract.startUrlRunner(app, fromSixth = true, splashBetweenUrls = false)
        val withoutSplash = shadowOf(app).nextStartedService
        assertFalse(withoutSplash.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
        assertFalse(withoutSplash.getBooleanExtra(ControlContract.EXTRA_SPLASH_BETWEEN_URLS, true))
    }

    @Test fun splashChoiceNoLongerDependsOnObsoleteSixthFlag() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, fromSixth = true, splashBetweenUrls = true)
        shadowOf(app).nextStartedService
        ControlContract.startUrlRunner(app, fromSixth = false, splashBetweenUrls = true)
        val normal = shadowOf(app).nextStartedService
        assertFalse(normal.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
        assertTrue(normal.getBooleanExtra(ControlContract.EXTRA_SPLASH_BETWEEN_URLS, false))
    }

    @Test fun normalStartDoesNotInheritSixthSelection() {
        val app = RuntimeEnvironment.getApplication()
        ControlContract.startUrlRunner(app, true)
        shadowOf(app).nextStartedService
        ControlContract.startUrlRunner(app)
        assertFalse(shadowOf(app).nextStartedService.getBooleanExtra(ControlContract.EXTRA_START_FROM_SIXTH, true))
    }
}

