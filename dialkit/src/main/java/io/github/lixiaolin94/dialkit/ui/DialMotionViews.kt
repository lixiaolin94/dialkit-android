package io.github.lixiaolin94.dialkit.ui

import io.github.lixiaolin94.dialkit.model.ControlKey
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import android.os.Bundle
import io.github.lixiaolin94.dialkit.R
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.ScrollView
import android.widget.LinearLayout
import kotlin.math.hypot
import io.github.lixiaolin94.dialkit.CurveMode
import io.github.lixiaolin94.dialkit.PadNode
import io.github.lixiaolin94.dialkit.TransitionNode
import io.github.lixiaolin94.dialkit.model.DialValue
import io.github.lixiaolin94.dialkit.model.PAD_GRID_DIVISIONS
import io.github.lixiaolin94.dialkit.model.PadAxis
import io.github.lixiaolin94.dialkit.model.easingGuideEnd
import io.github.lixiaolin94.dialkit.model.fitEasingGraph
import io.github.lixiaolin94.dialkit.model.formatEase
import io.github.lixiaolin94.dialkit.model.formatSliderValue
import io.github.lixiaolin94.dialkit.model.moveEasingHandle
import io.github.lixiaolin94.dialkit.model.normalizeEase
import io.github.lixiaolin94.dialkit.model.padGridIntersection
import io.github.lixiaolin94.dialkit.model.padValueFromPoint
import io.github.lixiaolin94.dialkit.model.parseEase
import io.github.lixiaolin94.dialkit.model.springParams
import io.github.lixiaolin94.dialkit.model.springProgress

/** DESIGN: finger-sized grab radius around the 12dp point. */
private const val PAD_POINT_TOUCH_RADIUS = 24f

/** DESIGN: hold-to-arm delay for placing the point from empty space; below the 400ms system long press. */
private const val PAD_HOLD_MS = 180L

private fun fraction(v: Float, axis: PadAxis) = ((v - axis.min) / (axis.max - axis.min)).coerceIn(0f, 1f)

/**
 * SOURCE dial-pad-control.ts + `.dialkit-pad-*`: a row of three fields (caption, X, Y) above a square surface
 * with a 6-division grid; the point lives on a plane inset 12dp. A tap places the point (snapping to a grid
 * intersection within 8dp), double tap resets. DESIGN: drags start on the point or after a short hold, so a
 * swipe over the pad still scrolls the panel; no Shift axis lock on touch.
 */
@SuppressLint("ViewConstructor")
internal class PadView(ui: DialUi, private val node: PadNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val xField = padField(ui, node.xLabel, node.x) { v -> node.set(node.state.value.copy(x = v)) }
    private val yField = padField(ui, node.yLabel, node.y) { v -> node.set(node.state.value.copy(y = v)) }
    private val surface = Surface()

    init {
        orientation = VERTICAL
        val fields = LinearLayout(context).apply { orientation = HORIZONTAL }
        fields.addView(
            label(context, node.label, p.textLabel).apply {
                background = roundRect(context, p.surface)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
            },
            llp(0, dp(ROW_HEIGHT), 1f),
        )
        fields.addView(xField.first, llp(0, dp(ROW_HEIGHT), 1f).apply { marginStart = dp(ROW_GAP) })
        fields.addView(yField.first, llp(0, dp(ROW_HEIGHT), 1f).apply { marginStart = dp(ROW_GAP) })
        addView(fields, llp(MATCH, WRAP))
        addView(surface, llp(MATCH, WRAP).apply { topMargin = dp(ROW_GAP) })
        whileAttached {
            xField.second.refresh()
            yField.second.refresh()
            surface.invalidate()
            surface.describeValue()
            node.state.listen {
                xField.second.refresh()
                yField.second.refresh()
                surface.invalidate()
                surface.describeValue()
            }
        }
    }

    /** SOURCE `.dialkit-pad-field`: axis name and an editable mono value, padding 0 12 0 10. */
    private fun padField(ui: DialUi, axisLabel: String, axis: PadAxis, onCommit: (Float) -> Unit): Pair<View, ValueField> {
        val value = ValueField(context, p, mono = true, alignEnd = true, current = {
            val v = if (axis === node.x) node.state.value.x else node.state.value.y
            formatSliderValue(v, axis.step, axis.min, axis.max)
        }) { text ->
            val v = text.toFloatOrNull() ?: return@ValueField false
            onCommit(v)
            true
        }
        val row = LinearLayout(ui.context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(context, p.surface)
            setPadding(dp(10), 0, dp(12), 0)
            addView(label(context, axisLabel, p.textLabel), llp(WRAP, WRAP).apply { marginEnd = dp(4) })
            addView(value, llp(0, WRAP, 1f))
        }
        return row to value
    }

    /**
     * The pad's plane. Accessibility (SOURCE dial-pad-control: labelled "Label: X x, Y y"; arrows move one step,
     * Shift ten; Home resets): move left / right / up / down and reset actions, and the same keys from a hardware
     * keyboard.
     */
    private inner class Surface : View(context) {
        init {
            isFocusable = true
        }

        fun describeValue() {
            val v = node.state.value
            describe(node.label, "${node.xLabel} ${formatSliderValue(v.x, node.x.step, node.x.min, node.x.max)}, " +
                "${node.yLabel} ${formatSliderValue(v.y, node.y.step, node.y.min, node.y.max)}")
        }

        /** SOURCE padValueFromKey: one step (ten with [large]) along the key's axis; Right / Up are positive. */
        private fun move(key: ControlKey, large: Boolean): Boolean {
            val v = node.state.value
            val k = if (large) 10 else 1
            when (key) {
                ControlKey.Left -> node.set(v.copy(x = v.x - node.x.step * k))
                ControlKey.Right -> node.set(v.copy(x = v.x + node.x.step * k))
                ControlKey.Up -> node.set(v.copy(y = v.y + node.y.step * k))
                ControlKey.Down -> node.set(v.copy(y = v.y - node.y.step * k))
                ControlKey.Home -> node.set(node.default)
                else -> return false
            }
            return true
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            event.controlKey(vertical = true)?.let { if (move(it, event.isShiftPressed)) return true }
            return super.onKeyDown(keyCode, event)
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            fun action(id: Int, text: Int) = info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, context.getString(text)))
            action(R.id.dialkit_action_left, R.string.dialkit_move_left)
            action(R.id.dialkit_action_right, R.string.dialkit_move_right)
            action(R.id.dialkit_action_up, R.string.dialkit_move_up)
            action(R.id.dialkit_action_down, R.string.dialkit_move_down)
            action(R.id.dialkit_action_reset, R.string.dialkit_reset)
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
            R.id.dialkit_action_left -> move(ControlKey.Left, false)
            R.id.dialkit_action_right -> move(ControlKey.Right, false)
            R.id.dialkit_action_up -> move(ControlKey.Up, false)
            R.id.dialkit_action_down -> move(ControlKey.Down, false)
            R.id.dialkit_action_reset -> move(ControlKey.Home, false)
            else -> super.performAccessibilityAction(action, arguments)
        }

        private val pointShadow = RadialShadow(dp(10f), 0x33000000, 0.5f) // ≈ box-shadow 0 2px 4px #0003
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val clip = Path()
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val handler = Handler(Looper.getMainLooper())
        private var dragging = false
        private var armed = false
        private var grab = 0f to 0f
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var lastTapUp = 0L
        private val ring = Fade(150) { invalidate() }
        private val arm = Runnable {
            armed = true
            dragging = true
            moved = true
            ring.to(1f)
            parent?.requestDisallowInterceptTouchEvent(true)
            hapticDragStart()
            place(downX, downY)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, w) // aspect-ratio: 1
        }

        private fun inset() = dp(12f)
        private fun plane() = width - inset() * 2
        private fun pointAt(): Pair<Float, Float> {
            val v = node.state.value
            return (inset() + plane() * fraction(v.x, node.x)) to (inset() + plane() * (1 - fraction(v.y, node.y)))
        }

        private fun place(x: Float, y: Float) {
            node.set(
                padValueFromPoint(
                    ((x - grab.first - inset()) / plane()).coerceIn(0f, 1f),
                    ((y - grab.second - inset()) / plane()).coerceIn(0f, 1f),
                    node.x, node.y,
                ),
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    moved = false
                    armed = false
                    val (px, py) = pointAt()
                    if (hypot(e.x - px, e.y - py) <= dp(PAD_POINT_TOUCH_RADIUS)) {
                        // On the point: drag at once, keeping the finger's offset (SOURCE).
                        grab = (e.x - px) to (e.y - py)
                        dragging = true
                        ring.to(1f)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        grab = 0f to 0f
                        dragging = false
                        handler.postDelayed(arm, PAD_HOLD_MS)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) {
                        moved = true
                        // Moved before the hold elapsed: the gesture belongs to the panel scroll.
                        if (!dragging) handler.removeCallbacks(arm)
                    }
                    if (dragging && moved) place(e.x, e.y)
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(arm)
                    if (moved || armed) {
                        if (dragging) place(e.x, e.y) // SOURCE: pointerup also moves
                    } else {
                        // SOURCE: "Snap clicks on release, including clicks on the point itself."
                        grab = 0f to 0f
                        val now = System.currentTimeMillis()
                        if (now - lastTapUp < ViewConfiguration.getDoubleTapTimeout()) {
                            node.set(node.default) // SOURCE dblclick → defaults
                            hapticConfirm()
                            lastTapUp = 0L
                        } else {
                            lastTapUp = now
                            // SOURCE: snap taps to a visible grid intersection; the grid spans the whole square.
                            val snap = padGridIntersection(e.x, e.y, width.toFloat(), height.toFloat(), dp(8f))
                            place(snap?.first ?: e.x, snap?.second ?: e.y)
                            if (snap != null) hapticTick()
                        }
                    }
                    dragging = false
                    ring.to(0f)
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(arm)
                    dragging = false
                    ring.to(0f)
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            clip.reset()
            clip.addRoundRect(r, dp(8f), dp(8f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.color = p.surface
            canvas.drawRect(r, paint)
            paint.color = withAlpha(p.border, 0.65f) // SOURCE grid lines: border, opacity .65
            paint.strokeWidth = dp(1f)
            for (i in 1 until PAD_GRID_DIVISIONS) {
                val x = width * i / PAD_GRID_DIVISIONS.toFloat()
                val y = height * i / PAD_GRID_DIVISIONS.toFloat()
                canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
            }
            paint.color = p.textTertiary
            canvas.drawCircle(width / 2f, height / 2f, dp(1.5f), paint)
            val (px, py) = pointAt()
            if (ring.value > 0f) {
                paint.color = withAlpha(p.borderHover, ring.value)
                canvas.drawCircle(px, py, dp(12f), paint) // SOURCE dragging: 0 0 0 6px border-hover
            }
            pointShadow.draw(canvas, paint, px, py + dp(2f))
            paint.color = p.textPrimary
            canvas.drawCircle(px, py, dp(6f), paint)
            canvas.restore()
        }
    }
}

/**
 * SOURCE TransitionControl.tsx: the curve preview, a Type switch (Easing / Time / Physics) and the mode's
 * controls. Ranges, steps and fallbacks are DialKit's (Duration .1–1, or –2 for easing, step .05; Bounce
 * 0–1/.05; Stiffness 1–1000/10; Damping 1–100/1; Mass .1–10/.1).
 */
@SuppressLint("ViewConstructor")
internal class TransitionContent(private val ui: DialUi, private val node: TransitionNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val modeViews = ArrayList<View>()
    private var builtMode: CurveMode? = null
    private var typeRow: LinearLayout? = null
    private var typeSegments: SegmentedView? = null
    private val spring get() = node.state.value as? DialValue.Spring ?: node.cache.getValue(CurveMode.Simple) as DialValue.Spring
    private val easing get() = node.state.value as? DialValue.Easing ?: node.cache.getValue(CurveMode.Easing) as DialValue.Easing

    init {
        orientation = VERTICAL
        build()
        whileAttached {
            build()
            refresh()
            val a = node.mode.listen { build() }
            val b = node.state.listen { refresh() }
            ({ a(); b() })
        }
    }

    private fun build() {
        val mode = node.mode.value
        if (mode == builtMode) return
        builtMode = mode
        val anchor = typeRow?.let { anchorScroll(it) }
        // The Type row survives mode switches, so accessibility and keyboard focus stay on it; the curve area above
        // and the parameter rows below are rebuilt.
        val typeRow = this.typeRow ?: labeledRow(ui, context.getString(R.string.dialkit_type)).first.also { row ->
            row.addView(
                SegmentedView(context, p, context.getString(R.string.dialkit_type), listOf(context.getString(R.string.dialkit_mode_easing), context.getString(R.string.dialkit_mode_time), context.getString(R.string.dialkit_mode_physics)), mode.ordinal) { node.switchMode(CurveMode.entries[it]) }
                    .also { typeSegments = it },
                llp(WRAP, WRAP).apply { marginEnd = -dp(6) },
            )
            this.typeRow = row
        }
        typeSegments?.selected = mode.ordinal // the mode can also change from a version load or an import
        for (i in childCount - 1 downTo 0) if (getChildAt(i) !== typeRow) removeViewAt(i)
        modeViews.clear()
        fun add(v: View) {
            addView(v, llp(MATCH, WRAP).apply { topMargin = dp(ROW_GAP) })
            modeViews += v
        }
        addView(if (mode == CurveMode.Easing) EasingEditor() else SpringPreview(), 0, llp(MATCH, WRAP))
        if (typeRow.parent == null) addView(typeRow, llp(MATCH, WRAP).apply { topMargin = dp(ROW_GAP) })
        modeViews += getChildAt(0)
        when (mode) {
            CurveMode.Easing -> {
                val (row, _) = labeledRow(ui, context.getString(R.string.dialkit_ease))
                row.setPadding(dp(12), 0, dp(12), 0)
                row.addView(
                    ValueField(context, p, mono = true, alignEnd = true, current = { formatEase(normalizeEase(easing.ease)) }) { text ->
                        val ease = parseEase(text) ?: return@ValueField false
                        node.update(easing.copy(ease = ease))
                        true
                    },
                    llp(0, WRAP, 1.6f),
                )
                add(row)
            }
            CurveMode.Simple -> add(slider(context.getString(R.string.dialkit_bounce), 0f, 1f, 0.05f, { spring.bounce ?: 0.2f }) {
                node.update(spring.copy(stiffness = null, damping = null, mass = null, bounce = it))
            })
            CurveMode.Advanced -> {
                fun physics(update: (DialValue.Spring) -> DialValue.Spring) = node.update(update(spring.copy(visualDuration = null, bounce = null)))
                add(slider(context.getString(R.string.dialkit_stiffness), 1f, 1000f, 10f, { spring.stiffness ?: 400f }) { v -> physics { it.copy(stiffness = v) } })
                add(slider(context.getString(R.string.dialkit_damping), 1f, 100f, 1f, { spring.damping ?: 17f }) { v -> physics { it.copy(damping = v) } })
                add(slider(context.getString(R.string.dialkit_mass), 0.1f, 10f, 0.1f, { spring.mass ?: 1f }) { v -> physics { it.copy(mass = v) } })
            }
        }
        if (mode != CurveMode.Advanced) {
            val isEasing = mode == CurveMode.Easing
            add(slider(context.getString(R.string.dialkit_duration), 0.1f, if (isEasing) 2f else 1f, 0.05f, { if (isEasing) easing.duration else spring.visualDuration ?: 0.3f }) { v ->
                if (isEasing) node.update(easing.copy(duration = v)) else node.update(spring.copy(stiffness = null, damping = null, mass = null, visualDuration = v))
            })
        }
        anchor?.invoke(typeRow)
    }

    /**
     * Scroll anchoring, as browsers do by default (CSS `overflow-anchor: auto`), which is why the web panel does
     * not jump here: the curve area above the Type row is 256:140 for springs but 256:180 for easing (SOURCE
     * styles.css `.dialkit-easing-viz`), so switching modes resizes content above the row just tapped. Keeps
     * that row where it was on screen by scrolling the panel by the size change; only when the row was at or
     * above the visible bottom (changes further down do not move anything in view).
     */
    private fun anchorScroll(old: View): ((View) -> Unit)? {
        val scroll = generateSequence(parent) { it.parent }.firstOrNull { it is ScrollView } as? ScrollView ?: return null
        if (!old.isLaidOut) return null
        val before = offsetIn(old, scroll) - scroll.scrollY
        if (before > scroll.height) return null
        return { new ->
            scroll.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    scroll.viewTreeObserver.removeOnPreDrawListener(this)
                    if (new.isAttachedToWindow) scroll.scrollBy(0, offsetIn(new, scroll) - scroll.scrollY - before)
                    return true
                }
            })
        }
    }

    /** [v]'s top in [scroll]'s content coordinates. */
    private fun offsetIn(v: View, scroll: ScrollView): Int {
        var y = 0
        var cur: View = v
        while (cur.parent !== scroll) {
            y += cur.top
            cur = cur.parent as? View ?: return y
        }
        return y + cur.top
    }

    private fun slider(name: String, min: Float, max: Float, step: Float, read: () -> Float, write: (Float) -> Unit) =
        SliderView(context, p, name, min, max, step, read, write)

    private fun refresh() {
        for (v in modeViews) {
            when (v) {
                is SliderView -> v.sync()
                is LinearLayout -> for (i in 0 until v.childCount) (v.getChildAt(i) as? ValueField)?.refresh()
                else -> v.invalidate()
            }
        }
    }

    /** SOURCE SpringVisualization.tsx: 256×140, 2s sampled in 100 steps, normalised into the middle 60%. */
    private inner class SpringPreview : View(context) {
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // decorative; the parameters are the sliders
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val r = RectF()
        private val clip = Path()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, Math.round(w * 140f / 256f))
        }

        private val samples = DoubleArray(101)
        private var sampledFor: Any? = null
        private var midDash: DashPathEffect? = null

        /** Re-samples only when the spring changes (2s in 100 steps). */
        private fun sample() {
            val simple = node.mode.value == CurveMode.Simple
            val s = spring
            val key = s to simple
            if (key == sampledFor) return
            sampledFor = key
            val params = springParams(
                if (simple) {
                    DialValue.Spring(visualDuration = s.visualDuration ?: 0.3f, bounce = s.bounce ?: 0.2f)
                } else {
                    DialValue.Spring(stiffness = s.stiffness ?: 400f, damping = s.damping ?: 17f, mass = s.mass ?: 1f)
                },
            )
            for (i in samples.indices) samples[i] = springProgress(i / 100.0 * 2.0, params)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val k = w / 256f
            midDash = DashPathEffect(floatArrayOf(4 * k, 4 * k), 0f)
        }

        override fun onDraw(canvas: Canvas) {
            sample()
            val w = width.toFloat()
            val h = height.toFloat()
            val k = w / 256f // SVG viewBox scaling
            r.set(0f, 0f, w, h)
            clip.reset()
            clip.addRoundRect(r, dp(8f), dp(8f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.style = Paint.Style.FILL
            paint.color = p.surface
            canvas.drawRect(r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = k
            paint.color = p.vizGrid
            for (i in 1 until 4) {
                canvas.drawLine(w / 4 * i, 0f, w / 4 * i, h, paint)
                canvas.drawLine(0f, h / 4 * i, w, h / 4 * i, paint)
            }
            paint.color = p.vizMidline
            paint.pathEffect = midDash
            canvas.drawLine(0f, h / 2, w, h / 2, paint)
            paint.pathEffect = null
            val minV = samples.min()
            val range = (samples.max() - minV).takeIf { it != 0.0 } ?: 1.0
            path.reset()
            samples.forEachIndexed { i, v ->
                val x = i / 100f * w
                val y = (h - (((v - minV) / range) * h * 0.6 + h * 0.2)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = p.vizCurve
            paint.strokeWidth = 2 * k
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    /**
     * SOURCE easing-control.ts + easing-geometry.ts: 256:180 graph, both axes fitted equally, draggable control
     * points (x clamped 0–1, y −1–2, rounded to .01). DESIGN: 20dp touch radius instead of the 24px target.
     */
    private inner class EasingEditor : View(context) {
        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the Ease field below edits the same curve
        }

        private val referenceDash = DashPathEffect(floatArrayOf(dp(3f), dp(4f)), 0f) // SOURCE dasharray 3 4
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val r = RectF()
        private val clip = Path()
        private var dragging = -1
        private var downX = 0f
        private var downY = 0f
        private var start: List<Float> = emptyList()
        private var scale = 1f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(w, Math.round(w * 180f / 256f))
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val g = fitEasingGraph(easing.ease, width.toFloat(), height.toFloat(), dp(12f))
                    val d = g.handles.map { hypot(it.x - e.x, it.y - e.y) }
                    val nearest = if (d[0] <= d[1]) 0 else 1
                    if (d[nearest] > dp(20f)) return false // not a handle: let the panel scroll
                    parent?.requestDisallowInterceptTouchEvent(true)
                    dragging = nearest
                    downX = e.x
                    downY = e.y
                    start = easing.ease
                    scale = g.scale
                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (dragging >= 0) {
                    val next = moveEasingHandle(start, dragging, e.x - downX, e.y - downY, scale)
                    if (next != easing.ease) node.update(easing.copy(ease = next))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = -1
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            r.set(0f, 0f, w, h)
            clip.reset()
            clip.addRoundRect(r, dp(8f), dp(8f), Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            paint.style = Paint.Style.FILL
            paint.color = p.surface
            canvas.drawRect(r, paint)
            val g = fitEasingGraph(easing.ease, w, h, dp(12f))
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = withAlpha(p.textTertiary, 0.4f)
            paint.pathEffect = referenceDash
            canvas.drawLine(g.start.x, g.start.y, g.end.x, g.end.y, paint)
            paint.pathEffect = null
            paint.color = p.textTertiary
            val handleR = dp(5f)
            listOf(g.start, g.end).forEachIndexed { i, pt ->
                val end = easingGuideEnd(pt, g.handles[i], handleR)
                canvas.drawLine(pt.x, pt.y, end.x, end.y, paint)
            }
            val (a, b) = g.handles
            path.reset()
            path.moveTo(g.start.x, g.start.y)
            path.cubicTo(a.x, a.y, b.x, b.y, g.end.x, g.end.y)
            paint.color = p.textPrimary
            paint.strokeWidth = dp(2f)
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = p.textSecondary
            listOf(g.start, g.end).forEach { canvas.drawCircle(it.x, it.y, dp(2.5f), paint) }
            g.handles.forEachIndexed { i, pt ->
                val active = dragging == i
                paint.style = Paint.Style.FILL
                paint.color = if (active) p.textPrimary else p.surface
                canvas.drawCircle(pt.x, pt.y, handleR, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f)
                paint.color = if (active) p.textPrimary else p.textSecondary
                canvas.drawCircle(pt.x, pt.y, handleR - dp(0.75f), paint)
            }
            canvas.restore()
        }
    }
}
