package jp.aruno.clicker.overlay

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import jp.aruno.clicker.data.SplashFrequencyStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class SplashControlsTest {
    @Test fun inlineListRemainsUsableAndPersistsSelectionEvenWithSplashOff() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("aruno_clicker_splash_frequency", 0).edit().clear().commit()
        var updates = 0
        val panel = SplashControls(context) { updates++ }
        val row = panel.getChildAt(1) as ViewGroup
        (row.getChildAt(0) as Button).performClick()
        assertFalse(SplashFrequencyStore(context).isEnabled())
        (row.getChildAt(1) as Button).performClick()
        val options = panel.getChildAt(2) as ViewGroup
        assertEquals(View.VISIBLE, options.visibility)
        (options.getChildAt(3) as Button).performClick()
        assertEquals(4, SplashFrequencyStore(context).interval())
        assertEquals(View.GONE, options.visibility)
        assertEquals(2, updates)
    }
}
