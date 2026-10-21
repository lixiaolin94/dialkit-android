package io.github.lixiaolin94.dialkit.overlay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot
import io.github.lixiaolin94.dialkit.ToggleGesture

/**
 * Watches the raw touch stream of a window. Returns true from [onTouchEvent] once the stream belongs
 * to the gesture; from then until the stream ends the host must not see it.
 */
internal interface ToggleGestureDetector {
    fun onTouchEvent(event: MotionEvent): Boolean
}

internal fun ToggleGesture.createDetector(context: Context, onTrigger: () -> Unit): ToggleGestureDetector? =
    when (this) {
        ToggleGesture.ThreeFingerDoubleTap -> ThreeFingerDoubleTapDetector(context, onTrigger)
        is ToggleGesture.ThreeFingerLongPress -> ThreeFingerLongPressDetector(context, durationMillis, onTrigger)
        ToggleGesture.None -> null
    }

/** Shared bookkeeping: per-pointer down positions and "did anyone wander" tracking. */
private class PointerSlop(context: Context) {
    // DESIGN: 2× touch slop — fingers on glass drift more than a single tap does.
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2f
    private val downs = SparseArray<FloatArray>()

    fun reset() = downs.clear()

    fun record(event: MotionEvent, index: Int) {
        downs.put(event.getPointerId(index), floatArrayOf(event.getX(index), event.getY(index)))
    }

    fun exceeded(event: MotionEvent): Boolean {
        for (i in 0 until event.pointerCount) {
            val start = downs.get(event.getPointerId(i)) ?: continue
            if (hypot(event.getX(i) - start[0], event.getY(i) - start[1]) > slop) return true
        }
        return false
    }
}

/**
 * Two three-finger taps. Parameters are DESIGN values awaiting on-device tuning (docs/DESIGN.md §4.2):
 * three fingers down within 150 ms, each tap ≤ 300 ms, second tap starts ≤ 350 ms after the first ends.
 */
internal class ThreeFingerDoubleTapDetector(
    context: Context,
    private val onTrigger: () -> Unit,
) : ToggleGestureDetector {
    private val slop = PointerSlop(context)
    private var streamStart = 0L
    private var captured = false
    private var clean = false
    private var tapCount = 0
    private var lastTapEnd = 0L

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                streamStart = event.eventTime
                slop.reset()
                slop.record(event, 0)
                captured = false
                clean = true
                if (event.eventTime - lastTapEnd > SECOND_TAP_WINDOW_MS) tapCount = 0
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                slop.record(event, event.actionIndex)
                when {
                    event.pointerCount > 3 -> clean = false
                    event.pointerCount == 3 && event.eventTime - streamStart > FINGERS_DOWN_WINDOW_MS -> clean = false
                    event.pointerCount == 3 && clean -> captured = true
                }
            }
            MotionEvent.ACTION_MOVE -> if (clean && slop.exceeded(event)) clean = false
            MotionEvent.ACTION_UP -> {
                val wasCaptured = captured
                val isTap = captured && clean && event.eventTime - streamStart <= TAP_TIMEOUT_MS
                if (isTap) {
                    tapCount++
                    lastTapEnd = event.eventTime
                    if (tapCount >= 2) {
                        tapCount = 0
                        onTrigger()
                    }
                } else {
                    tapCount = 0
                }
                captured = false
                return wasCaptured
            }
            MotionEvent.ACTION_CANCEL -> {
                val wasCaptured = captured
                captured = false
                tapCount = 0
                return wasCaptured
            }
        }
        return captured
    }

    private companion object {
        const val FINGERS_DOWN_WINDOW_MS = 150L
        const val TAP_TIMEOUT_MS = 300L
        const val SECOND_TAP_WINDOW_MS = 350L
    }
}

/** Three fingers held still for [durationMillis]. DESIGN parameters, same 150 ms landing window as the tap. */
internal class ThreeFingerLongPressDetector(
    context: Context,
    private val durationMillis: Long,
    private val onTrigger: () -> Unit,
) : ToggleGestureDetector {
    private val slop = PointerSlop(context)
    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable { onTrigger() }
    private var streamStart = 0L
    private var captured = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                streamStart = event.eventTime
                slop.reset()
                slop.record(event, 0)
                captured = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                slop.record(event, event.actionIndex)
                handler.removeCallbacks(fire)
                if (event.pointerCount == 3 && event.eventTime - streamStart <= 150L) {
                    captured = true
                    handler.postDelayed(fire, durationMillis)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> handler.removeCallbacks(fire)
            MotionEvent.ACTION_MOVE -> if (slop.exceeded(event)) handler.removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(fire)
                val wasCaptured = captured
                captured = false
                return wasCaptured
            }
        }
        return captured
    }
}
