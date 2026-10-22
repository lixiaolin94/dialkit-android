package io.github.lixiaolin94.dialkit

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry

internal val instrumentation get() = InstrumentationRegistry.getInstrumentation()

internal fun <T> onMain(block: () -> T): T {
    var result: Result<T>? = null
    instrumentation.runOnMainSync { result = runCatching(block) }
    return result!!.getOrThrow()
}

/** Polls [check] on the main thread until it holds, for up to [timeoutMs]. */
internal fun waitUntil(message: String, timeoutMs: Long = 3000, check: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        if (onMain(check)) return
        SystemClock.sleep(50)
    }
    throw AssertionError("timed out: $message")
}

internal fun launchHost(): HostActivity = instrumentation.startActivitySync(
    Intent(instrumentation.targetContext, HostActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
) as HostActivity

class TestDials : DialGroup() {
    var radius by slider(24f, 0f..64f)
    val tint by color("#a78bfa")
}
