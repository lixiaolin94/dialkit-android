package io.github.lixiaolin94.dialkit

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.github.lixiaolin94.dialkit.store.DialPanel
import io.github.lixiaolin94.dialkit.store.DialStore
import io.github.lixiaolin94.dialkit.ui.DialPalette
import io.github.lixiaolin94.dialkit.ui.DialUi
import io.github.lixiaolin94.dialkit.ui.EmbeddedPanel
import io.github.lixiaolin94.dialkit.ui.EmbeddedPopupHost
import io.github.lixiaolin94.dialkit.ui.MATCH
import io.github.lixiaolin94.dialkit.ui.dp
import io.github.lixiaolin94.dialkit.ui.label

/**
 * A group's panel as part of a screen — an Activity, a Fragment or any layout — rather than in the floating overlay.
 * DialKit: `<DialRoot mode="inline">`, here per group.
 *
 * ```
 * val dials = DialKit.panel(this, "Card") { CardDials() }   // register as usual
 * findViewById<DialPanelView>(R.id.tuning).group = dials
 * ```
 *
 * The panel shows the group's name, the toolbar (versions, Import, Copy) and its controls, full width on the panel
 * background. With a wrap-content height it grows with its controls, for a page that scrolls; with a bounded height
 * the header stays and the controls scroll inside. While it is attached the group is left out of the floating
 * overlay, its `getOpen()` is true and `setOpen()` does nothing.
 *
 * Works with the full `dialkit` artifact in any build type (the overlay itself still installs only in debuggable
 * builds). The `dialkit-noop` twin draws nothing.
 */
public class DialPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    /** The group to show. It must be registered (`DialKit.panel`, `DialKit.register` or `rememberDialKit`). */
    public var group: DialGroup? = null
        set(value) {
            if (field === value) return
            field = value
            bind()
        }

    private val palette = DialPalette.resolve(context, DialKit.config.theme)
    private var host: EmbeddedPopupHost? = null
    private var shown: DialPanel? = null
    private var stopListening: (() -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        DialStore.ensureInit(context)
        host = EmbeddedPopupHost.acquire(this, palette)
        stopListening = DialStore.panels.listen { bind() }
        bind()
    }

    override fun onDetachedFromWindow() {
        stopListening?.invoke()
        stopListening = null
        show(null)
        host?.let(EmbeddedPopupHost::release)
        host = null
        super.onDetachedFromWindow()
    }

    private fun bind() {
        val g = group
        val panel = if (g == null) null else DialStore.panels.peek.firstOrNull { it.group === g }
        if (panel == null && g != null && isAttachedToWindow) {
            showUnregistered()
            return
        }
        show(panel)
    }

    private fun show(panel: DialPanel?) {
        if (panel === shown && (panel != null || childCount == 0)) return
        shown?.let(DialStore::unembed)
        shown = null
        removeAllViews()
        val popups = host?.popups ?: return
        if (panel == null || !isAttachedToWindow) return
        DialStore.embed(panel)
        shown = panel
        addView(EmbeddedPanel(DialUi(context, palette, popups) { }, panel), LayoutParams(MATCH, MATCH))
    }

    private fun showUnregistered() {
        show(null)
        val text = context.getString(R.string.dialkit_embedded_unregistered)
        addView(
            label(context, text, palette.textTertiary).apply {
                maxLines = 3
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(palette.glassBg)
            },
            LayoutParams(MATCH, LayoutParams.WRAP_CONTENT),
        )
    }
}
