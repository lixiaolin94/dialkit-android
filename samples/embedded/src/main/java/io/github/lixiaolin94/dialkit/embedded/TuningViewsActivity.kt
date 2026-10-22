package io.github.lixiaolin94.dialkit.embedded

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.DialKit
import io.github.lixiaolin94.dialkit.DialPanelView
import io.github.lixiaolin94.dialkit.observe

/**
 * A Tuning screen with plain Views (android.app.Activity, no Compose): a preview card on top and a DialPanelView
 * filling the rest, so the panel keeps its header and scrolls the controls inside.
 */
class TuningViewsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(v: Float) = (v * density).toInt()

        val dials = DialKit.panel(this, CARD_NAME, id = CARD_ID, persist = CardPersist) { CardDials() }

        val shape = GradientDrawable()
        val title = TextView(this).apply { setTextColor(Color.WHITE); paint.isFakeBoldText = true }
        val subtitle = TextView(this).apply { setTextColor(Color.argb(217, 255, 255, 255)); textSize = 14f }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape
            addView(title)
            addView(subtitle)
        }
        val panel = DialPanelView(this).apply { group = dials }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F7"))
            addView(TextView(this@TuningViewsActivity).apply {
                text = "Tuning (Views)"
                textSize = 28f
                setTextColor(Color.parseColor("#111111"))
                setPadding(dp(20f), dp(20f), dp(20f), dp(12f))
            })
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(20f), 0, dp(20f), dp(20f))
            })
            addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        // Edge-to-edge (targetSdk 35): keep the content inside the system bars; dark icons on the light page.
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)

        dials.observe(this) { d ->
            shape.cornerRadius = d.radius * density
            shape.setColor(d.tint)
            card.elevation = d.elevation * density
            val pad = dp(d.padding)
            card.setPadding(pad, pad, pad, pad)
            title.text = d.title
            title.textSize = if (d.compact) 18f else 24f
            subtitle.text = d.subtitle
            subtitle.setPadding(0, dp(if (d.compact) 2f else 8f), 0, 0)
        }
    }
}
