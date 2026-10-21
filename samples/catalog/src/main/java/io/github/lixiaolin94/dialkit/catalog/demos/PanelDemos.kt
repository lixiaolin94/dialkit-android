package io.github.lixiaolin94.dialkit.catalog.demos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.catalog.BodyStyle
import io.github.lixiaolin94.dialkit.catalog.ButtonRow
import io.github.lixiaolin94.dialkit.catalog.CaptionStyle
import io.github.lixiaolin94.dialkit.catalog.Demo
import io.github.lixiaolin94.dialkit.catalog.DemoScreen
import io.github.lixiaolin94.dialkit.catalog.Notes
import io.github.lixiaolin94.dialkit.catalog.PanelState
import io.github.lixiaolin94.dialkit.catalog.PillButton
import io.github.lixiaolin94.dialkit.catalog.Section
import io.github.lixiaolin94.dialkit.catalog.Stage
import io.github.lixiaolin94.dialkit.catalog.ValueLine
import io.github.lixiaolin94.dialkit.catalog.toHex
import io.github.lixiaolin94.dialkit.compose.rememberDialKit

// ---- Multiple panels

class LightDials : DialGroup() {
    val direction by pad()
    val intensity by slider(0.8f, 0f..1f)
    val color by color("#fff7ed")
}

class MaterialDials : DialGroup() {
    val base by color("#6366f1")
    val size by slider(160f, 80f..220f)
    val shadow by slider(24f, 0f..48f)
}

@Composable
internal fun MultiplePanelsDemo(demo: Demo, onBack: () -> Unit) {
    val light = rememberDialKit("Light") { LightDials() }
    val material = rememberDialKit("Material", defaultCollapsed = true) { MaterialDials() }
    DemoScreen(demo, onBack, onOpenPanel = { light.setOpen(true) }) {
        item {
            Stage(height = 280.dp, background = Color(0xFF0F172A)) {
                val d = light.direction
                Box(
                    Modifier
                        .size(material.size.dp)
                        .shadow(material.shadow.dp, CircleShape)
                        .clip(CircleShape)
                        .drawBehind {
                            drawRect(Color(material.base))
                            // The highlight follows the light pad (y up).
                            val center = Offset(size.width * (0.5f + d.x * 0.35f), size.height * (0.5f - d.y * 0.35f))
                            drawRect(
                                Brush.radialGradient(
                                    listOf(Color(light.color).copy(alpha = light.intensity), Color.Transparent),
                                    center = center,
                                    radius = size.minDimension * 0.6f,
                                ),
                            )
                        },
                )
            }
        }
        item {
            Notes(
                "Two groups on one screen: the panel lists Light and Material as sections.",
                "Material starts collapsed (defaultCollapsed = true).",
                "Each section has its own versions and its own Copy output.",
            )
        }
    }
}

// ---- Controller API

class ControllerDials : DialGroup() {
    val size by slider(120f, 60f..200f)
    val tint by color("#60a5fa")
    val rounded by toggle(true)
    val label by text("Controlled")
}

@Composable
internal fun ControllerDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Controller") { ControllerDials() }
    var lastGetOpen by remember { mutableStateOf<Boolean?>(null) }
    DemoScreen(demo, onBack, onOpenPanel = null) {
        item {
            Stage(height = 240.dp) {
                Box(
                    Modifier
                        .size(dials.size.dp)
                        .clip(RoundedCornerShape(if (dials.rounded) 24.dp else 0.dp))
                        .background(Color(dials.tint)),
                    contentAlignment = Alignment.Center,
                ) { BasicText(dials.label, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)) }
            }
        }
        item {
            ButtonRow {
                PillButton("setOpen(true)", onClick = { dials.setOpen(true) })
                PillButton("setOpen(false)", onClick = { dials.setOpen(false) })
                PillButton("getOpen()", onClick = { lastGetOpen = dials.getOpen() })
            }
        }
        item {
            ButtonRow {
                PillButton("setValues(…)", onClick = {
                    dials.setValues("size" to 180, "tint" to "#f472b6", "rounded" to false, "label" to "From code")
                })
                PillButton("resetValues()", onClick = { dials.resetValues() })
            }
        }
        item {
            Section("State") {
                ValueLine("onOpenChange", if (PanelState.open) "open" else "closed")
                ValueLine("getOpen()", lastGetOpen?.toString() ?: "–")
                ValueLine("size", "%.0f".format(dials.size))
                ValueLine("tint", dials.tint.toHex())
                ValueLine("rounded", dials.rounded.toString())
                ValueLine("label", dials.label)
            }
        }
        item {
            Notes(
                "The host drives the panel: setValues writes several dials at once with plain Kotlin values.",
                "resetValues restores the declared defaults and leaves the active version.",
                "onOpenChange comes from DialKit.configure; getOpen reads the state on demand.",
            )
        }
    }
}

// ---- Persistence and versions

class PersistenceDials : DialGroup() {
    val headline by text("Saved across restarts")
    val accent by color("#10b981")
    val count by slider(3, 1..8)
}

@Composable
internal fun PersistenceDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Persistence", persist = DialPersist(key = "catalog:persistence")) { PersistenceDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(height = 200.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(dials.headline, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color(dials.accent)))
                    Row(Modifier.padding(top = 16.dp)) {
                        repeat(dials.count) {
                            Box(Modifier.padding(4.dp).size(20.dp).clip(CircleShape).background(Color(dials.accent)))
                        }
                    }
                }
            }
        }
        item {
            Notes(
                "Values and versions are saved to SharedPreferences under \"catalog:persistence\".",
                "Change something, swipe the app away, reopen this screen: the values come back.",
                "Open the version menu in the panel header to save, switch and delete versions.",
                "Copy puts DialKit's JSON (and a Kotlin declaration) on the clipboard; Import reads it back.",
                "Other screens keep values only while the app is running.",
            )
        }
    }
}

// ---- Host gestures

class HostDials : DialGroup() {
    val highlight by color("#fde68a")
}

@Composable
internal fun HostGesturesDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Host") { HostDials() }
    var taps by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val transform = rememberTransformableState { change, _, _ -> zoom = (zoom * change).coerceIn(0.5f, 3f) }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Section("Tap") {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(dials.highlight)).clickable { taps++ }.padding(16.dp),
                ) { BasicText("Tapped $taps times", style = BodyStyle) }
            }
        }
        item {
            Section("Pinch") {
                Box(
                    Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)).background(Color(dials.highlight)).transformable(transform),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("%.2f×".format(zoom), style = BodyStyle, modifier = Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom })
                }
            }
        }
        item {
            Notes(
                "With the panel open, taps outside it still reach the app.",
                "Two-finger gestures are not swallowed: pinch the box above.",
                "The entry and the panel can be dragged anywhere; the list below still scrolls.",
            )
        }
        items(30) { index ->
            BasicText("Row ${index + 1}", style = CaptionStyle, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
        }
    }
}
