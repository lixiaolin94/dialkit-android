package io.github.lixiaolin94.dialkit.overlay

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.MotionEvent
import android.view.Window
import androidx.annotation.RequiresApi
import io.github.lixiaolin94.dialkit.KeyShortcut

/**
 * Sits in front of the host's Window.Callback. Sees every touch before the view tree does, without
 * delaying or altering it, until the toggle gesture claims a stream; then the host gets ACTION_CANCEL.
 */
internal class OverlayWindowCallback(
    val delegate: Window.Callback,
    private val detector: ToggleGestureDetector?,
    private val keyShortcut: KeyShortcut?,
) : Window.Callback by delegate {
    private var claimed = false
    private var swallowKeyUp = -1

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val consume = detector?.onTouchEvent(event) == true
        if (consume) {
            if (!claimed) cancelHost(event)
            claimed = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            return true
        }
        claimed = false
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == swallowKeyUp) {
            swallowKeyUp = -1
            if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) OverlayController.back()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (keyShortcut?.matches(event) == true) {
                OverlayController.toggleEntry()
                swallowKeyUp = event.keyCode
                return true
            }
            // Fallback for hosts that did not opt into OnBackInvokedCallback; API 33+ opted-in hosts
            // never deliver KEYCODE_BACK and use the dispatcher registration in ActivityAttachment.
            if ((event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) && OverlayController.mode == OverlayMode.Expanded) {
                swallowKeyUp = event.keyCode
                return true
            }
        }
        return delegate.dispatchKeyEvent(event)
    }

    // Java default methods are not covered by `by delegate`; forward them explicitly.
    @RequiresApi(24)
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) =
        delegate.onProvideKeyboardShortcuts(data, menu, deviceId)

    @RequiresApi(26)
    override fun onPointerCaptureChanged(hasCapture: Boolean) = delegate.onPointerCaptureChanged(hasCapture)

    private fun cancelHost(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        delegate.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
}
