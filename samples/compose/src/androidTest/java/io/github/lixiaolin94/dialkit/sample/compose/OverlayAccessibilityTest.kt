package io.github.lixiaolin94.dialkit.sample.compose

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.test.platform.app.InstrumentationRegistry
import io.github.lixiaolin94.dialkit.DialKit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the overlay the way a screen reader does: find nodes by their spoken label, read their state, perform
 * accessibility actions. Run with `./gradlew :samples:compose:connectedDebugAndroidTest`.
 */
class OverlayAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.uiAutomation
    private lateinit var activity: Activity

    @Before
    fun open() {
        val context = instrumentation.targetContext
        context.getSharedPreferences("io.github.lixiaolin94.dialkit.values", 0).edit().clear().commit()
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.runOnMainSync { DialKit.open() }
        node("Radius", "android.widget.SeekBar") // wait until the panel is up
    }

    @After
    fun close() {
        instrumentation.runOnMainSync { DialKit.close() }
        activity.finish()
    }

    /** The first node whose description is [label] (and class [className]), waiting up to 5 s. */
    private fun node(label: String, className: String? = null): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            val hit = root?.let { find(it, label, className) }
            if (hit != null) return hit
            SystemClock.sleep(100)
        }
        throw AssertionError("no node \"$label\" ($className)")
    }

    private fun find(n: AccessibilityNodeInfo, label: String, className: String?): AccessibilityNodeInfo? {
        if (n.contentDescription?.toString() == label && (className == null || n.className == className)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c, label, className)?.let { return it } }
        return null
    }

    private fun AccessibilityNodeInfo.fresh(): AccessibilityNodeInfo {
        SystemClock.sleep(150)
        refresh()
        return this
    }

    private fun AccessibilityNodeInfo.customAction(label: String): AccessibilityAction =
        actionList.first { it.label?.toString() == label }

    private fun AccessibilityNodeInfo.state(): String? = if (Build.VERSION.SDK_INT >= 30) stateDescription?.toString() else null

    @Test
    fun sliderIsASeekBarThatStepsAndSetsProgress() {
        val radius = node("Radius", "android.widget.SeekBar")
        val range = radius.rangeInfo
        assertEquals(0f, range.min)
        assertEquals(64f, range.max)
        // Values persist for the process, so start from a known one.
        val mid = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 20f) }
        assertTrue(radius.performAction(android.R.id.accessibilityActionSetProgress, mid))
        val start = radius.fresh().rangeInfo.current
        assertEquals(20f, start)

        assertTrue(radius.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
        assertEquals(start + 1f, radius.fresh().rangeInfo.current)
        assertTrue(radius.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))
        assertEquals(start, radius.fresh().rangeInfo.current)

        if (Build.VERSION.SDK_INT >= 24) {
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 40.4f) }
            assertTrue(radius.performAction(android.R.id.accessibilityActionSetProgress, args))
            assertEquals(40f, radius.fresh().rangeInfo.current) // rounded to the step
            if (Build.VERSION.SDK_INT >= 30) assertEquals("40", radius.state())
        }
    }

    @Test
    fun sliderEditActionOpensAClampedInput() {
        val radius = node("Radius", "android.widget.SeekBar")
        assertTrue(radius.performAction(radius.customAction("Edit value").id))
        val input = node("Radius value")
        assertTrue(input.isEditable)
        val text = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "999") }
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text))
        if (Build.VERSION.SDK_INT >= 30) {
            assertTrue(input.performAction(android.R.id.accessibilityActionImeEnter))
        } else {
            input.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
        }
        assertEquals(64f, radius.fresh().rangeInfo.current) // SOURCE: clamped to max
    }

    @Test
    fun toggleIsASwitch() {
        val elevated = node("Elevated", "android.widget.Switch")
        assertTrue(elevated.isCheckable)
        val before = elevated.isChecked
        assertTrue(elevated.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(!before, elevated.fresh().isChecked)
        assertTrue(elevated.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals(before, elevated.fresh().isChecked)
    }

    @Test
    fun segmentedOffersEachOption() {
        val type = node("Type")
        val start = type.state()
        val physics = type.actionList.firstOrNull { it.label?.toString() == "Select Physics" }
            ?: type.customAction("Select Time")
        assertTrue(type.performAction(physics.id))
        SystemClock.sleep(200)
        val after = node("Type") // same control: the Type row survives mode switches
        if (Build.VERSION.SDK_INT >= 30) assertEquals(physics.label.toString().removePrefix("Select "), after.state())
        assertFalse(after.actionList.any { it.label == physics.label }) // the selected option is not offered again
        // Accessibility focus is not lost: the node performing the action is still attached.
        assertTrue(type.refresh())
        start?.let { assertTrue(after.performAction(after.customAction("Select $it").id)) }
    }

    @Test
    fun padMovesAndResets() {
        val pad = node("Origin", "android.view.View")
        assertNotNull(pad.actionList.firstOrNull { it.label?.toString() == "Move right" })
        assertTrue(pad.performAction(pad.customAction("Move right").id))
        assertTrue(pad.performAction(pad.customAction("Move up").id))
        if (Build.VERSION.SDK_INT >= 30) assertEquals("X 0.01, Y 0.01", pad.fresh().state())
        assertTrue(pad.performAction(pad.customAction("Reset").id))
        if (Build.VERSION.SDK_INT >= 30) assertEquals("X 0.00, Y 0.00", pad.fresh().state())
    }

    @Test
    fun foldersExpandAndCollapse() {
        val shadow = node("Shadow")
        val expand = shadow.actionList.any { it.id == AccessibilityNodeInfo.ACTION_EXPAND }
        val action = if (expand) AccessibilityNodeInfo.ACTION_EXPAND else AccessibilityNodeInfo.ACTION_COLLAPSE
        assertTrue(shadow.performAction(action))
        val flipped = if (expand) AccessibilityNodeInfo.ACTION_COLLAPSE else AccessibilityNodeInfo.ACTION_EXPAND
        assertTrue(shadow.fresh().actionList.any { it.id == flipped })
    }

    @Test
    fun imagePickerListsOptionsAndSelects() {
        val row = nodeStarting("Choose cover image")
        assertTrue(row.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        val dune = node("sample_dune")
        assertTrue(dune.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertTrue(node("sample_dune").fresh().isSelected)
        assertEquals("Choose cover image: sample_dune", nodeStarting("Choose cover image").contentDescription.toString())
        assertTrue(node("Remove cover image").performAction(AccessibilityNodeInfo.ACTION_CLICK))
        assertEquals("Choose cover image: No image", nodeStarting("Choose cover image").fresh().contentDescription.toString())
    }

    private fun nodeStarting(prefix: String): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            automation.rootInActiveWindow?.let { root -> findStarting(root, prefix)?.let { return it } }
            SystemClock.sleep(100)
        }
        throw AssertionError("no node starting \"$prefix\"")
    }

    private fun findStarting(n: AccessibilityNodeInfo, prefix: String): AccessibilityNodeInfo? {
        if (n.contentDescription?.toString()?.startsWith(prefix) == true) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findStarting(c, prefix)?.let { return it } }
        return null
    }
}
