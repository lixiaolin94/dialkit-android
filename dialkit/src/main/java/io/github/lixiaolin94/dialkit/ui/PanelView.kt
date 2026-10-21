package io.github.lixiaolin94.dialkit.ui

import io.github.lixiaolin94.dialkit.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import io.github.lixiaolin94.dialkit.ActionNode
import io.github.lixiaolin94.dialkit.store.DialKitPickerActivity
import io.github.lixiaolin94.dialkit.store.DialPanel
import io.github.lixiaolin94.dialkit.store.ImportSource
import io.github.lixiaolin94.dialkit.store.DialStore

/**
 * The expanded panel's inside. One registered panel → it is the root (titled with its name); several → a
 * "DialKit" root with one folder-style section per panel; none → an empty "DialKit" root. SOURCE
 * DialRoot.tsx:L210-256. The header sits outside the scroll, matching DialKit's sticky header.
 */
@SuppressLint("ViewConstructor")
internal class PanelView(private val ui: DialUi) : LinearLayout(ui.context) {
    private val p = ui.palette
    val header = LinearLayout(context).apply { orientation = VERTICAL }
    private val title = TextView(context).apply {
        dialText(15f, DialFonts.semibold, p.textRoot) // SOURCE `.dialkit-folder-title-root` 15/600
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val close = IconView(context, DialIcons.Close).apply {
        tint = p.textFocus
        contentDescription = context.getString(R.string.dialkit_close)
        isFocusable = true
    }
    private val toolbarSlot = FrameLayout(context)
    private val body = PanelScrollView(context).apply {
        isVerticalScrollBarEnabled = false
        // DESIGN (user): soften the edge that still has content beyond it (platform fading edge; DialKit has none).
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(FADE_EDGE))
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = false
    }
    private val content = FrameLayout(context)

    init {
        orientation = VERTICAL
        setPadding(dp(13), dp(1), dp(13), dp(1)) // 1dp border + SOURCE padding 10px 12px
        // SOURCE: sticky header padding-top 10, title row, header-top padding-bottom 8, toolbar (36, mb 6),
        // header padding-bottom 6, 1px subtle border, margin-bottom 12. DESIGN (user): title row gets HEADER_TOP above and
        // TITLE_BOTTOM below instead (more room around the title and close button).
        header.addView(View(context), llp(MATCH, dp(HEADER_TOP)))
        header.addView(
            FrameLayout(context).apply {
                // Title stops short of the centred grip and ellipsizes instead of running under it.
                addView(title, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL or Gravity.START))
                addView(DragGrip(context, p), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                addView(close, FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER_VERTICAL or Gravity.END))
            },
            llp(MATCH, dp(18)),
        )
        header.addView(View(context), llp(MATCH, dp(TITLE_BOTTOM)))
        header.addView(toolbarSlot, llp(MATCH, WRAP))
        header.addView(View(context), llp(MATCH, dp(6)))
        header.addView(View(context).apply { setBackgroundColor(p.surfaceSubtle) }, llp(MATCH, dp(1)))
        addView(header, llp(MATCH, WRAP))
        addView(View(context), llp(MATCH, dp(12)))
        body.addView(content, LayoutParams(MATCH, WRAP))
        addView(body, llp(MATCH, 0, 1f))
        addView(View(context), llp(MATCH, dp(10)))
        render(DialStore.panels.value)
        whileAttached {
            // Panels may (un)register between construction and attach (a Compose host registers on its first
            // frame, after onResume), so sync before listening.
            if (renderedFor != DialStore.panels.value) render(DialStore.panels.value)
            DialStore.panels.listen { render(it) }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Before measuring, and only on change: setMaxWidth always requests layout, so setting it in onLayout
        // re-ran layout on every pass (the whole overlay relaid out with each cursor blink).
        val max = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) / 2 - DragGrip.widthPx(this) / 2 - dp(8)
        if (title.maxWidth != max) title.maxWidth = max
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Height the panel needs to show everything: header + body content + chrome. */
    fun naturalHeight(width: Int): Int {
        val inner = width - paddingLeft - paddingRight
        header.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        content.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return header.measuredHeight + content.measuredHeight + dp(12) + dp(10) + paddingTop + paddingBottom
    }

    private var renderedFor: List<DialPanel>? = null

    private fun render(panels: List<DialPanel>) {
        renderedFor = panels
        title.text = panels.singleOrNull()?.name ?: context.getString(R.string.dialkit_name)
        toolbarSlot.removeAllViews()
        panels.singleOrNull()?.let { panel ->
            toolbarSlot.addView(PanelToolbar(ui, panel), FrameLayout.LayoutParams(MATCH, WRAP))
            toolbarSlot.setPadding(0, 0, 0, dp(6))
        } ?: toolbarSlot.setPadding(0, 0, 0, 0)
        content.removeAllViews()
        val view: View = when (panels.size) {
            0 -> label(context, context.getString(R.string.dialkit_empty), p.textTertiary).apply { setPadding(0, dp(8), 0, dp(8)) }
            1 -> panels.single().let { panel -> ControlList(ui.withActions(panel), panel.group, isRoot = true) }
            else -> LinearLayout(context).apply {
                orientation = VERTICAL
                panels.forEachIndexed { i, panel ->
                    val sectionUi = ui.withActions(panel)
                    addView(
                        FolderView(sectionUi, panel.name, panel.open, borderTop = i > 0, borderBottom = i < panels.lastIndex) {
                            LinearLayout(context).apply {
                                orientation = VERTICAL
                                addView(PanelToolbar(sectionUi, panel), llp(MATCH, WRAP))
                                addView(ControlList(sectionUi, panel.group, isRoot = false), llp(MATCH, WRAP).apply { topMargin = dp(ROW_GAP) })
                            }
                        },
                        llp(MATCH, WRAP),
                    )
                }
            }
        }
        content.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
        onContentChanged?.invoke()
    }

    /** Called when the set of panels (hence the natural height) changes. */
    var onContentChanged: (() -> Unit)? = null
}

private fun DialUi.withActions(panel: DialPanel) = DialUi(context, palette, popups) { action: ActionNode ->
    action.onClick?.invoke()
    DialStore.dispatchAction(panel, action.path)
}

/**
 * Title-bar drag handle. Starting point: Leva 0.10.1 (leva.esm.js L1938–1970), a 20×10 SVG over viewBox 28×14 with
 * r = 2 circles on a 12 × 10 grid, i.e. ×20/28 → dot Ø 2.857, pitch 8.571 × 7.143 (SOURCE, MAP px → dp).
 * Tuned by eye (DESIGN, user): 4 columns instead of 3, pitch −2dp; centred on the title row with the title and
 * the close button. Colour stays ours (text-tertiary). All knobs are the constants below.
 */
@SuppressLint("ViewConstructor")
internal class DragGrip(context: Context, private val p: DialPalette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthPx(this), Math.round(dp(PITCH_Y * (ROWS - 1) + DOT)))
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = p.textTertiary
        val r = dp(DOT) / 2
        for (row in 0 until ROWS) for (col in 0 until COLUMNS) {
            canvas.drawCircle(r + col * dp(PITCH_X), r + row * dp(PITCH_Y), r, paint)
        }
    }

    companion object {
        private const val SCALE = 20f / 28f
        /** Dot diameter, dp. SOURCE Leva r = 2 → Ø 4 × 20/28. */
        const val DOT = 4f * SCALE
        /** Centre-to-centre distance between columns / rows, dp. Leva 12 / 10 × 20/28, then −2 (DESIGN, user). */
        const val PITCH_X = 12f * SCALE - 2f
        const val PITCH_Y = 10f * SCALE - 2f
        const val COLUMNS = 4
        const val ROWS = 2
        fun widthPx(v: View) = Math.round(v.dp(PITCH_X * (COLUMNS - 1) + DOT))
    }
}

/** SOURCE Panel.tsx toolbar: version menu (flex 1) + 36×36 inverted Copy button, gap 6. */
@SuppressLint("ViewConstructor")
internal class PanelToolbar(private val ui: DialUi, private val panel: DialPanel) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val trigger = LinearLayout(context)
    private val triggerBg = roundRect(context, p.surface)
    private val triggerLabel = label(context, "", p.textLabel)
    private val chevron = IconView(context, DialIcons.Chevron).apply {
        tint = p.textLabel
        alpha = 0.6f
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    private val rotation = SpringValue(0f, threshold = 0.5f) { chevron.rotation = it }

    /** DESIGN: a secondary toolbar button beside Copy; opens the source menu. */
    private val importButton = ToolbarButton(context, p, DialIcons.Import, context.getString(R.string.dialkit_import), filled = false).apply {
        setOnClickListener {
            if (ui.popups.isOpenFor(this)) ui.popups.dismiss() else ui.popups.show(this, this, importSpec(p), importMenu())
        }
    }

    /** SOURCE copy button: clipboard ↔ check, the check held 1500ms. */
    private val copyButton = ToolbarButton(context, p, DialIcons.Clipboard, context.getString(R.string.dialkit_copy), filled = true)
    private val copied = copyButton.addIcon(DialIcons.Check).apply { alpha = 0f }
    private val revertCopy = Runnable { showCopied(false) }

    init {
        orientation = HORIZONTAL
        trigger.apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = triggerBg
            setPadding(dp(12), 0, dp(12), 0)
            addView(triggerLabel, llp(0, WRAP, 1f))
            addView(chevron, llp(dp(20), dp(20)))
            setOnClickListener {
                if (ui.popups.isOpenFor(panel)) ui.popups.dismiss() else ui.popups.show(panel, this, presetSpec(p), presetMenu())
            }
            isFocusable = true
        }
        copyButton.setOnClickListener {
            if (!DialStore.copy(panel)) {
                copyButton.hapticReject()
            } else {
                copyButton.hapticConfirm()
                showCopied(true)
                removeCallbacks(revertCopy)
                postDelayed(revertCopy, 1500)
            }
        }
        addView(trigger, llp(0, dp(ROW_HEIGHT), 1f))
        addView(importButton, llp(dp(ROW_HEIGHT), dp(ROW_HEIGHT)).apply { marginStart = dp(ROW_GAP) })
        addView(copyButton, llp(dp(ROW_HEIGHT), dp(ROW_HEIGHT)).apply { marginStart = dp(ROW_GAP) })
        render()
        whileAttached {
            render()
            val a = panel.activeId.listen { render() }
            val b = panel.presets.listen { render() }
            val c = ui.popups.onChange {
                val isOpen = ui.popups.isOpenFor(panel)
                rotation.animateTo(if (isOpen) 180f else 0f, timeSpring(0.2f, 0.15f))
                triggerBg.setColor(if (isOpen) p.surfaceActive else p.surface)
                importButton.active = ui.popups.isOpenFor(importButton)
            }
            val imported: (String, Boolean) -> Unit = { id, ok ->
                if (id == panel.id) if (ok) importButton.hapticConfirm() else importButton.hapticReject()
            }
            DialStore.importResults += imported
            ({ a(); b(); c(); DialStore.importResults -= imported })
        }
    }

    private fun render() {
        triggerLabel.text = panel.activePreset?.name ?: context.getString(R.string.dialkit_version_base)
    }

    private fun showCopied(done: Boolean) {
        copyButton.contentDescription = context.getString(if (done) R.string.dialkit_copied else R.string.dialkit_copy)
        listOf(copyButton.icon to !done, copied to done).forEach { (v, show) ->
            v.animate().alpha(if (show) 1f else 0f).scaleX(if (show) 1f else 0.8f).scaleY(if (show) 1f else 0.8f).setDuration(80).start()
        }
    }

    /**
     * SOURCE PresetManager.tsx: "Version 1" (base values), saved versions with a check on the active one and a
     * delete button, a divider, then "New version". DESIGN: delete always shown at 0.6 (no hover on touch).
     */
    private fun presetMenu(): DialMenu = DialMenu(context, p).apply {
        val base = panel.activePresetId == null
        item(context.getString(R.string.dialkit_version_base), DialIcons.Check.takeIf { base }, active = base, iconTint = p.textFocus) {
            DialStore.clearActivePreset(panel)
            ui.popups.dismiss()
        }
        panel.presets.value.forEach { preset ->
            val active = preset.id == panel.activePresetId
            val delete = IconView(context, DialIcons.Trash).apply {
                tint = p.textFocus
                alpha = 0.6f
                setPadding(dp(5), dp(5), dp(5), dp(5))
                setOnClickListener {
                    DialStore.deletePreset(panel, preset.id)
                    ui.popups.show(panel, trigger, presetSpec(p), presetMenu()) // keep the menu open, refreshed
                }
            }
            item(preset.name, DialIcons.Check.takeIf { active }, active = active, iconTint = p.textFocus, trailing = delete) {
                DialStore.loadPreset(panel, preset.id)
                ui.popups.dismiss()
            }
        }
        divider()
        item(context.getString(R.string.dialkit_version_new), DialIcons.Plus) {
            DialStore.saveNewPreset(panel)
            ui.popups.dismiss()
        }
    }

    /** DESIGN: where to import from. */
    private fun importMenu(): DialMenu = DialMenu(context, p).apply {
        item(context.getString(R.string.dialkit_import_clipboard), DialIcons.Clipboard) {
            ui.popups.dismiss()
            DialStore.importText(panel.id, DialStore.clipboardText(), ImportSource.Clipboard)
        }
        item(context.getString(R.string.dialkit_import_file), DialIcons.File) {
            ui.popups.dismiss()
            DialKitPickerActivity.startImport(context, panel.id)
        }
    }
}

/**
 * The panel body. One change from the platform ScrollView: a finger that stopped before lifting does not fling.
 * The platform VelocityTracker ignores ACTION_UP, so a pause before lifting never enters the estimate and the
 * pre-pause speed flings on; Compose's tracker (the previous implementation) counts the lift and treats a gap of
 * more than 40 ms between samples as a stop (SOURCE: Compose VelocityTracker AssumePointerMoveStoppedMilliseconds
 * = 40, same as the platform's ASSUME_POINTER_STOPPED_TIME). Replaying a move at the lift position after such a
 * gap makes the platform tracker reset, as Compose's would.
 */
internal class PanelScrollView(context: Context) : ScrollView(context) {
    private var lastMoveTime = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> lastMoveTime = ev.eventTime
            MotionEvent.ACTION_UP -> if (ev.eventTime - lastMoveTime > STOPPED_MS) {
                val still = MotionEvent.obtain(ev)
                still.action = MotionEvent.ACTION_MOVE
                super.onTouchEvent(still)
                still.recycle()
            }
        }
        return super.onTouchEvent(ev)
    }

    private companion object {
        const val STOPPED_MS = 40L
    }
}

private const val HEADER_TOP = 10 // dp above the title row. SOURCE 10 (user tried 6, then +4)
private const val TITLE_BOTTOM = 12 // dp between the title row and the toolbar. SOURCE 8, DESIGN (user) +4
private const val FADE_EDGE = 16 // dp. DESIGN
