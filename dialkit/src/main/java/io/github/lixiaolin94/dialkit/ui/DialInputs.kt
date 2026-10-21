package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.DialCell
import io.github.lixiaolin94.dialkit.SelectNode

/**
 * SOURCE SelectControl.tsx + `.dialkit-select-*`: a 36dp trigger (label left, value + chevron right) opening a
 * dropdown as wide as the trigger; chevron turns with spring(0.2, 0.15); the open trigger uses surface-active.
 */
@SuppressLint("ViewConstructor")
internal class SelectRow(private val ui: DialUi, private val node: SelectNode) : LinearLayout(ui.context) {
    private val palette = ui.palette
    private val bg = roundRect(context, palette.surface)
    private val value = label(context, "", palette.textLabel)
    private val chevron = IconView(context, DialIcons.Chevron).apply {
        tint = palette.textLabel
        alpha = 0.6f
        setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    private val rotation = SpringValue(0f, threshold = 0.5f) { chevron.rotation = it }
    private val open = Fade(150) { bg.setColor(lerpColor(palette.surface, palette.surfaceActive, it)) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(dp(12), 0, dp(12), 0)
        addView(label(context, node.label, palette.textLabel).apply { translationY = -dp(0.5f) }, llp(0, WRAP, 1f))
        addView(value.apply { translationY = -dp(0.5f) }, llp(WRAP, WRAP).apply { marginEnd = dp(8) })
        addView(chevron, llp(dp(20), dp(20)))
        setOnClickListener {
            if (ui.popups.isOpenFor(node)) ui.popups.dismiss() else ui.popups.show(node, this, selectSpec(palette), options())
        }
        isFocusable = true
        render()
        whileAttached {
            render()
            val a = node.state.listen { render() }
            val b = ui.popups.onChange {
                val isOpen = ui.popups.isOpenFor(node)
                rotation.animateTo(if (isOpen) 180f else 0f, timeSpring(0.2f, 0.15f))
                open.to(if (isOpen) 1f else 0f)
            }
            ({ a(); b() })
        }
    }

    private fun render() {
        value.text = node.options.firstOrNull { it.value == node.state.value }?.label ?: node.state.value
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(ROW_HEIGHT), MeasureSpec.EXACTLY))
    }

    /** SOURCE `.dialkit-select-option`: min 36, padding 8/10, radius 6; selected → text-primary on surface-active. */
    private fun options() = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        node.options.forEach { option ->
            addView(dropdownRow(ui, option.label, option.value == node.state.value) {
                node.state.value = option.value
                ui.popups.dismiss()
            }, llp(MATCH, WRAP))
        }
    }
}

internal fun dropdownRow(ui: DialUi, text: String, selected: Boolean, onClick: () -> Unit): View {
    val p = ui.palette
    val bg = roundRect(ui.context, if (selected) p.surfaceActive else 0, 6f)
    return object : TextView(ui.context) {
        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            if (!selected) bg.setColor(if (pressed) p.surfaceHover else 0)
        }
    }.apply {
        this.text = text
        dialText(13f, DialFonts.medium, if (selected) p.textPrimary else p.textLabel)
        background = bg
        minHeight = dp(36)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(8), dp(10), dp(8))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setOnClickListener { onClick() }
        isFocusable = true
        isSelected = selected // screen readers announce the current option
    }
}

/**
 * SOURCE TextControl.tsx + `.dialkit-text-*`: label left, a right-aligned multi-line field, padding (36 − 18)/2
 * vertical and 12 horizontal, up to five lines.
 */
@SuppressLint("ViewConstructor")
internal class TextRow(
    ui: DialUi,
    labelText: String,
    cell: DialCell<String>,
    placeholder: String?,
    maxLines: Int,
) : LinearLayout(ui.context) {
    init {
        val p = ui.palette
        orientation = HORIZONTAL
        background = roundRect(context, p.surface)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        minimumHeight = dp(ROW_HEIGHT)
        addView(label(context, labelText, p.textLabel).apply { setLineHeightCompat(dp(18)) }, llp(WRAP, WRAP).apply { marginEnd = dp(12) })
        val field = dialEditText(context, p, mono = false, alignEnd = true).apply {
            setText(cell.value)
            hint = placeholder
            this.maxLines = maxLines
            inputType = InputType.TYPE_CLASS_TEXT or (if (maxLines > 1) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0)
            addTextChangedListener(afterChanged { cell.value = it })
        }
        addView(field, llp(0, WRAP, 1f))
        whileAttached {
            if (field.text.toString() != cell.value) field.setText(cell.value)
            cell.listen { if (!field.hasFocus() && field.text.toString() != it) field.setText(it) }
        }
    }
}

internal fun TextView.setLineHeightCompat(px: Int) {
    val fm = paint.fontMetricsInt
    setLineSpacing((px - (fm.descent - fm.ascent)).toFloat().coerceAtLeast(0f), 1f)
}

internal fun dialEditText(context: Context, p: DialPalette, mono: Boolean, alignEnd: Boolean) = EditText(context).apply {
    background = null
    setPadding(0, 0, 0, 0)
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
    typeface = if (mono) DialFonts.mono else DialFonts.medium
    includeFontPadding = false
    setTextColor(p.textLabel)
    setHintTextColor(p.textTertiary)
    gravity = (if (alignEnd) Gravity.END else Gravity.START) or Gravity.TOP
    imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    setOnFocusChangeListener { _, focused -> setTextColor(if (focused) p.textFocus else p.textLabel) }
}

internal fun afterChanged(block: (String) -> Unit) = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: Editable?) = block(s?.toString().orEmpty())
}

/**
 * A value shown ellipsized until tapped, then edited in place: valid text commits as typed, invalid turns
 * red, leaving the field restores the current value. Used for colors, ease and pad coordinates.
 */
@SuppressLint("ViewConstructor")
internal class ValueField(
    context: Context,
    private val p: DialPalette,
    mono: Boolean,
    private val alignEnd: Boolean,
    private val current: () -> String,
    /** Returns false when the text is not a valid value (shown red, nothing committed). */
    private val commit: (String) -> Boolean,
) : FrameLayout(context) {
    var displayColor: Int = p.textLabel
        set(v) {
            field = v
            display.setTextColor(v)
        }
    private val display = TextView(context).apply {
        dialText(13f, if (mono) DialFonts.mono else DialFonts.medium, p.textLabel)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = (if (alignEnd) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
    }
    private val edit = dialEditText(context, p, mono, alignEnd).apply {
        isSingleLine = true
        visibility = GONE
        gravity = (if (alignEnd) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
    }
    private var editing = false

    init {
        addView(display, LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
        addView(edit, LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
        display.setOnClickListener { begin() }
        display.isFocusable = true
        edit.addTextChangedListener(afterChanged { text ->
            if (!editing) return@afterChanged
            val ok = commit(text)
            edit.setTextColor(if (ok) p.textFocus else p.error)
        })
        edit.setOnFocusChangeListener { _, focused -> if (!focused) end() }
        edit.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                // As Compose's clearFocus: done also dismisses the keyboard (clearFocus alone leaves it up).
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(edit.windowToken, 0)
                edit.clearFocus()
                true
            } else {
                false
            }
        }
        refresh()
    }

    fun refresh() {
        if (!editing) display.text = current()
    }

    private fun begin() {
        editing = true
        display.visibility = INVISIBLE
        edit.visibility = VISIBLE
        edit.setText(current())
        edit.setSelection(edit.text.length)
        edit.setTextColor(p.textFocus)
        edit.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(edit, 0)
    }

    private fun end() {
        editing = false
        edit.visibility = GONE
        display.visibility = VISIBLE
        refresh()
    }
}
