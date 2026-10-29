package jp.aruno.clicker.automation

import android.content.ComponentName
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class AccessibilityDiagnosticsTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val diagnostics get() = AccessibilityDiagnostics(app)

    @Before fun reset() {
        app.getSharedPreferences("aruno_accessibility_diagnostics", 0).edit().clear().commit()
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
    }

    @Test fun exactSystemComponentRequiredNotSubstringOrAnotherFlavor() {
        val expected = ComponentName(app.packageName, ArunoAccessibilityService::class.java.name)
        val unrelated = ComponentName("com.example.other", "com.example.other.ArunoAccessibilityService")
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            unrelated.flattenToString())
        assertFalse(diagnostics.snapshot().enabledInSettings)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            unrelated.flattenToString() + ":" + expected.flattenToShortString())
        assertTrue(diagnostics.snapshot().enabledInSettings)
        Settings.Secure.putString(app.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        assertFalse(diagnostics.snapshot().enabledInSettings)
    }

    @Test fun connectsAndDisconnectsHaveBoundedAppLocalHistory() {
        repeat(20) { diagnostics.connected() }
        assertEquals(15, diagnostics.history().size)
        diagnostics.disconnected()
        assertEquals("切断", diagnostics.history().first().category)
        assertEquals("ユーザー補助サービス切断", diagnostics.history().first().detail)
    }

    @Test fun interruptedEventIsNotAStopAndDoesNotCreateLastStop() {
        diagnostics.interrupted(true)
        diagnostics.interrupted(true)
        assertEquals("", diagnostics.snapshot().lastStop)
        assertEquals(1, diagnostics.history().count { it.category == "中断通知" })
        assertTrue(diagnostics.history().first().detail.contains("継続"))
    }

    @Test fun stopReasonsArePersistedAndUrlsRemovedFromDiagnostics() {
        diagnostics.stopped("URL https://lite.tiktok.com/t/SECRET1/ failed")
        val snapshot = AccessibilityDiagnostics(app).snapshot()
        assertTrue(snapshot.lastStop.contains("[URL]"))
        assertFalse(snapshot.lastStop.contains("SECRET1"))
        assertEquals("停止", snapshot.recent.first().category)
        assertTrue(snapshot.recent.first().at > 0)
    }
}
