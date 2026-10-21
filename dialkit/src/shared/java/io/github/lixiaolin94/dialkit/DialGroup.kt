package io.github.lixiaolin94.dialkit

import androidx.annotation.ColorInt
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import io.github.lixiaolin94.dialkit.model.ControlMeta
import io.github.lixiaolin94.dialkit.model.DialOption
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.formatLabel
import io.github.lixiaolin94.dialkit.model.imageLabel
import io.github.lixiaolin94.dialkit.model.inferRange
import io.github.lixiaolin94.dialkit.model.inferStep
import io.github.lixiaolin94.dialkit.model.parseColor
import io.github.lixiaolin94.dialkit.model.resolvePadAxis

/**
 * A set of live controls, declared with property delegates. The property name is the control key
 * (DialKit's config key) and the label defaults to its Title Case form.
 *
 * ```
 * class CardDials : DialGroup() {
 *     var radius by slider(24f, 0f..64f)          // DialKit: radius: [24, 0, 64]
 *     val scale by slider(1.2f)                   // DialKit: scale: 1.2 (range inferred)
 *     val visible by toggle(true)
 *     val layout by select("grid", "list")        // DialKit: { type: 'select', options }
 *     val tint by color("#a78bfa")                // DialKit: '#a78bfa'
 *     val enter by spring(visualDuration = 0.3f, bounce = 0.2f)
 *     val origin by pad()                         // DialKit: { type: 'pad' }
 *     val shadow by folder(collapsed = true) { ShadowDials() }
 *     val reset by action { resetValues() }
 * }
 * ```
 *
 * Reading a property reads a [DialCell]: View code observes changes with `observe`, and with `dialkit-compose`
 * a composable that reads a dial recomposes when that dial moves.
 */
public abstract class DialGroup {
    private val declared = mutableListOf<DialNode>()
    internal val nodes: List<DialNode> get() = declared

    /** Set by the registry for a registered root group: DialKit's panel-level reset (versions included). */
    internal var resetHook: (() -> Unit)? = null

    /** Set by the store while registered: reads and writes the panel's open state. */
    internal var openHook: OpenHook? = null

    internal fun assignPaths(prefix: String) {
        declared.forEach { it.assignPath(prefix) }
    }

    /** Slider over an explicit range. DialKit: `[default, min, max, step?]`. */
    protected fun slider(
        default: Float,
        range: ClosedFloatingPointRange<Float>,
        step: Float? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Float>> = declare { key ->
        val min = range.start
        val max = range.endInclusive
        SliderNode(key, label ?: formatLabel(key), default, min, max, step ?: inferStep(min, max))
    }.map(get = { it.value }, set = { node, v -> node.value = v })

    /** Slider whose range is inferred from the default. DialKit: a bare number. */
    protected fun slider(
        default: Float,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Float>> = declare { key ->
        val inferred = inferRange(default)
        SliderNode(key, label ?: formatLabel(key), default, inferred.min, inferred.max, inferred.step)
    }.map(get = { it.value }, set = { node, v -> node.value = v })

    /** Integer slider; stored as a number like any DialKit slider. */
    protected fun slider(
        default: Int,
        range: IntRange,
        step: Int = 1,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Int>> = declare { key ->
        SliderNode(
            key, label ?: formatLabel(key), default.toFloat(),
            range.first.toFloat(), range.last.toFloat(), step.toFloat(), isInt = true,
        )
    }.map(get = { Math.round(it.value) }, set = { node, v -> node.value = v.toFloat() })

    /** Off/On toggle. DialKit: a boolean. */
    protected fun toggle(
        default: Boolean,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Boolean>> = declare { key ->
        ToggleNode(key, label ?: formatLabel(key), default)
    }.map(get = { it.state.value }, set = { node, v -> node.state.value = v })

    /**
     * Dropdown over string options (labels are Title Case). [default] defaults to the first option.
     * DialKit: `{ type: 'select', options, default }`.
     */
    protected fun select(
        vararg options: String,
        default: String? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, String>> =
        selectOptions(options.map { DialOption(it) }, default, label)

    /** Dropdown with explicit labels. DialKit: options as `{ value, label }`. */
    protected fun selectOptions(
        options: List<DialOption>,
        default: String? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, String>> = declare { key ->
        require(options.isNotEmpty()) { "select needs at least one option" }
        SelectNode(key, label ?: formatLabel(key), options, default)
    }.map(get = { it.state.value }, set = { node, v -> if (node.options.any { it.value == v }) node.state.value = v })

    /**
     * Image picker (DialKit `{ type: 'image', options, default }`); reads the chosen image's URI as a string, ""
     * for none. Options are URIs Android can open: `android.resource://…`, `content://…`, `file://…`,
     * `file:///android_asset/…`, `http(s)://…` (with the host's INTERNET permission) or `data:` URLs. The panel can
     * also upload one through the system photo picker; it is copied into app storage and read as `file://…`.
     * Load it with `ImageView.setImageURI(Uri.parse(value))` or any image library.
     */
    protected fun image(
        vararg options: String,
        default: String? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, String>> =
        imageOptions(options.map { DialOption(it, imageLabel(it)) }, default, label)

    /** Image picker with explicit labels. DialKit: options as `{ value, label }`. */
    protected fun imageOptions(
        options: List<DialOption>,
        default: String? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, String>> = declare { key ->
        ImageNode(key, label ?: formatLabel(key), options, default)
    }.map(get = { it.state.value }, set = { node, v -> node.state.value = v })

    /** Free text, up to five visible lines. DialKit: `{ type: 'text', default, placeholder }`. */
    protected fun text(
        default: String = "",
        placeholder: String? = null,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, String>> = declare { key ->
        TextNode(key, label ?: formatLabel(key), default, placeholder)
    }.map(get = { it.state.value }, set = { node, v -> node.state.value = v })

    /**
     * Color picker. [default] is any CSS color DialKit accepts: hex, rgb(), hsl(), oklch(), color(display-p3 …).
     * Reads return an sRGB ARGB int (wide-gamut picks are fitted into sRGB); `Color(int)` in Compose.
     * The CSS string itself is what persistence and Copy carry, as in DialKit.
     */
    protected fun color(
        default: String,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Int>> = declare { key ->
        require(parseColor(default) != null) { "color(\"$default\") is not a CSS color DialKit can parse" }
        ColorNode(key, label ?: formatLabel(key), default)
    }.map(get = { it.argb }, set = { node, v -> node.state.value = argbToHex(v) })

    /** Color picker from an ARGB int (stored as sRGB hex). */
    protected fun color(
        @ColorInt default: Int,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, Int>> = color(argbToHex(default), label)

    /** Time-based spring. DialKit: `{ type: 'spring', visualDuration, bounce }`. */
    protected fun spring(
        visualDuration: Float = 0.3f,
        bounce: Float = 0.2f,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, DialTransition>> =
        transition(DialValue.Spring(visualDuration = visualDuration, bounce = bounce), label)

    /** Physics spring. DialKit: `{ type: 'spring', stiffness, damping, mass }`. */
    protected fun physicsSpring(
        stiffness: Float = 200f,
        damping: Float = 25f,
        mass: Float = 1f,
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, DialTransition>> =
        transition(DialValue.Spring(stiffness = stiffness, damping = damping, mass = mass), label)

    /** Cubic-bezier easing; [duration] in seconds. DialKit: `{ type: 'easing', duration, ease }`. */
    protected fun easing(
        duration: Float = 0.3f,
        ease: FloatArray = floatArrayOf(0.42f, 0f, 0.58f, 1f),
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, DialTransition>> {
        require(ease.size == 4) { "ease needs four control-point values" }
        return transition(DialValue.Easing(duration, ease.toList()), label)
    }

    private fun transition(default: DialValue, label: String?) =
        PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, DialTransition>> { _, property ->
            val node = TransitionNode(property.name, label ?: formatLabel(property.name), default)
            declared += node
            ReadOnlyProperty { _, _ -> DialTransition(node.state.value) }
        }

    /**
     * Two-axis pad; positive y points up. DialKit: `{ type: 'pad', x, y, labels }`.
     */
    protected fun pad(
        x: DialAxis = DialAxis(),
        y: DialAxis = DialAxis(),
        xLabel: String = "X",
        yLabel: String = "Y",
        label: String? = null,
    ): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, DialPoint>> = declare { key ->
        PadNode(
            key, label ?: formatLabel(key),
            resolvePadAxis(x.default, x.min, x.max, x.step),
            resolvePadAxis(y.default, y.min, y.max, y.step),
            xLabel, yLabel,
        )
    }.map(
        get = { node -> node.state.value.let { DialPoint(it.x, it.y) } },
        set = { node, v -> node.set(DialValue.Point(v.x, v.y)) },
    )

    /**
     * Nested group. DialKit: a nested object, with `_collapsed: true` mapped to [collapsed].
     * Child paths are `"<key>.<childKey>"`.
     */
    protected fun <G : DialGroup> folder(
        collapsed: Boolean = false,
        label: String? = null,
        factory: () -> G,
    ): PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, G>> =
        PropertyDelegateProvider { _, property ->
            val child = factory()
            declared += FolderNode(property.name, label ?: formatLabel(property.name), child, !collapsed)
            ReadOnlyProperty { _, _ -> child }
        }

    /**
     * Button that stores no value. Tapping it runs [onClick] and the panel's `onAction(path)`.
     * DialKit: `{ type: 'action', label }`.
     */
    protected fun action(
        label: String? = null,
        onClick: (() -> Unit)? = null,
    ): PropertyDelegateProvider<DialGroup, ReadOnlyProperty<DialGroup, DialAction>> =
        PropertyDelegateProvider { _, property ->
            val node = ActionNode(property.name, label ?: formatLabel(property.name), onClick)
            declared += node
            val action = DialAction(node)
            ReadOnlyProperty { _, _ -> action }
        }

    private fun <N : DialNode> declare(create: (key: String) -> N): NodeProvider<N> = NodeProvider { key ->
        create(key).also { declared += it }
    }

    /** Current values keyed by full path, in declaration order. */
    public fun values(): Map<String, DialValue> = buildMap {
        walk { node -> node.currentValue()?.let { put(node.path, it) } }
    }

    /** Control tree in DialKit's `ControlMeta` shape. */
    public fun controls(): List<ControlMeta> = nodes.map { it.meta() }

    /** Sets one value by full path (`"shadow.blur"`). Unknown paths and mismatched types are ignored. */
    public fun setValue(path: String, value: DialValue) {
        walk { node -> if (node.path == path) node.applyValue(value) }
    }

    /**
     * Sets several values at once (DialKit `setValues`). Keys are full paths (`"shadow.blur"`) or folder keys
     * holding a nested map (`"shadow" to mapOf("blur" to 12)`). Values may be [DialValue]s or plain Kotlin values:
     * numbers for sliders, booleans for toggles, strings for select / text / color (CSS), [DialPoint] for pads,
     * [DialTransition] for curves. Unknown paths, actions and mismatched types are ignored, as in DialKit.
     * Observers see one change.
     */
    public fun setValues(values: Map<String, Any?>) {
        applyUpdates(values, "")
    }

    /** [setValues] with pairs: `setValues("radius" to 32, "shadow.blur" to 12)`. */
    public fun setValues(vararg values: Pair<String, Any?>): Unit = setValues(values.toMap())

    private fun applyUpdates(values: Map<*, *>, prefix: String) {
        for ((key, value) in values) {
            val path = prefix + key
            when (value) {
                null -> Unit
                is Map<*, *> -> applyUpdates(value, "$path.")
                else -> dialValueOf(value)?.let { setValue(path, it) }
            }
        }
    }

    /**
     * Whether this panel is open (DialKit `getOpen`): its section when several panels are listed, or the panel
     * itself when it is the only one. null when the group is not registered (and always on dialkit-noop).
     */
    public fun getOpen(): Boolean? = openHook?.isOpen()

    /**
     * Opens or closes this panel (DialKit `setOpen`). The only panel opens and closes the whole overlay; with
     * several panels this sets its section, and opening also brings the overlay up.
     */
    public fun setOpen(open: Boolean) {
        openHook?.setOpen(open)
    }

    /**
     * Restores every control to its declared default. On a registered panel this also leaves the active
     * version and makes the defaults the base values, as DialKit's `resetValues()` does.
     */
    public fun resetValues() {
        resetHook?.invoke() ?: walk { it.resetToDefault() }
    }
}

/** Handle for an action control. */
public class DialAction internal constructor(private val node: ActionNode) {
    public val path: String get() = node.path
    public val label: String get() = node.label
}

internal fun interface NodeProvider<N : DialNode> {
    fun create(key: String): N
}

private inline fun <N : DialNode, T> NodeProvider<N>.map(
    crossinline get: (N) -> T,
    crossinline set: (N, T) -> Unit,
): PropertyDelegateProvider<DialGroup, ReadWriteProperty<DialGroup, T>> =
    PropertyDelegateProvider { _, property ->
        val node = create(property.name)
        object : ReadWriteProperty<DialGroup, T> {
            override fun getValue(thisRef: DialGroup, property: KProperty<*>): T = get(node)
            override fun setValue(thisRef: DialGroup, property: KProperty<*>, value: T) = set(node, value)
        }
    }

private fun argbToHex(argb: Int): String {
    val a = argb ushr 24
    val rgb = String.format("#%06x", argb and 0xFFFFFF)
    return if (a == 0xFF) rgb else rgb + String.format("%02x", a)
}

internal interface OpenHook {
    fun isOpen(): Boolean
    fun setOpen(open: Boolean)
}

private fun dialValueOf(value: Any): DialValue? = when (value) {
    is DialValue -> value
    is Number -> DialValue.Num(value.toFloat())
    is Boolean -> DialValue.Bool(value)
    is String -> DialValue.Str(value)
    is DialPoint -> DialValue.Point(value.x, value.y)
    is DialTransition -> value.value
    else -> null
}
