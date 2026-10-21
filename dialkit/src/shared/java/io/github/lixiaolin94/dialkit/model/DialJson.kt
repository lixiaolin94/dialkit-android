package io.github.lixiaolin94.dialkit.model

import org.json.JSONArray
import org.json.JSONObject

// DialValue ⇄ JSON in DialKit's shapes, for persistence (`dialkit:<id>`) and the Copy export.

internal fun DialValue.toJson(): Any = when (this) {
    is DialValue.Num -> value.toDouble().let { if (it == Math.rint(it)) it.toLong() else jsNumber(it).toDouble() }
    is DialValue.Bool -> value
    is DialValue.Str -> value
    is DialValue.Point -> JSONObject().put("x", jsDouble(x)).put("y", jsDouble(y))
    is DialValue.Spring -> JSONObject().apply {
        put("type", "spring")
        visualDuration?.let { put("visualDuration", jsDouble(it)) }
        bounce?.let { put("bounce", jsDouble(it)) }
        stiffness?.let { put("stiffness", jsDouble(it)) }
        damping?.let { put("damping", jsDouble(it)) }
        mass?.let { put("mass", jsDouble(it)) }
    }
    is DialValue.Easing -> JSONObject()
        .put("type", "easing")
        .put("duration", jsDouble(duration))
        .put("ease", JSONArray(ease.map { jsDouble(it) }))
}

private fun jsDouble(v: Float): Double = jsNumber(v.toDouble()).toDouble()

internal fun dialValueFromJson(raw: Any?): DialValue? = when (raw) {
    is Boolean -> DialValue.Bool(raw)
    is Number -> DialValue.Num(raw.toFloat())
    is String -> DialValue.Str(raw)
    is JSONObject -> when (raw.optString("type")) {
        "spring" -> DialValue.Spring(
            raw.optFloatOrNull("visualDuration"),
            raw.optFloatOrNull("bounce"),
            raw.optFloatOrNull("stiffness"),
            raw.optFloatOrNull("damping"),
            raw.optFloatOrNull("mass"),
        )
        "easing" -> raw.optJSONArray("ease")?.takeIf { it.length() == 4 }?.let { arr ->
            DialValue.Easing(raw.optDouble("duration", 0.3).toFloat(), List(4) { arr.getDouble(it).toFloat() })
        }
        else -> if (raw.has("x") && raw.has("y")) {
            DialValue.Point(raw.getDouble("x").toFloat(), raw.getDouble("y").toFloat())
        } else {
            null
        }
    }
    else -> null
}

private fun JSONObject.optFloatOrNull(key: String): Float? = if (has(key)) getDouble(key).toFloat() else null

internal fun Map<String, DialValue>.toJsonObject(): JSONObject = JSONObject().also { o ->
    forEach { (path, value) -> o.put(path, value.toJson()) }
}

internal fun JSONObject.toDialValues(): Map<String, DialValue> = buildMap {
    keys().forEach { key -> dialValueFromJson(opt(key))?.let { put(key, it) } }
}

/**
 * SOURCE copy-instruction.ts: DialKit's wording with JSON (2-space indent, `.__mode` keys dropped);
 * the Kotlin hint lines are DESIGN — the defaults as they would be written in the DialGroup.
 */
internal fun buildCopyInstruction(groupName: String, panelName: String, values: Map<String, DialValue>, kotlinHints: List<String>): String {
    val json = values.filterKeys { !it.endsWith(".__mode") }.toJsonObject().toString(2).replace("\\/", "/")
    return buildString {
        append("Update the DialKit defaults in $groupName for \"$panelName\" with these values:\n")
        append("```json\n").append(json).append("\n```\n")
        append("Apply these values as the new defaults in the $groupName declarations:\n")
        append("```kotlin\n").append(kotlinHints.joinToString("\n")).append("\n```")
    }
}
