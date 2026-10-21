package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes

// Small building blocks shared by the toolbar and its popups.

/** SOURCE `.dialkit-toolbar-add` whileTap: scale 0.9, spring(0.15, 0.3). */
internal open class PressFrame(context: Context) : FrameLayout(context) {
    private val press = SpringValue(1f, threshold = 0.002f) {
        scaleX = it
        scaleY = it
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> press.animateTo(0.9f, timeSpring(0.15f, 0.3f))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> press.animateTo(1f, timeSpring(0.15f, 0.3f))
        }
        return super.onTouchEvent(event)
    }
}

/**
 * A 36×36 toolbar button with a 16dp icon. Filled: SOURCE `.dialkit-toolbar-add.dialkit-toolbar-primary` (text-root
 * fill, glass-bg icon). Otherwise DESIGN: surface fill, text-label icon, surface-active while [active].
 */
@SuppressLint("ViewConstructor")
internal class ToolbarButton(
    context: Context,
    private val p: DialPalette,
    @DrawableRes icon: Int,
    description: String,
    private val filled: Boolean,
) : PressFrame(context) {
    private val bg = roundRect(context, if (filled) p.textRoot else p.surface)
    val icon = addIcon(icon)

    var active: Boolean = false
        set(v) {
            field = v
            if (!filled) bg.setColor(if (v) p.surfaceActive else p.surface)
        }

    init {
        background = bg
        contentDescription = description
        isFocusable = true
    }

    /** Stacks another icon in the same spot (for cross-fading states). */
    fun addIcon(@DrawableRes icon: Int): IconView = IconView(context, icon).also {
        it.tint = if (filled) p.glassBg else p.textLabel
        addView(it, LayoutParams(dp(16), dp(16), Gravity.CENTER))
    }
}

/**
 * Popup menu body. SOURCE `.dialkit-preset-dropdown` / `.dialkit-preset-item` / `.dialkit-preset-create` /
 * `.dialkit-preset-divider`: padding 4; items 32 high, padding 0 10, radius 8, 2 apart; 14dp icon 8 before the
 * label; active item on surface-active with text-primary; divider 1dp of surface-subtle, 4 above and below.
 */
@SuppressLint("ViewConstructor")
internal class DialMenu(context: Context, private val p: DialPalette) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    /**
     * One row. [icon] null leaves its slot empty (used for the unchecked state of the version list); [trailing]
     * sits at the end (e.g. a delete button).
     */
    fun item(
        text: String,
        @DrawableRes icon: Int?,
        active: Boolean = false,
        iconTint: Int = p.textLabel,
        trailing: View? = null,
        onClick: () -> Unit,
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(context, if (active) p.surfaceActive else 0)
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClick() }
            isFocusable = true
            isSelected = active
        }
        val glyph = IconView(context, icon ?: DialIcons.Check).apply {
            tint = iconTint
            visibility = if (icon == null) INVISIBLE else VISIBLE
        }
        row.addView(glyph, llp(dp(14), dp(14)).apply { marginEnd = dp(8) })
        row.addView(label(context, text, if (active) p.textPrimary else p.textLabel), llp(0, WRAP, 1f))
        trailing?.let { row.addView(it, llp(dp(24), dp(24))) }
        addView(row, llp(MATCH, dp(32)).apply { if (childCount > 0 && getChildAt(childCount - 1) !is Divider) topMargin = dp(2) })
        return row
    }

    fun divider() {
        addView(Divider(context).apply { setBackgroundColor(p.surfaceSubtle) }, llp(MATCH, dp(1)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        })
    }

    private class Divider(context: Context) : View(context)
}
