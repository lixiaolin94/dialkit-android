package io.github.lixiaolin94.dialkit.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** SOURCE --dial-row-height / --dial-row-gap / --dial-radius. */
internal const val ROW_HEIGHT = 36
internal const val ROW_GAP = 6
internal const val RADIUS = 8

/** Rounded solid fill, the background of every DialKit row. */
internal fun roundRect(context: Context, color: Int, radiusDp: Float = RADIUS.toFloat(), strokeColor: Int = 0, strokeDp: Float = 0f) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = context.dp(radiusDp)
        setColor(color)
        if (strokeDp > 0f) setStroke(Math.round(context.dp(strokeDp)), strokeColor)
    }

internal fun label(context: Context, text: String, color: Int, sizeDp: Float = 13f) = TextView(context).apply {
    this.text = text
    dialText(sizeDp, DialFonts.medium, color)
    maxLines = 1
    ellipsize = android.text.TextUtils.TruncateAt.END
}

internal fun lp(width: Int, height: Int) = ViewGroup.LayoutParams(width, height)
internal fun llp(width: Int, height: Int, weight: Float = 0f) = LinearLayout.LayoutParams(width, height, weight)
internal const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
internal const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** SOURCE SegmentedControl.tsx:L53 cubic-bezier(.25, 1, .5, 1). */
internal val SegmentEasing = PathInterpolator(0.25f, 1f, 0.5f, 1f)

/** A 0→1 tween for colour fades (DialKit's `transition: … 0.15s`). */
internal class Fade(private val durationMs: Long, private val onUpdate: (Float) -> Unit) {
    var value = 0f
        private set
    private var animator: ValueAnimator? = null

    fun to(target: Float) {
        if (animator == null && value == target) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(value, target).apply {
            duration = durationMs
            addUpdateListener {
                value = it.animatedValue as Float
                onUpdate(value)
            }
            start()
        }
    }

    fun snap(target: Float) {
        animator?.cancel()
        animator = null
        value = target
        onUpdate(target)
    }
}

/** Runs [block] when attached and the returned cleanup when detached (listener bookkeeping for cells). */
internal fun View.whileAttached(block: () -> () -> Unit) {
    var cleanup: (() -> Unit)? = null
    if (isAttachedToWindow) cleanup = block()
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            cleanup?.invoke()
            cleanup = block()
        }

        override fun onViewDetachedFromWindow(v: View) {
            cleanup?.invoke()
            cleanup = null
        }
    })
}

/**
 * A soft round drop shadow (≈ a small CSS box-shadow), built once and moved with a local matrix so drawing it
 * allocates nothing.
 */
internal class RadialShadow(private val radius: Float, color: Int, innerStop: Float) {
    private val shader = RadialGradient(0f, 0f, radius, intArrayOf(color, 0), floatArrayOf(innerStop, 1f), Shader.TileMode.CLAMP)
    private val matrix = Matrix()

    fun draw(canvas: Canvas, paint: Paint, cx: Float, cy: Float) {
        matrix.setTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
    }
}
