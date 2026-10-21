package io.github.lixiaolin94.dialkit.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected values computed with DialKit's sliderKeyValue / optionKeyIndex (control-keyboard.ts). */
class ControlKeysTest {
    @Test fun sliderStepsFromTheRangeMinimum() {
        assertEquals(0.3f, sliderKeyValue(ControlKey.Right, 0.2f, 0f, 1f, 0.1f)!!, 1e-6f)
        assertEquals(0.1f, sliderKeyValue(ControlKey.Left, 0.2f, 0f, 1f, 0.1f)!!, 1e-6f)
        // Off-grid values snap to the next grid point in the direction of travel.
        assertEquals(0.3f, sliderKeyValue(ControlKey.Up, 0.25f, 0f, 1f, 0.1f)!!, 1e-6f)
        assertEquals(0.2f, sliderKeyValue(ControlKey.Down, 0.25f, 0f, 1f, 0.1f)!!, 1e-6f)
        // Grid anchored at min: 0.15 … 0.95 in 0.2 steps.
        assertEquals(0.55f, sliderKeyValue(ControlKey.Right, 0.35f, 0.15f, 0.95f, 0.2f)!!, 1e-6f)
    }

    @Test fun sliderLargeStepsAndEnds() {
        assertEquals(34f, sliderKeyValue(ControlKey.PageUp, 24f, 0f, 64f, 1f))
        assertEquals(14f, sliderKeyValue(ControlKey.Left, 24f, 0f, 64f, 1f, large = true))
        assertEquals(64f, sliderKeyValue(ControlKey.PageUp, 60f, 0f, 64f, 1f)) // clamped
        assertEquals(0f, sliderKeyValue(ControlKey.Home, 24f, 0f, 64f, 1f))
        assertEquals(64f, sliderKeyValue(ControlKey.End, 24f, 0f, 64f, 1f))
        assertEquals(0f, sliderKeyValue(ControlKey.Right, 5f, 0f, 0f, 1f)) // empty range → min
    }

    @Test fun optionsMoveAndWrap() {
        assertEquals(1, optionKeyIndex(ControlKey.Right, 0, 3))
        assertEquals(2, optionKeyIndex(ControlKey.Right, 2, 3))
        assertEquals(0, optionKeyIndex(ControlKey.Right, 2, 3, wrap = true))
        assertEquals(2, optionKeyIndex(ControlKey.Up, 0, 3, wrap = true))
        assertEquals(0, optionKeyIndex(ControlKey.Home, 2, 3))
        assertEquals(2, optionKeyIndex(ControlKey.End, 0, 3))
        assertNull(optionKeyIndex(ControlKey.PageUp, 0, 3))
        assertNull(optionKeyIndex(ControlKey.Right, 0, 0))
    }
}
