package io.github.lixiaolin94.dialkit

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.net.Uri
import io.github.lixiaolin94.dialkit.store.DialStore

/**
 * Auto-install hook, merged into the host manifest. Storage is set up in every build, so persisted values and
 * versions load wherever groups register (in-page panels work in release builds too); only debuggable apps get
 * the overlay. Remove it with
 * `tools:node="remove"` to call [DialKit.install] yourself. A plain ContentProvider instead of
 * androidx.startup keeps DialKit free of AndroidX dependencies.
 */
public class DialKitInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        DialStore.init(app)
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) DialKit.install(app)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
