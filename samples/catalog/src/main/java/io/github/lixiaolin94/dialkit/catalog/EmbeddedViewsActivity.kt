package io.github.lixiaolin94.dialkit.catalog

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialKit
import io.github.lixiaolin94.dialkit.DialPanelView
import io.github.lixiaolin94.dialkit.observe

class ViewsPageDials : DialGroup() {
    val size by slider(140f, 60f..220f)
    val radius by slider(24f, 0f..110f)
    val tint by color("#f97316")
    val rotation by slider(0f, -45f..45f)
    val visible by toggle(true)
    val label by text("Views")
    val shape by folder { ViewsShapeDials() }
}

class ViewsShapeDials : DialGroup() {
    val stroke by slider(0f, 0f..12f)
    val strokeColor by color("#1f2937")
    val alpha by slider(1f, 0f..1f)
}

/**
 * A plain android.app.Activity (no Compose, no AndroidX) with the panel in its layout: a preview on top and a
 * DialPanelView filling the rest, so the panel has a bounded height and scrolls its controls inside.
 */
class EmbeddedViewsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val dials = DialKit.panel(this, "Views page") { ViewsPageDials() }

        val shape = GradientDrawable()
        val box = TextView(this).apply {
            background = shape
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
        }
        val preview = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(box, FrameLayout.LayoutParams(dp(140), dp(140), Gravity.CENTER))
        }
        val panel = DialPanelView(this).apply { group = dials }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F2"))
            addView(TextView(this@EmbeddedViewsActivity).apply {
                text = "Panel in a Views screen"
                textSize = 22f
                setTextColor(Color.parseColor("#1A1A1A"))
                setPadding(dp(16), dp(16), dp(16), dp(8))
            })
            addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)))
            // Bounded height: the panel keeps its title and toolbar and scrolls the controls.
            addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Edge-to-edge (targetSdk 35): keep the content inside the system bars.
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        // Dark status / navigation bar icons on the light page.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (android.os.Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)

        dials.observe(this) { d ->
            val size = (d.size * density).toInt()
            box.layoutParams = (box.layoutParams as FrameLayout.LayoutParams).apply { width = size; height = size }
            shape.cornerRadius = d.radius * density
            shape.setColor(d.tint)
            shape.setStroke((d.shape.stroke * density).toInt(), d.shape.strokeColor)
            box.rotation = d.rotation
            box.alpha = d.shape.alpha
            box.visibility = if (d.visible) View.VISIBLE else View.INVISIBLE
            box.text = d.label
        }
    }
}
