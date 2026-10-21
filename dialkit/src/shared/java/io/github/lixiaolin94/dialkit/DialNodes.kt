package io.github.lixiaolin94.dialkit

import io.github.lixiaolin94.dialkit.model.ControlMeta
import io.github.lixiaolin94.dialkit.model.ControlType
import io.github.lixiaolin94.dialkit.model.DialOption
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.Gamut
import io.github.lixiaolin94.dialkit.model.PadAxis
import io.github.lixiaolin94.dialkit.model.colorToRgb
import io.github.lixiaolin94.dialkit.model.fitGamut
import io.github.lixiaolin94.dialkit.model.jsNumber
import io.github.lixiaolin94.dialkit.model.parseColor
import io.github.lixiaolin94.dialkit.model.roundValue
import io.github.lixiaolin94.dialkit.model.snapPadAxis

/** One declared control. Each node owns its [DialCell]s, so reads can be tracked and writes are observable. */
internal sealed class DialNode(val key: String, val label: String) {
    var path: String = key
        private set

    open fun assignPath(prefix: String) {
        path = if (prefix.isEmpty()) key else "$prefix.$key"
    }

    abstract fun meta(): ControlMeta
}

internal class SliderNode(
    key: String,
    label: String,
    val default: Float,
    val min: Float,
    val max: Float,
    val step: Float,
    val isInt: Boolean = false,
) : DialNode(key, label) {
    val cell = DialCell(roundValue(default, step, min, max))

    var value: Float
        get() = cell.value
        set(v) {
            cell.value = roundValue(v, step, min, max)
        }

    override fun meta() = ControlMeta(ControlType.SLIDER, path, label, min = min, max = max, step = step)
}

internal class ToggleNode(key: String, label: String, val default: Boolean) : DialNode(key, label) {
    val state: DialCell<Boolean> = DialCell(default)
    override fun meta() = ControlMeta(ControlType.TOGGLE, path, label)
}

internal class SelectNode(key: String, label: String, val options: List<DialOption>, default: String?) : DialNode(key, label) {
    /** SOURCE: with no default, the first option is selected. */
    val default: String = default ?: options.firstOrNull()?.value.orEmpty()
    val state: DialCell<String> = DialCell(this.default)
    override fun meta() = ControlMeta(ControlType.SELECT, path, label, options = options)
}

/** SOURCE: the value is the image's URL (any string); no default means the first option, or "" when there is none. */
internal class ImageNode(key: String, label: String, val options: List<DialOption>, default: String?) : DialNode(key, label) {
    val default: String = default ?: options.firstOrNull()?.value.orEmpty()
    val state: DialCell<String> = DialCell(this.default)

    /** This session's uploads, offered again in the picker (SOURCE `uploaded`). */
    val uploaded = ArrayList<DialOption>()
    override fun meta() = ControlMeta(ControlType.IMAGE, path, label, options = options)
}

internal class TextNode(key: String, label: String, val default: String, val placeholder: String?) : DialNode(key, label) {
    val state: DialCell<String> = DialCell(default)
    override fun meta() = ControlMeta(ControlType.TEXT, path, label, placeholder = placeholder)
}

/** Stores the CSS string (DialKit's value); exposes it as an sRGB ARGB int for View and Compose code. */
internal class ColorNode(key: String, label: String, val default: String) : DialNode(key, label) {
    val state: DialCell<String> = DialCell(default)
    private var cachedCss: String? = null
    private var cachedArgb = 0
    val argb: Int
        get() {
            val css = state.value
            if (css != cachedCss) {
                cachedArgb = cssToArgb(css)
                cachedCss = css
            }
            return cachedArgb
        }
    override fun meta() = ControlMeta(ControlType.COLOR, path, label)
}

/** DialKit `CurveMode`, persisted at `<path>.__mode`. */
internal enum class CurveMode(val wire: String) {
    Easing("easing"), Simple("simple"), Advanced("advanced");

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.wire == wire }
        fun of(value: DialValue) = when {
            value is DialValue.Easing -> Easing
            value is DialValue.Spring && value.isPhysics -> Advanced
            else -> Simple
        }
    }
}

internal class TransitionNode(key: String, label: String, val default: DialValue) : DialNode(key, label) {
    val state: DialCell<DialValue> = DialCell(default)
    val mode: DialCell<CurveMode> = DialCell(CurveMode.of(default))
    val open: DialCell<Boolean> = DialCell(true) // SOURCE TransitionControl: Folder defaultOpen

    /** SOURCE TransitionControl.tsx: per-mode caches so switching back restores previous edits. */
    val cache: MutableMap<CurveMode, DialValue> = mutableMapOf(
        CurveMode.Easing to (default as? DialValue.Easing ?: DialValue.Easing(0.3f, listOf(1f, -0.4f, 0.5f, 1f))),
        CurveMode.Simple to ((default as? DialValue.Spring)?.takeIf { it.visualDuration != null } ?: DialValue.Spring(visualDuration = 0.3f, bounce = 0.2f)),
        CurveMode.Advanced to ((default as? DialValue.Spring)?.takeIf { it.stiffness != null } ?: DialValue.Spring(stiffness = 200f, damping = 25f, mass = 1f)),
    )

    fun update(value: DialValue) {
        cache[mode.value] = value
        state.value = value
    }

    fun switchMode(next: CurveMode) {
        mode.value = next
        state.value = cache.getValue(next)
    }

    override fun meta() = ControlMeta(ControlType.TRANSITION, path, label)
}

internal class PadNode(
    key: String,
    label: String,
    val x: PadAxis,
    val y: PadAxis,
    val xLabel: String,
    val yLabel: String,
) : DialNode(key, label) {
    val default = DialValue.Point(x.default, y.default)
    val state: DialCell<DialValue.Point> = DialCell(default)

    fun set(value: DialValue.Point) {
        state.value = DialValue.Point(snapPadAxis(value.x, x), snapPadAxis(value.y, y))
    }

    override fun meta() = ControlMeta(ControlType.PAD, path, label)
}

internal class FolderNode(
    key: String,
    label: String,
    val group: DialGroup,
    val defaultOpen: Boolean,
) : DialNode(key, label) {
    val open: DialCell<Boolean> = DialCell(defaultOpen)

    override fun assignPath(prefix: String) {
        super.assignPath(prefix)
        group.assignPaths(path)
    }

    override fun meta() = ControlMeta(
        ControlType.FOLDER, path, label,
        children = group.nodes.map { it.meta() },
        defaultOpen = defaultOpen,
    )
}

internal class ActionNode(key: String, label: String, val onClick: (() -> Unit)?) : DialNode(key, label) {
    override fun meta() = ControlMeta(ControlType.ACTION, path, label)
}

/** Walks a group tree depth-first, in declaration order. */
internal fun DialGroup.walk(visit: (DialNode) -> Unit) {
    nodes.forEach { node ->
        visit(node)
        if (node is FolderNode) node.group.walk(visit)
    }
}

internal fun DialNode.currentValue(): DialValue? = when (this) {
    is SliderNode -> DialValue.Num(value)
    is ToggleNode -> DialValue.Bool(state.value)
    is SelectNode -> DialValue.Str(state.value)
    is ImageNode -> DialValue.Str(state.value)
    is TextNode -> DialValue.Str(state.value)
    is ColorNode -> DialValue.Str(state.value)
    is TransitionNode -> state.value
    is PadNode -> state.value
    is FolderNode, is ActionNode -> null
}

internal fun DialNode.applyValue(value: DialValue) {
    when {
        this is SliderNode && value is DialValue.Num -> this.value = value.value
        this is ToggleNode && value is DialValue.Bool -> state.value = value.value
        this is SelectNode && value is DialValue.Str -> if (options.any { it.value == value.value }) state.value = value.value
        this is ImageNode && value is DialValue.Str -> state.value = value.value
        this is TextNode && value is DialValue.Str -> state.value = value.value
        this is ColorNode && value is DialValue.Str -> if (parseColor(value.value) != null) state.value = value.value
        this is TransitionNode && (value is DialValue.Spring || value is DialValue.Easing) -> {
            val m = CurveMode.of(value)
            cache[m] = value
            mode.value = m
            state.value = value
        }
        this is PadNode && value is DialValue.Point -> set(value)
    }
}

internal fun DialNode.resetToDefault() {
    when (this) {
        is SliderNode -> value = default
        is ToggleNode -> state.value = default
        is SelectNode -> state.value = default
        is ImageNode -> state.value = default
        is TextNode -> state.value = default
        is ColorNode -> state.value = default
        is TransitionNode -> applyValue(default)
        is PadNode -> state.value = this.default
        is FolderNode, is ActionNode -> Unit
    }
}

/** Value cells backing this group (not folder/open UI state), for change tracking. */
internal fun DialGroup.valueCells(): List<DialCell<*>> = buildList {
    walk { node ->
        when (node) {
            is SliderNode -> add(node.cell)
            is ToggleNode -> add(node.state)
            is SelectNode -> add(node.state)
            is ImageNode -> add(node.state)
            is TextNode -> add(node.state)
            is ColorNode -> add(node.state)
            is TransitionNode -> {
                add(node.state)
                add(node.mode)
            }
            is PadNode -> add(node.state)
            is FolderNode, is ActionNode -> Unit
        }
    }
}

/** Values plus DialKit's `<path>.__mode` entries, as persisted. */
internal fun DialGroup.persistedValues(): Map<String, DialValue> = buildMap {
    walk { node ->
        node.currentValue()?.let { put(node.path, it) }
        if (node is TransitionNode) put("${node.path}.__mode", DialValue.Str(node.mode.value.wire))
    }
}

internal fun DialGroup.restore(values: Map<String, DialValue>) {
    walk { node ->
        values[node.path]?.let { node.applyValue(it) }
        if (node is TransitionNode) {
            (values["${node.path}.__mode"] as? DialValue.Str)?.let { CurveMode.of(it.value) }
                ?.takeIf { CurveMode.of(node.state.value) == it }
                ?.let { node.mode.value = it }
        }
    }
}

/** Kotlin declaration hints for Copy (DESIGN): each control as it would be written with the new default. */
internal fun DialGroup.kotlinHints(): List<String> = buildList {
    fun f(v: Float) = "${jsNumber(v.toDouble())}f"
    walk { node ->
        val line = when (node) {
            is SliderNode -> if (node.isInt) {
                "val ${node.key} by slider(${Math.round(node.value)}, ${node.min.toInt()}..${node.max.toInt()})"
            } else {
                "val ${node.key} by slider(${f(node.value)}, ${f(node.min)}..${f(node.max)}, step = ${f(node.step)})"
            }
            is ToggleNode -> "val ${node.key} by toggle(${node.state.value})"
            is SelectNode -> "val ${node.key} by select(${node.options.joinToString { "\"${it.value}\"" }}, default = \"${node.state.value}\")"
            is ImageNode -> "val ${node.key} by image(${node.options.joinToString { "\"${it.value}\"" }}${if (node.options.isEmpty()) "" else ", "}default = \"${node.state.value}\")"
            is TextNode -> "val ${node.key} by text(\"${node.state.value.replace("\"", "\\\"")}\")"
            is ColorNode -> "val ${node.key} by color(\"${node.state.value}\")"
            is TransitionNode -> when (val v = node.state.value) {
                is DialValue.Easing -> "val ${node.key} by easing(duration = ${f(v.duration)}, ease = floatArrayOf(${v.ease.joinToString { f(it) }}))"
                is DialValue.Spring -> if (v.isPhysics) {
                    "val ${node.key} by physicsSpring(stiffness = ${f(v.stiffness ?: 200f)}, damping = ${f(v.damping ?: 25f)}, mass = ${f(v.mass ?: 1f)})"
                } else {
                    "val ${node.key} by spring(visualDuration = ${f(v.visualDuration ?: 0.3f)}, bounce = ${f(v.bounce ?: 0.2f)})"
                }
                else -> null
            }
            is PadNode -> node.state.value.let { p ->
                "val ${node.key} by pad(x = DialAxis(${f(p.x)}, ${f(node.x.min)}, ${f(node.x.max)}), y = DialAxis(${f(p.y)}, ${f(node.y.min)}, ${f(node.y.max)}))"
            }
            is FolderNode -> "// ${node.path} → ${node.group::class.simpleName}"
            is ActionNode -> null
        }
        line?.let { add(it) }
    }
}

/** Any DialKit CSS color as an sRGB ARGB int (wide-gamut values are fitted into sRGB). Unparseable → magenta. */
internal fun cssToArgb(css: String): Int {
    val c = parseColor(css) ?: return 0xFFFF00FF.toInt()
    val rgb = colorToRgb(fitGamut(c, Gamut.Srgb), Gamut.Srgb)
    fun ch(v: Double) = (v.coerceIn(0.0, 1.0) * 255).let { Math.round(it).toInt() }
    return (ch(c.a) shl 24) or (ch(rgb[0]) shl 16) or (ch(rgb[1]) shl 8) or ch(rgb[2])
}
