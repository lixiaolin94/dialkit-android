package io.github.lixiaolin94.dialkit

import android.app.Activity
import android.app.Application
import androidx.lifecycle.Lifecycle

/**
 * No-op DialKit for release builds: the same API as the full library, no overlay, no persistence. Panels
 * still build their groups, so dials read their declared defaults.
 */
public object DialKit {
    public val isInstalled: Boolean get() = false

    public fun configure(block: DialKitConfig.() -> Unit): Unit = Unit
    public fun install(application: Application): Unit = Unit
    public fun showEntry(): Unit = Unit
    public fun hideEntry(): Unit = Unit
    public fun toggleEntry(): Unit = Unit
    public fun open(): Unit = Unit
    public fun close(): Unit = Unit

    public fun <G : DialGroup> register(
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): DialRegistration<G> = DialRegistration(factory().also { it.assignPaths("") })

    public fun <G : DialGroup> panel(
        activity: Activity,
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): G = register(name, id, defaultCollapsed, persist, onAction, factory).group

    public fun <G : DialGroup> panel(
        lifecycle: Lifecycle,
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): G = register(name, id, defaultCollapsed, persist, onAction, factory).group
}

public class DialRegistration<G : DialGroup> internal constructor(public val group: G) {
    public fun setOnAction(onAction: ((path: String) -> Unit)?): Unit = Unit
    public fun release(): Unit = Unit
}

public fun <G : DialGroup> G.observe(onChange: (G) -> Unit): () -> Unit {
    onChange(this)
    return {}
}

public fun <G : DialGroup> G.observe(activity: Activity, onChange: (G) -> Unit) {
    onChange(this)
}

public fun <G : DialGroup> G.observe(lifecycle: Lifecycle, onChange: (G) -> Unit) {
    onChange(this)
}
