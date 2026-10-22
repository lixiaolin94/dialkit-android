package io.github.lixiaolin94.dialkit

import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import io.github.lixiaolin94.dialkit.overlay.DialKitLayer
import io.github.lixiaolin94.dialkit.overlay.OverlayController
import io.github.lixiaolin94.dialkit.overlay.OverlayMode

/** floatingPanel = false: nothing goes into the window, and requests to show the overlay are ignored. */
class FloatingPanelOffTest {
    private var activity: HostActivity? = null

    @After fun restore() {
        onMain { DialKit.config.floatingPanel = true }
        activity?.finish()
    }

    @Test fun noLayerAndNoWayToShowIt() {
        onMain {
            DialKit.hideEntry()
            DialKit.config.floatingPanel = false
        }
        val host = launchHost().also { activity = it }
        instrumentation.waitForIdleSync()
        onMain {
            val decor = host.window.decorView as ViewGroup
            assertFalse("no overlay layer", (0 until decor.childCount).any { decor.getChildAt(it) is DialKitLayer })
            DialKit.showEntry()
            DialKit.open()
            DialKit.toggleEntry()
            assertEquals(OverlayMode.Hidden, OverlayController.mode)
        }
    }
}
