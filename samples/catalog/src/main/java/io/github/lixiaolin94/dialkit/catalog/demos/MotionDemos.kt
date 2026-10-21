package io.github.lixiaolin94.dialkit.catalog.demos

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialTransition
import io.github.lixiaolin94.dialkit.catalog.CaptionStyle
import io.github.lixiaolin94.dialkit.catalog.Demo
import io.github.lixiaolin94.dialkit.catalog.DemoScreen
import io.github.lixiaolin94.dialkit.catalog.Notes
import io.github.lixiaolin94.dialkit.catalog.Palette
import io.github.lixiaolin94.dialkit.catalog.Section
import io.github.lixiaolin94.dialkit.catalog.Stage
import io.github.lixiaolin94.dialkit.catalog.ValueLine
import io.github.lixiaolin94.dialkit.compose.rememberDialKit
import io.github.lixiaolin94.dialkit.compose.toAnimationSpec
import kotlinx.coroutines.delay

/** Flips [flip] every [periodMillis] while [enabled]. */
@Composable
private fun Loop(enabled: Boolean, periodMillis: Long, flip: () -> Unit) {
    LaunchedEffect(enabled) {
        while (enabled) {
            delay(periodMillis)
            flip()
        }
    }
}

/** How a transition dial reads, for the "Reads" card. */
private fun DialTransition.describe(): String = when {
    isEasing -> "easing %.2fs [%s]".format(durationSeconds, ease?.joinToString { "%.2f".format(it) })
    isPhysicsSpring -> "physics spring, stiffness %.0f, ζ %.2f".format(stiffness, dampingRatio)
    else -> "spring %.2fs, bounce %.2f".format(visualDuration, bounce)
}

// ---- Springs

class SpringDials : DialGroup() {
    val timeSpring by spring(visualDuration = 0.4f, bounce = 0.3f, label = "Time spring")
    val physics by physicsSpring(stiffness = 300f, damping = 18f, mass = 1f, label = "Physics spring")
    val loop by toggle(false)
    val play by action("Play")
}

@Composable
internal fun SpringsDemo(demo: Demo, onBack: () -> Unit) {
    var atEnd by rememberSaveable { mutableStateOf(false) }
    val dials = rememberDialKit("Springs", onAction = { if (it == "play") atEnd = !atEnd }) { SpringDials() }
    Loop(dials.loop, 1400) { atEnd = !atEnd }
    val a by animateFloatAsState(if (atEnd) 1f else 0f, dials.timeSpring.toAnimationSpec(), label = "time")
    val b by animateFloatAsState(if (atEnd) 1f else 0f, dials.physics.toAnimationSpec(), label = "physics")
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(height = 220.dp) {
                Column(
                    Modifier.fillMaxSize().clickable { atEnd = !atEnd }.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    Track("Time spring", a, Color(0xFF8B5CF6))
                    Track("Physics spring", b, Color(0xFF0EA5E9))
                }
            }
        }
        item {
            Section("Reads") {
                ValueLine("Time spring", dials.timeSpring.describe())
                ValueLine("Physics spring", dials.physics.describe())
            }
        }
        item {
            Notes(
                "Tap the stage, or Play in the panel, to send both balls across.",
                "Turn on Loop to keep them moving while you tune.",
                "Each curve has a Type row: switch between time spring, physics spring and easing.",
                "The graph previews the curve; drag the sliders and watch it reshape.",
            )
        }
    }
}

@Composable
private fun Track(label: String, fraction: Float, color: Color, ball: Dp = 36.dp) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = CaptionStyle)
        BoxWithConstraints(Modifier.fillMaxWidth().height(ball).clip(RoundedCornerShape(50)).background(Palette.background)) {
            Box(
                Modifier
                    .offset(x = (maxWidth - ball) * fraction)
                    .size(ball)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// ---- Easing

class EasingDials : DialGroup() {
    val enter by easing(duration = 0.45f, ease = floatArrayOf(0.22f, 1f, 0.36f, 1f))
    val exit by easing(duration = 0.25f, ease = floatArrayOf(0.4f, 0f, 1f, 1f))
    val distance by slider(48f, 0f..120f)
    val loop by toggle(false)
    val play by action("Play")
}

@Composable
internal fun EasingDemo(demo: Demo, onBack: () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(true) }
    val dials = rememberDialKit("Easing", onAction = { if (it == "play") shown = !shown }) { EasingDials() }
    Loop(dials.loop, 1200) { shown = !shown }
    val spec = if (shown) dials.enter.toAnimationSpec<Float>() else dials.exit.toAnimationSpec()
    val progress by animateFloatAsState(if (shown) 1f else 0f, spec, label = "card")
    DemoScreen(demo, onBack, onOpenPanel = { dials.setOpen(true) }) {
        item {
            Stage(height = 240.dp) {
                Box(Modifier.fillMaxSize().clickable { shown = !shown }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                alpha = progress
                                translationY = (1f - progress) * dials.distance.dp.toPx()
                            }
                            .size(220.dp, 120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF111827)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            if (shown) "Enter" else "Exit",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White),
                        )
                    }
                }
            }
        }
        item {
            Section("Reads") {
                ValueLine("Enter", dials.enter.describe())
                ValueLine("Exit", dials.exit.describe())
            }
        }
        item {
            Notes(
                "Tap the stage, or Play in the panel, to show and hide the card.",
                "Drag the curve's control points in the editor; the four numbers follow.",
                "Enter and Exit are separate curves, as a real transition would use.",
            )
        }
    }
}
