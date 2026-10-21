package io.github.lixiaolin94.dialkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lixiaolin94.dialkit.model.ControlType
import io.github.lixiaolin94.dialkit.model.DialValue

class DialGroupTest {
    class Shadow : DialGroup() {
        val blur by slider(8f, 0f..40f)
    }

    class Card : DialGroup() {
        var radius by slider(24f, 0f..64f)
        val scale by slider(1.2f)
        var columns by slider(3, 1..6)
        var visible by toggle(true)
        var layout by select("grid", "list")
        var title by text("Hi", placeholder = "Title")
        var tint by color("#a78bfa")
        var origin by pad()
        val enter by spring(visualDuration = 0.3f, bounce = 0.2f)
        val fade by easing(duration = 0.4f)
        val shadow by folder(collapsed = true) { Shadow() }
        val reset by action("Reset")
    }

    private fun card() = Card().also { it.assignPaths("") }

    @Test fun keysPathsAndLabelsFollowDialKit() {
        val c = card()
        val metas = c.controls()
        assertEquals(listOf("radius", "scale", "columns", "visible", "layout", "title", "tint", "origin", "enter", "fade", "shadow", "reset"), metas.map { it.path })
        assertEquals("shadow.blur", metas.first { it.type == ControlType.FOLDER }.children.single().path)
        assertFalse(metas.first { it.path == "shadow" }.defaultOpen) // _collapsed: true
        assertEquals("Radius", metas.first().label)
        // Bare number → inferred range (DialStore.inferRange): 1.2 → 0..3.6, step 0.1.
        val scale = metas.first { it.path == "scale" }
        assertEquals(3.6f, scale.max!!, 1e-5f)
        assertEquals(0.1f, scale.step!!, 1e-6f)
    }

    @Test fun writesSnapClampAndValidate() {
        val c = card()
        c.radius = 30.4f
        assertEquals(30f, c.radius)
        c.radius = 99f
        assertEquals(64f, c.radius)
        c.columns = 9
        assertEquals(6, c.columns)
        c.layout = "masonry" // not an option: ignored
        assertEquals("grid", c.layout)
        c.origin = DialPoint(0.3333f, 2f)
        assertEquals(DialPoint(0.33f, 1f), c.origin) // pad step (max − min)/200 = 0.01, clamped
    }

    @Test fun colorStoresCssAndReadsArgb() {
        val c = card()
        assertEquals(0xFFA78BFA.toInt(), c.tint)
        c.tint = 0x80FF0000.toInt()
        assertEquals(DialValue.Str("#ff000080"), c.values()["tint"])
    }

    @Test fun valuesSetValueAndReset() {
        val c = card()
        c.setValue("shadow.blur", DialValue.Num(20f))
        assertEquals(20f, c.shadow.blur)
        c.setValue("radius", DialValue.Bool(true)) // wrong type: ignored
        assertEquals(24f, c.radius)
        c.visible = false
        c.resetValues()
        assertTrue(c.visible)
        assertEquals(8f, c.shadow.blur)
        assertEquals(DialValue.Spring(visualDuration = 0.3f, bounce = 0.2f), c.values()["enter"])
    }

    @Test fun transitionModesCacheLikeTransitionControl() {
        val c = card()
        val node = c.nodes.filterIsInstance<TransitionNode>().first()
        node.update(DialValue.Spring(visualDuration = 0.5f, bounce = 0.4f))
        node.switchMode(CurveMode.Advanced)
        assertEquals(DialValue.Spring(stiffness = 200f, damping = 25f, mass = 1f), node.state.value) // SOURCE defaults
        node.switchMode(CurveMode.Simple)
        assertEquals(DialValue.Spring(visualDuration = 0.5f, bounce = 0.4f), node.state.value) // restored
        assertTrue(c.enter.stiffness!! > 0f)
        assertEquals(0.6f, c.enter.dampingRatio!!, 1e-5f)
        assertEquals(400L, c.fade.durationMillis)
    }

    @Test fun persistedValuesCarryModesAndRestore() {
        val a = card()
        a.radius = 40f
        a.nodes.filterIsInstance<TransitionNode>().first().switchMode(CurveMode.Advanced)
        val saved = a.persistedValues()
        assertEquals(DialValue.Str("advanced"), saved["enter.__mode"])
        val b = card()
        b.restore(saved)
        assertEquals(40f, b.radius)
        assertEquals(CurveMode.Advanced, b.nodes.filterIsInstance<TransitionNode>().first().mode.value)
    }

    @Test fun kotlinHintsAreDeclarations() {
        val hints = card().apply { radius = 32f }.kotlinHints()
        assertTrue(hints.contains("val radius by slider(32f, 0f..64f, step = 1f)"))
        assertTrue(hints.contains("val columns by slider(3, 1..6)"))
        assertTrue(hints.contains("val tint by color(\"#a78bfa\")"))
        assertTrue(hints.any { it.startsWith("// shadow → Shadow") })
    }

    @Test fun setValuesTakesFlatPathsNestedFoldersAndPlainValues() {
        val c = card()
        c.setValues(
            "radius" to 40,                          // Int → slider
            "visible" to false,
            "layout" to "list",
            "tint" to "#112233",
            "origin" to DialPoint(0.5f, -0.5f),
            "shadow" to mapOf("blur" to 12.0),       // nested folder, Double
        )
        val v = c.values()
        assertEquals(DialValue.Num(40f), v["radius"])
        assertEquals(DialValue.Bool(false), v["visible"])
        assertEquals(DialValue.Str("list"), v["layout"])
        assertEquals(DialValue.Str("#112233"), v["tint"])
        assertEquals(DialValue.Point(0.5f, -0.5f), v["origin"])
        assertEquals(DialValue.Num(12f), v["shadow.blur"])
    }

    @Test fun setValuesIgnoresUnknownPathsActionsAndMismatchedTypes() {
        val c = card()
        val before = c.values()
        c.setValues("radius" to "big", "layout" to "masonry", "nope" to 1, "reset" to true, "shadow.blur" to null)
        assertEquals(before, c.values())
    }

    @Test fun openStateNeedsARegisteredPanel() {
        val c = card()
        assertEquals(null, c.getOpen())
        c.setOpen(true) // no-op, no crash
        assertEquals(null, c.getOpen())
    }

    class Gallery : DialGroup() {
        var hero by image("a.png", "b.png")
        val empty by image()
    }

    @Test fun imageDefaultsToTheFirstOptionAndTakesAnyUri() {
        val g = Gallery().also { it.assignPaths("") }
        assertEquals("a.png", g.hero)
        assertEquals("", g.empty)
        g.hero = "content://media/1"
        assertEquals(DialValue.Str("content://media/1"), g.values()["hero"])
        assertEquals(ControlType.IMAGE, g.controls().first().type)
        g.resetValues()
        assertEquals("a.png", g.hero)
    }
}
