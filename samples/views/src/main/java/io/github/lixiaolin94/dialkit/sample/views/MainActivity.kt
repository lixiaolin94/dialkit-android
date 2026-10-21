package io.github.lixiaolin94.dialkit.sample.views

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialKit
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.DialPosition
import io.github.lixiaolin94.dialkit.observe

class BoxDials : DialGroup() {
    val radius by slider(16f, 0f..90f)
    val alpha by slider(1f)
    val scale by slider(1f, 0.5f..1.5f)
    val dark by toggle(false)
    val tint by color("#A78BFA")
    val shape by select("rounded", "circle")
    val nudge by action("Nudge")
}

/** A plain android.app.Activity: not a LifecycleOwner, no AppCompat, no Compose of its own. */
class MainActivity : Activity() {
    private var taps = 0
    private lateinit var dials: BoxDials

    /** DialKit `setValues`: several dials at once, plain values, one update for observers. */
    private fun applyPreset() = dials.setValues("radius" to 48, "scale" to 1.2f, "tint" to "#34D399", "shape" to "rounded")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val status = TextView(this).apply { text = "Panel: closed"; setPadding((16 * density).toInt(), 0, 0, (8 * density).toInt()) }
        // Start-up options are read when this Activity first resumes, so configuring here is in time.
        DialKit.configure {
            entryPosition = DialPosition.BottomEnd
            onOpenChange = { open -> status.text = if (open) "Panel: open" else "Panel: closed" }
        }
        val box = View(this)
        val shape = GradientDrawable()
        box.background = shape
        val counter = TextView(this).apply {
            text = "Host taps: 0"
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            setBackgroundColor(Color.WHITE)
            setOnClickListener { text = "Host taps: ${++taps}" }
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (48 * density).toInt(), 0, (48 * density).toInt())
            addView(TextView(this@MainActivity).apply {
                text = "View host · three-finger double tap shows the entry"
                setPadding((16 * density).toInt(), 0, 0, (16 * density).toInt())
            })
            addView(FrameLayout(this@MainActivity).apply {
                addView(box, FrameLayout.LayoutParams((180 * density).toInt(), (180 * density).toInt(), Gravity.CENTER))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (260 * density).toInt()))
            addView(counter)
            addView(status)
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(this@MainActivity).apply { text = "Apply preset"; setOnClickListener { applyPreset() } })
                addView(Button(this@MainActivity).apply { text = "Open / close panel"; setOnClickListener { dials.setOpen(dials.getOpen() != true) } })
            })
            repeat(40) { i -> addView(TextView(this@MainActivity).apply { text = "Row $i"; setPadding((16 * density).toInt(), 12, 0, 12) }) }
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F2F2F2"))
            addView(list)
        })

        dials = DialKit.panel(this, "Box", persist = DialPersist(key = "views:box", presets = false), onAction = { path ->
            if (path == "nudge") box.animate().translationXBy(24 * density).withEndAction { box.translationX = 0f }.start()
        }) { BoxDials() }

        dials.observe(this) { d ->
            shape.cornerRadius = if (d.shape == "circle") 90 * density else d.radius * density
            shape.setColor(if (d.dark) Color.parseColor("#3B0764") else d.tint)
            box.alpha = d.alpha
            box.scaleX = d.scale
            box.scaleY = d.scale
        }
    }
}
