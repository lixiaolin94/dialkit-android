package io.github.lixiaolin94.dialkit.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import io.github.lixiaolin94.dialkit.DialTheme
import io.github.lixiaolin94.dialkit.R

/**
 * The design tokens as ARGB ints, read once per overlay from `res/values(-night)/dialkit_colors.xml` (SOURCE values
 * from DialKit styles.css are documented there). Hosts restyle DialKit by overriding those colour resources.
 */
internal class DialPalette private constructor(c: Context) {
    val surface = c.getColor(R.color.dialkit_surface)
    val surfaceHover = c.getColor(R.color.dialkit_surface_hover)
    val surfaceActive = c.getColor(R.color.dialkit_surface_active)
    val surfaceSubtle = c.getColor(R.color.dialkit_surface_subtle)
    val textRoot = c.getColor(R.color.dialkit_text_root)
    val textSection = c.getColor(R.color.dialkit_text_section)
    val textLabel = c.getColor(R.color.dialkit_text_label)
    val textFocus = c.getColor(R.color.dialkit_text_focus)
    val textPrimary = c.getColor(R.color.dialkit_text_primary)
    val textSecondary = c.getColor(R.color.dialkit_text_secondary)
    val textTertiary = c.getColor(R.color.dialkit_text_tertiary)
    val border = c.getColor(R.color.dialkit_border)
    val borderHover = c.getColor(R.color.dialkit_border_hover)
    val glassBg = c.getColor(R.color.dialkit_glass_bg)
    val dropdownBg = c.getColor(R.color.dialkit_dropdown_bg)
    val vizGrid = c.getColor(R.color.dialkit_viz_grid)
    val vizMidline = c.getColor(R.color.dialkit_viz_midline)
    val vizCurve = c.getColor(R.color.dialkit_viz_curve)
    val error = c.getColor(R.color.dialkit_error)
    val grabber = c.getColor(R.color.dialkit_grabber)
    val checkerDark = c.getColor(R.color.dialkit_checker_dark)
    val checkerLight = c.getColor(R.color.dialkit_checker_light)
    val thumb = c.getColor(R.color.dialkit_thumb)

    companion object {
        /** [DialTheme.System] follows the host's night mode; Light / Dark force the matching resource set. */
        fun resolve(context: Context, theme: DialTheme): DialPalette = DialPalette(
            when (theme) {
                DialTheme.System -> context
                DialTheme.Light -> context.withNightMode(false)
                DialTheme.Dark -> context.withNightMode(true)
            },
        )

        private fun Context.withNightMode(night: Boolean): Context {
            val config = Configuration(resources.configuration)
            val mode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            return createConfigurationContext(config)
        }
    }
}

internal fun withAlpha(color: Int, alpha: Float): Int =
    (Math.round((color ushr 24) * alpha) shl 24) or (color and 0x00FFFFFF)

/** Linear ARGB blend (for short colour transitions). */
internal fun lerpColor(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = Math.round(((a shr shift) and 0xFF) + (((b shr shift) and 0xFF) - ((a shr shift) and 0xFF)) * t)
    return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** MAP: CSS px → dp at 1:1. */
internal fun View.dp(v: Float): Float = v * resources.displayMetrics.density
internal fun View.dp(v: Int): Int = Math.round(v * resources.displayMetrics.density)
internal fun Context.dp(v: Float): Float = v * resources.displayMetrics.density

/** DialKit's type: system sans 500/600 and a monospace for values. */
internal object DialFonts {
    val regular: Typeface = weight(400)
    val medium: Typeface = weight(500)
    val semibold: Typeface = weight(600)
    val mono: Typeface = Typeface.MONOSPACE

    private fun weight(w: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(Typeface.SANS_SERIF, w, false)
        } else {
            Typeface.create(if (w >= 600) "sans-serif-medium" else "sans-serif-medium", Typeface.NORMAL)
        }
}

/**
 * Text sized in dp (COMPLEX_UNIT_DIP), so the debug panel ignores the host's font scale (DESIGN), with
 * DialKit's 13px/500 label style by default.
 */
internal fun TextView.dialText(sizeDp: Float = 13f, typeface: Typeface = DialFonts.medium, color: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    this.typeface = typeface
    setTextColor(color)
    includeFontPadding = false
}
