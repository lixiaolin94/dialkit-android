package io.github.lixiaolin94.dialkit.sample.compose

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.compose.rememberDialKit
import io.github.lixiaolin94.dialkit.compose.toAnimationSpec

class CardDials : DialGroup() {
    val title by text("Hello DialKit", placeholder = "Card title")
    val tint by color("#a78bfa")
    val cover by image(
        "android.resource://io.github.lixiaolin94.dialkit.sample.compose/drawable/sample_aurora",
        "android.resource://io.github.lixiaolin94.dialkit.sample.compose/drawable/sample_dune",
        "android.resource://io.github.lixiaolin94.dialkit.sample.compose/drawable/sample_lagoon",
        // Unsplash, free licence: "foggy mountain summit" (https://unsplash.com/photos/1Z2niiBPg5A)
        "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&q=80&fm=jpg",
    )
    val radius by slider(24f, 0f..64f)
    val scale by slider(1f, 0.5f..1.5f)
    val rotation by slider(0f, -45f..45f)
    val origin by pad()
    val layout by select("grid", "list")
    val columns by slider(3, 1..6)
    val elevated by toggle(true)
    val press by spring(visualDuration = 0.3f, bounce = 0.4f)
    val fade by easing(duration = 0.4f)
    val shadow by folder(collapsed = true) { ShadowDials() }
    val reset by action { resetValues() }
}

class ShadowDials : DialGroup() {
    val elevation by slider(12f, 0f..48f)
    val tint by slider(0.6f)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Demo() }
    }
}

private val Body = TextStyle(fontSize = 14.sp, color = Color(0xFF333333))

@Composable
private fun Demo() {
    val dials = rememberDialKit("Card", persist = DialPersist()) { CardDials() }
    var taps by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(if (pressed) 0.85f else 1f, dials.press.toAnimationSpec(), label = "press")
    var shown by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(if (shown) 1f else 0.15f, dials.fade.toAnimationSpec(), label = "fade")
    var zoom by remember { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { change, _, _ -> zoom = (zoom * change).coerceIn(0.5f, 3f) }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BasicText(
                "Three-finger double tap, Ctrl+Shift+D or an adb broadcast shows the entry",
                style = Body,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(180.dp)
                        .graphicsLayer {
                            scaleX = dials.scale * pressScale
                            scaleY = dials.scale * pressScale
                            rotationZ = dials.rotation
                            this.alpha = alpha
                            // Pad: x/y in −1..1 → transform origin 0..1 (y up).
                            transformOrigin = TransformOrigin((dials.origin.x + 1) / 2, (1 - dials.origin.y) / 2)
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    pressed = true
                                    tryAwaitRelease()
                                    pressed = false
                                },
                                onDoubleTap = { shown = !shown },
                            )
                        }
                        .shadow(
                            if (dials.elevated) dials.shadow.elevation.dp else 0.dp,
                            RoundedCornerShape(dials.radius.dp),
                            spotColor = Color.Black.copy(alpha = dials.shadow.tint),
                        )
                        .clip(RoundedCornerShape(dials.radius.dp))
                        .background(Color(dials.tint)),
                    contentAlignment = Alignment.Center,
                ) {
                    uriBitmap(dials.cover)?.let { Image(it, contentDescription = null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    BasicText(dials.title, style = TextStyle(fontSize = 16.sp, color = Color.White))
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val perRow = if (dials.layout == "list") 1 else dials.columns
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(perRow) { col ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(if (perRow == 1) 4f else 1f)
                                    .clip(RoundedCornerShape((dials.radius / 3).dp))
                                    .background(Color(0xFF7C3AED).copy(alpha = 0.25f + 0.1f * ((row + col) % 4))),
                            )
                        }
                    }
                }
            }
        }
        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { taps++ }
                    .padding(16.dp),
            ) { BasicText("Host taps: $taps (touches pass through)", style = Body) }
        }
        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .transformable(transform),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "Pinch: %.2f× (multi-touch is not swallowed)".format(zoom),
                    style = Body,
                    modifier = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom },
                )
            }
        }
        items(30) { index ->
            BasicText("Row $index (scrolling)", style = Body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

/** The image dial's value is a URI string; decode it off the main thread (no image library in the sample). */
@Composable
private fun uriBitmap(uri: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri.isEmpty()) null else withContext(Dispatchers.IO) {
            runCatching {
                val stream = if (uri.startsWith("http")) java.net.URL(uri).openStream() else context.contentResolver.openInputStream(Uri.parse(uri))
                stream?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}
