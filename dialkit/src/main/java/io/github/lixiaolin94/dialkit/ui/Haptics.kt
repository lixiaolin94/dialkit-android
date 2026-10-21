@file:SuppressLint("InlinedApi") // the API 30/34 constants are compile-time ints, only passed when SDK_INT >= 34

package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Touch feedback, one function per meaning, for operations inside the panel only (the entry and the toggle
 * gesture stay silent). DESIGN (user): Android 14+ semantic constants only; older releases stay silent rather
 * than approximate. performHapticFeedback already honours the system touch-feedback setting
 * and needs no permission; [enabled] mirrors `DialKitConfig.haptics`.
 */
internal object Haptics {
    @JvmField var enabled: Boolean = true
}

private fun View.feedback(constant: Int) {
    if (Build.VERSION.SDK_INT >= 34 && Haptics.enabled) performHapticFeedback(constant)
}

/** A drag reached a limit: slider min / max, panel min / max height. */
internal fun View.hapticBoundary() = feedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)

/** A fine detent: a slider step (coarse sliders only), a pad tap snapping to a grid intersection. */
internal fun View.hapticTick() = feedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)

/** A different option in a segmented control (transition type, colour format). */
internal fun View.hapticSegment() = feedback(HapticFeedbackConstants.SEGMENT_TICK)

internal fun View.hapticToggle(on: Boolean) =
    feedback(if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)

/** A long press took effect: a slider's value opens for typing. */
internal fun View.hapticLongPress() = feedback(HapticFeedbackConstants.LONG_PRESS)

/** Something starts following the finger: a pad point after the hold. */
internal fun View.hapticDragStart() = feedback(HapticFeedbackConstants.DRAG_START)

/** An action took effect: reset, copied, imported. */
internal fun View.hapticConfirm() = feedback(HapticFeedbackConstants.CONFIRM)

/** An action could not complete: clipboard write failed, nothing to import. */
internal fun View.hapticReject() = feedback(HapticFeedbackConstants.REJECT)
