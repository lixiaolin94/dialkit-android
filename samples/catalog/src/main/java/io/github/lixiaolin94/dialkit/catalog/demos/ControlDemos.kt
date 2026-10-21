package io.github.lixiaolin94.dialkit.catalog.demos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialAxis
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.catalog.BodyStyle
import io.github.lixiaolin94.dialkit.catalog.CaptionStyle
import io.github.lixiaolin94.dialkit.catalog.Demo
import io.github.lixiaolin94.dialkit.catalog.DemoScreen
import io.github.lixiaolin94.dialkit.catalog.Notes
import io.github.lixiaolin94.dialkit.catalog.Palette
import io.github.lixiaolin94.dialkit.catalog.Section
import io.github.lixiaolin94.dialkit.catalog.Stage
import io.github.lixiaolin94.dialkit.catalog.ValueLine
import io.github.lixiaolin94.dialkit.catalog.rememberUriBitmap
import io.github.lixiaolin94.dialkit.catalog.toHex
import io.github.lixiaolin94.dialkit.compose.rememberDialKit
import io.github.lixiaolin94.dialkit.model.DialOption

// ---- Sliders

class SliderDials : DialGroup() {
    val width by slider(180f, 60f..300f)                 // explicit range, step inferred
    val height by slider(120f, 40f..200f, step = 10f)    // explicit step
    val radius by slider(16f)                            // bare number: range and step inferred
    val opacity by slider(1f, 0f..1f)
    val bars by slider(5, 1..12)                         // integer slider
}

@Composable
internal fun SlidersDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Sliders") { SliderDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(dials.width.dp.coerceAtMost(300.dp), dials.height.dp)
                            .graphicsLayer { alpha = dials.opacity }
                            .clip(RoundedCornerShape(dials.radius.dp))
                            .background(Palette.accent),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(dials.bars) { Box(Modifier.size(10.dp, 24.dp).clip(RoundedCornerShape(3.dp)).background(Palette.accent.copy(alpha = 0.4f))) }
                    }
                }
            }
        }
        item {
            Notes(
                "Drag anywhere on a row: the value follows the finger.",
                "Tap to jump: sliders with ten steps or fewer snap to a step, others to the nearest tenth.",
                "Drag past either end to stretch the track like a rubber band.",
                "Long-press a slider to type an exact value.",
                "Radius is declared as a bare number, so its range is inferred from the default.",
            )
        }
    }
}

// ---- Toggles

class ToggleDials : DialGroup() {
    val visible by toggle(true)
    val rounded by toggle(true)
    val outlined by toggle(false)
    val shadow by toggle(true)
}

@Composable
internal fun TogglesDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Toggles") { ToggleDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage {
                if (dials.visible) {
                    val shape = RoundedCornerShape(if (dials.rounded) 28.dp else 0.dp)
                    Box(
                        Modifier
                            .size(140.dp)
                            .shadow(if (dials.shadow) 16.dp else 0.dp, shape)
                            .clip(shape)
                            .background(Color(0xFF34D399))
                            .then(if (dials.outlined) Modifier.border(4.dp, Palette.text, shape) else Modifier),
                    )
                } else {
                    BasicText("Hidden", style = CaptionStyle)
                }
            }
        }
        item { Notes("Tap either side of a toggle, or slide its thumb.", "Each toggle is a two-segment control, as in DialKit.") }
    }
}

// ---- Select

class SelectDials : DialGroup() {
    val shape by select("circle", "square", "pill")
    val size by select("small", "medium", "large", default = "medium")
    val align by selectOptions(
        listOf(DialOption("start", "Left"), DialOption("center", "Center"), DialOption("end", "Right")),
        default = "center",
        label = "Alignment",
    )
}

@Composable
internal fun SelectDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Select") { SelectDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage {
                val side = when (dials.size) { "small" -> 64.dp; "large" -> 140.dp; else -> 100.dp }
                val width = if (dials.shape == "pill") side * 2 else side
                val shape = when (dials.shape) { "square" -> RoundedCornerShape(12.dp); else -> RoundedCornerShape(50) }
                val alignment = when (dials.align) { "start" -> Alignment.CenterStart; "end" -> Alignment.CenterEnd; else -> Alignment.Center }
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = alignment) {
                    Box(Modifier.size(width, side).clip(shape).background(Color(0xFF60A5FA)))
                }
            }
        }
        item {
            Notes(
                "Option labels are Title Case by default: \"medium\" shows as Medium.",
                "Alignment uses selectOptions with explicit labels; the value stays \"start\" / \"center\" / \"end\".",
            )
        }
    }
}

// ---- Color

class ColorDials : DialGroup() {
    val hex by color("#a78bfa", label = "Hex")
    val oklch by color("oklch(0.75 0.15 160)", label = "OKLCH")
    val displayP3 by color("color(display-p3 1 0.45 0.2)", label = "Display P3")
    val background by color(0xFF1F2937.toInt())
}

@Composable
internal fun ColorDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Color") { ColorDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(background = Color(dials.background)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(dials.hex, dials.oklch, dials.displayP3).forEach { argb ->
                        Box(Modifier.size(80.dp).clip(CircleShape).background(Color(argb)))
                    }
                }
            }
        }
        item {
            Section("Reads") {
                ValueLine("Hex", dials.hex.toHex())
                ValueLine("OKLCH", dials.oklch.toHex())
                ValueLine("Display P3", dials.displayP3.toHex())
                ValueLine("Background", dials.background.toHex())
            }
        }
        item {
            Notes(
                "Tap a swatch to open the picker: saturation and lightness plane, hue and opacity tracks.",
                "Each color keeps the format it was declared in (hex, oklch, display-p3); switch with the segmented control.",
                "Reads are always sRGB ARGB ints; wide-gamut picks are fitted into sRGB.",
                "Tap the value to type any CSS color.",
            )
        }
    }
}

// ---- Text

class TextDials : DialGroup() {
    val title by text("Weekend in Lisbon", placeholder = "Title")
    val body by text("Three days of tiles, trams and custard tarts.", placeholder = "Body")
    val badge by text("", placeholder = "Optional badge")
}

@Composable
internal fun TextDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Text") { TextDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF7ED)).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (dials.badge.isNotEmpty()) {
                        BasicText(
                            dials.badge.uppercase(),
                            style = CaptionStyle.copy(color = Color(0xFFC2410C), fontWeight = FontWeight.SemiBold),
                        )
                    }
                    BasicText(dials.title, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Palette.text))
                    BasicText(dials.body, style = BodyStyle)
                }
            }
        }
        item { Notes("Text fields grow up to five lines.", "An empty field shows its placeholder; Badge starts empty.") }
    }
}

// ---- Pad

class PadDials : DialGroup() {
    val position by pad()
    val light by pad(
        x = DialAxis(default = -0.5f),
        y = DialAxis(default = 0.5f),
        xLabel = "H",
        yLabel = "V",
    )
    val snap by toggle(false, label = "Snap to grid")
}

@Composable
internal fun PadDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Pad") { PadDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(height = 280.dp) {
                // Pad x / y run −1..1 with y pointing up; map them into the stage.
                val p = dials.position
                val x = if (dials.snap) Math.round(p.x * 2) / 2f else p.x
                val y = if (dials.snap) Math.round(p.y * 2) / 2f else p.y
                val light = dials.light
                Box(
                    Modifier
                        .offset(x = (x * 110).dp, y = (-y * 100).dp)
                        .size(64.dp)
                        .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(Color(0xFFF472B6)),
                )
                Box(
                    Modifier
                        .offset(x = (x * 110 + light.x * 18).dp, y = (-y * 100 - light.y * 18).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f)),
                )
            }
        }
        item {
            Section("Reads") {
                ValueLine("Position", "%.2f, %.2f".format(dials.position.x, dials.position.y))
                ValueLine("Light", "%.2f, %.2f".format(dials.light.x, dials.light.y))
            }
        }
        item {
            Notes(
                "Press and hold the pad, then drag: the point keeps its offset from your finger.",
                "Double-tap the pad to return to the default.",
                "Light uses custom axis labels (H, V) and starts off-centre.",
            )
        }
    }
}

// ---- Image

private const val RES = "android.resource://io.github.lixiaolin94.dialkit.catalog/drawable"

class ImageDials : DialGroup() {
    val cover by imageOptions(
        listOf(
            DialOption("$RES/sample_aurora", "Aurora"),
            DialOption("$RES/sample_dune", "Dune"),
            DialOption("$RES/sample_lagoon", "Lagoon"),
            // Unsplash, free licence: "foggy mountain summit" (https://unsplash.com/photos/1Z2niiBPg5A)
            DialOption("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=800&q=80&fm=jpg", "Mountain"),
        ),
    )
    val fit by select("crop", "fit")
    val radius by slider(20f, 0f..48f)
}

@Composable
internal fun ImageDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Image") { ImageDials() }
    val bitmap = rememberUriBitmap(dials.cover)
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(height = 280.dp) {
                Box(
                    Modifier.padding(24.dp).fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(dials.radius.dp)).background(Palette.outline),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (dials.fit == "fit") ContentScale.Fit else ContentScale.Crop,
                        )
                    } else {
                        BasicText(if (dials.cover.isEmpty()) "No image" else "Loading…", style = CaptionStyle)
                    }
                }
            }
        }
        item { Section("Value") { BasicText(dials.cover.ifEmpty { "\"\"" }, style = CaptionStyle) } }
        item {
            Notes(
                "The image dial reads a URI string; this screen decodes it itself.",
                "Upload image picks from the system photo picker and copies the file into app storage.",
                "Remove clears the value to an empty string.",
                "Mountain is loaded over https and needs a network connection.",
            )
        }
    }
}

// ---- Folders and actions

class FolderDials : DialGroup() {
    val layout by folder { LayoutDials() }
    val style by folder(collapsed = true) { StyleDials() }
    // Writes a nested path from inside the group; the panel follows.
    val shuffle by action("Shuffle color") {
        setValues("style.tint" to "#%06x".format(java.util.Random().nextInt(0xFFFFFF + 1)))
    }
    val reset by action("Reset all") { resetValues() }
}

class LayoutDials : DialGroup() {
    val columns by slider(3, 1..5)
    val gap by slider(8f, 0f..24f)
    val corners by folder(collapsed = true) { CornerDials() }
}

class CornerDials : DialGroup() {
    val radius by slider(10f, 0f..32f)
    val square by toggle(false)
}

class StyleDials : DialGroup() {
    val tint by color("#f59e0b")
    val fade by slider(0.15f, 0f..0.5f, label = "Fade per tile")
}

@Composable
internal fun FoldersDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Folders") { FolderDials() }
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            val layout = dials.layout
            val corner = if (layout.corners.square) 0.dp else layout.corners.radius.dp
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(layout.gap.dp)) {
                        repeat(layout.columns) { col ->
                            val i = row * layout.columns + col
                            val base = Color(dials.style.tint)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(corner))
                                    .background(base.copy(alpha = (1f - dials.style.fade * i).coerceAtLeast(0.1f))),
                            )
                        }
                    }
                }
            }
        }
        item {
            Notes(
                "Folders nest: Layout holds Corners, which starts collapsed.",
                "Folder paths join with dots: \"layout.corners.radius\".",
                "Shuffle color calls setValues(\"style.tint\" to …) from its callback; Reset all calls resetValues().",
            )
        }
    }
}
