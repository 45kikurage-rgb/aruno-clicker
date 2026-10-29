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
        // This fixture verifies the legacy Activity lifecycle; route navigation
        // is tested separately against actual node selectors and navigator ports.
        jp.aruno.clicker.data.GiftHomeModeStore(app).setMode(jp.aruno.clicker.data.GiftHomeMode.OFF)
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

    private fun saveSevenSlots(blankSixth: Boolean = false) {
        val urls = (1..7).map { if (it == 6 && blankSixth) "" else "https://lite.tiktok.com/t/slot$it/" }
        assertTrue(UrlRunnerRepository(app).save(UrlRunnerConfig(urls = urls, commonSeconds = 1)).success)
        urls.filter { it.isNotBlank() }.forEach { value ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value)).setPackage(AutomationConfig.TARGET_PACKAGES.first())
            shadowOf(app.packageManager).addResolveInfoForIntent(intent, ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = AutomationConfig.TARGET_PACKAGES.first()
                    name = "TargetActivity"
                }
            })
        }
    }

    @Test fun standaloneTestNeedsNoSavedUrlsAndNeverRecordsViewsOrOpensALink() {
        jp.aruno.clicker.data.GiftHomeModeStore(app).setMode(jp.aruno.clicker.data.GiftHomeMode.AUTO)
        app.getSharedPreferences("aruno_clicker_url_runner", Context.MODE_PRIVATE).edit().clear().commit()
        java.io.File(app.filesDir, "url-runner").deleteRecursively()
        val before = UrlRunnerRepository(app).loadForStart()
        assertNull(before)
        val views = jp.aruno.clicker.data.DailyVideoViewStore(app)
        val count = views.count(url)
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(AutomationConfig.TARGET_PACKAGES.first())
        shadowOf(app.packageManager).addResolveInfoForIntent(launch, ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = AutomationConfig.TARGET_PACKAGES.first()
                name = "TargetActivity"
            }
        })
        service.startGiftHomeTest()
        val opened = shadowOf(app).nextStartedActivity
        assertNotNull(opened)
        assertNull(opened.data)
        assertEquals(Intent.ACTION_MAIN, opened.action)
        assertTrue(AutomationRuntime.snapshot().requested)
        assertFalse(AutomationRuntime.snapshot().urlRunnerActive)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertFalse(AutomationRuntime.snapshot().requested)
        assertTrue(AutomationRuntime.snapshot().message.startsWith("テスト失敗"))
        assertEquals(0, openedCount())
        assertEquals(count, views.count(url))
        assertEquals(before, UrlRunnerRepository(app).loadForStart())
    }

    @Test fun standaloneTestOffAndLockedDeviceDoNotLaunchAnything() {
        service.startGiftHomeTest()
        assertFalse(AutomationRuntime.snapshot().requested)
        assertEquals(0, openedCount())
        jp.aruno.clicker.data.GiftHomeModeStore(app).setMode(jp.aruno.clicker.data.GiftHomeMode.AUTO)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        service.startGiftHomeTest()
        assertFalse(AutomationRuntime.snapshot().requested)
        assertTrue(AutomationRuntime.snapshot().message.contains("画面ロック"))
        assertEquals(0, openedCount())
    }

    @Test fun standaloneTestCannotReplaceAnExistingRun() {
        start()
        openedCount()
        val before = AutomationRuntime.snapshot()
        service.startGiftHomeTest()
        assertEquals(before, AutomationRuntime.snapshot())
        assertEquals(0, openedCount())
    }

    @Test fun sixthStartOpensSlotSixWithoutChangingSavedDataset() {
        saveSevenSlots()
        val before = UrlRunnerRepository(app).loadForStart()
        service.startUrlRunnerSequence(true)
        assertEquals("https://lite.tiktok.com/t/slot6/", shadowOf(app).nextStartedActivity.data.toString())
        assertTrue(AutomationRuntime.snapshot().message.contains("6番目"))
        assertEquals(before, UrlRunnerRepository(app).loadForStart())
    }

    @Test fun sixthEmptySlotSkipsToSeventhPosition() {
        saveSevenSlots(blankSixth = true)
        service.startUrlRunnerSequence(true)
        assertEquals("https://lite.tiktok.com/t/slot7/", shadowOf(app).nextStartedActivity.data.toString())
        assertTrue(AutomationRuntime.snapshot().message.contains("7番目"))
    }

    @Test fun missingSixthRangeDoesNotRunEarlierSlots() {
        service.startUrlRunnerSequence(true)
        assertEquals(0, openedCount())
        assertFalse(AutomationRuntime.snapshot().requested)
        assertTrue(AutomationRuntime.snapshot().message.contains("6番目以降"))
    }

    @Test fun sixthOverlayRequestUsesSameRunnerAndScreenOffStillCancels() {
        saveSevenSlots()
        val controller = Robolectric.buildService(AutomationOverlayService::class.java).create()
        val overlay = controller.get()
        overlay.onStartCommand(Intent(AutomationContract.ACTION_START_AUTOMATION)
            .putExtra(AutomationContract.EXTRA_START_FROM_SIXTH, true), 0, 1)
        assertEquals("https://lite.tiktok.com/t/slot6/", shadowOf(app).nextStartedActivity.data.toString())
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertEquals(0, openedCount())
        assertFalse(AutomationRuntime.snapshot().requested)
        controller.destroy()
    }

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
        service.stopAutomation("停止しました")
        val stopped = AutomationRuntime.snapshot()
        assertFalse(stopped.urlRunnerActive)
        assertNull(stopped.urlRunnerSecondsToNext)
    }

    @Test fun briefAccessibilityInterruptionDoesNotStopVideoSequence() {
        start()
        assertEquals(url, shadowOf(app).nextStartedActivity.data.toString())
        service.onInterrupt()
        assertTrue(AutomationRuntime.snapshot().requested)
        assertTrue(AutomationRuntime.snapshot().urlRunnerActive)
        assertEquals(0, openedCount())
        assertTrue(AccessibilityDiagnostics(app).history().any { it.category == "中断通知" })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_500))
        assertEquals("https://lite.tiktok.com/t/BBB/",
            shadowOf(app).nextStartedActivity.data.toString())
    }

    @Test fun realUnbindStopsPlaybackAndCancelsPendingLinks() {
        start()
        openedCount()
        service.onUnbind(null)
        assertFalse(AutomationRuntime.snapshot().requested)
        assertNull(ArunoAccessibilityService.instance)
        val health = AccessibilityDiagnostics(app).snapshot()
        assertTrue(health.lastStop.contains("切断"))
        assertTrue(health.recent.any { it.category == "切断" })
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(0, openedCount())
    }

    @Test fun unbindThenDestroyKeepsOriginalStopReason() {
        start()
        openedCount()
        service.onUnbind(null)
        val before = AccessibilityDiagnostics(app).snapshot().lastStop
        service.onDestroy()
        assertEquals(before, AccessibilityDiagnostics(app).snapshot().lastStop)
        assertEquals(1, AccessibilityDiagnostics(app).history().count { it.category == "切断" })
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

    @Test fun explicitStopAfterInterruptionClearsPersistedRequest() {
        start()
        openedCount()
        service.onInterrupt()
        assertTrue(AutomationRuntime.snapshot().requested)
        service.stopAutomation("停止しました")
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

