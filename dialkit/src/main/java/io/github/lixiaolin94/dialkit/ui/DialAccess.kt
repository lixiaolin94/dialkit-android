package io.github.lixiaolin94.dialkit.ui

import android.os.Build
import android.view.KeyEvent
import android.view.View
import io.github.lixiaolin94.dialkit.model.ControlKey

// Accessibility and hardware-key plumbing shared by the custom-drawn controls (DialKit's keyboard support,
// SOURCE control-keyboard.ts, mapped onto Android's accessibility actions and key events).

/**
 * The DialKit key for a hardware key. [vertical] false leaves Up / Down to focus navigation, as the platform
 * SeekBar does (DESIGN: DialKit uses them to step; on Android they move between controls).
 */
internal fun KeyEvent.controlKey(vertical: Boolean): ControlKey? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> ControlKey.Left
    KeyEvent.KEYCODE_DPAD_RIGHT -> ControlKey.Right
    KeyEvent.KEYCODE_DPAD_UP -> if (vertical) ControlKey.Up else null
    KeyEvent.KEYCODE_DPAD_DOWN -> if (vertical) ControlKey.Down else null
    KeyEvent.KEYCODE_PAGE_UP -> ControlKey.PageUp
    KeyEvent.KEYCODE_PAGE_DOWN -> ControlKey.PageDown
    KeyEvent.KEYCODE_MOVE_HOME -> ControlKey.Home
    KeyEvent.KEYCODE_MOVE_END -> ControlKey.End
    else -> null
}

internal fun KeyEvent.isActivation() = keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
    keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE

/**
 * What a screen reader says: the label as the description and the value as the state (API 30+), or both in the
 * description before that.
 */
internal fun View.describe(label: String, state: String) {
    if (Build.VERSION.SDK_INT >= 30) {
        if (contentDescription != label) contentDescription = label
        if (stateDescription != state) stateDescription = state
    } else {
        val text = "$label: $state"
        if (contentDescription != text) contentDescription = text
    }
}
