package io.github.lixiaolin94.dialkit.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

// SOURCE color.ts — dependency-free CSS color math (CSS Color 4, D65). Ported 1:1.

internal data class OkColor(val l: Double, val c: Double, val h: Double, val a: Double)

internal enum class ColorFormat { Hex, Oklch, P3 }

internal enum class Gamut { Srgb, P3 }

private fun clamp01(n: Double) = n.coerceIn(0.0, 1.0)
internal fun wrapHue(h: Double) = ((h % 360) + 360) % 360

private typealias Triple3 = DoubleArray

private fun multiply(m: Array<DoubleArray>, v: Triple3): Triple3 =
    DoubleArray(3) { r -> m[r][0] * v[0] + m[r][1] * v[1] + m[r][2] * v[2] }

private fun linearize(v: Double) = if (abs(v) <= 0.04045) v / 12.92 else sign(v) * ((abs(v) + 0.055) / 1.055).pow(2.4)
private fun encode(v: Double) = if (abs(v) <= 0.0031308) 12.92 * v else sign(v) * (1.055 * abs(v).pow(1 / 2.4) - 0.055)

private val RGB_XYZ = arrayOf(
    doubleArrayOf(0.4123907993, 0.3575843394, 0.1804807884),
    doubleArrayOf(0.2126390059, 0.7151686788, 0.0721923154),
    doubleArrayOf(0.0193308187, 0.1191947798, 0.9505321522),
)
private val P3_XYZ = arrayOf(
    doubleArrayOf(0.4865709486, 0.2656676932, 0.1982172852),
    doubleArrayOf(0.2289745641, 0.6917385218, 0.0792869141),
    doubleArrayOf(0.0, 0.0451133819, 1.0439443689),
)
private val XYZ_RGB = arrayOf(
    doubleArrayOf(3.2409699419, -1.5373831776, -0.4986107603),
    doubleArrayOf(-0.9692436363, 1.8759675015, 0.0415550574),
    doubleArrayOf(0.0556300797, -0.2039769589, 1.0569715142),
)
private val XYZ_P3 = arrayOf(
    doubleArrayOf(2.4934969119, -0.9313836179, -0.4027107845),
    doubleArrayOf(-0.8294889696, 1.7626640603, 0.0236246858),
    doubleArrayOf(0.0358458302, -0.0761723893, 0.9568845240),
)
private val XYZ_LMS = arrayOf(
    doubleArrayOf(0.819022438, 0.3619062601, -0.1288737815),
    doubleArrayOf(0.0329836539, 0.9292868616, 0.0361446664),
    doubleArrayOf(0.0481771894, 0.2642395318, 0.6335478285),
)
private val LMS_XYZ = arrayOf(
    doubleArrayOf(1.2268798734, -0.5578149966, 0.2813910502),
    doubleArrayOf(-0.0405757626, 1.1122868294, -0.0717110667),
    doubleArrayOf(-0.0763729497, -0.421493324, 1.5869240244),
)

internal fun rgbToColor(rgb: Triple3, a: Double = 1.0, gamut: Gamut = Gamut.Srgb): OkColor {
    val xyz = multiply(if (gamut == Gamut.P3) P3_XYZ else RGB_XYZ, DoubleArray(3) { linearize(rgb[it]) })
    val lms = multiply(XYZ_LMS, xyz).let { v -> DoubleArray(3) { cbrt(v[it]) } }
    val (l, m, s) = Triple(lms[0], lms[1], lms[2])
    val bigL = 0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s
    val bigA = 1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s
    val bigB = 0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s
    val c = hypot(bigA, bigB)
    return OkColor(
        clamp01(bigL),
        if (c < 1e-7) 0.0 else c,
        if (c < 1e-7) 0.0 else wrapHue(atan2(bigB, bigA) * 180 / PI),
        clamp01(a),
    )
}

internal fun colorToRgb(color: OkColor, gamut: Gamut = Gamut.Srgb): Triple3 {
    val a = color.c * cos(color.h * PI / 180)
    val b = color.c * sin(color.h * PI / 180)
    val lms = doubleArrayOf(
        (color.l + 0.3963377774 * a + 0.2158037573 * b).pow(3),
        (color.l - 0.1055613458 * a - 0.0638541728 * b).pow(3),
        (color.l - 0.0894841775 * a - 1.291485548 * b).pow(3),
    )
    val xyz = multiply(LMS_XYZ, lms)
    val rgb = multiply(if (gamut == Gamut.P3) XYZ_P3 else XYZ_RGB, xyz)
    return DoubleArray(3) { encode(rgb[it]) }
}

internal fun inGamut(color: OkColor, gamut: Gamut = Gamut.Srgb): Boolean =
    colorToRgb(color, gamut).all { it >= -0.00001 && it <= 1.00001 }

/** Preserve lightness and hue when reducing chroma to an RGB gamut (20-step bisection). */
internal fun fitGamut(color: OkColor, gamut: Gamut = Gamut.Srgb): OkColor {
    if (inGamut(color, gamut)) return color
    var lo = 0.0
    var hi = color.c
    repeat(20) {
        val mid = (lo + hi) / 2
        if (inGamut(color.copy(c = mid), gamut)) lo = mid else hi = mid
    }
    return color.copy(c = lo)
}

/** The right edge of the picker at a given lightness and hue. */
internal fun maxChroma(l: Double, h: Double, gamut: Gamut = Gamut.Srgb): Double =
    if (l <= 0 || l >= 1) 0.0 else fitGamut(OkColor(l, 0.5, h, 1.0), gamut).c

internal fun colorFormat(value: String): ColorFormat {
    val t = value.trim()
    return when {
        Regex("^oklch\\(", RegexOption.IGNORE_CASE).containsMatchIn(t) -> ColorFormat.Oklch
        Regex("^color\\(display-p3\\s", RegexOption.IGNORE_CASE).containsMatchIn(t) -> ColorFormat.P3
        else -> ColorFormat.Hex
    }
}

private fun round(v: Double, digits: Int = 4): String {
    val r = Math.round(v * 10.0.pow(digits)) / 10.0.pow(digits)
    // JS Number→string: no trailing zeros, no exponent for these magnitudes.
    return java.math.BigDecimal(r).setScale(digits, java.math.RoundingMode.HALF_UP).stripTrailingZeros()
        .toPlainString().let { if (it == "-0") "0" else it }
}

internal fun formatColor(color: OkColor, format: ColorFormat): String {
    val alpha = if (color.a < 1) " / ${round(color.a)}" else ""
    if (format == ColorFormat.Oklch) return "oklch(${round(color.l)} ${round(color.c)} ${round(color.h, 2)}$alpha)"
    val gamut = if (format == ColorFormat.P3) Gamut.P3 else Gamut.Srgb
    val rgb = colorToRgb(fitGamut(color, gamut), gamut)
    if (format == ColorFormat.P3) return "color(display-p3 ${rgb.joinToString(" ") { round(clamp01(it), 5) }}$alpha)"
    val bytes = rgb.map { (clamp01(it) * 255).roundToInt() }.toMutableList()
    if (color.a < 1) bytes += (color.a * 255).roundToInt()
    return "#" + bytes.joinToString("") { it.toString(16).padStart(2, '0') }
}

private val NUMBER = Regex("^[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:e[+-]?\\d+)?(%|deg|grad|rad|turn)?$", RegexOption.IGNORE_CASE)

private fun number(value: String, percentScale: Double = 1.0, hue: Boolean = false): Double? {
    val match = NUMBER.matchEntire(value) ?: return null
    val unit = match.groupValues[1].lowercase().ifEmpty { null }
    val n = value.substring(0, value.length - (unit?.length ?: 0)).toDoubleOrNull() ?: return null
    if (!n.isFinite()) return null
    if (hue) {
        return when (unit) {
            "rad" -> n * 180 / PI
            "turn" -> n * 360
            "grad" -> n * 0.9
            null, "deg" -> n
            else -> null
        }
    }
    return when (unit) {
        "%" -> n * percentScale / 100
        null -> n
        else -> null
    }
}

/** Absolute hex, RGB, HSL, OKLCH and Display P3 colors. */
internal fun parseColor(value: String): OkColor? {
    val text = value.trim().lowercase()
    if (text == "transparent") return OkColor(0.0, 0.0, 0.0, 0.0)
    if (Regex("^#(?:[\\da-f]{3,4}|[\\da-f]{6}|[\\da-f]{8})$").matches(text)) {
        var hex = text.substring(1)
        if (hex.length <= 4) hex = hex.map { "$it$it" }.joinToString("")
        val bytes = hex.chunked(2).map { it.toInt(16) / 255.0 }
        return rgbToColor(doubleArrayOf(bytes[0], bytes[1], bytes[2]), bytes.getOrElse(3) { 1.0 })
    }
    val match = Regex("^(oklch|rgb|rgba|hsl|hsla|color)\\(([^()]*)\\)$").matchEntire(text) ?: return null
    val kind = match.groupValues[1]
    var body = match.groupValues[2].trim()
    val p3 = kind == "color"
    if (p3) {
        if (!body.startsWith("display-p3 ")) return null
        body = body.substring(11).trim()
    }
    val legacy = body.contains(',')
    if (legacy && (p3 || kind == "oklch" || body.contains('/'))) return null
    val parts = if (legacy) body.split(',').map { it.trim() } else body.split(Regex("\\s*/\\s*"))
    if (!legacy && parts.size > 2) return null
    val channels = if (legacy) parts.take(3) else parts[0].split(Regex("\\s+"))
    if (channels.size != 3 || (legacy && parts.size != 3 && parts.size != 4)) return null
    if (legacy && kind.startsWith("rgb") && channels.any { it.endsWith('%') } && !channels.all { it.endsWith('%') }) return null
    val alphaText = if (legacy) parts.getOrNull(3) else parts.getOrNull(1)
    val alpha = if (alphaText == null) 1.0 else number(alphaText) ?: return null
    if (kind == "oklch") {
        val l = number(channels[0]) ?: return null
        val c = number(channels[1], 0.4) ?: return null
        val h = number(channels[2], 1.0, hue = true) ?: return null
        return OkColor(clamp01(l), maxOf(0.0, c), wrapHue(h), clamp01(alpha))
    }
    if (kind.startsWith("hsl")) {
        val h = number(channels[0], 1.0, hue = true) ?: return null
        val s = number(channels[1]) ?: return null
        val l = number(channels[2]) ?: return null
        if (!channels[1].endsWith('%') || !channels[2].endsWith('%')) return null
        val sat = clamp01(s)
        val light = clamp01(l)
        val a = sat * minOf(light, 1 - light)
        fun f(n: Int): Double {
            val k = (n + wrapHue(h) / 30) % 12
            return light - a * maxOf(-1.0, minOf(k - 3, 9 - k, 1.0))
        }
        return rgbToColor(doubleArrayOf(f(0), f(8), f(4)), alpha)
    }
    val values = channels.map { number(it, if (p3) 1.0 else 255.0) ?: return null }
    return rgbToColor(
        DoubleArray(3) { if (p3) values[it] else clamp01(values[it] / 255) },
        alpha,
        if (p3) Gamut.P3 else Gamut.Srgb,
    )
}
