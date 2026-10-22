package io.github.lixiaolin94.dialkit.overlay

import io.github.lixiaolin94.dialkit.DialKitConfig
import io.github.lixiaolin94.dialkit.DialPosition
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

internal enum class OverlayMode { Hidden, Entry, Expanded }

internal enum class EntrySide { Start, End }

/** A point in dp. */
internal data class DpPoint(val x: Float, val y: Float)

/**
 * Overlay state shared by every attached Activity, so the entry and panel survive navigation and
 * recreation. Geometry is kept in dp; each Activity clamps it to its own safe area.
 */
internal object OverlayController {
    private const val PREFS = "io.github.lixiaolin94.dialkit.overlay"
    private const val KEY_ENTRY_VISIBLE = "entry_visible"
    private const val KEY_ENTRY_SIDE = "entry_side"
    private const val KEY_ENTRY_FRACTION = "entry_fraction"
    private const val KEY_PANEL_HEIGHT = "panel_height_dp"

    private var prefs: SharedPreferences? = null
    private val listeners = CopyOnWriteArrayList<(OverlayMode) -> Unit>()

    var mode: OverlayMode = OverlayMode.Hidden
        private set

    // SOURCE: DialKit defaults to `position: 'top-right'`.
    var entrySide: EntrySide = EntrySide.End
        private set

    /** 0 = top of the entry's vertical travel, 1 = bottom. */
    var entryFraction: Float = 0f
        private set

    /** Panel top-left in window dp after the user dragged it; null = anchor to the entry (SOURCE panel-drag.ts). */
    var panelOffsetDp: DpPoint? = null

    /** Height the user pinned with the resize handle; null = auto height. DESIGN (DialKit has no resize). */
    var pinnedHeightDp: Float? = null
        private set

    private var config: DialKitConfig? = null
    private var warnedOff = false
    private var resolved = false

    /** At install (from the init provider, before Application.onCreate): storage only, no state yet. */
    fun init(context: Context, config: DialKitConfig) {
        this.config = config
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Initial state, applied when the first Activity resumes, so options set in Application.onCreate or the first
     * Activity's onCreate count. An explicit show / hide / open before that wins over the defaults.
     */
    fun start() = resolve(explicit = false)

    private fun resolve(explicit: Boolean) {
        if (resolved) return
        resolved = true
        val p = prefs ?: return
        val config = config ?: return
        // Until the entry is first dropped somewhere, it starts in the configured corner.
        val start = config.entryPosition == DialPosition.TopStart || config.entryPosition == DialPosition.BottomStart
        val bottom = config.entryPosition == DialPosition.BottomStart || config.entryPosition == DialPosition.BottomEnd
        entrySide = when (p.getString(KEY_ENTRY_SIDE, null)) {
            EntrySide.Start.name -> EntrySide.Start
            EntrySide.End.name -> EntrySide.End
            else -> if (start) EntrySide.Start else EntrySide.End
        }
        entryFraction = p.getFloat(KEY_ENTRY_FRACTION, if (bottom) 1f else 0f)
        pinnedHeightDp = p.getFloat(KEY_PANEL_HEIGHT, -1f).takeIf { it > 0f }
        if (explicit) return
        if (!hasEntry) {
            // No entry to restore: start closed unless the host asked for the panel to open at launch.
            transitionTo(if (config.defaultOpen) OverlayMode.Expanded else OverlayMode.Hidden)
            return
        }
        val visible = p.getBoolean(KEY_ENTRY_VISIBLE, config.entryVisibleByDefault)
        transitionTo(if (!visible) OverlayMode.Hidden else if (config.defaultOpen) OverlayMode.Expanded else OverlayMode.Entry)
    }

    /** False when the host hides the entry button ([DialKitConfig.entryButton]); collapsing then hides everything. */
    private val hasEntry: Boolean get() = config?.entryButton != false

    fun addListener(listener: (OverlayMode) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (OverlayMode) -> Unit) {
        listeners -= listener
    }

    fun transitionTo(requested: OverlayMode) {
        if (requested != OverlayMode.Hidden && config?.floatingPanel == false) {
            if (!warnedOff) Log.i("DialKit", "floatingPanel = false: there is no floating panel; show groups with DialPanelView / DialKitPanel")
            warnedOff = true
            return
        }
        resolve(explicit = true)
        val next = if (requested == OverlayMode.Entry && !hasEntry) OverlayMode.Hidden else requested
        if (mode == next) return
        val previous = mode
        val wasVisible = mode != OverlayMode.Hidden
        mode = next
        val visible = next != OverlayMode.Hidden
        if (wasVisible != visible && hasEntry) prefs?.edit()?.putBoolean(KEY_ENTRY_VISIBLE, visible)?.apply()
        listeners.forEach { it(next) }
        if ((previous == OverlayMode.Expanded) != (next == OverlayMode.Expanded)) {
            config?.onOpenChange?.invoke(next == OverlayMode.Expanded)
        }
    }

    /** The toggle gesture, shortcut and adb toggle; without an entry button they open and close the panel itself. */
    fun toggleEntry() = when {
        mode != OverlayMode.Hidden -> transitionTo(OverlayMode.Hidden)
        hasEntry -> transitionTo(OverlayMode.Entry)
        else -> transitionTo(OverlayMode.Expanded)
    }

    fun expand() {
        if (mode == OverlayMode.Entry) transitionTo(OverlayMode.Expanded)
    }

    /** The attached overlay's chance to consume Back first (an open popup closes before the panel). */
    var backHandler: (() -> Boolean)? = null

    /** Back / Escape while expanded: close an open popup, else collapse. SOURCE Escape closes popovers first. */
    fun back() {
        if (backHandler?.invoke() != true) collapse()
    }

    /** Shows the entry if needed and expands (straight from hidden when there is no entry button). */
    fun reveal() {
        if (!hasEntry) {
            transitionTo(OverlayMode.Expanded)
            return
        }
        if (mode == OverlayMode.Hidden) transitionTo(OverlayMode.Entry)
        expand()
    }

    fun collapse() {
        if (mode == OverlayMode.Expanded) transitionTo(OverlayMode.Entry)
    }

    fun moveEntry(side: EntrySide, fraction: Float) {
        entrySide = side
        entryFraction = fraction.coerceIn(0f, 1f)
        prefs?.edit()?.putString(KEY_ENTRY_SIDE, side.name)?.putFloat(KEY_ENTRY_FRACTION, entryFraction)?.apply()
    }

    fun pinHeight(heightDp: Float?) {
        pinnedHeightDp = heightDp
        prefs?.edit()?.putFloat(KEY_PANEL_HEIGHT, heightDp ?: -1f)?.apply()
    }
}
