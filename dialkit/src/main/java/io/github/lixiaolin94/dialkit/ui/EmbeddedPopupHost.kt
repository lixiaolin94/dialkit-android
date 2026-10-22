package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import io.github.lixiaolin94.dialkit.R
import io.github.lixiaolin94.dialkit.overlay.OverlayInsets
import kotlin.math.max

/**
 * DESIGN: the popup layer for panels shown inside a page (`DialPanelView`). Dropdowns, the version and Import menus
 * and the colour / image pickers must float above the page, so one full-window layer per window is appended to the
 * DecorView while any embedded panel in it is attached, independent of the floating overlay (which may not be
 * installed at all). It takes no touches until a popup is open; then an outside tap dismisses it, as in the overlay.
 * Back / Escape close an open popup first: an OnBackInvokedCallback on API 33+ and key events below (and for hosts
 * that have not opted into predictive back). Popups are clamped to the safe area, keyboard included.
 */
@SuppressLint("ViewConstructor")
internal class EmbeddedPopupHost private constructor(context: Context, palette: DialPalette) : FrameLayout(context) {
    val popups = PopupLayer(context, palette)
    private var users = 0
    private var insets = OverlayInsets()
    private var backCallback: Any? = null

    init {
        addView(popups, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        popups.onChange { onPopupsChanged() }
    }

    override fun onApplyWindowInsets(windowInsets: WindowInsets): WindowInsets {
        refreshInsets(windowInsets)
        return windowInsets
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        rootWindowInsets?.let { refreshInsets(it) }
        val i = insets
        popups.viewport.set(i.left, i.top, (r - l) - i.right, (b - t) - max(i.bottom, i.ime))
        super.onLayout(changed, l, t, r, b)
    }

    private fun refreshInsets(wi: WindowInsets) {
        val next = OverlayInsets.of(wi)
        if (next == insets) return
        // SOURCE Escape cancels a slider's input; on API 33+ Back goes to the IME first, which only hides itself.
        if (insets.ime > 0 && next.ime == 0) (rootView.findFocus()?.parent as? SliderView)?.onImeHidden()
        insets = next
        popups.requestLayout()
        requestLayout()
    }

    private fun onPopupsChanged() {
        if (popups.isOpen) {
            // Key fallback: hold focus so Back reaches dispatchKeyEvent (a field inside the popup keeps it in here).
            isFocusableInTouchMode = true
            if (!hasFocus()) requestFocus()
            if (Build.VERSION.SDK_INT >= 33 && backCallback == null) backCallback = Api33.register(this) { popups.dismiss() }
        } else {
            if (hasFocus()) clearFocus()
            isFocusableInTouchMode = false
            backCallback?.let { if (Build.VERSION.SDK_INT >= 33) Api33.unregister(it) }
            backCallback = null
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (popups.isOpen && (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE)) {
            if (event.action == KeyEvent.ACTION_UP) popups.dismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @RequiresApi(33)
    private object Api33 {
        fun register(view: View, onBack: () -> Unit): Any? {
            val dispatcher = view.findOnBackInvokedDispatcher() ?: return null
            val callback = OnBackInvokedCallback { onBack() }
            dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            return callback to dispatcher
        }

        fun unregister(registration: Any) {
            val pair = registration as Pair<*, *>
            (pair.second as OnBackInvokedDispatcher).unregisterOnBackInvokedCallback(pair.first as OnBackInvokedCallback)
        }
    }

    companion object {
        /** The layer for [anchor]'s window, created on first use; pair with [release]. Null when not attached. */
        fun acquire(anchor: View, palette: DialPalette): EmbeddedPopupHost? {
            val decor = anchor.rootView as? ViewGroup ?: return null
            val host = decor.getTag(R.id.dialkit_popup_host) as? EmbeddedPopupHost
                ?: EmbeddedPopupHost(anchor.context, palette).also {
                    decor.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    // Above anything elevated in the page; no background, so no shadow of its own.
                    it.translationZ = 64f * anchor.resources.displayMetrics.density
                    decor.setTag(R.id.dialkit_popup_host, it)
                }
            host.users++
            return host
        }

        fun release(host: EmbeddedPopupHost) {
            if (--host.users > 0) return
            host.popups.dismiss()
            val decor = host.parent as? ViewGroup ?: return
            // Callers release from onDetachedFromWindow, which runs while the DecorView is still walking its
            // children to detach them (an Activity being destroyed); removing a child of the DecorView there
            // shrinks that walk and crashes it. Remove on the next loop instead, unless a new user arrived.
            decor.post {
                if (host.users > 0 || host.parent !== decor) return@post
                if (decor.getTag(R.id.dialkit_popup_host) === host) decor.setTag(R.id.dialkit_popup_host, null)
                decor.removeView(host)
            }
        }
    }
}
