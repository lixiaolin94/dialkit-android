package io.github.lixiaolin94.dialkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lixiaolin94.dialkit.model.DialValue

/**
 * Values that reach a panel from outside (persistence, versions, Import, adb, setValues) must never put a control
 * into a state the platform throws on, and values saved under an older declaration must fit the current one.
 * Scenarios follow the regression suites of the dialkit-ios / dialkit-macos ports.
 */
class RobustnessTest {
    private fun card() = DialGroupTest.Card().also { it.assignPaths("") }

    @Test fun nonFiniteSliderValuesAreIgnored() {
        val c = card()
        c.setValue("radius", DialValue.Num(Float.NaN))
        assertEquals(24f, c.radius)
        c.setValue("radius", DialValue.Num(Float.POSITIVE_INFINITY))
        assertEquals(24f, c.radius)
        c.setValues("radius" to Double.NaN)
        assertEquals(24f, c.radius)
    }

    class Wide : DialGroup() {
        val size by slider(48f, 0f..64f)
        val fine by slider(0.37f, 0f..1f, step = 0.01f)
        val layout by select("grid", "list", "masonry", default = "masonry")
    }

    class Narrow : DialGroup() {
        val size by slider(16f, 0f..32f)
        val fine by slider(0.5f, 0f..1f, step = 0.25f)
        val layout by select("grid", "list")
    }

    @Test fun valuesSavedUnderAnOlderDeclarationFitTheCurrentOne() {
        val saved = Wide().also { it.assignPaths("") }.persistedValues()
        val now = Narrow().also { it.assignPaths("") }
        now.restore(saved)
        assertEquals(32f, now.size) // clamped into the narrower range
        assertEquals(0.25f, now.fine) // snapped to the coarser step
        assertEquals("grid", now.layout) // a removed option keeps the current, valid value
    }

    @Test fun invalidCurvesAreRejectedAndEasingXIsClamped() {
        val c = card()
        c.setValue("fade", DialValue.Easing(0.3f, listOf(1.5f, 0f, -0.2f, 1f)))
        assertEquals(listOf(1f, 0f, 0f, 1f), c.fade.ease)

        val before = c.fade.ease
        c.setValue("fade", DialValue.Easing(-1f, listOf(0f, 0f, 1f, 1f)))
        c.setValue("fade", DialValue.Easing(0.3f, listOf(0f, Float.NaN, 1f, 1f)))
        c.setValue("fade", DialValue.Easing(0.3f, listOf(0f, 0f, 1f)))
        assertEquals(before, c.fade.ease)

        c.setValue("enter", DialValue.Spring(stiffness = 0f, damping = 10f, mass = 1f))
        c.setValue("enter", DialValue.Spring(stiffness = 200f, damping = -1f, mass = 1f))
        c.setValue("enter", DialValue.Spring(stiffness = 200f, damping = 10f, mass = 0f))
        c.setValue("enter", DialValue.Spring(visualDuration = 0f, bounce = 0.2f))
        c.setValue("enter", DialValue.Spring(visualDuration = 0.3f, bounce = Float.NaN))
        assertEquals(0.3f, c.enter.visualDuration)
        assertEquals(0.2f, c.enter.bounce)

        // A valid physics spring is still accepted (and switches the mode).
        c.setValue("enter", DialValue.Spring(stiffness = 300f, damping = 20f, mass = 1f))
        assertTrue(c.enter.isPhysicsSpring)
    }

    @Test fun zeroDurationEasingIsValidAndSwitchingAwayGivesAValidSpring() {
        val c = card()
        c.setValue("fade", DialValue.Easing(0f, listOf(0f, 0f, 1f, 1f)))
        assertEquals(0L, c.fade.durationMillis)
        val node = c.nodes.filterIsInstance<TransitionNode>().first { it.key == "fade" }
        node.switchMode(CurveMode.Simple)
        assertTrue((c.fade.stiffness ?: 0f) > 0f)
        assertTrue((c.fade.dampingRatio ?: -1f) >= 0f)
    }

    @Test fun aHalfBrokenImportAppliesTheRestAndReportsTheBadParts() {
        val json = extractImportJson(
            """{ "radius": 40, "fade": { "type": "easing", "duration": -1, "ease": [0, 0, 1, 1] },
               "enter": { "type": "spring", "stiffness": 0, "damping": 10, "mass": 1 }, "columns": 5 }""",
        )!!
        val c = card()
        val result = c.resolveImport(json)
        assertEquals(setOf("radius", "columns"), result.values.keys)
        assertTrue(result.ignored.containsAll(listOf("fade", "enter")))
        c.restore(result.values)
        assertEquals(40f, c.radius)
        assertEquals(5, c.columns)
    }

    @Test fun declarationsRejectValuesThatCannotWork() {
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by slider(Float.NaN) } }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by slider(1f, 5f..5f) } }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by slider(1f, 0f..5f, step = 0f) } }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by slider(1, 0..5, step = 0) } }
        assertThrows(IllegalArgumentException::class.java) {
            object : DialGroup() { val e by easing(ease = floatArrayOf(2f, 0f, 1f, 1f)) }
        }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val e by easing(duration = -0.1f) } }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by spring(visualDuration = 0f) } }
        assertThrows(IllegalArgumentException::class.java) { object : DialGroup() { val s by physicsSpring(mass = 0f) } }
    }
}
