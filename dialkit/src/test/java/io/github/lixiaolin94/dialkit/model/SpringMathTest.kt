package io.github.lixiaolin94.dialkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class SpringMathTest {
    /** DialKit's springProgress exactly as transition-math.ts writes it, for comparison. */
    private fun sourceProgress(t: Double, p: SpringParams): Double {
        if (t <= 0) return 0.0
        val w0 = sqrt(p.stiffness / p.mass)
        val zeta = p.damping / (2 * sqrt(p.stiffness * p.mass))
        if (zeta < 0.9999) {
            val wd = w0 * sqrt(1 - zeta * zeta)
            return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0) / wd * sin(wd * t))
        }
        if (zeta < 1.0001) return 1 - exp(-w0 * t) * (1 + w0 * t)
        val wd = w0 * sqrt(zeta * zeta - 1)
        val r1 = -zeta * w0 + wd
        val r2 = -zeta * w0 - wd
        return 1 + (r2 * exp(r1 * t) - r1 * exp(r2 * t)) / (r1 - r2)
    }

    private val times = (1..200).map { it * 0.01 }

    @Test fun stableFormMatchesDialKitAwayFromTheCriticalBand() {
        for (p in listOf(SpringParams(300.0, 25.0, 0.8), SpringParams(200.0, 10.0, 1.0), SpringParams(100.0, 40.0, 1.0))) {
            for (t in times) assertEquals("$p t=$t", sourceProgress(t, p), springProgress(t, p), 1e-12)
        }
    }

    @Test fun nearCriticalDampingIsContinuous() {
        val k = 400.0
        val critical = 2 * sqrt(k)
        for (t in times) {
            val at = springProgress(t, SpringParams(k, critical, 1.0))
            for (d in listOf(1e-9, 1e-7, 1e-5, 1e-3)) {
                assertEquals(at, springProgress(t, SpringParams(k, critical * (1 - d), 1.0)), 2e-3)
                assertEquals(at, springProgress(t, SpringParams(k, critical * (1 + d), 1.0)), 2e-3)
            }
        }
    }

    @Test fun heavilyOverdampedSpringsRiseMonotonicallyWithoutBlowingUp() {
        for (p in listOf(SpringParams(1.0, 2000.0, 1.0), SpringParams(1e6, 1e5, 0.01), SpringParams(0.01, 50.0, 1.0))) {
            var last = 0.0
            for (t in times + listOf(10.0, 100.0)) {
                val v = springProgress(t, p)
                assertTrue("$p t=$t v=$v", v.isFinite() && v >= last - 1e-12 && v <= 1.0 + 1e-12)
                last = v
            }
        }
    }

    @Test fun invalidParametersGiveZeroInsteadOfNaN() {
        for (p in listOf(SpringParams(0.0, 10.0, 1.0), SpringParams(100.0, -1.0, 1.0), SpringParams(100.0, 10.0, 0.0),
            SpringParams(Double.NaN, 10.0, 1.0))) {
            assertEquals(0.0, springProgress(0.5, p), 0.0)
        }
    }
}
