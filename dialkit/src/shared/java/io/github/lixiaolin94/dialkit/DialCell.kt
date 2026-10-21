package io.github.lixiaolin94.dialkit

/**
 * A main-thread observable value: the core's replacement for Compose snapshot state, so the library has no
 * Compose dependency. UI adapters hook reads and writes (see `dialkit-compose`, which mirrors each cell into
 * a Compose state so a composable that reads a dial recomposes when exactly that dial changes).
 */
public class DialCell<T> internal constructor(initial: T) {
    private var current: T = initial
    private val listeners = ArrayList<(T) -> Unit>(2)

    public var value: T
        get() {
            readHook?.invoke(this)
            return current
        }
        set(v) {
            if (v == current) return
            current = v
            writeHook?.invoke(this)
            // Copy: a listener may remove itself.
            for (l in listeners.toTypedArray()) l(v)
            DialCells.notifyChanged(this)
        }

    /** Current value without notifying the read hook (for the library's own bookkeeping). */
    internal val peek: T get() = current

    internal fun listen(listener: (T) -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    public companion object {
        /** Called on every read; an adapter uses it to subscribe the current reader. */
        @JvmStatic public var readHook: ((DialCell<*>) -> Unit)? = null

        /** Called after every change, before listeners. */
        @JvmStatic public var writeHook: ((DialCell<*>) -> Unit)? = null
    }
}

/** Library-wide change fan-out, used by the store to auto-save and by `observe`. */
internal object DialCells {
    private val owners = java.util.IdentityHashMap<DialCell<*>, (DialCell<*>) -> Unit>()

    fun own(cell: DialCell<*>, onChange: (DialCell<*>) -> Unit) {
        owners[cell] = onChange
    }

    fun disown(cell: DialCell<*>) {
        owners.remove(cell)
    }

    fun notifyChanged(cell: DialCell<*>) {
        owners[cell]?.invoke(cell)
    }
}
