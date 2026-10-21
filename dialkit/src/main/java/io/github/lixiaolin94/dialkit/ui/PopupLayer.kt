package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ScrollView
import kotlin.math.max
import kotlin.math.min

/** How a popup is sized, placed and animated. Values per DialKit variant (SOURCE noted at the call sites). */
internal class PopupSpec(
    val gapDp: Float,
    val maxHeightDp: Float,
    /** null = the anchor's width (select, versions); otherwise a fixed width clamped to the viewport. */
    val widthDp: Float?,
    val radiusDp: Float,
    val background: Int,
    val elevationDp: Float,
    val offsetDp: Float,
    val startScale: Float,
    /** true = spring(0.15, 0); false = 0.16s ease-out (the colour popover's CSS keyframes). */
    val springed: Boolean,
    /** true = the card's right edge lines up with the anchor's (menus opened from a toolbar button). */
    val alignEnd: Boolean = false,
)

/**
 * One floating layer above the panel (dropdowns, version menu, color picker), inside the overlay rather than a
 * separate window, so the layer's touch rules and z-order stay in one place. SOURCE dropdown-position.ts:
 * below the trigger with a gap, flipped above when there is not enough room below and more above; 8dp viewport
 * margin; capped height.
 */
@SuppressLint("ViewConstructor")
internal class PopupLayer(context: Context, private val palette: DialPalette) : FrameLayout(context) {
    private class Shown(val owner: Any, val anchor: Rect, val spec: PopupSpec, val card: FrameLayout)

    private var shown: Shown? = null
    private val catcher = object : View(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_UP) dismiss()
            return true // outside taps never reach the panel or the scrim
        }
    }
    private val listeners = ArrayList<() -> Unit>()
    private val progress = SpringValue(0f, threshold = 0.002f) { applyMotion() }
    private var above = false

    /** The safe area popups are clamped to, in this layer's coordinates. */
    var viewport = Rect()

    fun isOpenFor(owner: Any) = shown?.owner === owner

    val isOpen: Boolean get() = shown != null

    fun onChange(listener: () -> Unit): () -> Unit {
        listeners += listener
        return { listeners -= listener }
    }

    /** [anchorView] is measured relative to this layer at show time. */
    fun show(owner: Any, anchorView: View, spec: PopupSpec, content: View) {
        removeShown()
        val a = IntArray(2)
        val me = IntArray(2)
        anchorView.getLocationInWindow(a)
        getLocationInWindow(me)
        val anchor = Rect(a[0] - me[0], a[1] - me[1], a[0] - me[0] + anchorView.width, a[1] - me[1] + anchorView.height)
        val card = BorderedCard(context, spec.background, palette.border, dp(spec.radiusDp)).apply {
            elevation = dp(spec.elevationDp) // DESIGN (user): native platform shadow
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, dp(spec.radiusDp))
            }
            clipToOutline = true
            isClickable = true // taps inside never dismiss
            addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                addView(content)
            })
        }
        addView(catcher, LayoutParams(MATCH, MATCH))
        addView(card, LayoutParams(WRAP, WRAP))
        shown = Shown(owner, anchor, spec, card)
        // Opened from a hardware keyboard: move focus into the popup (SOURCE popovers take focus).
        if (!isInTouchMode) card.post { card.requestFocus() }
        progress.snapTo(0f)
        if (spec.springed) {
            progress.animateTo(1f, timeSpring(0.15f, 0f))
        } else {
            card.animate().cancel()
            progress.snapTo(0f)
            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 160
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { progress.snapTo(it.animatedValue as Float) }
                start()
            }
        }
        listeners.toList().forEach { it() }
    }

    fun dismiss() {
        if (shown == null) return
        removeShown()
        listeners.toList().forEach { it() }
    }

    private fun removeShown() {
        val s = shown ?: return
        removeView(catcher)
        removeView(s.card)
        shown = null
    }

    private fun applyMotion() {
        val s = shown ?: return
        val p = progress.value
        s.card.alpha = p.coerceIn(0f, 1f)
        val scale = s.spec.startScale + (1 - s.spec.startScale) * p
        s.card.scaleX = scale
        s.card.scaleY = scale
        val dy = dp(s.spec.offsetDp) * (1 - p)
        s.card.translationY = if (above) dy else if (s.spec.springed) -dy else dy
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        catcher.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        val s = shown ?: return
        val margin = dp(8f)
        val width = min(s.spec.widthDp?.let { dp(it) } ?: s.anchor.width().toFloat(), max(0f, viewport.width() - margin * 2))
        val gap = dp(s.spec.gapDp)
        val spaceBelow = max(0f, viewport.bottom - s.anchor.bottom - gap - margin)
        val spaceAbove = max(0f, s.anchor.top - viewport.top - gap - margin)
        // Natural height first (min width = anchor for select/versions), then cap and flip.
        val minW = if (s.spec.widthDp == null) s.anchor.width() else Math.round(width)
        s.card.measure(
            MeasureSpec.makeMeasureSpec(Math.round(width), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(Math.round(dp(s.spec.maxHeightDp)), MeasureSpec.AT_MOST),
        )
        val cardW = max(minW, s.card.measuredWidth).coerceAtMost(Math.round(width))
        val natural = s.card.measuredHeight.toFloat()
        above = spaceBelow < natural && spaceAbove > spaceBelow
        val height = min(natural, min(dp(s.spec.maxHeightDp), if (above) spaceAbove else spaceBelow))
        s.card.measure(
            MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(Math.round(height), MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        catcher.layout(0, 0, r - l, b - t)
        val s = shown ?: return
        val margin = dp(8f)
        val gap = dp(s.spec.gapDp)
        val cw = s.card.measuredWidth
        val ch = s.card.measuredHeight
        val start = if (s.spec.alignEnd) (s.anchor.right - cw).toFloat() else s.anchor.left.toFloat()
        val left = max(viewport.left + margin, min(start, viewport.right - cw - margin))
        val top = max(viewport.top + margin, if (above) s.anchor.top - ch - gap else s.anchor.bottom + gap)
        s.card.pivotX = cw / 2f
        s.card.pivotY = if (above) ch.toFloat() else 0f
        s.card.layout(Math.round(left), Math.round(top), Math.round(left) + cw, Math.round(top) + ch)
        applyMotion()
    }
}

/**
 * Popup card: fill over the whole bounds, children, then a 1dp border inside the edge (SOURCE `border: 1px solid
 * var(--dial-border)`), drawn like the panel's frame. A GradientDrawable stroke straddles its fill's edge, so the
 * translucent border's outer half blended with the shadow behind and read thinner and darker.
 */
@SuppressLint("ViewConstructor")
private class BorderedCard(context: Context, private val fill: Int, private val border: Int, private val radius: Float) : FrameLayout(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val r = android.graphics.RectF()

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        paint.style = android.graphics.Paint.Style.FILL
        paint.color = fill
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(r, radius, radius, paint)
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        super.dispatchDraw(canvas)
        val stroke = dp(1f)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = border
        r.set(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        canvas.drawRoundRect(r, radius - stroke / 2, radius - stroke / 2, paint)
    }
}

/** SOURCE `.dialkit-select-dropdown` / SelectControl.tsx: glass-bg, radius 8, y ±8, scale .95, spring(0.15, 0). */
internal fun selectSpec(p: DialPalette) = PopupSpec(4f, 320f, null, 8f, p.glassBg, 8f, 8f, 0.95f, springed = true)

/** SOURCE `.dialkit-preset-dropdown` / PresetManager.tsx: dropdown-bg, radius 12, y 4, scale .97. */
internal fun presetSpec(p: DialPalette) = PopupSpec(4f, 320f, null, 12f, p.dropdownBg, 8f, 4f, 0.97f, springed = true)

/** DESIGN: the Import menu — presetSpec's look, fixed width, right-aligned under its button. */
internal fun importSpec(p: DialPalette) = PopupSpec(4f, 320f, 200f, 12f, p.dropdownBg, 8f, 4f, 0.97f, springed = true, alignEnd = true)

/** SOURCE `.dialkit-image-popover` + position(): 320 wide, max 560, gap 8, radius 14; enters like the colour popover. */
internal fun imageSpec(p: DialPalette) = PopupSpec(8f, 560f, 320f, 14f, p.glassBg, 16f, 3f, 0.98f, springed = false)

/** SOURCE `.dialkit-color-popover`: 280 wide, radius 14, gap 8, max 480, keyframes translateY(3) scale .98. */
internal fun colorSpec(p: DialPalette) = PopupSpec(8f, 480f, 280f, 14f, p.glassBg, 16f, 3f, 0.98f, springed = false)
