package io.github.lixiaolin94.dialkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the ported DialKit 2.0.2 rules (dialkit@2.0.2, src/) to the values the JS produces. */
class DialKitMathTest {
    @Test fun roundValueSnapsRelativeToMinAndKeepsEndpoints() {
        assertEquals(0.6f, roundValue(0.6012f, 0.01f, 0f, 1f))
        assertEquals(47f, roundValue(47.4f, 1f, 0f, 64f))
        assertEquals(64f, roundValue(70f, 1f, 0f, 64f))
        assertEquals(0.3f, roundValue(0.31f, 0.05f, 0.1f, 1f)) // 0.1 + 4 × 0.05
    }

    @Test fun inferRangeMatchesDialStore() {
        assertEquals(InferredRange(0f, 1f, 0.01f), inferRange(0.5f))
        assertEquals(InferredRange(0f, 3.6000001f, 0.1f), inferRange(1.2f))
        assertEquals(InferredRange(0f, 72f, 1f), inferRange(24f))
        assertEquals(InferredRange(0f, 1500f, 10f), inferRange(500f))
        assertEquals(InferredRange(-30f, 30f, 1f), inferRange(-10f))
    }

    @Test fun inferStepAndDecimals() {
        assertEquals(0.01f, inferStep(0f, 1f))
        assertEquals(1f, inferStep(0f, 64f))
        assertEquals(2, decimalsForStep(0.01f))
        assertEquals("0.60", formatSliderValue(0.6f, 0.01f, 0f, 1f))
        assertEquals("24", formatSliderValue(24f, 1f, 0f, 64f))
    }

    @Test fun decileSnapAndLabels() {
        assertEquals(0.5f, snapToDecile(0.52f, 0f, 1f), 1e-6f)
        assertEquals(0.56f, snapToDecile(0.56f, 0f, 1f), 1e-6f)
        assertEquals("Border Radius", formatLabel("borderRadius"))
    }

    @Test fun colorRoundTripsAndFormats() {
        val c = parseColor("#a78bfa")
        assertNotNull(c)
        assertEquals("#a78bfa", formatColor(c!!, ColorFormat.Hex))
        assertEquals(ColorFormat.Oklch, colorFormat("oklch(0.7 0.1 290)"))
        assertEquals("#ff0000", formatColor(parseColor("rgb(255 0 0)")!!, ColorFormat.Hex))
        assertEquals("#00ff00", formatColor(parseColor("hsl(120 100% 50%)")!!, ColorFormat.Hex))
        assertEquals("#ff000080", formatColor(parseColor("rgba(255, 0, 0, 0.5)")!!, ColorFormat.Hex))
        assertNull(parseColor("rgb(255 0 0"))
        assertNull(parseColor("not-a-color"))
    }

    @Test fun easingGeometry() {
        assertEquals(listOf(0.42f, 0f, 0.58f, 1f), parseEase("0.42, 0, 0.58, 1"))
        assertNull(parseEase("1.2, 0, 0.5, 1")) // x out of range
        assertEquals(listOf(0.5f, 2f, 0.5f, -1f), parseEase("0.5, 3, 0.5, -4")) // y clamped to [-1, 2]
        val g = fitEasingGraph(listOf(0.42f, 0f, 0.58f, 1f), 888f, 624f, 42f)
        assertEquals(540f, g.scale, 1e-3f)
        assertEquals(listOf(0.62f, 0.2f, 0.58f, 1f), moveEasingHandle(listOf(0.42f, 0f, 0.58f, 1f), 0, 108f, -108f, g.scale))
        assertEquals("1, -0.4, 0.5, 1", formatEase(listOf(1f, -0.4f, 0.5f, 1f)))
    }

    @Test fun padSnapsAndFindsGrid() {
        val axis = resolvePadAxis(0f, -1f, 1f, null)
        assertEquals(0.01f, axis.step)
        assertEquals(0.37f, snapPadAxis(0.3684f, axis))
        assertNotNull(padGridIntersection(606f, 310f, 888f, 888f, 28f))
        assertNull(padGridIntersection(10f, 10f, 888f, 888f, 28f)) // outer edge is not an intersection
    }

    @Test fun springMatchesMotionMapping() {
        val p = springParams(DialValue.Spring(visualDuration = 0.3f, bounce = 0.2f))
        assertEquals(304.6, p.stiffness, 0.1) // (2π / 0.36)²
        assertEquals(27.92, p.damping, 0.01) // 2 × 0.8 × √k
        assertEquals(0.0, springProgress(0.0, p), 0.0)
        assertEquals(1.0, springProgress(2.0, p), 1e-3)
    }
}
