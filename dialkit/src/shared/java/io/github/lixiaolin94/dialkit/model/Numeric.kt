package io.github.lixiaolin94.dialkit.model

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.roundToLong

// Ports of DialKit's numeric rules. Every constant here is SOURCE unless tagged otherwise.

/** SOURCE numeric.ts:decimalsForStep — decimal places needed by a step or range endpoint. */
internal fun decimalsForStep(step: Float, min: Float = 0f, max: Float = 0f): Int =
    maxOf(decimalsOf(step), decimalsOf(min), decimalsOf(max)).coerceIn(0, 100)

private fun decimalsOf(value: Float): Int {
    if (!value.isFinite()) return 0
    return BigDecimal(value.toString()).stripTrailingZeros().scale().coerceAtLeast(0)
}

/** SOURCE numeric.ts:roundValue — snap relative to the minimum and keep exact endpoints reachable. */
internal fun roundValue(value: Float, step: Float, min: Float, max: Float): Float {
    val clamped = value.coerceIn(min, max)
    if (clamped == min || clamped == max || !step.isFinite() || step <= 0f) return clamped
    val steps = ((clamped - min).toDouble() / step).roundToLong()
    // Decimal arithmetic on the floats' shortest strings, so 60 × 0.01f is 0.6f, not 0.59999996f
    // (the JS original gets this from doubles + toPrecision(14)).
    val snapped = decimal(min).add(BigDecimal(steps).multiply(decimal(step)))
    return snapped.round(MathContext(14)).toFloat().coerceIn(min, max)
}

/** SOURCE DialStore.ts:inferStep — default step for an explicit range. */
internal fun decimal(v: Float): BigDecimal = BigDecimal(v.toString())

internal fun inferStep(min: Float, max: Float): Float {
    val range = max - min
    return when {
        range <= 1f -> 0.01f
        range <= 10f -> 0.1f
        range <= 100f -> 1f
        else -> 10f
    }
}

internal data class InferredRange(val min: Float, val max: Float, val step: Float)

/** SOURCE DialStore.ts:inferRange (L1005) — range for a bare-number slider. */
internal fun inferRange(value: Float): InferredRange = when {
    value >= 0f && value <= 1f -> InferredRange(0f, 1f, 0.01f)
    value >= 0f && value <= 10f -> InferredRange(0f, (value * 3).orIfZero(10f), 0.1f)
    value >= 0f && value <= 100f -> InferredRange(0f, (value * 3).orIfZero(100f), 1f)
    value >= 0f -> InferredRange(0f, (value * 3).orIfZero(1000f), 10f)
    else -> InferredRange(value * 3, -value * 3, 1f)
}

private fun Float.orIfZero(fallback: Float) = if (this == 0f) fallback else this

/** SOURCE shortcut-utils.ts:snapToDecile — magnetic snap to tenths within ±0.03125. */
internal fun snapToDecile(raw: Float, min: Float, max: Float): Float {
    val normalized = (raw - min) / (max - min)
    val nearest = Math.round(normalized * 10f) / 10f
    return if (abs(normalized - nearest) <= 0.03125f) min + nearest * (max - min) else raw
}

/** SOURCE DialStore.ts:formatLabel — camelCase → Title Case. */
internal fun formatLabel(key: String): String =
    key.replace(Regex("([A-Z])"), " $1").replaceFirstChar { it.uppercaseChar() }.trim()

/** SOURCE Slider.tsx — `value.toFixed(decimalsForStep(step, min, max))`. */
internal fun formatSliderValue(value: Float, step: Float, min: Float, max: Float): String {
    val decimals = decimalsForStep(step, min, max)
    return BigDecimal(value.toDouble()).setScale(decimals, java.math.RoundingMode.HALF_UP).toPlainString()
}
