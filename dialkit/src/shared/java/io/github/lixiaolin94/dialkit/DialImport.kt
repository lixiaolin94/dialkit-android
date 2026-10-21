package io.github.lixiaolin94.dialkit

import org.json.JSONArray
import org.json.JSONObject
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.dialValueFromJson
import io.github.lixiaolin94.dialkit.model.parseColor

// DESIGN: DialKit 2.0.2 has no import; this is the inverse of its Copy export, so values tuned in a web demo (or
// copied from this panel on another device) can be brought back in.

/** What an import text resolves to against one group: values to apply (with `.__mode` entries), and leftovers. */
internal class DialImport(val values: Map<String, DialValue>, val ignored: List<String>) {
    /** Controls set, not counting `.__mode` bookkeeping. */
    val count: Int get() = values.keys.count { !it.endsWith(".__mode") }
}

/**
 * Finds the JSON object in [text]. Accepted, in order: a fenced ```json block (this panel's and DialKit's Copy
 * text), the whole text, or the span from the first `{` to the last `}`. A persisted panel state
 * (`{ version, values, … }`, DialKit's `localStorage["dialkit:<id>"]` shape) is unwrapped to its `values`.
 */
internal fun extractImportJson(text: String): JSONObject? {
    val t = text.trim()
    val fenced = Regex("```(?:json)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(t)?.groupValues?.get(1)
    val braces = t.indexOf('{').let { a -> t.lastIndexOf('}').let { b -> if (a in 0 until b) t.substring(a, b + 1) else null } }
    val obj = listOfNotNull(fenced, t, braces).firstNotNullOfOrNull { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val inner = obj.optJSONObject("values")
    return if (inner != null && (obj.has("version") || obj.has("presets"))) inner else obj
}

/**
 * Matches [json] against this group's controls. Keys may be flat dotted paths (`"shadow.blur": 8`, as Copy writes
 * them) or nested objects (`"shadow": { "blur": 8 }`, as a DialKit config is written); a slider also takes
 * DialKit's config tuple `[value, min, max]`. Values the control cannot take (wrong type, unknown select option,
 * unparseable color) are reported as ignored, never applied.
 */
internal fun DialGroup.resolveImport(json: JSONObject): DialImport {
    val flat = LinkedHashMap<String, Any>()
    fun flatten(o: JSONObject, prefix: String) {
        o.keys().forEach { key ->
            val v = o.opt(key) ?: return@forEach
            val path = prefix + key
            flat[path] = v
            if (v is JSONObject && v.optString("type") !in setOf("spring", "easing")) flatten(v, "$path.")
        }
    }
    flatten(json, "")

    val out = LinkedHashMap<String, DialValue>()
    val used = HashSet<String>()
    fun consume(path: String) {
        used += path
        flat.keys.filter { it.startsWith("$path.") }.forEach { used += it } // a point's own x / y keys
    }
    walk { node ->
        val raw = flat[node.path]
        if (node is FolderNode) {
            if (raw != null) used += node.path
            return@walk
        }
        val value = raw?.let { node.importValue(it) }
        if (value != null) {
            out[node.path] = value
            consume(node.path)
        }
        if (node is TransitionNode) {
            val modePath = "${node.path}.__mode"
            (flat[modePath] as? String)?.let { mode ->
                used += modePath
                if (value != null) out[modePath] = DialValue.Str(mode)
            }
        }
    }
    // Nested containers whose children were all taken are structure, not leftovers.
    val ignored = flat.keys.filter { key ->
        key !in used && !(flat[key] is JSONObject && flat.keys.any { it.startsWith("$key.") } &&
            flat.keys.filter { it.startsWith("$key.") }.all { it in used || flat[it] is JSONObject })
    }
    return DialImport(out, ignored)
}

private fun DialNode.importValue(raw: Any): DialValue? = when (this) {
    is SliderNode -> when (raw) {
        is Number -> DialValue.Num(raw.toFloat())
        is JSONArray -> (raw.opt(0) as? Number)?.let { DialValue.Num(it.toFloat()) }
        else -> null
    }
    is ToggleNode -> (raw as? Boolean)?.let { DialValue.Bool(it) }
    is SelectNode -> (raw as? String)?.takeIf { v -> options.any { it.value == v } }?.let { DialValue.Str(it) }
    is ImageNode -> (raw as? String)?.let { DialValue.Str(it) }
    is TextNode -> (raw as? String)?.let { DialValue.Str(it) }
    is ColorNode -> (raw as? String)?.takeIf { parseColor(it) != null }?.let { DialValue.Str(it) }
    is TransitionNode -> dialValueFromJson(raw)?.takeIf { it is DialValue.Spring || it is DialValue.Easing }
    is PadNode -> when (raw) {
        is JSONArray -> if (raw.length() == 2 && raw.opt(0) is Number && raw.opt(1) is Number) {
            DialValue.Point(raw.getDouble(0).toFloat(), raw.getDouble(1).toFloat())
        } else {
            null
        }
        else -> dialValueFromJson(raw) as? DialValue.Point
    }
    is FolderNode, is ActionNode -> null
}
