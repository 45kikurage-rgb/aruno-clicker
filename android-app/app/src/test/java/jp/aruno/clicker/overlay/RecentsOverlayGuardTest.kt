package jp.aruno.clicker.overlay

import android.app.Activity
import android.view.View
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class RecentsOverlayGuardTest {
    @Test fun registeredWindowStopsInterceptingThenRestoresItsExactState() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get(); val view=activity.window.decorView; val params=activity.window.attributes
        val flags=params.flags; val visibility=view.visibility; view.alpha=.7f
        PopupOverlayAvoidance.register(view,params)
        try {
            assertTrue(view.isAttachedToWindow)
            val restore=PopupOverlayAvoidance.suspendTouches()
            assertNotNull(restore)
            assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
            assertEquals(View.INVISIBLE,view.visibility); assertEquals(0f,view.alpha,0.001f)
            restore!!(); assertEquals(flags,params.flags);assertEquals(visibility,view.visibility);assertEquals(.7f,view.alpha,0.001f)
            restore();assertEquals(flags,params.flags);assertEquals(.7f,view.alpha,0.001f)
        } finally { PopupOverlayAvoidance.unregister(view);controller.pause().stop().destroy() }
    }
    @Test fun restorationRemainsSafeWhenTheOverlayIsRemovedBeforeCallback() {
        val controller=Robolectric.buildActivity(Activity::class.java).setup()
        val activity=controller.get();val view=activity.window.decorView;val params=activity.window.attributes
        val flags=params.flags
        PopupOverlayAvoidance.register(view,params)
        val restore=PopupOverlayAvoidance.suspendTouches()
        PopupOverlayAvoidance.unregister(view);controller.pause().stop().destroy()
        assertNotNull(restore);restore!!();assertEquals(flags,params.flags)
    }
}
