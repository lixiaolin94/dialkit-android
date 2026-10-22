package io.github.lixiaolin94.dialkit.ui

import android.view.Choreographer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** A unit-mass spring: stiffness k/m and damping ratio, as Compose and SpringForce take it. */
internal class SpringSpec(val stiffness: Float, val dampingRatio: Float)

/**
 * motion's time-based spring `{ visualDuration, bounce }`.
 * SOURCE transition-math.ts:L30: k = (2π / (1.2·vd))², c = 2·clamp(1 − bounce, .05, 1)·√k, m = 1,
 * hence dampingRatio = clamp(1 − bounce, .05, 1).
 */
internal fun timeSpring(visualDuration: Float, bounce: Float): SpringSpec {
    val root = 2.0 * PI / (1.2 * visualDuration.coerceAtLeast(0.05f))
    return SpringSpec((root * root).toFloat(), (1f - bounce).coerceIn(0.05f, 1f))
}


/**
 * One animated float on the platform's frame clock. Each frame advances the closed-form damped oscillator
 * (exact for any frame time, like Compose's SpringSimulation), so retargeting mid-flight keeps position and
 * velocity continuous. Main thread only.
 */
internal class SpringValue(
    initial: Float,
    /** Settle once both displacement and velocity/10 fall below this (in the value's own units). */
    private val threshold: Float = 0.01f,
    private val onUpdate: (Float) -> Unit,
) : Choreographer.FrameCallback {
    var value: Float = initial
        private set
    var velocity: Float = 0f
        private set
    var target: Float = initial
        private set
    val isRunning: Boolean get() = running

    private var spec = SpringSpec(1500f, 1f)
    private var running = false
    private var lastFrameNanos = 0L
    private var onEnd: (() -> Unit)? = null

    fun snapTo(v: Float) {
        stop()
        value = v
        target = v
        velocity = 0f
        onUpdate(v)
    }

    fun animateTo(target: Float, spec: SpringSpec, initialVelocity: Float = velocity, onEnd: (() -> Unit)? = null) {
        this.target = target
        this.spec = spec
        this.velocity = initialVelocity
        this.onEnd = onEnd
        if (!running) {
            running = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stop() {
        if (running) Choreographer.getInstance().removeFrameCallback(this)
        running = false
        velocity = 0f
        onEnd = null
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        // First frame: start the clock (one frame of 16 ms) so motion begins immediately.
        val dt = if (lastFrameNanos == 0L) 0.016f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0f, 0.064f)
        lastFrameNanos = frameTimeNanos
        step(dt)
        if (abs(value - target) < threshold && abs(velocity) < threshold * 10) {
            value = target
            velocity = 0f
            running = false
            onUpdate(value)
            val end = onEnd
            onEnd = null
            end?.invoke()
            return
        }
        onUpdate(value)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun step(t: Float) {
        val (x, v) = springStep((value - target).toDouble(), velocity.toDouble(), t.toDouble(), spec)
        value = (target + x).toFloat()
        velocity = v.toFloat()
    }
}

/**
 * Closed-form damped harmonic oscillator: displacement and velocity after [t] seconds, starting from
 * displacement [x0] and velocity [v0] (unit mass). Exact for any step size, so frame drops cannot change the
 * curve; matches DialKit's springProgress (transition-math.ts) for x0 = −1, v0 = 0.
 */
internal fun springStep(x0: Double, v0: Double, t: Double, spec: SpringSpec): Pair<Double, Double> {
    val w0 = sqrt(spec.stiffness.toDouble())
    val z = spec.dampingRatio.toDouble()
    if (!(t > 0) || !(w0 > 0) || !w0.isFinite() || !(z >= 0)) return x0 to v0
    val decay = z * w0
    // DESIGN: same branches as springProgress, in its numerically stable form (relative critical tolerance,
    // sin(φ)/φ near ωd → 0, slow overdamped root as ω²/fast).
    return when {
        abs(decay - w0) <= w0 * 1e-8 -> {
            val e = exp(-w0 * t)
            val b = v0 + w0 * x0
            e * (x0 + b * t) to e * (v0 - w0 * b * t)
        }
        decay < w0 -> {
            val wd = sqrt((w0 - decay) * (w0 + decay))
            val phase = wd * t
            val sinc = if (abs(phase) < 1e-8) 1.0 else sin(phase) / phase // sin(ωd·t) / ωd = t·sinc
            val e = exp(-decay * t)
            val c = cos(phase)
            e * (x0 * c + (v0 + decay * x0) * t * sinc) to e * (v0 * c - (decay * v0 + w0 * w0 * x0) * t * sinc)
        }
        else -> {
            val root = sqrt((decay - w0) * (decay + w0))
            val fast = decay + root
            val slow = w0 * w0 / fast
            val r1 = -slow
            val r2 = -fast
            val c1 = (v0 - r2 * x0) / (r1 - r2)
            val c2 = x0 - c1
            c1 * exp(r1 * t) + c2 * exp(r2 * t) to c1 * r1 * exp(r1 * t) + c2 * r2 * exp(r2 * t)
        }
    }
}

