package io.github.lixiaolin94.dialkit.ui

import io.github.lixiaolin94.dialkit.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import io.github.lixiaolin94.dialkit.DialKitConfig
import io.github.lixiaolin94.dialkit.overlay.DialKitLayer
import io.github.lixiaolin94.dialkit.overlay.DpPoint
import io.github.lixiaolin94.dialkit.overlay.EntrySide
import io.github.lixiaolin94.dialkit.overlay.OverlayController
import io.github.lixiaolin94.dialkit.overlay.OverlayMode

// Geometry (dp). Values tagged as elsewhere: SOURCE = DialKit 2.0.2, DESIGN = ours.
private const val ENTRY_SIZE = 48f // DESIGN (user): Android's minimum touch target (DialKit: 42)
private const val ENTRY_ICON = 18f // DialKit's 16 / 42 icon ratio, scaled to 48
private const val EDGE_MARGIN = 16f // SOURCE styles.css L185-208
private const val PANEL_WIDTH = 280f // SOURCE Folder.tsx:L162
private const val PANEL_HEIGHT_INSET = 32f // SOURCE Folder.tsx:L162 (innerHeight − 32)
private const val PANEL_MIN_HEIGHT = 160f // DESIGN
private const val PANEL_RADIUS = 14f // SOURCE `.dialkit-panel-inner`
private const val ENTRY_ELEVATION = 10f // DESIGN (user): native shadow, a notch above a FAB's 6dp
private const val PANEL_ELEVATION = 16f // DESIGN
private const val DEFAULT_ASPECT = 16f / 9f // DESIGN (user): default height = width × 16/9, rounded to 10dp
private const val READY_TIMEOUT_MS = 600L // DESIGN: longest a tap waits for the panel to build
private const val RELEASE_PROJECTION_S = 0.1f // DESIGN: fling projection for the entry's edge choice
private const val RUBBER_COEFFICIENT = 0.55f // DESIGN: UIScrollView-style rubber band

private fun rubber(value: Float, min: Float, max: Float, dimension: Float): Float {
    fun band(o: Float) = (1f - 1f / (o * RUBBER_COEFFICIENT / dimension + 1f)) * dimension
    return when {
        value < min -> min - band(min - value)
        value > max -> max + band(value - max)
        else -> value
    }
}

/**
 * The overlay's view tree inside [DialKitLayer]: scrim, the morphing entry/panel container, the resize grabber
 * and the popup layer. Every moving value is a [SpringValue]; a frame only re-lays out the container and sets
 * RenderNode properties on the pre-built panel, so the morph never rebuilds UI.
 */
@SuppressLint("ViewConstructor")
internal class OverlayRoot(
    context: Context,
    private val layer: DialKitLayer,
    config: DialKitConfig,
    private val onHiddenSettled: () -> Unit,
) : ViewGroup(context) {
    private val p = DialPalette.resolve(context, config.theme)
    val popups = PopupLayer(context, p)
    private val ui = DialUi(context, p, popups) { }
    private val scrim = Scrim()
    private val container = Container()
    private val icon = IconView(context, DialIcons.Panel).apply { tint = p.textFocus }
    private val grabber = Grabber()
    private var lastIme = 0
    private var panel: PanelView? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    // ---- live state
    private val progress = SpringValue(if (OverlayController.mode == OverlayMode.Expanded) 1f else 0f, 0.001f) { applyFrame() }
    private val appear = SpringValue(if (OverlayController.mode == OverlayMode.Expanded) 1f else 0f, 0.001f) { applyFrame() }
    private val press = SpringValue(1f, 0.001f) { applyFrame() }
    private val entryX = SpringValue(0f, 0.5f) { applyFrame() }
    private val entryY = SpringValue(0f, 0.5f) { applyFrame() }
    private val panelX = SpringValue(0f, 0.5f) { applyFrame() }
    private val panelY = SpringValue(0f, 0.5f) { applyFrame() }
    private val resize = SpringValue(0f, 0.5f) { applyFrame() }
    private var entryHeld = false
    private var panelHeld = false
    private var resizing = false
    private var entryPlaced = false
    private var naturalHeight = 0
    private var panelReady = false
    private var pendingExpand = false

    // ---- geometry, recomputed on layout
    private var winW = 0f
    private var winH = 0f
    private val safe = RectF()
    private val d = context.resources.displayMetrics.density
    private fun px(dp: Float) = dp * d

    init {
        clipChildren = false
        addView(scrim)
        addView(container)
        addView(grabber)
        // Above the panel's Z: an elevated sibling's shadow is drawn over everything below its Z, which darkened
        // the grabber. No background, so no outline and no shadow of its own.
        grabber.translationZ = px(PANEL_ELEVATION + 1f)
        addView(popups)
        // Siblings dispatch touches and draw in Z order; the panel's native-shadow elevation would otherwise put it
        // above the popups. The popup layer has no background, so this Z casts no shadow of its own.
        popups.translationZ = px(64f)
        container.addView(icon)
        container.contentDescription = context.getString(R.string.dialkit_name)
        scrim.visibility = if (OverlayController.mode == OverlayMode.Expanded) VISIBLE else GONE
        grabber.visibility = scrim.visibility
        if (OverlayController.mode == OverlayMode.Expanded) buildPanel()
    }

    private val back: () -> Boolean = {
        if (popups.isOpen) {
            popups.dismiss()
            true
        } else {
            false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        OverlayController.backHandler = back
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (OverlayController.backHandler === back) OverlayController.backHandler = null
    }

    // ===== mode changes (from ActivityAttachment)

    fun onMode(mode: OverlayMode) {
        when (mode) {
            OverlayMode.Hidden -> {
                collapseUi()
                progress.animateTo(0f, timeSpring(0.3f, 0.1f), onEnd = ::announceLayout)
                appear.animateTo(0f, timeSpring(0.2f, 0f)) { onHiddenSettled() }
            }
            OverlayMode.Entry -> {
                container.isFocusable = true
                collapseUi()
                progress.animateTo(0f, timeSpring(0.3f, 0.1f), onEnd = ::announceLayout)
                appear.animateTo(1f, timeSpring(0.3f, 0.2f)) { buildPanel() } // pre-build after the entry settles
            }
            OverlayMode.Expanded -> {
                container.isFocusable = false
                scrim.visibility = VISIBLE
                grabber.visibility = VISIBLE
                appear.animateTo(1f, timeSpring(0.3f, 0.2f))
                if (panelReady) {
                    progress.animateTo(1f, timeSpring(0.3f, 0.1f), onEnd = ::announceLayout)
                } else {
                    // DESIGN (user): build first, wait for its first layout + two frames, then morph in one go.
                    pendingExpand = true
                    buildPanel()
                    postDelayed({ startPendingExpand() }, READY_TIMEOUT_MS)
                }
            }
        }
    }

    private fun startPendingExpand() {
        if (!pendingExpand || OverlayController.mode != OverlayMode.Expanded) return
        pendingExpand = false
        progress.animateTo(1f, timeSpring(0.3f, 0.1f), onEnd = ::announceLayout)
    }

    /**
     * The container is placed by hand every frame, which sends no accessibility events; once a morph, drag or
     * resize settles, tell TalkBack (and UI automation) that the window's content moved.
     */
    private fun announceLayout() {
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun collapseUi() {
        pendingExpand = false
        popups.dismiss()
        findFocus()?.let { focused ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
        scrim.visibility = GONE
        grabber.visibility = GONE
    }

    private fun buildPanel() {
        if (panel != null) return
        val v = PanelView(ui)
        v.onContentChanged = { naturalHeight = 0; requestLayout() }
        v.alpha = 0f
        attachHeaderGestures(v)
        v.close.setOnClickListener { OverlayController.collapse() }
        container.addView(v, 0)
        panel = v
        requestLayout()
        // Ready = measured, laid out and drawn twice.
        viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                Choreographer.getInstance().postFrameCallback {
                    Choreographer.getInstance().postFrameCallback {
                        panelReady = true
                        startPendingExpand()
                    }
                }
                return true
            }
        })
    }

    // ===== geometry

    private val panelWidth get() = min(px(PANEL_WIDTH), safe.width() - 2 * px(EDGE_MARGIN))
    private val minHeight get() = px(PANEL_MIN_HEIGHT)
    private val maxHeight get() = max(minHeight, safe.height() - px(PANEL_HEIGHT_INSET))
    private val growLimit get() = max(min(naturalHeight.toFloat(), maxHeight), minHeight)
    private val restingHeight: Float
        get() {
            val defaultH = px(Math.round(panelWidth / d * DEFAULT_ASPECT / 10f) * 10f)
            val auto = min(min(naturalHeight.toFloat(), defaultH), maxHeight)
            val pinned = OverlayController.pinnedHeightDp?.let { px(it).coerceIn(minHeight, maxHeight) }?.let { min(it, growLimit) }
            return pinned ?: auto
        }
    private val liveHeight get() = if (resizing) resize.value else restingHeight

    private fun entryTarget(side: EntrySide = OverlayController.entrySide, fraction: Float = OverlayController.entryFraction): Pair<Float, Float> {
        val m = px(EDGE_MARGIN)
        val e = px(ENTRY_SIZE)
        val x = if (side == EntrySide.Start) safe.left + m else safe.right - m - e
        val travel = max(0f, safe.height() - 2 * m - e)
        return x to safe.top + m + fraction.coerceIn(0f, 1f) * travel
    }

    private fun entryFraction(y: Float): Float {
        val m = px(EDGE_MARGIN)
        val travel = safe.height() - 2 * m - px(ENTRY_SIZE)
        return if (travel <= 0f) 0f else ((y - safe.top - m) / travel).coerceIn(0f, 1f)
    }

    private fun panelXRange() = (safe.left + px(EDGE_MARGIN))..max(safe.left + px(EDGE_MARGIN), safe.right - px(EDGE_MARGIN) - panelWidth)
    private fun panelYRange(h: Float) = (safe.top + px(EDGE_MARGIN))..max(safe.top + px(EDGE_MARGIN), safe.bottom - px(EDGE_MARGIN) - h)
    private fun clampPanel(x: Float, y: Float, h: Float = restingHeight) = x.coerceIn(panelXRange()) to y.coerceIn(panelYRange(h))

    /** SOURCE panel-drag.ts: open toward the corner nearest the collapsed button, sharing that corner. */
    private fun panelTarget(): Pair<Float, Float> {
        OverlayController.panelOffsetDp?.let { return clampPanel(px(it.x), px(it.y)) }
        val (ex, ey) = entryTarget()
        val e = px(ENTRY_SIZE)
        val h = restingHeight
        val x = if (ex + e / 2 > winW / 2) ex + e - panelWidth else ex
        val y = if (ey + e / 2 > winH / 2) ey + e - h else ey
        return clampPanel(x, y, h)
    }

    private fun livePanelPos() = if (panelHeld) panelX.value to panelY.value else panelTarget()
    private fun maxHeightBelow(top: Float) = max(minHeight, min(min(maxHeight, growLimit), safe.bottom - px(EDGE_MARGIN) - top))

    // ===== layout and per-frame placement

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        scrim.measure(exactW, exactH)
        popups.measure(exactW, exactH)
        grabber.measure(MeasureSpec.makeMeasureSpec(Math.round(px(120f)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.round(px(28f)), MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        winW = (r - l).toFloat()
        winH = (b - t).toFloat()
        val i = layer.insets
        safe.set(i.left.toFloat(), i.top.toFloat(), winW - i.right, winH - max(i.bottom, i.ime))
        // SOURCE Escape cancels a slider's input. On API 33+ Back first goes to the IME, which only hides itself,
        // so a keyboard that closes while a slider is being typed into counts as Escape.
        if (lastIme > 0 && i.ime == 0) (findFocus()?.parent as? SliderView)?.onImeHidden()
        lastIme = i.ime
        scrim.layout(0, 0, r - l, b - t)
        popups.viewport.set(safe.left.toInt(), safe.top.toInt(), safe.right.toInt(), safe.bottom.toInt())
        popups.layout(0, 0, r - l, b - t)
        // Natural height follows content changes (folders opening, panels registering) on every layout pass.
        panel?.let { if (panelWidth > 0) naturalHeight = it.naturalHeight(panelWidth.roundToInt()) }
        if (!entryHeld) {
            val (x, y) = entryTarget()
            if (!entryPlaced || !(entryX.isRunning || entryY.isRunning)) {
                entryX.snapTo(x)
                entryY.snapTo(y)
            }
            entryPlaced = true
        }
        applyFrame()
    }

    private fun applyFrame() {
        if (winW == 0f) return
        val pr = progress.value
        val pc = pr.coerceIn(0f, 1f)
        val e = px(ENTRY_SIZE)
        val eL = entryX.value
        val eT = entryY.value
        val (pL, pT) = livePanelPos()
        val pW = panelWidth
        val pH = liveHeight
        val left = lerp(eL, pL, pr)
        val top = lerp(eT, pT, pr)
        val w = max(0f, lerp(e, pW, pr))
        val h = max(0f, lerp(e, pH, pr))
        container.radius = lerp(e / 2, px(PANEL_RADIUS), pc)
        container.layout(left.roundToInt(), top.roundToInt(), (left + w).roundToInt(), (top + h).roundToInt())
        container.invalidateOutline()
        container.invalidate()
        val a = appear.value.coerceIn(0f, 1f)
        val s = (0.5f + 0.5f * appear.value) * press.value // DESIGN appear from 0.5
        container.pivotX = w / 2
        container.pivotY = h / 2
        container.scaleX = s
        container.scaleY = s
        container.alpha = a
        // Rising with the appear animation also guarantees the native shadow is drawn from the first frame.
        container.elevation = lerp(px(ENTRY_ELEVATION), px(PANEL_ELEVATION), pc) * a

        val iconSize = px(ENTRY_ICON).roundToInt()
        icon.alpha = 1f - (pr / 0.3f).coerceIn(0f, 1f)
        icon.measure(MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY))
        val iL = ((w - iconSize) / 2).roundToInt()
        val iT = ((h - iconSize) / 2).roundToInt()
        icon.layout(iL, iT, iL + iconSize, iT + iconSize)

        panel?.let { v ->
            val wi = pW.roundToInt()
            val hi = pH.roundToInt().coerceAtLeast(0)
            if (v.measuredWidth != wi || v.measuredHeight != hi || v.isLayoutRequested) {
                v.measure(MeasureSpec.makeMeasureSpec(wi, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(hi, MeasureSpec.EXACTLY))
                v.layout(0, 0, wi, hi)
            }
            // DESIGN (user): scale uniformly to fit the morphing bounds instead of being clipped, pinned to the
            // corner the panel grows from (the one it shares with the entry).
            val scale = min(1f, min(w / pW, h / pH)).coerceAtLeast(0f)
            val fromRight = eL + e / 2 > pL + pW / 2
            val fromBottom = eT + e / 2 > pT + pH / 2
            v.pivotX = 0f
            v.pivotY = 0f
            v.scaleX = scale
            v.scaleY = scale
            v.translationX = if (fromRight) w - pW * scale else 0f
            v.translationY = if (fromBottom) h - pH * scale else 0f
            v.alpha = ((pr - 0.2f) / 0.5f).coerceIn(0f, 1f)
        }

        if (grabber.visibility == VISIBLE) {
            val gx = (pL + pW / 2 - px(60f)).roundToInt()
            val gy = (pT + pH).roundToInt()
            grabber.layout(gx, gy, gx + grabber.measuredWidth, gy + grabber.measuredHeight)
            grabber.alpha = ((pr - 0.2f) / 0.5f).coerceIn(0f, 1f)
        }

        // Entry hit area for the layer's touch gating (scale about the centre included).
        val cx = left + w / 2
        val cy = top + h / 2
        layer.entryBounds.set((cx - w * s / 2).toInt(), (cy - h * s / 2).toInt(), (cx + w * s / 2).toInt(), (cy + h * s / 2).toInt())
    }

    // ===== gestures

    /**
     * Velocity from screen coordinates. The dragged views move with the finger, so their local coordinates
     * (what addMovement reads) barely change and would report ~0 velocity.
     */
    private fun VelocityTracker.addRaw(e: MotionEvent) {
        val copy = MotionEvent.obtain(e)
        copy.setLocation(e.rawX, e.rawY)
        addMovement(copy)
        copy.recycle()
    }

    /** Panel header: drag moves the panel (rubber-banded at the safe area), a tap collapses. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachHeaderGestures(v: PanelView) {
        var downX = 0f
        var downY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        var tracker: VelocityTracker? = null
        v.header.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                    tracker = VelocityTracker.obtain().also { it.addRaw(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addRaw(e)
                    if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                        dragging = true
                        val k = slop / hypot(e.rawX - downX, e.rawY - downY) // only the part past the slop moves
                        downX += (e.rawX - downX) * k
                        downY += (e.rawY - downY) * k
                        v.header.parent?.requestDisallowInterceptTouchEvent(true)
                        val (x, y) = livePanelPos()
                        startX = x
                        startY = y
                        panelHeld = true
                        panelX.snapTo(x)
                        panelY.snapTo(y)
                    }
                    if (dragging) {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        val xr = panelXRange()
                        val yr = panelYRange(restingHeight)
                        panelX.snapTo(rubber(startX + dx, xr.start, xr.endInclusive, winW))
                        panelY.snapTo(rubber(startY + dy, yr.start, yr.endInclusive, winH))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addRaw(e)
                    if (dragging) {
                        val (tx, ty) = clampPanel(startX + e.rawX - downX, startY + e.rawY - downY)
                        OverlayController.panelOffsetDp = DpPoint(tx / d, ty / d)
                        val vt = tracker?.apply { computeCurrentVelocity(1000) }
                        val spec = timeSpring(0.35f, 0.15f) // DESIGN
                        panelX.animateTo(tx, spec, vt?.xVelocity ?: 0f)
                        panelY.animateTo(ty, spec, vt?.yVelocity ?: 0f) {
                            panelHeld = false
                            announceLayout()
                        }
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        OverlayController.collapse()
                    }
                    tracker?.recycle()
                    tracker = null
                    dragging = false
                }
            }
            true
        }
    }

    /** The morphing box. Collapsed: takes the entry's tap and drag itself. Expanded: empty space ends text editing. */
    private inner class Container : ViewGroup(context) {
        var radius = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragging = false
        private var tracker: VelocityTracker? = null

        init {
            setWillNotDraw(false)
            clipChildren = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
            clipToOutline = true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(0, 0)
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = Unit // children placed in applyFrame

        override fun onDraw(canvas: Canvas) {
            paint.style = Paint.Style.FILL
            paint.color = p.glassBg
            r.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(r, radius, radius, paint)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val stroke = px(1f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = p.border
            r.set(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
            canvas.drawRoundRect(r, radius - stroke / 2, radius - stroke / 2, paint)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = OverlayController.mode == OverlayMode.Entry

        // The collapsed entry opens from a screen reader's double tap and from Enter on a hardware keyboard.
        override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            if (OverlayController.mode == OverlayMode.Entry) {
                info.isClickable = true
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            }
        }

        override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
            if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK && OverlayController.mode == OverlayMode.Entry) {
                OverlayController.expand()
                return true
            }
            return super.performAccessibilityAction(action, arguments)
        }

        override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
            if (OverlayController.mode == OverlayMode.Entry && event.isActivation()) {
                OverlayController.expand()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        /**
         * As Compose's detectDragGestures: once past the slop (Euclidean), the drag delivers only the part beyond
         * it, so the entry neither jumps by the slop nor lags by a whole first move event.
         */
        private fun passSlop(e: MotionEvent) {
            val k = slop / hypot(e.rawX - downX, e.rawY - downY)
            downX += (e.rawX - downX) * k
            downY += (e.rawY - downY) * k
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (OverlayController.mode != OverlayMode.Entry) {
                if (e.actionMasked == MotionEvent.ACTION_UP) clearFocusAndIme()
                return true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                    tracker = VelocityTracker.obtain().also { it.addRaw(e) }
                    press.animateTo(0.9f, timeSpring(0.15f, 0.3f)) // SOURCE Folder.tsx whileTap
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addRaw(e)
                    if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                        dragging = true
                        entryHeld = true
                        startX = entryX.value
                        startY = entryY.value
                        passSlop(e)
                        press.animateTo(1f, timeSpring(0.15f, 0.3f))
                    }
                    if (dragging) {
                        entryX.snapTo(startX + e.rawX - downX)
                        entryY.snapTo(startY + e.rawY - downY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addRaw(e)
                    press.animateTo(1f, timeSpring(0.15f, 0.3f))
                    if (dragging) {
                        val vt = tracker?.apply { computeCurrentVelocity(1000) }
                        releaseEntry(vt?.xVelocity ?: 0f, vt?.yVelocity ?: 0f)
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        OverlayController.expand()
                    }
                    tracker?.recycle()
                    tracker = null
                    dragging = false
                }
            }
            return true
        }
    }

    private fun releaseEntry(vx: Float, vy: Float) {
        val x = entryX.value
        val y = entryY.value
        val side = if (x + px(ENTRY_SIZE) / 2 + vx * RELEASE_PROJECTION_S < winW / 2) EntrySide.Start else EntrySide.End
        val fraction = entryFraction(y + vy * RELEASE_PROJECTION_S)
        OverlayController.moveEntry(side, fraction)
        OverlayController.panelOffsetDp = null // re-anchor the panel to the moved entry
        val (tx, ty) = entryTarget(side, fraction)
        val spec = timeSpring(0.35f, 0.15f) // DESIGN
        entryX.animateTo(tx, spec, vx)
        entryY.animateTo(ty, spec, vy) {
            entryHeld = false
            announceLayout()
        }
    }

    fun clearFocusAndIme() {
        findFocus()?.let { focused ->
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(focused.windowToken, 0)
            focused.clearFocus()
        }
    }

    /** Outside tap collapses; every touch is kept from the host. */
    private inner class Scrim : View(context) {
        private var downX = 0f
        private var downY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                }
                MotionEvent.ACTION_UP -> if (abs(e.x - downX) + abs(e.y - downY) < slop) OverlayController.collapse()
            }
            return true
        }
    }

    /** Resize grabber under the panel. DESIGN: drag to pin a height, double-tap to return to auto. */
    private inner class Grabber : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.grabber }
        private var downY = 0f
        private var raw = 0f
        private var top = 0f
        private var dragging = false
        private var lastTap = 0L
        private var atLimit = false
        private var tracker: VelocityTracker? = null

        override fun onDraw(canvas: Canvas) {
            val w = px(36f)
            val h = px(4f)
            val l = (width - w) / 2
            val t = px(6f)
            canvas.drawRoundRect(l, t, l + w, t + h, h / 2, h / 2, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY
                    dragging = false
                    tracker = VelocityTracker.obtain().also { it.addRaw(e) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addRaw(e)
                    if (!dragging && abs(e.rawY - downY) > slop) {
                        dragging = true
                        raw = liveHeight
                        val (x, y) = livePanelPos()
                        top = y
                        // Freeze the top edge so only the bottom follows the finger.
                        OverlayController.panelOffsetDp = DpPoint(x / d, y / d)
                        resizing = true
                        resize.snapTo(raw)
                        atLimit = false
                        downY += if (e.rawY > downY) slop.toFloat() else -slop.toFloat() // only the part past the slop
                    }
                    if (dragging) {
                        val h = raw + e.rawY - downY
                        val limit = h <= minHeight || h >= maxHeightBelow(top)
                        if (limit && !atLimit) hapticBoundary()
                        atLimit = limit
                        resize.snapTo(rubber(h, minHeight, maxHeightBelow(top), winH))
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addRaw(e)
                    if (dragging) {
                        val target = (raw + e.rawY - downY).coerceIn(minHeight, maxHeightBelow(top))
                        OverlayController.pinHeight(target / d)
                        val vy = tracker?.apply { computeCurrentVelocity(1000) }?.yVelocity ?: 0f
                        resize.animateTo(target, timeSpring(0.35f, 0.15f), vy) { // DESIGN
                            resizing = false
                            announceLayout()
                        }
                    } else if (e.actionMasked == MotionEvent.ACTION_UP) {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < ViewConfiguration.getDoubleTapTimeout()) {
                            OverlayController.pinHeight(null)
                            applyFrame()
                            hapticConfirm()
                        }
                        lastTap = now
                    }
                    tracker?.recycle()
                    tracker = null
                    dragging = false
                }
            }
            return true
        }
    }
}
