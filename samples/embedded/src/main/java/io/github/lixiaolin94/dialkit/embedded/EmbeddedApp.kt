package io.github.lixiaolin94.dialkit.embedded

import android.app.Application
import io.github.lixiaolin94.dialkit.DialKit

/** Turns the floating panel off for the whole app: the dials are tuned only on the app's own Tuning screens. */
class EmbeddedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DialKit.configure { floatingPanel = false }
    }
}
