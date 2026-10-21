package io.github.lixiaolin94.dialkit.model

// Key and accessibility stepping, SOURCE control-keyboard.ts. Hardware keys and accessibility actions share these.

/** Keys DialKit's controls answer to, independent of the platform's key codes. */
internal enum class ControlKey { Left, Right, Up, Down, PageUp, PageDown, Home, End }

/**
 * SOURCE sliderKeyValue: the next slider value for [key], or null when the key does not move it. Steps are counted
 * from [min] (so fractional ranges stay on the grid); PageUp / PageDown and [large] (Shift) move 10 steps.
 */
internal fun sliderKeyValue(key: ControlKey, value: Float, min: Float, max: Float, step: Float, large: Boolean = false): Float? {
    if (key == ControlKey.Home) return min
    if (key == ControlKey.End) return max
    val direction = when (key) {
        ControlKey.Right, ControlKey.Up, ControlKey.PageUp -> 1
        ControlKey.Left, ControlKey.Down, ControlKey.PageDown -> -1
        else -> return null
    }
    if (!(step > 0f) || max <= min) return min
    val amount = if (key == ControlKey.PageUp || key == ControlKey.PageDown || large) 10 else 1
    // Floats go through their decimal form, as the JS original works on the same decimal values (0.35f is
    // 0.3499999940… as a Double, which would floor one step short).
    val v = value.toString().toDouble()
    val lo = min.toString().toDouble()
    val s = step.toString().toDouble()
    val position = (v - lo) / s
    val nextStep = if (direction > 0) Math.floor(position + 1e-9) + amount else Math.ceil(position - 1e-9) - amount
    val next = lo + nextStep * s
    // SOURCE Number(next.toPrecision(14))
    return java.math.BigDecimal(next).round(java.math.MathContext(14)).toFloat().coerceIn(min, max)
}

/** SOURCE optionKeyIndex: the option [key] moves to among [count], wrapping when [wrap] (segmented controls). */
internal fun optionKeyIndex(key: ControlKey, index: Int, count: Int, wrap: Boolean = false): Int? {
    if (count == 0) return null
    if (key == ControlKey.Home) return 0
    if (key == ControlKey.End) return count - 1
    val delta = when (key) {
        ControlKey.Right, ControlKey.Down -> 1
        ControlKey.Left, ControlKey.Up -> -1
        else -> return null
    }
    return if (wrap) (index + delta + count) % count else (index + delta).coerceIn(0, count - 1)
}
