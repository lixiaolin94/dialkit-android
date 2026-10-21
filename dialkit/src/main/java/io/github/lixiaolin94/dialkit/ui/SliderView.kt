package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import io.github.lixiaolin94.dialkit.R
import io.github.lixiaolin94.dialkit.model.ControlKey
import io.github.lixiaolin94.dialkit.model.formatSliderValue
import io.github.lixiaolin94.dialkit.model.sliderKeyValue
import io.github.lixiaolin94.dialkit.model.roundValue
import io.github.lixiaolin94.dialkit.model.snapToDecile

/**
 * Fill spring for taps, keys and typed values. SOURCE handlePointerUp uses the physics spring
 * (stiffness 300, damping 25, mass 0.8): ω = √(300/0.8) ≈ 19.36, ζ = 25/(2·√(300·0.8)) ≈ 0.807, i.e.
 * visualDuration = 2π/(1.2·ω) ≈ 0.270, bounce ≈ 0.193. DESIGN (user): rounded to the 0.05 grid as (0.25, 0.2).
 */
private val FILL_SPRING = timeSpring(0.25f, 0.2f)

/**
 * Port of DialKit's Slider (Slider.tsx + `.dialkit-slider-*`): the whole 36dp row is the track, the fill follows
 * the finger by absolute position, a tap snaps (nearest step when ≤ 10 steps, else magnetic tenths) with a
 * spring, and dragging past either end stretches the track like a rubber band.
 *
 * Touch adaptation (DESIGN): a drag starts after the platform touch slop and only when horizontal (then the
 * parent may not intercept), so the panel still scrolls vertically; "active" means "finger down".
 *
 * Typing a value (SOURCE: DialKit's value turns editable after an 800ms hover, then a click opens an input; Enter
 * commits, Escape cancels, blur commits; the typed number is clamped and rounded to the step). DESIGN: no hover on
 * touch, so a still long press anywhere on the row opens the input.
 *
 * Accessibility: exposed as a SeekBar with a range, scroll forward / backward by one step and set-progress, plus
 * an "Edit value" action; Left / Right, PageUp / PageDown, Home / End and Enter work from a hardware keyboard
 * (SOURCE sliderKeyValue; Shift moves 10 steps).
 */
@SuppressLint("ViewConstructor")
internal class SliderView(
    context: Context,
    private val palette: DialPalette,
    private val label: String,
    private val min: Float,
    private val max: Float,
    private val step: Float,
    private val read: () -> Float,
    private val write: (Float) -> Unit,
) : FrameLayout(context) {
    private val range = (max - min).takeIf { it > 0f } ?: 1f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var lastWritten = 0f
    private var atLimit = false

    private val fill = SpringValue(fraction(read()), threshold = 0.0005f) { invalidate() }
    private val stretch = SpringValue(0f, threshold = 0.1f) { invalidate() }
    private val handleScaleX = SpringValue(0.25f, threshold = 0.002f) { invalidate() }
    private val handleScaleY = SpringValue(1f, threshold = 0.002f) { invalidate() }
    private val handleAlpha = Fade(150) { invalidate() } // SOURCE opacity { duration: 0.15 }
    private val activeColor = Fade(150) { invalidate() }
    private val hashColor = Fade(200) { invalidate() } // SOURCE `.dialkit-slider-hashmark` transition 200ms

    private var interacting = false
    private var dragging = false
    private var editing = false
    private var longPressed = false
    private var activationDown = false
    private val longPress = Runnable {
        if (interacting && !dragging) {
            longPressed = true
            endInteraction()
            hapticLongPress()
            beginEdit()
        }
    }

    /** SOURCE `.dialkit-slider-input`: right 12, 3–6ch wide, 13/500 mono, text-label with a 1px underline. */
    private val editor: EditText = object : EditText(context) {
        // SOURCE Escape cancels. Back is the touch equivalent; it would otherwise only hide the keyboard.
        override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
            if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) && editing) {
                if (event.action == KeyEvent.ACTION_DOWN) return true
                endEdit(commit = false)
                return true
            }
            return super.onKeyPreIme(keyCode, event)
        }
    }.apply {
        visibility = GONE
        background = UnderlineDrawable(palette.textLabel, dp(1f))
        setPadding(0, 0, 0, dp(1))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13f)
        typeface = DialFonts.mono
        includeFontPadding = false
        gravity = Gravity.END
        setTextColor(palette.textFocus) // SOURCE :focus → text-focus
        setSingleLine()
        minEms = 3
        maxEms = 6
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        contentDescription = context.getString(R.string.dialkit_value_of, label)
        setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                endEdit(commit = true)
                true
            } else {
                false
            }
        }
        setOnFocusChangeListener { _, focused -> if (!focused && editing) endEdit(commit = true) }
    }
    private var downX = 0f
    private var downY = 0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(13f)
        typeface = DialFonts.medium
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(13f)
        typeface = DialFonts.mono
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    init {
        setWillNotDraw(false)
        isFocusable = true // hardware keyboard (focusable in touch mode stays off)
        addView(editor, LayoutParams(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = dp(12) })
        describe()
    }

    fun sync() {
        if (!interacting && !fill.isRunning) fill.snapTo(fraction(read()))
        describe()
        invalidate()
    }

    private fun describe() = describe(label, valueText())

    private fun fraction(v: Float) = ((v - min) / range).coerceIn(0f, 1f)
    private fun valueAt(x: Float) = min + (x / width).coerceIn(0f, 1f) * range

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(ROW_HEIGHT), MeasureSpec.EXACTLY))
    }

    private fun labelRight() = dp(10f) + textPaint.measureText(label)
    private fun valueText() = formatSliderValue(read(), step, min, max)
    private fun valueLeft() = width - dp(12f) - valuePaint.measureText(valueText())

    /** SOURCE Slider.tsx value dodge: fade the handle when it overlaps the label or the value. */
    private fun dodging(): Boolean {
        val pct = fraction(read())
        val buffer = dp(8f)
        return pct * width < labelRight() + buffer || pct * width > valueLeft() - buffer
    }

    private fun updateHandle() {
        val dodge = dodging()
        handleAlpha.to(
            when {
                !interacting -> 0f
                dodge -> 0.1f
                dragging -> 0.9f
                else -> 0.5f
            },
        )
        handleScaleX.animateTo(if (interacting) 1f else 0.25f, timeSpring(0.25f, 0.15f))
        handleScaleY.animateTo(if (interacting && dodge) 0.75f else 1f, timeSpring(0.2f, 0.1f))
        activeColor.to(if (interacting) 1f else 0f)
        hashColor.to(if (interacting) 1f else 0f)
    }

    override fun onDraw(canvas: Canvas) {
        val s = stretch.value
        val w = width + abs(s)
        val left = min(s, 0f)
        canvas.save()
        canvas.translate(left, 0f)
        rect.set(0f, 0f, w, height.toFloat())
        clip.reset()
        clip.addRoundRect(rect, dp(RADIUS.toFloat()), dp(RADIUS.toFloat()), Path.Direction.CW)
        canvas.clipPath(clip) // SOURCE overflow: hidden, radius 8
        paint.color = palette.surface
        canvas.drawRect(rect, paint)

        // Hash marks: every step when ≤ 10 positions, otherwise tenths, shown while active.
        paint.color = lerpColor(withAlpha(palette.borderHover, 0f), palette.borderHover, hashColor.value)
        val discrete = range / step
        val marks = if (discrete <= 10f) (1 until discrete.roundToInt()).map { it * step / range } else (1..9).map { it / 10f }
        val markW = dp(1f)
        val markH = dp(8f)
        for (p in marks) {
            rect.set(w * p - markW / 2, (height - markH) / 2, w * p + markW / 2, (height + markH) / 2)
            canvas.drawRoundRect(rect, markW, markW, paint)
        }

        val fillW = w * fill.value
        paint.color = lerpColor(palette.surfaceActive, palette.borderHover, activeColor.value)
        canvas.drawRect(0f, 0f, fillW, height.toFloat(), paint)

        // Handle 3×20, left = max(5px, pct% − 9px), centred. SOURCE `.dialkit-slider-handle`.
        val hw = dp(3f) * handleScaleX.value
        val hh = dp(20f) * handleScaleY.value
        val cx = max(dp(5f), fillW - dp(9f)) + dp(1.5f)
        paint.color = withAlpha(palette.textPrimary, handleAlpha.value)
        rect.set(cx - hw / 2, (height - hh) / 2, cx + hw / 2, (height + hh) / 2)
        canvas.drawRoundRect(rect, hw / 2, hw / 2, paint)

        // Label left 10 (−0.5 dp), value right 12 (+0.5 dp). SOURCE `.dialkit-slider-label/value`.
        textPaint.color = palette.textLabel
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, dp(10f), baseline - dp(0.5f), textPaint)
        if (!editing) {
            valuePaint.color = lerpColor(palette.textLabel, palette.textFocus, activeColor.value)
            val v = valueText()
            canvas.drawText(v, w - dp(12f) - valuePaint.measureText(v), baseline + dp(0.5f), valuePaint)
        }
        canvas.restore()
    }

    /** SOURCE Slider.tsx computeRubberStretch: 8·√min((past − 32)/200, 1), in dp. */
    private fun rubber(x: Float): Float {
        val sign = if (x < 0f) -1f else 1f
        val past = if (x < 0f) -x else x - width
        val overflow = max(0f, past - dp(32f))
        return sign * dp(8f) * sqrt(min(overflow / dp(200f), 1f))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (editing) endEdit(commit = true) // SOURCE blur commits
                longPressed = false
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                downX = e.x
                downY = e.y
                lastWritten = read()
                atLimit = lastWritten <= min || lastWritten >= max
                interacting = true
                dragging = false
                updateHandle()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val dy = e.y - downY
                if (abs(dx) > slop || abs(dy) > slop) removeCallbacks(longPress)
                if (!dragging && abs(dx) > slop && abs(dx) >= abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    val raw = valueAt(e.x)
                    stretch.snapTo(if (e.x < 0f || e.x > width) rubber(e.x) else 0f)
                    fill.snapTo(fraction(raw))
                    commitDrag(roundValue(raw, step, min, max))
                    describe()
                    updateHandle()
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (longPressed) {
                    longPressed = false
                    return true // the long press opened the input; this lift is not a tap
                }
                if (dragging) {
                    // DESIGN: Android's UP carries the final position; DialKit's pointerup does not move.
                    val raw = valueAt(e.x)
                    fill.snapTo(fraction(raw))
                    commitDrag(roundValue(raw, step, min, max))
                } else {
                    // SOURCE handlePointerUp: tap snaps and the fill springs; see FILL_SPRING.
                    val raw = valueAt(e.x)
                    val snapped = if (range / step <= 10f) {
                        (min + Math.round((raw - min) / step) * step).coerceIn(min, max)
                    } else {
                        snapToDecile(raw, min, max)
                    }
                    write(roundValue(snapped, step, min, max))
                    fill.animateTo(fraction(snapped), FILL_SPRING)
                }
                describe()
                endInteraction()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                endInteraction()
            }
        }
        return true
    }

    /**
     * Writes a dragged value with feedback (DESIGN, user): a limit tap once on reaching min or max (again only
     * after leaving it), and a detent per step on coarse sliders — the ≤ 10-step ones that show every hashmark.
     */
    private fun commitDrag(v: Float) {
        val limit = v <= min || v >= max
        if (limit && !atLimit) hapticBoundary() else if (!limit && v != lastWritten && range / step <= 10f) hapticTick()
        atLimit = limit
        lastWritten = v
        write(v)
    }

    private fun endInteraction() {
        interacting = false
        dragging = false
        if (stretch.value != 0f) stretch.animateTo(0f, timeSpring(0.35f, 0.15f))
        updateHandle()
        invalidate()
    }

    // ---- typing a value

    /** The keyboard went away while typing (Back on API 33+ goes to the IME, not to the view): cancel. */
    fun onImeHidden() {
        if (editing && editor.hasFocus()) endEdit(commit = false)
    }

    private fun beginEdit() {
        if (editing) return
        editing = true
        editor.setText(valueText()) // SOURCE value.toFixed(decimalsForStep)
        editor.visibility = VISIBLE
        editor.requestFocus()
        editor.selectAll()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, 0)
        invalidate()
    }

    private fun endEdit(commit: Boolean) {
        if (!editing) return
        editing = false
        // SOURCE handleInputSubmit: parseFloat, clamp, roundValue; unparseable text keeps the value.
        if (commit) editor.text.toString().replace(',', '.').trim().toFloatOrNull()?.let { set(it.coerceIn(min, max)) }
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
        editor.clearFocus()
        editor.visibility = GONE
        if (isFocusable && !isInTouchMode) requestFocus() // SOURCE: focus returns to the track
        invalidate()
    }

    /** Writes [v] (rounded to the step) and springs the fill there, as a tap does. */
    private fun set(v: Float) {
        val next = roundValue(v, step, min, max)
        write(next)
        fill.animateTo(fraction(next), FILL_SPRING)
        describe()
        invalidate()
    }

    private fun stepBy(key: ControlKey, large: Boolean = false): Boolean {
        val next = sliderKeyValue(key, read(), min, max, step, large) ?: return false
        set(next)
        return true
    }

    // ---- hardware keys and accessibility

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!editing) {
            event.controlKey(vertical = false)?.let { if (stepBy(it, event.isShiftPressed)) return true }
            if (event.isActivation()) {
                activationDown = true // open on the key's release, or the input would take the release as Done
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (activationDown && event.isActivation()) {
            activationDown = false
            beginEdit()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.SeekBar"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val v = read()
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, min, max, v)
        if (v < max) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (v > min) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (Build.VERSION.SDK_INT >= 24) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.dialkit_action_edit, context.getString(R.string.dialkit_edit_value)))
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when {
        action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> stepBy(ControlKey.Right)
        action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> stepBy(ControlKey.Left)
        Build.VERSION.SDK_INT >= 24 && action == android.R.id.accessibilityActionSetProgress &&
            arguments?.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) == true -> {
            set(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE).coerceIn(min, max))
            true
        }
        action == R.id.dialkit_action_edit -> {
            beginEdit()
            true
        }
        else -> super.performAccessibilityAction(action, arguments)
    }
}

/** A 1px bottom rule, as the input's `border-bottom`. */
private class UnderlineDrawable(color: Int, private val stroke: Float) : android.graphics.drawable.Drawable() {
    private val paint = Paint().apply { this.color = color }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRect(b.left.toFloat(), b.bottom - stroke, b.right.toFloat(), b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
