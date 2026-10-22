package io.github.lixiaolin94.dialkit

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** No-op twin of the in-page panel: same API, draws nothing (a wrap-content view takes no space). */
public class DialPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    public var group: DialGroup? = null
}
