package io.github.lixiaolin94.dialkit.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.springParams
import io.github.lixiaolin94.dialkit.model.springProgress

/** The View UI's frame-stepped spring must trace the same curve DialKit/motion draws (transition-math.ts). */
class DialSpringTest {
    private fun traced(spec: SpringSpec, dt: Double, until: Double): List<Pair<Double, Double>> {
        var x = -1.0 // value 0 heading to target 1
        var v = 0.0
        var t = 0.0
        val out = ArrayList<Pair<Double, Double>>()
        while (t < until) {
            val (nx, nv) = springStep(x, v, dt, spec)
            x = nx
            v = nv
            t += dt
            out += t to (1 + x)
        }
        return out
    }

    private fun assertMatches(spec: SpringSpec, dial: DialValue.Spring, dt: Double) {
        val params = springParams(dial)
        for ((t, value) in traced(spec, dt, 2.0)) {
            assertEquals("t=$t", springProgress(t, params), value, 1e-6)
        }
    }

    @Test fun timeSpringMatchesMotionCurveAt60And120Hz() {
        val dial = DialValue.Spring(visualDuration = 0.3f, bounce = 0.2f)
        assertMatches(timeSpring(0.3f, 0.2f), dial, 1.0 / 60)
        assertMatches(timeSpring(0.3f, 0.2f), dial, 1.0 / 120)
    }

    @Test fun unevenFrameTimesDoNotChangeTheCurve() {
        // Exact integration: one 50 ms step lands where three ~16.7 ms steps do.
        val spec = timeSpring(0.35f, 0.1f)
        val (a, _) = springStep(-1.0, 0.0, 0.05, spec)
        var x = -1.0
        var v = 0.0
        repeat(3) { springStep(x, v, 0.05 / 3, spec).let { x = it.first; v = it.second } }
        assertEquals(a, x, 1e-9)
    }

    @Test fun criticalAndOverdampedMatch() {
        assertMatches(timeSpring(0.3f, 0f), DialValue.Spring(visualDuration = 0.3f, bounce = 0f), 1.0 / 60)
        // ζ = 40 / (2·√100) = 2
        assertMatches(SpringSpec(100f, 2f), DialValue.Spring(stiffness = 100f, damping = 40f, mass = 1f), 1.0 / 60)
    }

    @Test fun timeSpringConstants() {
        val s = timeSpring(0.3f, 0.2f)
        assertEquals(304.6f, s.stiffness, 0.1f) // (2π / 0.36)²
        assertEquals(0.8f, s.dampingRatio, 1e-6f)
    }
}
