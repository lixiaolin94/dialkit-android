package io.github.lixiaolin94.dialkit

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.github.lixiaolin94.dialkit.store.DialStore
import io.github.lixiaolin94.dialkit.ui.EmbeddedPanel
import io.github.lixiaolin94.dialkit.ui.EmbeddedPopupHost
import io.github.lixiaolin94.dialkit.ui.SliderView
import io.github.lixiaolin94.dialkit.ui.presetSpec
import io.github.lixiaolin94.dialkit.ui.DialPalette

/** DialPanelView in a real window: embedding, its popup layer, Back, and an unregistered group. */
class PanelViewTest {
    private lateinit var activity: HostActivity

    @Before fun open() {
        activity = launchHost()
    }

    @After fun close() {
        activity.finish()
    }

    private val decor get() = activity.window.decorView as ViewGroup
    private fun popupHost() = decor.getTag(R.id.dialkit_popup_host) as? EmbeddedPopupHost

    private fun <T : View> ViewGroup.find(type: Class<T>): T? {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (type.isInstance(c)) return type.cast(c)
            if (c is ViewGroup) c.find(type)?.let { return it }
        }
        return null
    }

    @Test fun showsTheGroupInThePageAndLeavesTheOverlay() {
        val dials = onMain { DialKit.panel(activity, "In page", id = "view-embed") { TestDials() } }
        val view = onMain { DialPanelView(activity).also { it.group = dials; activity.setContentView(it) } }
        waitUntil("panel built") { view.find(EmbeddedPanel::class.java) != null }
        onMain {
            val panel = DialStore.panels.peek.first { it.group === dials }
            assertTrue(DialStore.isEmbedded(panel))
            assertFalse(panel in DialStore.floating.peek)
            assertNotNull("popup layer on the decor", popupHost())
        }

        // A slider in the page writes the group, as the floating panel would.
        onMain {
            val slider = view.find(SliderView::class.java)!!
            val args = android.os.Bundle().apply { putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 50f) }
            assertTrue(slider.performAccessibilityAction(android.R.id.accessibilityActionSetProgress, args))
        }
        waitUntil("radius 50") { dials.radius == 50f }

        // Leaving the page gives the group back to the overlay and removes the popup layer.
        onMain { activity.setContentView(FrameLayout(activity)) }
        waitUntil("unembedded") { DialStore.panels.peek.first { it.group === dials } in DialStore.floating.peek }
        waitUntil("popup layer removed") { popupHost() == null }
    }

    @Test fun finishingTheScreenWithAPanelAttachedDoesNotCrash() {
        // Regression: the popup layer used to leave the DecorView while it was detaching its children.
        val dials = onMain { DialKit.panel(activity, "Finish", id = "view-finish") { TestDials() } }
        val view = onMain { DialPanelView(activity).also { it.group = dials; activity.setContentView(it) } }
        waitUntil("panel built") { view.find(EmbeddedPanel::class.java) != null }
        onMain { activity.finish() }
        waitUntil("destroyed", timeoutMs = 5000) { activity.isDestroyed }
        instrumentation.waitForIdleSync()
        onMain { assertFalse(DialStore.panels.peek.any { it.group === dials }) }
    }

    @Test fun backClosesAnOpenPopupBeforeTheScreen() {
        val dials = onMain { DialKit.panel(activity, "Back", id = "view-back") { TestDials() } }
        val view = onMain { DialPanelView(activity).also { it.group = dials; activity.setContentView(it) } }
        waitUntil("panel built") { view.find(EmbeddedPanel::class.java) != null }
        onMain {
            val host = popupHost()!!
            val palette = DialPalette.resolve(activity, DialKit.config.theme)
            host.popups.show(this, view, presetSpec(palette), TextView(activity).apply { text = "popup" })
            assertTrue(host.popups.isOpen)
        }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("popup dismissed") { popupHost()?.popups?.isOpen == false }
        onMain { assertFalse("the screen stays", activity.isFinishing) }
    }

    @Test fun anUnregisteredGroupShowsAHintInsteadOfNothing() {
        val view = onMain { DialPanelView(activity).also { it.group = TestDials(); activity.setContentView(it) } }
        waitUntil("hint shown") { view.childCount == 1 && view.getChildAt(0) is TextView }
        onMain {
            assertEquals(activity.getString(R.string.dialkit_embedded_unregistered), (view.getChildAt(0) as TextView).text.toString())
        }
    }
}
