package io.github.lixiaolin94.dialkit.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// SOURCE transition-math.ts + easing-geometry.ts, ported 1:1.

internal data class SpringParams(val stiffness: Double, val damping: Double, val mass: Double)

/** SOURCE transition-math.ts:springParams — Motion's visualDuration/bounce → stiffness/damping mapping. */
internal fun springParams(spring: DialValue.Spring): SpringParams {
    if (spring.isPhysics) {
        return SpringParams(
            (spring.stiffness ?: 200f).toDouble(),
            (spring.damping ?: 25f).toDouble(),
            (spring.mass ?: 1f).toDouble(),
        )
    }
    val visualDuration = max(0.05, (spring.visualDuration ?: 0.3f).toDouble())
    val bounce = (spring.bounce ?: 0.3f).toDouble()
    val root = 2 * PI / (visualDuration * 1.2)
    val stiffness = root * root
    val damping = 2 * min(1.0, max(0.05, 1 - bounce)) * sqrt(stiffness)
    return SpringParams(stiffness, damping, 1.0)
}

/**
 * SOURCE transition-math.ts:springProgress — closed-form damped oscillator, 0 → 1 from rest.
 * DESIGN: the same three branches written in a numerically stable form (after dialkit-macos SpringResponse.swift):
 * a relative tolerance for critical damping instead of DialKit's fixed ζ ∈ [0.9999, 1.0001] band, sin(φ)/φ so the
 * underdamped branch stays finite as ωd → 0, and the slow overdamped root taken as ω²/fast rather than a
 * difference of two nearly equal numbers. Invalid parameters (non-finite, k ≤ 0, m ≤ 0, c < 0) give 0.
 */
internal fun springProgress(t: Double, p: SpringParams): Double {
    val (k, c, m) = p
    if (!(t > 0) || !k.isFinite() || !c.isFinite() || !m.isFinite() || k <= 0 || m <= 0 || c < 0) return 0.0
    if (t.isInfinite()) return 1.0
    val w0 = sqrt(k / m)
    val decay = c / (2 * m) // ζ·ω0
    if (abs(decay - w0) <= w0 * 1e-8) return 1 - exp(-w0 * t) * (1 + w0 * t)
    if (decay < w0) {
        val wd = sqrt((w0 - decay) * (w0 + decay))
        val phase = wd * t
        val sinc = if (abs(phase) < 1e-8) 1.0 else sin(phase) / phase
        return 1 - exp(-decay * t) * (cos(phase) + decay * t * sinc)
    }
    val root = sqrt((decay - w0) * (decay + w0))
    val fast = decay + root
    val slow = w0 * w0 / fast
    return 1 - (fast * exp(-slow * t) - slow * exp(-fast * t)) / (fast - slow)
}

internal data class GraphPoint(val x: Float, val y: Float)

internal class EasingGraph(val scale: Float, val start: GraphPoint, val end: GraphPoint, val handles: List<GraphPoint>)

private fun clampEaseY(v: Float) = v.coerceIn(-1f, 2f)

/** SOURCE easing-geometry.ts:formatEase — `ease.join(', ')` with JS number formatting. */
internal fun formatEase(ease: List<Float>): String = ease.joinToString(", ") { jsNumber(it.toDouble()) }

/** SOURCE easing-geometry.ts:parseEase. */
internal fun parseEase(text: String): List<Float>? {
    val parts = text.split(',').map { it.trim() }
    val num = Regex("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:e[+-]?\\d+)?$", RegexOption.IGNORE_CASE)
    if (parts.size != 4 || parts.any { !num.matches(it) }) return null
    val v = parts.map { it.toFloat() }.toMutableList()
    if (!v.all { it.isFinite() } || v[0] < 0 || v[0] > 1 || v[2] < 0 || v[2] > 1) return null
    v[1] = clampEaseY(v[1])
    v[3] = clampEaseY(v[3])
    return v
}

/** SOURCE easing-geometry.ts:normalizeEase — x in [0, 1], y untouched. */
internal fun normalizeEase(ease: List<Float>): List<Float> = ease.mapIndexed { i, value ->
    val finite = if (value.isFinite()) value else if (i < 2) 0f else 1f
    if (i % 2 == 0) finite.coerceIn(0f, 1f) else finite
}

/** SOURCE easing-geometry.ts:fitEasingGraph — equal scale on both axes so the 0→1 reference stays at 45°. */
internal fun fitEasingGraph(ease: List<Float>, width: Float, height: Float, padding: Float): EasingGraph {
    val v = normalizeEase(ease)
    val pad = min(padding, min(width / 4, height / 4))
    val radiusY = maxOf(0.5f, abs(v[1] - 0.5f), abs(v[3] - 0.5f))
    val unit = min(width - pad * 2, (height / 2 - pad) / radiusY)
    fun project(x: Float, y: Float) = GraphPoint(width / 2 + (x - 0.5f) * unit, height / 2 - (y - 0.5f) * unit)
    return EasingGraph(unit, project(0f, 0f), project(1f, 1f), listOf(project(v[0], v[1]), project(v[2], v[3])))
}

/** SOURCE easing-geometry.ts:moveEasingHandle — deltas use the pointer-down scale; values rounded to 0.01. */
internal fun moveEasingHandle(ease: List<Float>, handle: Int, dx: Float, dy: Float, scale: Float): List<Float> {
    val next = ease.toMutableList()
    if (!scale.isFinite() || scale <= 0f) return next
    val i = handle * 2
    val x = ease[i] + dx / scale
    val y = ease[i + 1] - dy / scale
    if (dx != 0f) next[i] = Math.round(x.coerceIn(0f, 1f) * 100) / 100f
    if (dy != 0f && y.isFinite()) next[i + 1] = Math.round(clampEaseY(y) * 100) / 100f
    return next
}

/** SOURCE easing-geometry.ts:easingGuideEnd — stop the tangent at the handle's outer radius. */
internal fun easingGuideEnd(start: GraphPoint, handle: GraphPoint, radius: Float): GraphPoint {
    val dx = handle.x - start.x
    val dy = handle.y - start.y
    val d = hypot(dx, dy)
    if (d <= radius) return start
    return GraphPoint(handle.x - dx / d * radius, handle.y - dy / d * radius)
}

/** JS `String(number)` for the magnitudes DialKit prints (no exponent, no trailing zeros). */
internal fun jsNumber(v: Double): String {
    if (v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
    return java.math.BigDecimal(v.toFloat().toString()).stripTrailingZeros().toPlainString()
}
