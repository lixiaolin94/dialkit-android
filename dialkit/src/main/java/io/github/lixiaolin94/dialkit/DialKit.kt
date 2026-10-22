package io.github.lixiaolin94.dialkit

import io.github.lixiaolin94.dialkit.R
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.WeakHashMap
import io.github.lixiaolin94.dialkit.overlay.ActivityTracker
import io.github.lixiaolin94.dialkit.overlay.OverlayController
import io.github.lixiaolin94.dialkit.overlay.OverlayMode
import io.github.lixiaolin94.dialkit.store.DialPanel
import io.github.lixiaolin94.dialkit.store.DialStore
import io.github.lixiaolin94.dialkit.store.ImportSource
import io.github.lixiaolin94.dialkit.ui.Haptics

/**
 * Entry point. Installed automatically in debuggable builds; call [install] yourself if you removed the
 * `DialKitInitProvider` from the merged manifest.
 *
 * Show/hide the entry button with the configured gesture (three-finger double tap), the keyboard shortcut,
 * or `adb shell am broadcast -a <applicationId>.dialkit.TOGGLE -p <applicationId>` (extra `cmd`:
 * toggle | show | hide | open | close | import, the last with `--es json '<DialKit JSON>'`).
 */
public object DialKit {
    internal val config = DialKitConfig()
    private var tracker: ActivityTracker? = null

    public val isInstalled: Boolean get() = tracker != null

    /**
     * Adjusts options. Call it before the first Activity resumes (Application.onCreate or the launcher Activity's
     * onCreate): the start-up options (entry visibility, [DialKitConfig.entryPosition], [DialKitConfig.defaultOpen])
     * are read then; the rest take effect for Activities attached afterwards.
     */
    public fun configure(block: DialKitConfig.() -> Unit) {
        config.block()
        Haptics.enabled = config.haptics
    }

    public fun install(application: Application) {
        if (tracker != null) return
        OverlayController.init(application, config)
        DialStore.init(application)
        val t = ActivityTracker(config)
        application.registerActivityLifecycleCallbacks(t)
        tracker = t
        registerAdbReceiver(application)
    }

    public fun showEntry() {
        if (OverlayController.mode == OverlayMode.Hidden) OverlayController.transitionTo(OverlayMode.Entry)
    }

    public fun hideEntry(): Unit = OverlayController.transitionTo(OverlayMode.Hidden)
    public fun toggleEntry(): Unit = OverlayController.toggleEntry()

    /** Expands the panel, showing the entry first if needed (or directly, with `entryButton = false`). */
    public fun open(): Unit = OverlayController.reveal()

    public fun close(): Unit = OverlayController.collapse()

    /**
     * Registers a panel and returns a handle; call [DialRegistration.release] when the screen goes away.
     * Panels with the same id share one set of values; [id] defaults to [name]. Values outlive the
     * registration (rotation, navigation) for the life of the process.
     *
     * @param persist also keep values (and, by default, saved versions) across app restarts, in SharedPreferences
     *   with DialKit's JSON shape; see [DialPersist]. null (the default) keeps them for the process only.
     */
    public fun <G : DialGroup> register(
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): DialRegistration<G> {
        val panel = DialStore.acquire(id ?: name, name, defaultCollapsed, persist, factory)
        if (onAction != null) panel.onAction = onAction
        return DialRegistration(panel)
    }

    /** [register] for an Activity; released when it is destroyed. Works for any `android.app.Activity`. */
    public fun <G : DialGroup> panel(
        activity: Activity,
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): G {
        DialStore.ensureInit(activity) // a host that removed the init provider and never called install()
        val registration = register(name, id, defaultCollapsed, persist, onAction, factory)
        ActivityDisposer.onDestroy(activity) { registration.release() }
        return registration.group
    }

    /**
     * [register] tied to an androidx [Lifecycle] (e.g. a Fragment's `viewLifecycleOwner.lifecycle`).
     * Needs androidx.lifecycle on the host's classpath; DialKit itself does not bring it.
     */
    public fun <G : DialGroup> panel(
        lifecycle: Lifecycle,
        name: String,
        id: String? = null,
        defaultCollapsed: Boolean = false,
        persist: DialPersist? = null,
        onAction: ((path: String) -> Unit)? = null,
        factory: () -> G,
    ): G {
        val registration = register(name, id, defaultCollapsed, persist, onAction, factory)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = registration.release()
        })
        return registration.group
    }

    private fun registerAdbReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra("cmd") ?: "toggle") {
                    "show" -> showEntry()
                    "hide" -> hideEntry()
                    "open" -> open()
                    "close" -> close()
                    // DESIGN: `--es json '<DialKit JSON>' [--es id <panel id>]`, same rules as the Import button.
                    "import" -> DialStore.importText(intent.getStringExtra("id"), intent.getStringExtra("json"), ImportSource.Adb)
                    // DESIGN: `--es cmd dump [--es id <panel id>]`, Copy's text to logcat (see DialStore.dump).
                    "dump" -> DialStore.dump(intent.getStringExtra("id"))
                    else -> toggleEntry()
                }
            }
        }
        val filter = IntentFilter("${context.packageName}.dialkit.TOGGLE")
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}

/** A registered panel. Release it once; extra calls are ignored. */
public class DialRegistration<G : DialGroup> internal constructor(private val panel: DialPanel) {
    private var released = false

    @Suppress("UNCHECKED_CAST")
    public val group: G get() = panel.group as G

    /** Replaces the panel's `onAction(path)` callback (DialKit `onAction`). */
    public fun setOnAction(onAction: ((path: String) -> Unit)?) {
        panel.onAction = onAction
    }

    public fun release() {
        if (released) return
        released = true
        DialStore.release(panel)
    }
}

/**
 * Calls [onChange] now and after changes to this group's values (coalesced to one call per main-thread
 * turn) until the returned function is invoked.
 */
public fun <G : DialGroup> G.observe(onChange: (G) -> Unit): () -> Unit {
    val group = this
    val main = Handler(Looper.getMainLooper())
    var pending = false
    var disposed = false
    val dispatch = Runnable {
        pending = false
        if (!disposed) onChange(group)
    }
    val unsubscribe = valueCells().map { cell ->
        cell.listen {
            if (!pending) {
                pending = true
                main.post(dispatch)
            }
        }
    }
    onChange(group)
    return {
        disposed = true
        main.removeCallbacks(dispatch)
        unsubscribe.forEach { it() }
    }
}

/** [observe] until [activity] is destroyed. */
public fun <G : DialGroup> G.observe(activity: Activity, onChange: (G) -> Unit) {
    val dispose = observe(onChange)
    ActivityDisposer.onDestroy(activity, dispose)
}

/** [observe] until [lifecycle] is destroyed (needs androidx.lifecycle on the host's classpath). */
public fun <G : DialGroup> G.observe(lifecycle: Lifecycle, onChange: (G) -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) return
    val dispose = observe(onChange)
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) = dispose()
    })
}

/** Runs cleanups when an Activity is destroyed, with plain platform callbacks (no androidx). */
internal object ActivityDisposer : Application.ActivityLifecycleCallbacks {
    private val pending = WeakHashMap<Activity, MutableList<() -> Unit>>()
    private val registered = HashSet<Application>()

    fun onDestroy(activity: Activity, action: () -> Unit) {
        val app = activity.application
        if (registered.add(app)) app.registerActivityLifecycleCallbacks(this)
        pending.getOrPut(activity) { mutableListOf() } += action
    }

    override fun onActivityDestroyed(activity: Activity) {
        pending.remove(activity)?.forEach { it() }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
