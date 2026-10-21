package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.lixiaolin94.dialkit.ImageNode
import io.github.lixiaolin94.dialkit.R
import io.github.lixiaolin94.dialkit.model.DialOption
import io.github.lixiaolin94.dialkit.model.IMAGE_LABEL_NONE
import io.github.lixiaolin94.dialkit.model.IMAGE_LABEL_UPLOADED
import io.github.lixiaolin94.dialkit.model.MAX_IMAGE_BYTES
import io.github.lixiaolin94.dialkit.model.imageChoices
import io.github.lixiaolin94.dialkit.store.DialKitPickerActivity
import io.github.lixiaolin94.dialkit.store.ImagePick
import io.github.lixiaolin94.dialkit.store.ImagePicks
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Thumbnail decoding with platform APIs only: a small LRU cache and two background threads. Understands the URIs
 * an image dial can hold (see `DialGroup.image`); anything that fails shows the fallback icon, as DialKit does on
 * an `<img>` error.
 */
internal object DialImageLoader {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val failed = HashSet<String>()
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "dialkit-image").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /** Calls [onResult] on the main thread with a bitmap at least [size] px on its short side, or null. */
    fun load(context: Context, uri: String, size: Int, onResult: (Bitmap?) -> Unit) {
        val key = "$uri@${bucket(size)}"
        cache.get(key)?.let { return onResult(it) }
        if (key in failed) return onResult(null)
        val app = context.applicationContext
        executor.execute {
            val bitmap = runCatching { decode(app, uri, bucket(size)) }
                .onFailure { Log.d("DialKit", "Image unavailable: ${uri.take(80)}", it) }.getOrNull()
            main.post {
                if (bitmap != null) cache.put(key, bitmap) else failed += key
                onResult(bitmap)
            }
        }
    }

    private fun bucket(size: Int) = Integer.highestOneBit(size.coerceAtLeast(16) * 2 - 1) // next power of two

    private fun decode(context: Context, uri: String, size: Int): Bitmap? {
        if (uri.startsWith("android.resource://")) return resource(context, Uri.parse(uri), size)
        val bytes = when {
            uri.startsWith("data:") -> uri.substringAfter(',', "").takeIf { uri.substringBefore(',').endsWith(";base64") }
                ?.let { Base64.decode(it, Base64.DEFAULT) }
            uri.startsWith("http://") || uri.startsWith("https://") -> {
                val c = URL(uri).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 10_000
                try {
                    read(c.inputStream)
                } finally {
                    c.disconnect()
                }
            }
            uri.startsWith("file:///android_asset/") -> read(context.assets.open(uri.removePrefix("file:///android_asset/")))
            uri.startsWith("/") -> read(File(uri).inputStream())
            else -> read(context.contentResolver.openInputStream(Uri.parse(uri)))
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** `android.resource://pkg/type/name` or `android.resource://pkg/id`; drawn through Drawable, so vectors work. */
    // By-name lookup is what such a URI means (ContentResolver resolves it the same way).
    @SuppressLint("DiscouragedApi")
    private fun resource(context: Context, uri: Uri, size: Int): Bitmap? {
        val pkg = uri.authority ?: return null
        val res = if (pkg == context.packageName) context.resources else context.packageManager.getResourcesForApplication(pkg)
        val segments = uri.pathSegments
        val id = when (segments.size) {
            1 -> segments[0].toIntOrNull()
            2 -> res.getIdentifier(segments[1], segments[0], pkg).takeIf { it != 0 }
            else -> null
        } ?: return null
        @Suppress("DEPRECATION")
        val drawable = res.getDrawable(id) ?: return null
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val scale = maxOf(1f, size.toFloat() / minOf(w, h))
        val bitmap = Bitmap.createBitmap(Math.round(w * scale), Math.round(h * scale), Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun read(input: InputStream?): ByteArray? = input?.use {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = it.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_IMAGE_BYTES) return null
        }
        out.toByteArray()
    }
}

/**
 * SOURCE `.dialkit-image-frame`: rounded, a surface checkerboard (12px tiles) behind a center-cropped image
 * (`object-fit: cover`), or the image icon in text-tertiary while loading or when it fails. [inset] adds the
 * thumbnail's 1px inner border; [aspect] (height / width) sizes it from its width.
 */
@SuppressLint("ViewConstructor")
internal class ImageThumbView(
    context: Context,
    private val p: DialPalette,
    private val radiusDp: Float,
    private val iconDp: Float,
    private val inset: Boolean = false,
    private val aspect: Float = 0f,
) : View(context) {
    var uri: String = ""
        set(v) {
            if (field == v) return
            field = v
            bitmap = null
            request()
            invalidate()
        }
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Its own opaque paint: drawBitmap takes the paint's alpha, and the checkerboard's surface colour is ~4% alpha.
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clip = Path()
    private val r = RectF()
    private val src = Rect()
    private val icon = context.getDrawable(DialIcons.Image)!!.mutate().apply { setTint(p.textTertiary) }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the row or option describes it
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (aspect <= 0f) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, Math.round(w * aspect))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = request()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        request()
    }

    private fun request() {
        if (uri.isEmpty() || width == 0 || !isAttachedToWindow) return
        val asked = uri
        DialImageLoader.load(context, asked, maxOf(width, height)) { b ->
            if (asked == uri) {
                bitmap = b
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(r, dp(radiusDp), dp(radiusDp), Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.color = p.surface
        canvas.drawRect(r, paint)
        // SOURCE repeating-conic-gradient(surface 0% 25%, transparent 0% 50%) 0 / 12px 12px
        val cell = dp(6f)
        var y = 0f
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < w) {
                canvas.drawRect(x, y, x + cell, y + cell, paint)
                x += cell * 2
            }
            y += cell
            row++
        }
        val b = bitmap
        if (b != null) {
            // object-fit: cover
            val scale = maxOf(w / b.width, h / b.height)
            val sw = Math.round(w / scale)
            val sh = Math.round(h / scale)
            src.set((b.width - sw) / 2, (b.height - sh) / 2, (b.width + sw) / 2, (b.height + sh) / 2)
            canvas.drawBitmap(b, src, r, bitmapPaint)
        } else {
            val s = dp(iconDp).toInt()
            val l = ((w - s) / 2).toInt()
            val t = ((h - s) / 2).toInt()
            icon.setBounds(l, t, l + s, t + s)
            icon.draw(canvas)
        }
        canvas.restore()
        if (inset) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = p.border
            r.inset(dp(0.5f), dp(0.5f))
            canvas.drawRoundRect(r, dp(radiusDp) - dp(0.5f), dp(radiusDp) - dp(0.5f), paint)
            paint.style = Paint.Style.FILL
        }
    }
}

/** SOURCE imageLabel results, localized. */
internal fun Context.imageName(option: DialOption?): String = when (val label = option?.label ?: IMAGE_LABEL_NONE) {
    IMAGE_LABEL_NONE -> getString(R.string.dialkit_image_none)
    IMAGE_LABEL_UPLOADED -> getString(R.string.dialkit_image_uploaded)
    else -> label
}

/**
 * SOURCE `.dialkit-image-control`: a 36dp row (padding 0 6 0 12, gap 8) with the label, the image's name (12,
 * text-secondary, right-aligned, ellipsized) and a 38×26 thumbnail (radius 5, inset border); open → surface-active
 * with an inset border-hover ring and text-primary. Opens the picker.
 */
@SuppressLint("ViewConstructor")
internal class ImageRow(private val ui: DialUi, private val node: ImageNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val bg = roundRect(context, p.surface)
    private val title = label(context, node.label, p.textLabel)
    private val name = TextView(context).apply {
        dialText(12f, DialFonts.medium, p.textSecondary)
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val thumb = ImageThumbView(context, p, 5f, 16f, inset = true)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(dp(12), 0, dp(6), 0)
        addView(title, llp(WRAP, WRAP))
        addView(name, llp(0, WRAP, 1f).apply { marginStart = dp(8) })
        addView(thumb, llp(dp(38), dp(26)).apply { marginStart = dp(8) })
        isFocusable = true
        setOnClickListener {
            if (ui.popups.isOpenFor(node)) ui.popups.dismiss() else ui.popups.show(node, this, imageSpec(p), ImagePicker(ui, node))
        }
        render()
        whileAttached {
            render()
            val a = node.state.listen { render() }
            val b = ui.popups.onChange { render() }
            ({ a(); b() })
        }
    }

    private fun render() {
        val v = node.state.value
        val current = imageChoices(node.options, node.uploaded, v).firstOrNull { it.value == v }
        name.text = context.imageName(current)
        thumb.uri = v
        val open = ui.popups.isOpenFor(node)
        bg.setColor(if (open) p.surfaceActive else p.surface)
        bg.setStroke(if (open) dp(1) else 0, p.borderHover)
        title.setTextColor(if (open) p.textPrimary else p.textLabel)
        // SOURCE aria-label `Choose ${label.toLowerCase()} image: ${name}`
        contentDescription = context.getString(R.string.dialkit_image_choose, node.label.lowercase(), name.text)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(ROW_HEIGHT), MeasureSpec.EXACTLY))
    }
}

/**
 * SOURCE `.dialkit-image-popover` contents: padding 12, gap 10; a heading (title + "Remove"), a 3-column grid of
 * 4:3 options (padding 3, 1px border, radius 11; selected → text-label border on surface-active), an empty state,
 * "Upload image" and a status line. Picking an option keeps the picker open; Remove closes it.
 */
@SuppressLint("ViewConstructor")
private class ImagePicker(private val ui: DialUi, private val node: ImageNode) : LinearLayout(ui.context) {
    private val p = ui.palette
    private val remove = TextView(context).apply {
        text = context.getString(R.string.dialkit_image_remove)
        dialText(11f, DialFonts.medium, p.textTertiary)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        isFocusable = true
        contentDescription = context.getString(R.string.dialkit_image_remove_desc, node.label.lowercase())
        setOnClickListener {
            node.state.value = ""
            ui.popups.dismiss()
        }
    }
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val empty = TextView(context).apply {
        text = context.getString(R.string.dialkit_image_empty)
        dialText(13f, DialFonts.regular, p.textTertiary)
        gravity = Gravity.CENTER
        background = roundRect(context, p.surface)
        setPadding(dp(12), dp(28), dp(12), dp(28))
    }
    private val uploadText = label(context, context.getString(R.string.dialkit_image_upload), p.textSecondary)
    private val upload = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = roundRect(context, p.surface)
        setPadding(dp(16), dp(10), dp(16), dp(10)) // SOURCE `.dialkit-button`
        addView(IconView(context, DialIcons.Upload).apply { tint = p.textSecondary }, llp(dp(15), dp(15)).apply { marginEnd = dp(8) })
        addView(uploadText, llp(WRAP, WRAP))
        isFocusable = true
        setOnClickListener { pick() }
    }
    private val status = TextView(context).apply {
        dialText(11f, DialFonts.regular, p.textLabel)
        setLineSpacing(0f, 1.5f)
        visibility = GONE
    }
    private var pending = -1
    private var shownItems: List<DialOption> = emptyList()
    private val optionViews = ArrayList<Pair<String, FrameLayout>>()

    init {
        orientation = VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        val heading = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label(context, node.label, p.textPrimary), llp(0, WRAP, 1f))
            addView(remove, llp(WRAP, WRAP).apply { setMargins(0, -dp(4), -dp(4), -dp(4)) })
        }
        addView(heading, llp(MATCH, WRAP))
        addView(grid, llp(MATCH, WRAP).apply { topMargin = dp(10) })
        addView(empty, llp(MATCH, WRAP).apply { topMargin = dp(10) })
        addView(upload, llp(MATCH, WRAP).apply { topMargin = dp(10) })
        addView(status, llp(MATCH, WRAP).apply { topMargin = dp(10) })
        render()
        whileAttached {
            render()
            val off = node.state.listen { render() }
            ({
                off()
                if (pending >= 0) ImagePicks.cancel(pending) // SOURCE close() aborts a pending upload
            })
        }
    }

    private fun render() {
        val v = node.state.value
        val items = imageChoices(node.options, node.uploaded, v)
        remove.visibility = if (v.isEmpty()) GONE else VISIBLE
        empty.visibility = if (items.isEmpty()) VISIBLE else GONE
        grid.visibility = if (items.isEmpty()) GONE else VISIBLE
        // SOURCE updatePicker: rebuild the buttons only when the items change, then just mark the selected one,
        // so focus (keyboard or screen reader) stays on the option just chosen.
        if (items != shownItems) {
            shownItems = items
            grid.removeAllViews()
            optionViews.clear()
            items.chunked(3).forEachIndexed { r, rowItems ->
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                for (c in 0 until 3) {
                    val item = rowItems.getOrNull(c)
                    val lp = llp(0, WRAP, 1f).apply { if (c > 0) marginStart = dp(ROW_GAP) }
                    row.addView(if (item == null) View(context) else option(item).also { optionViews += item.value to it }, lp)
                }
                grid.addView(row, llp(MATCH, WRAP).apply { if (r > 0) topMargin = dp(ROW_GAP) })
            }
        }
        for ((value, view) in optionViews) {
            val selected = value == v
            if (view.isSelected == selected && view.background != null) continue
            view.background = roundRect(context, if (selected) p.surfaceActive else 0, RADIUS + 3f, if (selected) p.textLabel else 0, if (selected) 1f else 0f)
            view.isSelected = selected // SOURCE aria-pressed
        }
    }

    private fun option(item: DialOption): FrameLayout = FrameLayout(context).apply {
        setPadding(dp(4), dp(4), dp(4), dp(4)) // padding 3 + the 1px border
        addView(ImageThumbView(context, p, RADIUS - 1f, 24f, aspect = 3f / 4f).apply { uri = item.value }, FrameLayout.LayoutParams(MATCH, WRAP))
        contentDescription = context.imageName(item)
        isFocusable = true
        setOnClickListener {
            if (pending >= 0) ImagePicks.cancel(pending).also { pending = -1; setBusy(false) } // a pending upload must not overwrite this
            node.state.value = item.value
        }
    }

    private fun setBusy(busy: Boolean) {
        uploadText.text = context.getString(if (busy) R.string.dialkit_image_loading else R.string.dialkit_image_upload)
        upload.alpha = if (busy) 0.6f else 1f
    }

    private fun pick() {
        if (pending >= 0) return
        status.visibility = GONE
        setBusy(true)
        pending = ImagePicks.request { result ->
            pending = -1
            setBusy(false)
            when (result) {
                is ImagePick.Picked -> {
                    if (node.uploaded.none { it.value == result.uri }) node.uploaded += DialOption(result.uri, result.name)
                    node.state.value = result.uri
                    upload.hapticConfirm()
                }
                is ImagePick.Failed -> {
                    status.text = context.getString(
                        when (result.reason) {
                            ImagePick.Reason.TooLarge -> R.string.dialkit_image_too_large
                            ImagePick.Reason.NotImage -> R.string.dialkit_image_not_image
                            ImagePick.Reason.Unreadable -> R.string.dialkit_image_unreadable
                            ImagePick.Reason.NoPicker -> R.string.dialkit_image_no_picker
                        },
                    )
                    status.visibility = VISIBLE
                    upload.hapticReject()
                }
                ImagePick.Cancelled -> Unit
            }
        }
        DialKitPickerActivity.startImage(context, pending)
    }
}
