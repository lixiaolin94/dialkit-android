package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.store.DialPanel

/**
 * One group's panel laid out as part of a page. SOURCE DialKit `mode: 'inline'` (styles.css `.dialkit-panel-inline`,
 * Folder.tsx): the panel's glass-bg and 10 / 12 padding without border, radius or shadow, full width, height from
 * the content; the root title row neither collapses nor shows the panel icon; the toolbar stays.
 * DESIGN (Android):
 * - no close button and no drag grip; the title row keeps the floating panel's spacing (HEADER_TOP / TITLE_BOTTOM);
 * - wrap-content height: the controls grow and the host's scroll container scrolls them, as in DialKit, where the
 *   panel uses the host's scroll; given a bounded height, the header stays and the controls scroll inside.
 */
@SuppressLint("ViewConstructor")
internal class EmbeddedPanel(ui: DialUi, panel: DialPanel) : LinearLayout(ui.context) {
    init {
        val p = ui.palette
        orientation = VERTICAL
        setBackgroundColor(p.glassBg)
        setPadding(dp(12), 0, dp(12), 0) // SOURCE `.dialkit-panel-inner` padding 10px 12px (the 10 is HEADER_TOP / bottom)
        addView(View(context), llp(MATCH, dp(HEADER_TOP)))
        addView(
            TextView(context).apply {
                dialText(15f, DialFonts.semibold, p.textRoot) // SOURCE `.dialkit-folder-title-root` 15/600
                text = panel.name
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            },
            llp(MATCH, dp(18)),
        )
        addView(View(context), llp(MATCH, dp(TITLE_BOTTOM)))
        addView(PanelToolbar(ui, panel), llp(MATCH, WRAP))
        addView(View(context), llp(MATCH, dp(6)))
        addView(View(context).apply { setBackgroundColor(p.surfaceSubtle) }, llp(MATCH, dp(1)))
        addView(View(context), llp(MATCH, dp(12)))
        val body = PanelScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true // DESIGN (user), as in the floating panel
            setFadingEdgeLength(dp(FADE_EDGE))
            overScrollMode = OVER_SCROLL_NEVER
            addView(ControlList(ui.withActions(panel), panel.group, isRoot = true), FrameLayout.LayoutParams(MATCH, WRAP))
        }
        // Weighted: measured at its content height when the page gives no bound, shrunk to scroll when it does.
        addView(body, llp(MATCH, 0, 1f))
        addView(View(context), llp(MATCH, dp(10)))
    }
}
