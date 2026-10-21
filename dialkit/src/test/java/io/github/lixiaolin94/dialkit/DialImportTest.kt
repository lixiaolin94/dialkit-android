package io.github.lixiaolin94.dialkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.buildCopyInstruction

class DialImportTest {
    private fun card() = DialGroupTest.Card().also { it.assignPaths("") }

    private fun resolve(text: String): DialImport = card().resolveImport(extractImportJson(text)!!)

    @Test fun roundTripsThisPanelsCopyText() {
        val source = card()
        source.radius = 40f
        source.tint = 0xFF112233.toInt()
        source.origin = DialPoint(0.5f, -0.25f)
        val copied = buildCopyInstruction("Card", "Card", source.persistedValues(), source.kotlinHints())

        val target = card()
        val result = target.resolveImport(extractImportJson(copied)!!)
        target.restore(result.values)
        assertEquals(source.persistedValues(), target.persistedValues())
        assertEquals(emptyList<String>(), result.ignored)
    }

    @Test fun acceptsFlatAndNestedPathsAndDialKitTuples() {
        val r = resolve("""{ "radius": [32, 0, 64], "shadow": { "blur": 12 }, "fade": { "type": "easing", "duration": 0.2, "ease": [0, 0, 1, 1] } }""")
        assertEquals(DialValue.Num(32f), r.values["radius"])
        assertEquals(DialValue.Num(12f), r.values["shadow.blur"])
        assertEquals(DialValue.Easing(0.2f, listOf(0f, 0f, 1f, 1f)), r.values["fade"])
        assertEquals(emptyList<String>(), r.ignored)
        assertEquals(3, r.count)
    }

    @Test fun reportsWhatItCannotApply() {
        val r = resolve("""{ "radius": "big", "layout": "masonry", "tint": "notacolor", "visible": false, "unknown": 1, "origin": { "x": 0.2, "y": 0.4 } }""")
        assertEquals(setOf("visible", "origin"), r.values.keys)
        assertEquals(DialValue.Point(0.2f, 0.4f), r.values["origin"])
        assertEquals(setOf("radius", "layout", "tint", "unknown"), r.ignored.toSet())
    }

    @Test fun unwrapsPersistedStateAndFindsJsonInProse() {
        val r = resolve("""here: {"version":1,"values":{"columns":5},"presets":[]} thanks""")
        assertEquals(DialValue.Num(5f), r.values["columns"])
        assertNull(extractImportJson("no json here"))
        assertTrue(resolve("{}").values.isEmpty())
    }

    @Test fun keepsTransitionModeOnlyWithItsValue() {
        val r = resolve("""{ "enter": { "type": "spring", "stiffness": 300, "damping": 20, "mass": 1 }, "enter.__mode": "advanced", "fade.__mode": "easing" }""")
        assertEquals(DialValue.Str("advanced"), r.values["enter.__mode"])
        assertNull(r.values["fade.__mode"])
        assertEquals(1, r.count)
    }
}
