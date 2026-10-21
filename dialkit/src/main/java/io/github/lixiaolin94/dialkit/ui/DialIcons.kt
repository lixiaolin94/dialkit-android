package io.github.lixiaolin94.dialkit.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import io.github.lixiaolin94.dialkit.R

/** The icon set, as VectorDrawables in `res/drawable` (paths from DialKit icons.ts; see each file's header). */
internal object DialIcons {
    @DrawableRes val Close = R.drawable.dialkit_ic_close
    @DrawableRes val Chevron = R.drawable.dialkit_ic_chevron
    @DrawableRes val Check = R.drawable.dialkit_ic_check
    @DrawableRes val Plus = R.drawable.dialkit_ic_plus
    @DrawableRes val Trash = R.drawable.dialkit_ic_trash
    @DrawableRes val Clipboard = R.drawable.dialkit_ic_clipboard
    @DrawableRes val Import = R.drawable.dialkit_ic_import
    @DrawableRes val File = R.drawable.dialkit_ic_file
    @DrawableRes val Panel = R.drawable.dialkit_ic_panel
    @DrawableRes val Image = R.drawable.dialkit_ic_image
    @DrawableRes val Upload = R.drawable.dialkit_ic_upload
}

/** A tinted vector icon fitted to the view (padding respected); rotation goes through View.rotation. */
@SuppressLint("ViewConstructor")
internal class IconView(context: Context, @DrawableRes icon: Int) : ImageView(context) {
    @get:ColorInt
    var tint: Int = 0xFFFFFFFF.toInt()
        set(@ColorInt v) {
            field = v
            imageTintList = ColorStateList.valueOf(v)
        }

    init {
        setImageResource(icon)
        scaleType = ScaleType.FIT_CENTER
        imageTintList = ColorStateList.valueOf(tint)
    }
}
