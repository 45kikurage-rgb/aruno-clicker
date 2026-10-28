package jp.aruno.clicker.automation

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import jp.aruno.clicker.data.UrlRunnerConfig
import jp.aruno.clicker.data.UrlRunnerRepository
import jp.aruno.clicker.overlay.AutomationOverlayService
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class UrlRunnerLifecycleTest {
    private lateinit var service: ArunoAccessibilityService
    private val app get() = RuntimeEnvironment.getApplication()
    private val url = "https://lite.tiktok.com/t/AAA/"

    @Before fun setup() {
        AutomationRuntime.stop(app)
        shadowOf(app.getSystemService(PowerManager::class.java)).setIsInteractive(true)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        assertTrue(UrlRunnerRepository(app).save(UrlRunnerConfig(
            urls = listOf(url, "https://lite.tiktok.com/t/BBB/"),
            individualSeconds = List(5) { 1 },
        )).success)
        for (value in listOf(url, "https://lite.tiktok.com/t/BBB/")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value)).setPackage(AutomationConfig.TARGET_PACKAGES.first())
            shadowOf(app.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = AutomationConfig.TARGET_PACKAGES.first()
                    name = "TargetActivity"
                }
            })
        }
        service = Robolectric.buildService(ArunoAccessibilityService::class.java).create().get()
        connect()
    }

    private fun connect() {
        ArunoAccessibilityService::class.java.getDeclaredMethod("onServiceConnected").apply {
            isAccessible = true
        }.invoke(service)
    }

    private fun start() {
        service.onAccessibilityEvent(AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = AutomationConfig.TARGET_PACKAGES.first()
        })
        service.startUrlRunnerSequence()
    }

    private fun openedCount(): Int {
        var count = 0
        while (shadowOf(app).nextStartedActivity != null) count++
        return count
    }

    @After fun teardown() { service.onDestroy() }

    @Test fun duplicateBridgeStartDoesNotReopenFirstUrl() {
        start()
        assertEquals(1, openedCount())
        service.startUrlRunnerSequence()
        assertEquals(0, openedCount())
        assertTrue(AutomationRuntime.snapshot().requested)
    }


    @Test fun eachSuccessiveUrlRecreatesTikTokActivityTaskWithoutDeletingAppData() {
        start()
        val first = shadowOf(app).nextStartedActivity
        assertNotNull(first)
        assertEquals(url, first!!.data.toString())
        assertEquals(0, first.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
        assertTrue(first.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        // First clip: 600 ms stable target foreground + one-second hold.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))
        val second = shadowOf(app).nextStartedActivity
        assertNotNull("Second URL must be launched", second)
        assertEquals("https://lite.tiktok.com/t/BBB/", second!!.data.toString())
        assertTrue("Following URLs must get a new Activity task",
            second.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
        assertTrue(second.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun urlRunnerCountdownTracksNextVideoAndResetsWhenStopped() {
        start()
        shadowOf(app).nextStartedActivity
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        val during = AutomationRuntime.snapshot()
        assertTrue(during.urlRunnerActive)
        assertTrue(during.urlRunnerHasNext)
        assertNotNull(during.urlRunnerSecondsToNext)
        assertTrue(during.urlRunnerSecondsToNext!! in 0..1)
        service.onInterrupt()
        val stopped = AutomationRuntime.snapshot()
        assertFalse(stopped.urlRunnerActive)
        assertNull(stopped.urlRunnerSecondsToNext)
    }

    @Test fun screenOffCancelsDelayedNextUrl() {
        start()
        assertEquals(1, openedCount())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertFalse(AutomationRuntime.snapshot().requested)
        assertEquals(0, openedCount())
    }

    @Test fun interruptCancelsAndClearsPersistedRequest() {
        start()
        openedCount()
        service.onInterrupt()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(0, openedCount())
        assertNull(AutomationRuntime.restoreRequested(app))
    }

    @Test fun reconnectDoesNotConsumeOldRepeatRequest() {
        AutomationRuntime.requestStart(app, 0, AutomationContract.START_MODE_REPEAT)
        service.onDestroy()
        service = Robolectric.buildService(ArunoAccessibilityService::class.java).create().get()
        connect()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertEquals(0, openedCount())
        assertNull(AutomationRuntime.restoreRequested(app))
    }

    @Test fun lockedDeviceNeverLaunchesUrl() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        start()
        assertEquals(0, openedCount())
        assertFalse(AutomationRuntime.snapshot().requested)
    }

    @Test fun overlayStartUsesSameRunnerAndStopInvalidatesCallbacks() {
        val controller = Robolectric.buildService(AutomationOverlayService::class.java).create()
        val overlay = controller.get()
        overlay.onStartCommand(Intent(AutomationContract.ACTION_START_AUTOMATION), 0, 1)
        assertEquals(1, openedCount())
        overlay.onStartCommand(Intent(AutomationContract.ACTION_START_AUTOMATION), 0, 2)
        assertEquals(0, openedCount())
        overlay.onStartCommand(Intent(AutomationContract.ACTION_STOP_AUTOMATION), 0, 3)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertFalse(AutomationRuntime.snapshot().requested)
        assertEquals(0, openedCount())
        controller.destroy()
    }
}
