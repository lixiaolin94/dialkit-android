package io.github.lixiaolin94.dialkit.model

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// SOURCE dial-pad.ts, ported 1:1.

/** SOURCE: PAD_GRID_DIVISIONS = 6. */
internal const val PAD_GRID_DIVISIONS = 6

/** Resolved `[default, min, max, step?]`; step defaults to range / 200 (SOURCE resolvePadAxis). */
internal data class PadAxis(val default: Float, val min: Float, val max: Float, val step: Float)

internal fun resolvePadAxis(default: Float, min: Float, max: Float, step: Float?): PadAxis {
    val s = step ?: (max - min) / 200f
    require(listOf(default, min, max, s, max - min).all { it.isFinite() } && max > min && s > 0f) {
        "DialPad axes need finite [default, min, max, step?] values, min < max, and a positive step."
    }
    val axis = PadAxis(default, min, max, s)
    return axis.copy(default = snapPadAxis(default, axis))
}

internal fun snapPadAxis(value: Float, axis: PadAxis): Float {
    if (!value.isFinite()) return axis.default
    val clamped = value.coerceIn(axis.min, axis.max)
    if (clamped == axis.min || clamped == axis.max) return clamped
    val steps = ((clamped - axis.min).toDouble() / axis.step).roundToLong()
    val snapped = decimal(axis.min).add(BigDecimal(steps).multiply(decimal(axis.step)))
    return snapped.round(MathContext(12)).toFloat().coerceIn(axis.min, axis.max)
}

/** Fractions of the plane, left/bottom = minima. SOURCE padValueFromPoint. */
internal fun padValueFromPoint(fx: Float, fy: Float, x: PadAxis, y: PadAxis): DialValue.Point = DialValue.Point(
    snapPadAxis(x.min + fx * (x.max - x.min), x),
    snapPadAxis(y.max - fy * (y.max - y.min), y),
)

/** Interior grid intersection within 8 units on both axes, else null. SOURCE padGridIntersection. */
internal fun padGridIntersection(px: Float, py: Float, width: Float, height: Float, tolerance: Float): Pair<Float, Float>? {
    if (width <= 0f || height <= 0f) return null
    val column = (px / width * PAD_GRID_DIVISIONS).roundToInt()
    val row = (py / height * PAD_GRID_DIVISIONS).roundToInt()
    if (column < 1 || column >= PAD_GRID_DIVISIONS || row < 1 || row >= PAD_GRID_DIVISIONS) return null
    val tx = column.toFloat() / PAD_GRID_DIVISIONS * width
    val ty = row.toFloat() / PAD_GRID_DIVISIONS * height
    return if (abs(px - tx) <= tolerance && abs(py - ty) <= tolerance) tx to ty else null
}
