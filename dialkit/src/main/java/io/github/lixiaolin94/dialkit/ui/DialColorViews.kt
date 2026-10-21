package io.github.lixiaolin94.dialkit.ui

import io.github.lixiaolin94.dialkit.model.sliderKeyValue
import io.github.lixiaolin94.dialkit.model.ControlKey
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import android.os.Bundle
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.ColorNode
import io.github.lixiaolin94.dialkit.model.ColorFormat
import io.github.lixiaolin94.dialkit.model.Gamut
import io.github.lixiaolin94.dialkit.model.OkColor
import io.github.lixiaolin94.dialkit.model.colorFormat
import io.github.lixiaolin94.dialkit.model.colorToRgb
import io.github.lixiaolin94.dialkit.model.fitGamut
import io.github.lixiaolin94.dialkit.model.formatColor
import io.github.lixiaolin94.dialkit.model.maxChroma
import io.github.lixiaolin94.dialkit.R
import io.github.lixiaolin94.dialkit.model.parseColor

/** 8dp tile of 4dp squares, as DialKit's swatch and opacity backgrounds (colours: `dialkit_checker_*`). */
internal fun View.drawChecker(canvas: Canvas, area: RectF, paint: Paint) {
    val cell = dp(4f)
    paint.shader = null
    paint.color = context.getColor(R.color.dialkit_checker_light)
    canvas.drawRect(area, paint)
    paint.color = context.getColor(R.color.dialkit_checker_dark)
    var y = area.top
    var row = 0
    while (y < area.bottom) {
        var x = area.left + if (row % 2 == 0) 0f else cell
        while (x < area.right) {
            canvas.drawRect(x, y, minOf(x + cell, area.right), minOf(y + cell, area.bottom), paint)
            x += cell * 2
        }
        y += cell
        row++
    }
}

internal fun OkColor.srgb(alpha: Double = a): Int {
    val rgb = colorToRgb(this, Gamut.Srgb)
    fun ch(v: Double) = Math.round(v.coerceIn(0.0, 1.0) * 255).toInt()
    return (ch(alpha) shl 24) or (ch(rgb[0]) shl 16) or (ch(rgb[1]) shl 8) or ch(rgb[2])
}

/**
 * SOURCE color-control.ts + `.dialkit-color-control`: label, an editable mono value and a 20dp swatch.
 * DESIGN: tapping the label opens the picker too (the 20dp swatch is a small touch target).
 */
@SuppressLint("ViewConstructor")
internal class ColorRow(private val ui: DialUi, private val node: ColorNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val bg = roundRect(context, p.surface)
    private val field = ValueField(context, p, mono = true, alignEnd = true, current = { node.state.value }) { text ->
        val c = parseColor(text) ?: return@ValueField false
        node.state.value = formatColor(c, colorFormat(text))
        true
    }
    private val swatch = object : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val clip = Path()
        override fun onDraw(canvas: Canvas) {
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            clip.reset()
            clip.addRoundRect(r, dp(5f), dp(5f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            drawChecker(canvas, r, paint)
            paint.color = node.argb
            canvas.drawRect(r, paint)
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = p.borderHover
            r.inset(dp(0.5f), dp(0.5f))
            canvas.drawRoundRect(r, dp(4.5f), dp(4.5f), paint)
            paint.style = Paint.Style.FILL
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(dp(12), 0, dp(12), 0)
        val name = label(context, node.label, p.textLabel).apply {
            gravity = Gravity.CENTER_VERTICAL // full-height tap target, text centred like the other rows
            translationY = -dp(0.5f)
            setOnClickListener { toggle() }
            isFocusable = true
        }
        addView(name, llp(WRAP, MATCH).apply { marginEnd = dp(12) })
        addView(field, llp(0, WRAP, 1f).apply { marginEnd = dp(8) })
        addView(swatch.apply { setOnClickListener { toggle() }; isFocusable = true }, llp(dp(20), dp(20)))
        whileAttached {
            field.refresh()
            swatch.invalidate()
            val a = node.state.listen {
                field.refresh()
                swatch.invalidate()
            }
            val b = ui.popups.onChange {
                val isOpen = ui.popups.isOpenFor(node)
                bg.setColor(if (isOpen) p.surfaceActive else p.surface)
                bg.setStroke(if (isOpen) dp(1) else 0, p.borderHover) // SOURCE open: inset 0 0 0 1px border-hover
                field.displayColor = if (isOpen) p.textPrimary else p.textLabel
            }
            ({ a(); b() })
        }
    }

    private fun toggle() {
        if (ui.popups.isOpenFor(node)) ui.popups.dismiss() else ui.popups.show(node, this, colorSpec(p), ColorPicker(ui, node))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(ROW_HEIGHT), MeasureSpec.EXACTLY))
    }
}

/**
 * SOURCE color-control.ts popover: chroma × lightness plane at the current hue, Hue and Opacity tracks,
 * Hex / OKLCH / Display P3 switch and a CSS input. Hex keeps the handle inside sRGB, P3 inside Display P3;
 * OKLCH is unbounded (SOURCE `commit`).
 */
@SuppressLint("ViewConstructor")
internal class ColorPicker(ui: DialUi, private val node: ColorNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private var format = colorFormat(node.state.value)
    private var color = parseColor(node.state.value) ?: OkColor(0.7, 0.1, 290.0, 1.0)
    private var saturationFallback = 0f
    private val space get() = if (format == ColorFormat.Hex) Gamut.Srgb else Gamut.P3 // SOURCE planeSpace()
    private val plane = PlaneView()
    private val hue = TrackView(context.getString(R.string.dialkit_hue), checker = false, keyStep = 1f / 3600, text = { "${Math.round(color.h * 10) / 10.0}°" }) { v ->
        // SOURCE: hue step 0.1. DESIGN: capped at 359.9 — 360 wraps to 0 on re-parse and the thumb jumps left.
        val h = minOf(Math.round(v * 3600) / 10.0, 359.9)
        commit(color.copy(h = h, c = saturation() * maxChroma(color.l, h, space)))
    }
    private val opacity = TrackView(context.getString(R.string.dialkit_opacity), checker = true, keyStep = 0.01f, text = { "${Math.round(color.a * 100)}%" }) { v ->
        commit(color.copy(a = Math.round(v * 100) / 100.0))
    }
    private val formats = SegmentedView(context, p, context.getString(R.string.dialkit_color_format), listOf(context.getString(R.string.dialkit_format_hex), context.getString(R.string.dialkit_format_oklch), context.getString(R.string.dialkit_format_p3)), formatIndex(), fill = true) {
        commit(color, listOf(ColorFormat.Hex, ColorFormat.Oklch, ColorFormat.P3)[it])
    }
    private val css = ValueField(context, p, mono = true, alignEnd = false, current = { node.state.value }) { text ->
        val c = parseColor(text) ?: return@ValueField false
        node.state.value = formatColor(c, colorFormat(text))
        true
    }

    init {
        orientation = VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(plane, llp(MATCH, dp(160)))
        addView(trackRow(context.getString(R.string.dialkit_hue), hue), llp(MATCH, dp(ROW_HEIGHT)).apply { topMargin = dp(ROW_GAP) })
        addView(trackRow(context.getString(R.string.dialkit_opacity), opacity), llp(MATCH, dp(ROW_HEIGHT)).apply { topMargin = dp(ROW_GAP) })
        addView(
            android.widget.FrameLayout(context).apply {
                background = roundRect(context, p.surface)
                setPadding(dp(2), dp(2), dp(2), dp(2))
                addView(formats, android.widget.FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
            },
            llp(MATCH, dp(ROW_HEIGHT)).apply { topMargin = dp(ROW_GAP) },
        )
        addView(
            android.widget.FrameLayout(context).apply {
                background = roundRect(context, p.surface)
                setPadding(dp(12), 0, dp(12), 0)
                addView(css, android.widget.FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
            },
            llp(MATCH, dp(ROW_HEIGHT)).apply { topMargin = dp(ROW_GAP) },
        )
        whileAttached {
            node.state.listen { value ->
                // Follow external edits (row field, presets) without fighting our own commits.
                if (formatColor(color, format) != value) {
                    parseColor(value)?.let {
                        color = it
                        format = colorFormat(value)
                        formats.selected = formatIndex()
                    }
                }
                css.refresh()
                refreshAll()
            }
        }
    }

    private fun formatIndex() = when (format) {
        ColorFormat.Hex -> 0
        ColorFormat.Oklch -> 1
        ColorFormat.P3 -> 2
    }

    /** SOURCE: saturation is relative chroma; it only survives on its own where max chroma is 0 (black/white). */
    private fun saturation(): Float {
        val max = maxChroma(color.l, color.h, space)
        return if (max > 0) (color.c / max).toFloat().coerceIn(0f, 1f) else saturationFallback
    }

    private fun commit(next: OkColor, nextFormat: ColorFormat = format) {
        format = nextFormat
        color = if (nextFormat == ColorFormat.Oklch) next else fitGamut(next, if (nextFormat == ColorFormat.P3) Gamut.P3 else Gamut.Srgb)
        node.state.value = formatColor(color, format)
        refreshAll()
    }

    private fun refreshAll() {
        plane.invalidate()
        hue.set((color.h / 360).toFloat(), hueStops(), color.srgb(1.0))
        val opaque = color.srgb(1.0)
        opacity.set(color.a.toFloat(), intArrayOf(withAlpha(opaque, 0f), opaque), color.srgb())
    }

    private fun hueStops(): IntArray {
        val sat = saturation()
        return IntArray(13) { i ->
            val h = i * 30.0
            OkColor(color.l, sat * maxChroma(color.l, h, space), h, 1.0).srgb(1.0)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshAll()
    }

    private fun trackRow(name: String, track: View) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(context, p.surface)
        setPadding(dp(12), 0, dp(12), 0)
        addView(TextView(context).apply { text = name; dialText(13f, DialFonts.medium, p.textLabel) }, llp(dp(52), WRAP).apply { marginEnd = dp(12) })
        addView(track, llp(0, dp(24), 1f))
    }

    /** DESIGN: 126×80 samples scaled up with filtering (SOURCE canvas is 252×160), redrawn on every hue drag. */
    private inner class PlaneView : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bitmap = Bitmap.createBitmap(PLANE_W, PLANE_H, Bitmap.Config.ARGB_8888)
        private val pixels = IntArray(PLANE_W * PLANE_H)
        private var builtFor = ""
        private val r = RectF()
        private val src = Rect(0, 0, PLANE_W, PLANE_H)
        private val clip = Path()

        private val markerShadow = RadialShadow(dp(9f), 0x4D000000, 0.55f) // SOURCE 0 2 4 rgb(0 0 0 / 30%)

        init {
            isFocusable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the hex / oklch field below edits the same colour
        }

        /** SOURCE plane keydown: arrows move saturation (x) and lightness (y) by 0.01, Shift 0.1. */
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            val step = if (event.isShiftPressed) 0.1 else 0.01
            val (ds, dl) = when (event.controlKey(vertical = true)) {
                ControlKey.Left -> -step to 0.0
                ControlKey.Right -> step to 0.0
                ControlKey.Up -> 0.0 to step
                ControlKey.Down -> 0.0 to -step
                else -> return super.onKeyDown(keyCode, event)
            }
            val l = (color.l + dl).coerceIn(0.0, 1.0)
            val sat = (saturation() + ds).coerceIn(0.0, 1.0).toFloat()
            saturationFallback = sat
            commit(color.copy(l = l, c = sat * maxChroma(l, color.h, space)))
            return true
        }

        private fun rebuild() {
            val key = "${Math.round(color.h * 10)}:$space"
            if (key == builtFor) return
            builtFor = key
            for (y in 0 until PLANE_H) {
                val l = 1.0 - y.toDouble() / (PLANE_H - 1)
                val max = maxChroma(l, color.h, space)
                for (x in 0 until PLANE_W) pixels[y * PLANE_W + x] = OkColor(l, x.toDouble() / (PLANE_W - 1) * max, color.h, 1.0).srgb(1.0)
            }
            bitmap.setPixels(pixels, 0, PLANE_W, 0, 0, PLANE_W, PLANE_H)
        }

        override fun onDraw(canvas: Canvas) {
            rebuild()
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            clip.reset()
            clip.addRoundRect(r, dp(8f), dp(8f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(bitmap, src, r, paint)
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = 0x1A000000 // SOURCE inset 0 0 0 1px rgb(0 0 0 / 10%)
            r.inset(dp(0.5f), dp(0.5f))
            canvas.drawRoundRect(r, dp(7.5f), dp(7.5f), paint)
            // SOURCE `.dialkit-color-marker`: 12px, 2px white border, 0 2 4 rgb(0 0 0 / 30%).
            val cx = saturation() * width
            val cy = ((1 - color.l) * height).toFloat()
            paint.style = Paint.Style.FILL
            markerShadow.draw(canvas, paint, cx, cy + dp(2f))
            paint.color = color.srgb(1.0)
            canvas.drawCircle(cx, cy, dp(5f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color = context.getColor(R.color.dialkit_thumb)
            canvas.drawCircle(cx, cy, dp(5f), paint)
            paint.style = Paint.Style.FILL
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val s = (e.x / width).coerceIn(0f, 1f)
                    val l = 1.0 - (e.y / height).coerceIn(0f, 1f)
                    saturationFallback = s
                    commit(color.copy(l = l, c = s * maxChroma(l, color.h, space)))
                }
            }
            return true
        }
    }

    /**
     * SOURCE `.dialkit-color-track-row` + range thumb: 16dp track (radius 4) and a 16×24 thumb (radius 5,
     * 2px white border inside the box), positioned like an `<input type=range>`.
     */
    /**
     * Accessibility: a SeekBar over 0–1; screen-reader steps are 5% (the platform SeekBar's default), keyboard
     * steps are DialKit's input step ([keyStep]: hue 0.1°, opacity 0.01), PageUp / PageDown ten of them.
     */
    private inner class TrackView(
        private val name: String,
        private val checker: Boolean,
        private val keyStep: Float,
        private val text: () -> String,
        private val onChange: (Float) -> Unit,
    ) : View(context) {
        init {
            isFocusable = true
        }

        private fun nudge(key: ControlKey, step: Float, large: Boolean = false): Boolean {
            val next = sliderKeyValue(key, value, 0f, 1f, step, large) ?: return false
            onChange(next)
            return true
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            event.controlKey(vertical = false)?.let { if (nudge(it, keyStep, event.isShiftPressed)) return true }
            return super.onKeyDown(keyCode, event)
        }

        override fun getAccessibilityClassName(): CharSequence = "android.widget.SeekBar"

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 1f, value)
            if (value < 1f) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            if (value > 0f) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> nudge(ControlKey.Right, A11Y_STEP)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> nudge(ControlKey.Left, A11Y_STEP)
            else -> super.performAccessibilityAction(action, arguments)
        }

        private var value = 0f
        private var stops = intArrayOf(0, 0)
        private var thumb = 0
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val clip = Path()

        private var gradient: LinearGradient? = null

        fun set(v: Float, stops: IntArray, thumb: Int) {
            value = v
            describe(name, text())
            if (!stops.contentEquals(this.stops)) gradient = null
            this.stops = stops
            this.thumb = thumb
            invalidate()
        }

        private fun track() = LinearGradient(0f, 0f, width.toFloat(), 0f, stops, null, Shader.TileMode.CLAMP)

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            gradient = null
        }

        override fun onDraw(canvas: Canvas) {
            val trackH = dp(16f)
            val top = (height - trackH) / 2
            r.set(0f, top, width.toFloat(), top + trackH)
            clip.reset()
            clip.addRoundRect(r, dp(4f), dp(4f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            if (checker) drawChecker(canvas, r, paint)
            paint.shader = gradient ?: track().also { gradient = it }
            canvas.drawRect(r, paint)
            paint.shader = null
            canvas.restore()

            val tw = dp(16f)
            val th = dp(24f)
            val left = value.coerceIn(0f, 1f) * (width - tw)
            r.set(left, (height - th) / 2, left + tw, (height + th) / 2)
            paint.color = 0x2E000000 // ≈ 0 2 4 rgb(0 0 0 / 30%)
            canvas.drawRoundRect(r.left, r.top + dp(2f), r.right, r.bottom + dp(2f), dp(5f), dp(5f), paint)
            clip.reset()
            clip.addRoundRect(r, dp(5f), dp(5f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            if (checker) drawChecker(canvas, r, paint)
            paint.color = thumb
            canvas.drawRect(r, paint)
            canvas.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2f)
            paint.color = context.getColor(R.color.dialkit_thumb)
            r.inset(dp(1f), dp(1f))
            canvas.drawRoundRect(r, dp(4f), dp(4f), paint)
            paint.style = Paint.Style.FILL
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val tw = dp(16f)
                    onChange(((e.x - tw / 2) / (width - tw)).coerceIn(0f, 1f))
                }
            }
            return true
        }
    }

    private companion object {
        const val A11Y_STEP = 0.05f
        const val PLANE_W = 126
        const val PLANE_H = 80
    }
}
