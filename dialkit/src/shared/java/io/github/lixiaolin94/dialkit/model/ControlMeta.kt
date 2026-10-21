package io.github.lixiaolin94.dialkit.model

/** Mirrors DialKit's `ControlMeta.type` (store/DialStore.ts). */
public enum class ControlType { SLIDER, TOGGLE, TRANSITION, FOLDER, ACTION, SELECT, COLOR, IMAGE, TEXT, PAD }

/**
 * Serializable description of one control, field-for-field with DialKit's `ControlMeta`.
 * `path` follows DialKit: `prefix ? "$prefix.$key" : key`.
 */
public data class ControlMeta(
    val type: ControlType,
    val path: String,
    val label: String,
    val min: Float? = null,
    val max: Float? = null,
    val step: Float? = null,
    val children: List<ControlMeta> = emptyList(),
    val defaultOpen: Boolean = true,
    val options: List<DialOption> = emptyList(),
    val placeholder: String? = null,
)

/** A select option. DialKit: `string | { value, label }`; bare strings get a Title Case label. */
public data class DialOption(val value: String, val label: String = titleCase(value))

/** SOURCE SelectControl.tsx:toTitleCase — `s.replace(/\b\w/g, c => c.toUpperCase())`. */
internal fun titleCase(s: String): String = Regex("\\b\\w").replace(s) { it.value.uppercase() }

/** A stored control value. Shapes match DialKit's `DialValue` so persistence and Copy round-trip. */
public sealed interface DialValue {
    public data class Num(val value: Float) : DialValue
    public data class Bool(val value: Boolean) : DialValue

    /** text, select and color (a CSS color string, as DialKit stores it). */
    public data class Str(val value: String) : DialValue

    public data class Point(val x: Float, val y: Float) : DialValue

    /** DialKit `SpringConfig`: time fields (visualDuration/bounce) or physics fields (stiffness/damping/mass). */
    public data class Spring(
        val visualDuration: Float? = null,
        val bounce: Float? = null,
        val stiffness: Float? = null,
        val damping: Float? = null,
        val mass: Float? = null,
    ) : DialValue {
        val isPhysics: Boolean get() = stiffness != null || damping != null || mass != null
    }

    /** DialKit `EasingConfig`: duration in seconds and cubic-bezier control points. */
    public data class Easing(val duration: Float, val ease: List<Float>) : DialValue
}
