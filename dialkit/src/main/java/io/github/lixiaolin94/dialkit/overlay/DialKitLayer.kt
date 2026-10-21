package io.github.lixiaolin94.dialkit.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.FrameLayout

/** Safe-area insets in px, read from the root window insets rather than dispatch (see [DialKitLayer]). */
internal data class OverlayInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val ime: Int = 0,
)

/**
 * Full-window layer appended to the Activity's DecorView. It only takes touches the overlay needs: none while
 * hidden, the entry button's bounds while collapsed, everything while expanded (a tap outside the panel
 * collapses it and is not passed to the host).
 *
 * Insets are read from getRootWindowInsets on every layout: below API 30 a sibling that consumes insets
 * (fitsSystemWindows content) stops dispatch before it reaches this last child.
 */
@SuppressLint("ViewConstructor")
internal class DialKitLayer(context: Context) : FrameLayout(context) {
    val entryBounds = Rect()
    var insets = OverlayInsets()
        private set
    private var accepting = false

    init {
        clipChildren = false
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshInsets() }
    }

    override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        refreshInsets(windowInsets)
        return windowInsets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshInsets()
    }

    private fun refreshInsets(given: WindowInsets? = null) {
        val wi = given ?: rootWindowInsets ?: return
        val next = if (Build.VERSION.SDK_INT >= 30) {
            val bars = wi.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            OverlayInsets(bars.left, bars.top, bars.right, bars.bottom, wi.getInsets(WindowInsets.Type.ime()).bottom)
        } else {
            val cutout = if (Build.VERSION.SDK_INT >= 28) wi.displayCutout?.let { intArrayOf(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight) } else null
            @Suppress("DEPRECATION")
            OverlayInsets(
                maxOf(wi.systemWindowInsetLeft, cutout?.get(0) ?: 0),
                maxOf(wi.systemWindowInsetTop, cutout?.get(1) ?: 0),
                maxOf(wi.systemWindowInsetRight, cutout?.get(2) ?: 0),
                // Pre-30 the IME arrives folded into the bottom system-window inset.
                wi.systemWindowInsetBottom,
                0,
            )
        }
        if (next != insets) {
            insets = next
            // The overlay keeps its size when the IME shows, so ask the children too — requestLayout() here
            // alone would not re-run their onLayout, and the safe area would go stale.
            for (i in 0 until childCount) getChildAt(i).requestLayout()
            requestLayout()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            accepting = when (OverlayController.mode) {
                OverlayMode.Hidden -> false
                OverlayMode.Entry -> entryBounds.contains(event.x.toInt(), event.y.toInt())
                OverlayMode.Expanded -> true
            }
        }
        if (!accepting) return false
        super.dispatchTouchEvent(event)
        return true
    }
}
