package io.github.lixiaolin94.dialkit.overlay

import android.app.Activity
import android.os.Build
import android.view.ViewGroup
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.RequiresApi
import io.github.lixiaolin94.dialkit.DialKitConfig
import io.github.lixiaolin94.dialkit.ui.OverlayRoot

/** The overlay as installed into one Activity: layer in the DecorView, wrapped Window.Callback, back handling. */
internal class ActivityAttachment(private val activity: Activity, private val config: DialKitConfig) {
    private val layer = DialKitLayer(activity)
    private var root: OverlayRoot? = null
    private var callbackInstalled = false
    private var backCallback: Any? = null
    private val modeListener: (OverlayMode) -> Unit = { onModeChanged(it) }

    fun attach() {
        placeLayer()
        installCallback()
        OverlayController.removeListener(modeListener)
        OverlayController.addListener(modeListener)
        onModeChanged(OverlayController.mode)
    }

    /**
     * Keeps the layer the last child of the current DecorView. A parent alone is not proof it is in the window:
     * re-add it when it hangs off another view, or when the DecorView is attached but the layer is not. On first
     * resume the DecorView itself is not attached yet; it attaches the layer when the window is added.
     */
    private fun placeLayer() {
        val decor = activity.window.decorView as ViewGroup
        val stale = layer.parent !== decor || (decor.isAttachedToWindow && !layer.isAttachedToWindow)
        if (stale) {
            (layer.parent as? ViewGroup)?.removeView(layer)
            decor.addView(layer, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else if (decor.getChildAt(decor.childCount - 1) !== layer) {
            layer.bringToFront()
        }
    }

    fun detach() {
        OverlayController.removeListener(modeListener)
        unregisterBack()
        (layer.parent as? ViewGroup)?.removeView(layer)
        root?.release()
        root = null
    }

    private fun installCallback() {
        if (callbackInstalled) return
        val window = activity.window
        val current = window.callback ?: return
        val detector = config.toggleGesture.createDetector(activity) {
            OverlayController.toggleEntry()
        }
        window.callback = OverlayWindowCallback(current, detector, config.keyShortcut)
        callbackInstalled = true
    }

    private fun onModeChanged(mode: OverlayMode) {
        if (mode != OverlayMode.Hidden) placeLayer()
        if (mode != OverlayMode.Hidden && root == null) {
            root = OverlayRoot(activity, layer, config, onHiddenSettled = ::removeRoot).also {
                layer.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        root?.onMode(mode)
        if (mode == OverlayMode.Expanded) registerBack() else unregisterBack()
    }

    private fun removeRoot() {
        if (OverlayController.mode != OverlayMode.Hidden) return
        root?.let { layer.removeView(it) }
        root = null
        layer.entryBounds.setEmpty()
    }

    private fun registerBack() {
        if (Build.VERSION.SDK_INT < 33 || backCallback != null) return
        backCallback = Api33.register(activity)
    }

    private fun unregisterBack() {
        val callback = backCallback ?: return
        if (Build.VERSION.SDK_INT >= 33) Api33.unregister(activity, callback)
        backCallback = null
    }

    @RequiresApi(33)
    private object Api33 {
        fun register(activity: Activity): Any {
            val callback = OnBackInvokedCallback { OverlayController.back() }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY, callback)
            return callback
        }

        fun unregister(activity: Activity, callback: Any) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback as OnBackInvokedCallback)
        }
    }
}
