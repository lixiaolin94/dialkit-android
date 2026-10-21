package io.github.lixiaolin94.dialkit.store

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import io.github.lixiaolin94.dialkit.model.MAX_IMAGE_BYTES
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * A translucent, finish-on-result trampoline for the system pickers, so DialKit never needs the host Activity's
 * onActivityResult (nor AndroidX). No permission is involved; the overlay is not attached to it (ActivityTracker
 * skips it).
 *
 * - Import: the document picker (Storage Access Framework); the file's text goes to [DialStore.importText].
 * - Image: the photo picker on API 33+ (`ACTION_PICK_IMAGES`), else the document picker for `image/…`. The image is
 *   checked (type, ≤ 10 MB, decodes) and copied into app storage, so the value stays readable after a restart.
 */
internal class DialKitPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // picker already open (recreated after a config change)
        val image = intent.getIntExtra(EXTRA_IMAGE_REQUEST, -1) >= 0
        val pick = when {
            !image -> Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            Build.VERSION.SDK_INT >= 33 -> Intent(MediaStore.ACTION_PICK_IMAGES)
            else -> Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        }
        try {
            @Suppress("DEPRECATION") // the platform Activity API; AndroidX's result API is not a dependency here
            startActivityForResult(pick, REQUEST)
        } catch (e: ActivityNotFoundException) {
            Log.w("DialKit", "No picker on this device", e)
            if (image) ImagePicks.deliver(intent.getIntExtra(EXTRA_IMAGE_REQUEST, -1), ImagePick.Failed(ImagePick.Reason.NoPicker))
            else DialStore.importText(intent.getStringExtra(EXTRA_PANEL), null, ImportSource.NoPicker)
            finish()
        }
    }

    @Deprecated("Platform Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri = data?.data
        val imageRequest = intent.getIntExtra(EXTRA_IMAGE_REQUEST, -1)
        if (requestCode != REQUEST || resultCode != RESULT_OK || uri == null) {
            if (imageRequest >= 0) ImagePicks.deliver(imageRequest, ImagePick.Cancelled)
            finish()
            return
        }
        val app = applicationContext
        val panelId = intent.getStringExtra(EXTRA_PANEL)
        val main = Handler(Looper.getMainLooper())
        Thread {
            // Finish only after reading: the picker's read grant lasts as long as this Activity.
            if (imageRequest >= 0) {
                val result = copyImage(app, uri)
                main.post { ImagePicks.deliver(imageRequest, result); finish() }
            } else {
                val text = runCatching { readCapped(app.contentResolver.openInputStream(uri), 1 shl 20)?.toString("UTF-8") }
                    .onFailure { Log.w("DialKit", "Import: cannot read $uri", it) }.getOrNull()
                main.post { DialStore.importText(panelId, text, ImportSource.File); finish() }
            }
        }.start()
    }

    internal companion object {
        private const val REQUEST = 0x0D1A
        private const val EXTRA_PANEL = "io.github.lixiaolin94.dialkit.panel"
        private const val EXTRA_IMAGE_REQUEST = "io.github.lixiaolin94.dialkit.image"

        fun startImport(context: Context, panelId: String) {
            context.startActivity(Intent(context, DialKitPickerActivity::class.java).putExtra(EXTRA_PANEL, panelId))
        }

        /** Picks an image for the [ImagePicks] request [requestId]. */
        fun startImage(context: Context, requestId: Int) {
            context.startActivity(Intent(context, DialKitPickerActivity::class.java).putExtra(EXTRA_IMAGE_REQUEST, requestId))
        }

        private fun readCapped(input: InputStream?, max: Int): ByteArrayOutputStream? = input?.use {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) return null
            }
            out
        }

        /** SOURCE acceptFile: image type, ≤ 10 MB, and it must decode, before the value changes. */
        private fun copyImage(context: Context, uri: Uri): ImagePick {
            val resolver = context.contentResolver
            var name = "image"
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            }
            val type = resolver.getType(uri)
            if (type != null && !type.startsWith("image/")) return ImagePick.Failed(ImagePick.Reason.NotImage)
            if (size > MAX_IMAGE_BYTES) return ImagePick.Failed(ImagePick.Reason.TooLarge)
            val bytes = runCatching { readCapped(resolver.openInputStream(uri), MAX_IMAGE_BYTES) }.getOrNull()
                ?: return ImagePick.Failed(if (size < 0) ImagePick.Reason.TooLarge else ImagePick.Reason.Unreadable)
            val data = bytes.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ImagePick.Failed(ImagePick.Reason.Unreadable)
            // DESIGN: DialKit keeps uploads as data: URLs; on Android that would put megabytes into SharedPreferences,
            // so the file is copied into app storage under a content hash, keeping its name for the label.
            val hash = MessageDigest.getInstance("SHA-1").digest(data).take(6).joinToString("") { "%02x".format(it) }
            val safeName = name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(80).ifEmpty { "image" }
            val file = File(File(context.filesDir, "dialkit/images/$hash"), safeName)
            return runCatching {
                file.parentFile?.mkdirs()
                if (!file.exists()) file.writeBytes(data)
                ImagePick.Picked(Uri.fromFile(file).toString(), name)
            }.getOrElse { ImagePick.Failed(ImagePick.Reason.Unreadable) }
        }
    }
}

/** The outcome of an image pick. */
internal sealed class ImagePick {
    class Picked(val uri: String, val name: String) : ImagePick()
    object Cancelled : ImagePick()
    class Failed(val reason: Reason) : ImagePick()
    enum class Reason { NotImage, TooLarge, Unreadable, NoPicker }
}

/** Process-local callbacks for picks in flight (a pick outliving its panel is simply dropped). */
internal object ImagePicks {
    private val pending = HashMap<Int, (ImagePick) -> Unit>()
    private var next = 0

    fun request(onResult: (ImagePick) -> Unit): Int = next++.also { pending[it] = onResult }

    fun cancel(id: Int) {
        pending.remove(id)
    }

    fun deliver(id: Int, result: ImagePick) {
        pending.remove(id)?.invoke(result)
    }
}
