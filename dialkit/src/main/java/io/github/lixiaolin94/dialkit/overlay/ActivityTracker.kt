package io.github.lixiaolin94.dialkit.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.WeakHashMap
import io.github.lixiaolin94.dialkit.DialKitConfig
import io.github.lixiaolin94.dialkit.store.DialKitPickerActivity

/**
 * Attaches the overlay on resume (after the host's onCreate, so an early getDecorView() cannot break
 * requestWindowFeature and AppCompat has already wrapped the Window.Callback).
 */
internal class ActivityTracker(private val config: DialKitConfig) : Application.ActivityLifecycleCallbacks {
    private val attachments = WeakHashMap<Activity, ActivityAttachment>()

    override fun onActivityResumed(activity: Activity) {
        if (activity is DialKitPickerActivity) return // DialKit's own trampoline: no overlay, no gesture hook
        OverlayController.start()
        attachments.getOrPut(activity) { ActivityAttachment(activity, config) }.attach()
    }

    override fun onActivityDestroyed(activity: Activity) {
        attachments.remove(activity)?.detach()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
