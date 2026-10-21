package io.github.lixiaolin94.dialkit

import android.view.animation.PathInterpolator
import kotlin.math.roundToInt
import kotlin.math.sqrt
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.springParams

/** A pad value. Positive y points up, as in DialKit. */
public data class DialPoint(val x: Float, val y: Float)

/** One pad axis: DialKit's `[default, min, max, step?]`. Step defaults to (max − min) / 200. */
public data class DialAxis(
    val default: Float = 0f,
    val min: Float = -1f,
    val max: Float = 1f,
    val step: Float? = null,
)

/**
 * A spring or easing curve from a transition dial (DialKit `TransitionConfig`).
 * View code: [toInterpolator] + [durationMillis] for easing, or [stiffness] / [dampingRatio] for a spring.
 * Compose: `toAnimationSpec()` from `dialkit-compose`.
 */
public class DialTransition internal constructor(internal val value: DialValue) {
    public val isEasing: Boolean get() = value is DialValue.Easing
    public val isPhysicsSpring: Boolean get() = (value as? DialValue.Spring)?.isPhysics == true

    /** Time-mode spring fields (null in physics/easing mode). */
    public val visualDuration: Float? get() = (value as? DialValue.Spring)?.takeIf { !it.isPhysics }?.visualDuration
    public val bounce: Float? get() = (value as? DialValue.Spring)?.takeIf { !it.isPhysics }?.bounce

    /** Easing fields (null for springs). */
    public val durationSeconds: Float? get() = (value as? DialValue.Easing)?.duration
    public val ease: List<Float>? get() = (value as? DialValue.Easing)?.ease

    /** Easing duration in ms, or null for springs. */
    public val durationMillis: Long? get() = (value as? DialValue.Easing)?.let { (it.duration * 1000).roundToInt().toLong() }

    /** Unit-mass spring stiffness (k / m), as Compose and SpringForce take it; null for easing. */
    public val stiffness: Float?
        get() = (value as? DialValue.Spring)?.let { springParams(it) }?.let { (it.stiffness / it.mass).toFloat() }

    /** Damping ratio c / (2√(k·m)); null for easing. */
    public val dampingRatio: Float?
        get() = (value as? DialValue.Spring)?.let { springParams(it) }
            ?.let { (it.damping / (2 * sqrt(it.stiffness * it.mass))).toFloat() }

    /** The easing's cubic-bezier as a platform interpolator; null for springs. */
    public fun toInterpolator(): PathInterpolator? =
        (value as? DialValue.Easing)?.ease?.let { PathInterpolator(it[0], it[1], it[2], it[3]) }

    override fun equals(other: Any?): Boolean = other is DialTransition && other.value == value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "DialTransition($value)"
}
