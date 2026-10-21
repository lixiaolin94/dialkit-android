package io.github.lixiaolin94.dialkit.catalog

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The catalog's own look: plain foundation, no Material dependency. */
internal object Palette {
    val background = Color(0xFFF2F2F2)
    val surface = Color.White
    val text = Color(0xFF1A1A1A)
    val secondary = Color(0xFF6B6B6B)
    val outline = Color(0xFFE2E2E2)
    val accent = Color(0xFF7C3AED)
}

internal val TitleStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Palette.text)
internal val HeadingStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.secondary)
internal val BodyStyle = TextStyle(fontSize = 15.sp, color = Palette.text, lineHeight = 21.sp)
internal val CaptionStyle = TextStyle(fontSize = 13.sp, color = Palette.secondary, lineHeight = 18.sp)

/**
 * One demo screen: a back link, the title and what to try, an optional "Open panel" button, then [content].
 * [onOpenPanel] is usually `{ dials.setOpen(true) }`.
 */
@Composable
internal fun DemoScreen(
    demo: Demo,
    onBack: () -> Unit,
    onOpenPanel: (() -> Unit)?,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(
                    "‹ Catalog",
                    style = BodyStyle.copy(color = Palette.accent),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 8.dp),
                )
                BasicText(demo.title, style = TitleStyle)
                BasicText(demo.summary, style = CaptionStyle)
                if (onOpenPanel != null) PillButton("Open panel", onClick = onOpenPanel)
            }
        }
        content()
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** A white card that centres what the dials drive. */
@Composable
internal fun Stage(height: Dp = 260.dp, background: Color = Palette.surface, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A white card for text and rows. */
@Composable
internal fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) BasicText(title.uppercase(), style = HeadingStyle)
        content()
    }
}

/** "Try this" notes under a stage. */
@Composable
internal fun Notes(vararg lines: String) {
    Section("Try this") {
        lines.forEach { BasicText("• $it", style = BodyStyle) }
    }
}

@Composable
internal fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = BodyStyle.copy(color = Palette.accent, fontWeight = FontWeight.Medium),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Palette.accent.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun ButtonRow(content: @Composable () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A label / value line, for showing what the dials read. */
@Composable
internal fun ValueLine(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        BasicText(name, style = CaptionStyle, modifier = Modifier.weight(1f))
        BasicText(value, style = BodyStyle)
    }
}

/** `#RRGGBB` (plus alpha when not opaque) of an ARGB int, for display. */
internal fun Int.toHex(): String =
    if (ushr(24) == 0xFF) "#%06X".format(this and 0xFFFFFF) else "#%08X".format(this)

/** An image dial's value is a URI string; decode it off the main thread (no image library in the sample). */
@Composable
internal fun rememberUriBitmap(uri: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                val stream = if (uri.startsWith("http")) {
                    java.net.URL(uri).openStream()
                } else {
                    context.contentResolver.openInputStream(Uri.parse(uri))
                }
                stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}
