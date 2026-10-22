package io.github.lixiaolin94.dialkit.compose

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.WeakHashMap
import io.github.lixiaolin94.dialkit.DialCell
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialKit
import io.github.lixiaolin94.dialkit.DialPanelView
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.DialRegistration
import io.github.lixiaolin94.dialkit.DialTransition

/**
 * Registers a dial panel while the caller stays in composition and returns its group. Reading a dial in a
 * composable subscribes that composable to exactly that dial. DialKit: `useDialKit(name, config, options)`.
 *
 * @param id panels with the same id share one set of values; defaults to [name]. Values outlive the
 *   composition (rotation, navigation) for the life of the process.
 * @param persist also keep values (and, by default, versions) across app restarts; see [DialPersist].
 */
@Composable
public fun <G : DialGroup> rememberDialKit(
    name: String,
    id: String? = null,
    defaultCollapsed: Boolean = false,
    persist: DialPersist? = null,
    onAction: ((path: String) -> Unit)? = null,
    factory: () -> G,
): G {
    ComposeBridge.install()
    val holder = remember(id ?: name) { Holder(DialKit.register(name, id, defaultCollapsed, persist, null, factory)) }
    SideEffect { holder.registration.setOnAction(onAction) }
    return holder.registration.group
}

private class Holder<G : DialGroup>(val registration: DialRegistration<G>) : RememberObserver {
    override fun onRemembered() = Unit
    override fun onForgotten() = registration.release()
    override fun onAbandoned() = registration.release()
}

/**
 * Mirrors each [DialCell] into a Compose state on first read, so composition tracks dials precisely and a
 * change invalidates only the composables that read that dial.
 */
private object ComposeBridge {
    private var installed = false
    private val mirrors = WeakHashMap<DialCell<*>, MutableIntState>()

    fun install() {
        if (installed) return
        installed = true
        DialCell.readHook = { cell -> mirrors.getOrPut(cell) { mutableIntStateOf(0) }.intValue }
        DialCell.writeHook = { cell -> mirrors[cell]?.let { it.intValue++ } }
    }
}

/**
 * The transition as a Compose spec: springs via motion's mapping (SOURCE transition-math.ts), easing as
 * `tween(duration, CubicBezierEasing(ease))`.
 */
public fun <T> DialTransition.toAnimationSpec(visibilityThreshold: T? = null): FiniteAnimationSpec<T> {
    val e = ease
    val ms = durationMillis
    return if (e != null && ms != null) {
        tween(durationMillis = ms.toInt(), easing = CubicBezierEasing(e[0], e[1], e[2], e[3]))
    } else {
        spring(dampingRatio!!, stiffness!!, visibilityThreshold)
    }
}

/**
 * A group's panel as part of the screen instead of the floating overlay (DialKit `mode: 'inline'`): the group's
 * name, the toolbar and its controls, full width. In a scrolling column it grows with its controls; given a fixed
 * height it scrolls inside. While shown, the group is left out of the floating overlay. Draws nothing with
 * `dialkit-noop`. See `DialPanelView`.
 *
 * ```
 * val dials = rememberDialKit("Card") { CardDials() }
 * Column(Modifier.verticalScroll(rememberScrollState())) {
 *     Preview(dials)
 *     DialKitPanel(dials)
 * }
 * ```
 */
@Composable
public fun DialKitPanel(group: DialGroup, modifier: Modifier = Modifier) {
    AndroidView(factory = { DialPanelView(it) }, modifier = modifier, update = { it.group = group })
}
