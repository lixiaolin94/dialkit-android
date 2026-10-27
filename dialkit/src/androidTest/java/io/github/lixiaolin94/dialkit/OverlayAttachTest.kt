package io.github.lixiaolin94.dialkit

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lixiaolin94.dialkit.overlay.DialKitLayer
import io.github.lixiaolin94.dialkit.overlay.OverlayController
import io.github.lixiaolin94.dialkit.overlay.OverlayMode
import io.github.lixiaolin94.dialkit.ui.OverlayRoot

/** An overlay that is not in the window must neither block the host's touches nor leave it stuck expanded. */
class OverlayAttachTest {
    private var activity: HostActivity? = null

    @After fun restore() {
        onMain { DialKit.hideEntry() }
        activity?.finish()
    }

    private fun showHost(): HostActivity {
        onMain { DialKit.showEntry() }
        val host = launchHost().also { activity = it }
        instrumentation.waitForIdleSync()
        return host
    }

    private fun HostActivity.layer(): DialKitLayer = onMain {
        val decor = window.decorView as ViewGroup
        (0 until decor.childCount).map { decor.getChildAt(it) }.filterIsInstance<DialKitLayer>().single()
    }

    private fun down(target: DialKitLayer): Boolean = onMain {
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        target.dispatchTouchEvent(e).also { e.recycle() }
    }

    @Test fun openingPutsALayerThatLeftTheWindowBackIntoTheDecorView() {
        val host = showHost()
        val layer = host.layer()
        onMain {
            // A parent that is not in any window: the layer has one, yet is not attached.
            (layer.parent as ViewGroup).removeView(layer)
            FrameLayout(host).addView(layer)
            assertFalse(layer.isAttachedToWindow)
            DialKit.open()
            assertSame(host.window.decorView, layer.parent)
            assertTrue(layer.isAttachedToWindow)
        }
        waitUntil("panel opening") { layer.panelOpen }
        assertTrue("expanded panel takes touches", down(layer))
    }

    @Test fun aLayerNotInAWindowTakesNoTouches() {
        val host = showHost()
        val detached = onMain {
            DialKit.open()
            DialKitLayer(host).apply { panelOpen = true }
        }
        assertFalse(down(detached))
    }

    @Test fun anExpandThatCanNeverShowFallsBackToTheEntry() {
        val host = showHost()
        onMain {
            val layer = DialKitLayer(host)
            layer.addView(OverlayRoot(host, layer, DialKit.config) {})
            DialKit.open()
            (layer.getChildAt(0) as OverlayRoot).onMode(OverlayMode.Expanded)
            assertFalse("no touches while waiting for the panel", layer.panelOpen)
        }
        waitUntil("collapsed after the ready timeout", timeoutMs = 3000) { OverlayController.mode == OverlayMode.Entry }
        onMain { assertEquals(OverlayMode.Entry, OverlayController.mode) }
    }
}
