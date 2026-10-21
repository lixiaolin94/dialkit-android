package io.github.lixiaolin94.dialkit.ui

import io.github.lixiaolin94.dialkit.model.optionKeyIndex
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import android.os.Bundle
import io.github.lixiaolin94.dialkit.R
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.ActionNode
import io.github.lixiaolin94.dialkit.ColorNode
import io.github.lixiaolin94.dialkit.DialCell
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialNode
import io.github.lixiaolin94.dialkit.FolderNode
import io.github.lixiaolin94.dialkit.ImageNode
import io.github.lixiaolin94.dialkit.PadNode
import io.github.lixiaolin94.dialkit.SelectNode
import io.github.lixiaolin94.dialkit.SliderNode
import io.github.lixiaolin94.dialkit.TextNode
import io.github.lixiaolin94.dialkit.ToggleNode
import io.github.lixiaolin94.dialkit.TransitionNode

/** What every control needs from the panel: colours, the popup layer and how actions are dispatched. */
internal class DialUi(
    val context: Context,
    val palette: DialPalette,
    val popups: PopupLayer,
    val onAction: (ActionNode) -> Unit,
)

/**
 * A group's controls as DialKit's `.dialkit-folder-inner`: a column with 6dp gaps, where nested folders (and
 * transitions, which render as folders) carry their own 4dp margins and hairline borders, and adjacent folders
 * collapse into one divider. SOURCE styles.css `.dialkit-folder:not(.dialkit-folder-root)` rules.
 */
@SuppressLint("ViewConstructor")
internal class ControlList(ui: DialUi, group: DialGroup, isRoot: Boolean) : LinearLayout(ui.context) {
    init {
        orientation = VERTICAL
        val nodes = group.nodes
        fun DialNode?.foldsLike() = this is FolderNode || this is TransitionNode
        val folderCount = nodes.count { it.foldsLike() }
        nodes.forEachIndexed { index, node ->
            val prevIsFolder = nodes.getOrNull(index - 1).foldsLike()
            val isFolder = node.foldsLike()
            val isLast = index == nodes.lastIndex
            val loneFirstFolder = isRoot && index == 0 && isFolder && folderCount == 1
            val gap = when {
                index == 0 -> if (isFolder && !loneFirstFolder) 4 else 0
                isFolder && prevIsFolder -> 0 // 4 (prev margin) + 6 (gap) − 10 (folder + folder)
                else -> 6 + (if (prevIsFolder) 4 else 0) + (if (isFolder) 4 else 0)
            }
            val borderTop = !prevIsFolder && !loneFirstFolder
            val view: View = when (node) {
                is SliderNode -> SliderView(ui.context, ui.palette, node.label, node.min, node.max, node.step, { node.value }, { node.value = it })
                    .also { v -> v.whileAttached { v.sync(); node.cell.listen { v.sync() } } }
                is ToggleNode -> ToggleRow(ui, node)
                is ActionNode -> ActionButton(ui, node.label) { ui.onAction(node) }
                is SelectNode -> SelectRow(ui, node)
                is ImageNode -> ImageRow(ui, node)
                is TextNode -> TextRow(ui, node.label, node.state, node.placeholder, maxLines = 5)
                is ColorNode -> ColorRow(ui, node)
                is PadNode -> PadView(ui, node)
                is TransitionNode -> FolderView(ui, node.label, node.open, borderTop, !isLast) { TransitionContent(ui, node) }
                is FolderNode -> FolderView(ui, node.label, node.open, borderTop, !isLast) { ControlList(ui, node.group, isRoot = false) }
            }
            addView(view, LayoutParams(MATCH, WRAP).apply { topMargin = dp(gap) })
        }
    }
}

/**
 * SOURCE SegmentedControl.tsx: a sliding pill behind the active option, 0.2s cubic(.25,1,.5,1). Buttons pad
 * 6/8, 13/500; the container pads 2. [fill] gives equal segments across the width (`.dialkit-color-formats`).
 *
 * Accessibility: an Off/On pair is a Switch (double-tap toggles); otherwise the selected option is the state,
 * a click moves to the next option and each option has a "Select …" action. Left / Right / Home / End from a
 * hardware keyboard (SOURCE handleSegmentKey, wrapping).
 */
@SuppressLint("ViewConstructor")
internal class SegmentedView(
    context: Context,
    private val palette: DialPalette,
    /** What a screen reader calls the control (its row's label). */
    private val label: String,
    private val options: List<String>,
    selected: Int,
    private val fill: Boolean = false,
    /** Off/On semantics for the feedback (TOGGLE_ON / OFF) instead of SEGMENT_TICK. */
    private val isToggle: Boolean = false,
    private val onSelect: (Int) -> Unit,
) : View(context) {
    var selected: Int = selected
        set(v) {
            if (field == v) return
            field = v
            describe()
            animatePill()
            invalidate()
        }

    init {
        isFocusable = true
        describe()
    }

    private fun describe() = describe(label, options[selected])

    /** Selects [index] as the user did, through any input. */
    private fun choose(index: Int, touch: Boolean) {
        if (index == selected) return
        if (touch) if (isToggle) hapticToggle(index == 1) else hapticSegment()
        selected = index
        onSelect(index)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(13f)
        typeface = DialFonts.medium
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var pillLeft = -1f
    private var pillWidth = 0f
    private var animator: ValueAnimator? = null

    private fun pad() = dp(2f)
    private fun segmentWidths(): FloatArray {
        val natural = FloatArray(options.size) { textPaint.measureText(options[it]) + dp(16f) }
        if (!fill || width == 0) return natural
        val each = (width - pad() * 2) / options.size
        return FloatArray(options.size) { each }
    }

    private fun segmentLeft(index: Int): Float = pad() + segmentWidths().take(index).sum()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Buttons pad 6 around a 13px line (~15.4 at CSS `line-height: normal`, as the Compose port measured).
        val h = Math.round(dp(6f) * 2 + dp(15.4f) + pad() * 2)
        val w = if (fill) MeasureSpec.getSize(widthMeasureSpec) else Math.round(segmentWidths().sum() + pad() * 2)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pillLeft = segmentLeft(selected)
        pillWidth = segmentWidths()[selected]
    }

    private fun animatePill() {
        val fromL = pillLeft
        val fromW = pillWidth
        val toL = segmentLeft(selected)
        val toW = segmentWidths()[selected]
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = SegmentEasing
            addUpdateListener {
                val t = it.animatedValue as Float
                pillLeft = lerp(fromL, toL, t)
                pillWidth = lerp(fromW, toW, t)
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = palette.surfaceActive
        rect.set(pillLeft, pad(), pillLeft + pillWidth, height - pad())
        canvas.drawRoundRect(rect, dp(6f), dp(6f), paint) // SOURCE pill radius 6
        val widths = segmentWidths()
        var x = pad()
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        options.forEachIndexed { i, text ->
            textPaint.color = if (i == selected) palette.textPrimary else palette.textLabel
            canvas.drawText(text, x + (widths[i] - textPaint.measureText(text)) / 2, baseline, textPaint)
            x += widths[i]
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val widths = segmentWidths()
            var x = pad()
            for (i in widths.indices) {
                if (e.x >= x && e.x < x + widths[i]) {
                    choose(i, touch = true)
                    break
                }
                x += widths[i]
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        event.controlKey(vertical = false)?.let { key ->
            optionKeyIndex(key, selected, options.size, wrap = true)?.let {
                choose(it, touch = false)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun getAccessibilityClassName(): CharSequence = if (isToggle) "android.widget.Switch" else super.getAccessibilityClassName()

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isClickable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        if (isToggle) {
            info.isCheckable = true
            info.isChecked = selected == 1
        } else {
            options.forEachIndexed { i, option ->
                if (i != selected) {
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(OPTION_ACTION_BASE + i, context.getString(R.string.dialkit_select_option, option)))
                }
            }
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when {
        action == AccessibilityNodeInfo.ACTION_CLICK -> {
            choose((selected + 1) % options.size, touch = false)
            true
        }
        action >= OPTION_ACTION_BASE && action < OPTION_ACTION_BASE + options.size -> {
            choose(action - OPTION_ACTION_BASE, touch = false)
            true
        }
        else -> super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        // Custom action ids need only be unique within the node; resource ids start far above this.
        const val OPTION_ACTION_BASE = 0x01000000
    }
}

/** A 36dp row on surface, padded like `.dialkit-labeled-control` (2 10 2 12). */
internal fun labeledRow(ui: DialUi, labelText: String): Pair<LinearLayout, TextView> {
    val row = LinearLayout(ui.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(context, ui.palette.surface)
        setPadding(dp(12), dp(2), dp(10), dp(2))
        minimumHeight = dp(ROW_HEIGHT)
        // The segmented control's −6dp end margin (SOURCE margin-right: -6px) reaches into this padding.
        clipToPadding = false
        clipChildren = false
    }
    val text = label(ui.context, labelText, ui.palette.textLabel)
    row.addView(text, llp(0, WRAP, 1f))
    return row to text
}

/** Off/On segmented control in a labeled row. SOURCE Toggle.tsx. */
@SuppressLint("ViewConstructor")
internal class ToggleRow(ui: DialUi, node: ToggleNode) : FrameLayout(ui.context) {
    init {
        val (row, _) = labeledRow(ui, node.label)
        val segmented = SegmentedView(ui.context, ui.palette, node.label, listOf(ui.context.getString(R.string.dialkit_off), ui.context.getString(R.string.dialkit_on)), if (node.state.value) 1 else 0, isToggle = true) {
            node.state.value = it == 1
        }
        row.addView(segmented, llp(WRAP, WRAP).apply { marginEnd = -dp(6) }) // SOURCE margin-right: -6px
        addView(row, lp(MATCH, dp(ROW_HEIGHT)))
        whileAttached {
            segmented.selected = if (node.state.value) 1 else 0
            node.state.listen { segmented.selected = if (it) 1 else 0 }
        }
    }
}

/** SOURCE `.dialkit-button`: padding 10/16, 13/500, text-secondary on surface; pressed → surface-active. */
@SuppressLint("ViewConstructor")
internal class ActionButton(ui: DialUi, text: String, onClick: () -> Unit) : TextView(ui.context) {
    private val palette = ui.palette
    private val bg = roundRect(context, palette.surface)
    private val press = Fade(150) { t ->
        bg.setColor(lerpColor(palette.surface, palette.surfaceActive, t))
        setTextColor(lerpColor(palette.textSecondary, palette.textPrimary, t))
    }

    init {
        this.text = text
        dialText(13f, DialFonts.medium, palette.textSecondary)
        gravity = Gravity.CENTER
        background = bg
        isFocusable = true
        setPadding(dp(16), dp(10), dp(16), dp(10))
        setOnClickListener { onClick() }
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        press.to(if (pressed) 1f else 0f)
    }
}

/**
 * Nested folder (and the frame of a transition control). Header 36dp with a 13/600 title and a chevron that
 * turns 180° (spring 0.35/0.15); content height animates 0 ↔ auto (spring 0.35/0.1). SOURCE Folder.tsx.
 */
@SuppressLint("ViewConstructor")
internal class FolderView(
    ui: DialUi,
    title: String,
    private val open: DialCell<Boolean>,
    private val borderTop: Boolean,
    private val borderBottom: Boolean,
    content: () -> View,
) : LinearLayout(ui.context) {
    private val palette = ui.palette
    private val chevron = IconView(context, DialIcons.Chevron).apply {
        tint = palette.textLabel
        alpha = 0.6f
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    private val body = ClipFrame(context)
    private val paint = Paint().apply { color = palette.surfaceSubtle }
    private val rotation = SpringValue(if (open.value) 180f else 0f, threshold = 0.5f) { chevron.rotation = it }
    private val reveal = SpringValue(if (open.value) 1f else 0f, threshold = 0.002f) { body.reveal = it }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { open.value = !open.value }
            isFocusable = true
            contentDescription = title
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(if (open.value) AccessibilityNodeInfo.AccessibilityAction.ACTION_COLLAPSE else AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND)
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
                    AccessibilityNodeInfo.ACTION_EXPAND, AccessibilityNodeInfo.ACTION_COLLAPSE -> {
                        open.value = action == AccessibilityNodeInfo.ACTION_EXPAND
                        true
                    }
                    else -> super.performAccessibilityAction(host, action, args)
                }
            }
        }
        header.addView(
            TextView(context).apply {
                text = title
                dialText(13f, DialFonts.semibold, palette.textSection)
                letterSpacing = -0.01f // SOURCE letter-spacing: -0.01em
                translationY = -dp(0.5f)
                maxLines = 1
            },
            llp(0, WRAP, 1f),
        )
        header.addView(chevron, llp(dp(20), dp(20)))
        addView(header, llp(MATCH, dp(ROW_HEIGHT)))
        body.addView(content().apply { setPadding(0, 0, 0, dp(10)) }, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(body, llp(MATCH, WRAP))
        chevron.rotation = rotation.value
        body.reveal = reveal.value
        whileAttached {
            if ((rotation.target == 180f) != open.value) {
                rotation.snapTo(if (open.value) 180f else 0f)
                reveal.snapTo(if (open.value) 1f else 0f)
            }
            open.listen { isOpen ->
                rotation.animateTo(if (isOpen) 180f else 0f, timeSpring(0.35f, 0.15f))
                reveal.animateTo(if (isOpen) 1f else 0f, timeSpring(0.35f, 0.1f))
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val hairline = dp(1f)
        if (borderTop) canvas.drawRect(0f, 0f, width.toFloat(), hairline, paint)
        if (borderBottom) canvas.drawRect(0f, height.toFloat() - hairline, width.toFloat(), height.toFloat(), paint)
    }
}

/** Measures its child at natural height and shows [reveal] × that height, clipped (0 ↔ auto). */
internal class ClipFrame(context: Context) : FrameLayout(context) {
    var reveal: Float = 1f
        set(v) {
            field = v
            visibility = if (v <= 0.001f) GONE else VISIBLE
            requestLayout()
        }

    init {
        clipChildren = true
        clipToPadding = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(measuredWidth, Math.round(measuredHeight * reveal.coerceAtLeast(0f)))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight) // top-anchored; the frame clips below
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
