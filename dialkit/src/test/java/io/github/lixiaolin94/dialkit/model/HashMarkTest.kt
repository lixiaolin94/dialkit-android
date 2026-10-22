package io.github.lixiaolin94.dialkit.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HashMarkTest {
    @Test fun hashMarksFollowDialKitsCountRule() {
        assertEquals(listOf(0.25f, 0.5f, 0.75f), hashMarkFractions(0f, 1f, 0.25f))
        assertEquals(9, hashMarkFractions(0f, 10f, 1f).size) // 10 steps: a mark per inner step
        assertEquals((1..9).map { it / 10f }, hashMarkFractions(0f, 100f, 1f)) // > 10 steps: tenths
        assertEquals(2, hashMarkFractions(0f, 3.6f, 1f).size) // length = trunc(3.6 − 1); no mark on the short last step
        assertEquals(2, hashMarkFractions(0f, 0.3f, 0.1f).size) // decimal: 3 steps, not JS's 2.9999…
        assertEquals(emptyList<Float>(), hashMarkFractions(0f, 1f, 1f))
    }

    @Test fun everyHashMarkSitsOnAValueATapSnapsTo() {
        val cases = listOf(Triple(0f, 1f, 0.25f), Triple(-5f, 5f, 1f), Triple(10f, 13.6f, 1f), Triple(0f, 0.3f, 0.1f), Triple(2f, 8f, 2f))
        for ((min, max, step) in cases) {
            for (f in hashMarkFractions(min, max, step)) {
                val value = min + f * (max - min)
                assertEquals("$min..$max/$step at $f", value, roundValue(value, step, min, max), 1e-5f)
            }
        }
    }
}
