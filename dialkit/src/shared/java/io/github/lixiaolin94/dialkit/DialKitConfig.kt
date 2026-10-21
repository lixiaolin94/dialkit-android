package io.github.lixiaolin94.dialkit

import android.view.KeyEvent

/** Global options. Set them before the first Activity resumes (e.g. in `Application.onCreate`). */
public class DialKitConfig internal constructor() {
    /** Gesture that shows and hides the entry button. */
    public var toggleGesture: ToggleGesture = ToggleGesture.ThreeFingerDoubleTap

    /** Hardware-keyboard shortcut doing the same as [toggleGesture]; useful on emulators. `null` disables it. */
    public var keyShortcut: KeyShortcut? = KeyShortcut()

    /** Entry visibility on first install. Later launches restore the last state. */
    public var entryVisibleByDefault: Boolean = false

    /**
     * Corner the entry first appears in (DialKit `position`, default top-right). After the entry has been
     * dragged, its last spot wins.
     */
    public var entryPosition: DialPosition = DialPosition.TopEnd

    /**
     * Open the panel at launch when the entry is visible (DialKit `defaultOpen`). DESIGN: off by default, unlike
     * DialKit, because on a phone the panel covers much of the screen.
     */
    public var defaultOpen: Boolean = false

    /** Called with true when the panel opens and false when it closes or hides (DialKit `onOpenChange`). */
    public var onOpenChange: ((open: Boolean) -> Unit)? = null

    /** Touch feedback on Android 14+ (slider limits, toggles, snaps, confirmations). Follows the system setting. */
    public var haptics: Boolean = true

    public var theme: DialTheme = DialTheme.System
}

public sealed interface ToggleGesture {
    /**
     * Two quick three-finger taps. The host stops receiving the stream as soon as the third finger lands,
     * so it never sees a click. DESIGN: chosen because two-finger gestures collide with pinch and
     * three-finger long-press/swipe are taken by ColorOS/MIUI screenshots.
     */
    public data object ThreeFingerDoubleTap : ToggleGesture

    /** Three fingers held still. May be taken by ROM screenshot gestures. */
    public data class ThreeFingerLongPress(val durationMillis: Long = 600L) : ToggleGesture

    /** Only [DialKit.toggleEntry], the key shortcut and the adb broadcast. */
    public data object None : ToggleGesture
}

/** Default: Ctrl + Shift + D. */
public data class KeyShortcut(
    val keyCode: Int = KeyEvent.KEYCODE_D,
    val ctrl: Boolean = true,
    val shift: Boolean = true,
    val alt: Boolean = false,
) {
    internal fun matches(event: KeyEvent): Boolean =
        event.keyCode == keyCode &&
            event.isCtrlPressed == ctrl &&
            event.isShiftPressed == shift &&
            event.isAltPressed == alt
}

public enum class DialTheme { System, Light, Dark }

/** Screen corners; Start / End follow the layout direction. DialKit: 'top-left' … 'bottom-right'. */
public enum class DialPosition { TopStart, TopEnd, BottomStart, BottomEnd }

/**
 * Keep a panel's values across app restarts (DialKit `persist`). Off unless given, like DialKit, so changed code
 * defaults are not silently overridden by stale saved values.
 *
 * @param key SharedPreferences key; defaults to `dialkit:<id>`, DialKit's localStorage key.
 * @param presets also keep saved versions and the base values; false keeps only the current values.
 */
public data class DialPersist(val key: String? = null, val presets: Boolean = true)
