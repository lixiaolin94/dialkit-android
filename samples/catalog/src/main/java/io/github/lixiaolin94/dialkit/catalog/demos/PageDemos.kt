package io.github.lixiaolin94.dialkit.catalog.demos

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.catalog.BodyStyle
import io.github.lixiaolin94.dialkit.catalog.CaptionStyle
import io.github.lixiaolin94.dialkit.catalog.Demo
import io.github.lixiaolin94.dialkit.catalog.DemoScreen
import io.github.lixiaolin94.dialkit.catalog.EmbeddedViewsActivity
import io.github.lixiaolin94.dialkit.catalog.Notes
import io.github.lixiaolin94.dialkit.catalog.Palette
import io.github.lixiaolin94.dialkit.catalog.PillButton
import io.github.lixiaolin94.dialkit.catalog.Stage
import io.github.lixiaolin94.dialkit.catalog.TitleStyle
import io.github.lixiaolin94.dialkit.compose.DialKitPanel
import io.github.lixiaolin94.dialkit.compose.rememberDialKit
import io.github.lixiaolin94.dialkit.compose.toAnimationSpec

class PageCardDials : DialGroup() {
    val title by text("Tuned in the page")
    val tint by color("#22d3ee")
    val radius by slider(20f, 0f..48f)
    val elevation by slider(10f, 0f..32f)
    val layout by select("square", "wide", "tall")
    val press by spring(visualDuration = 0.35f, bounce = 0.35f)
    val details by folder(collapsed = true) { PageDetailDials() }
}

class PageDetailDials : DialGroup() {
    val opacity by slider(1f, 0f..1f)
    val outline by toggle(false)
}

/**
 * The panel as part of a Compose page: it sits in the page's scrolling Column, grows with its controls, and the
 * page scrolls it. A plain Column rather than a LazyColumn, so the panel stays composed while scrolled away.
 */
@Composable
internal fun PagePanelDemo(demo: Demo, onBack: () -> Unit) {
    val dials = rememberDialKit("Page panel") { PageCardDials() }
    var pressed by rememberSaveable { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, dials.press.toAnimationSpec(), label = "press")
    Column(
        Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(
                "‹ Catalog",
                style = BodyStyle.copy(color = Palette.accent),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 8.dp),
            )
            BasicText(demo.title, style = TitleStyle)
            BasicText(demo.summary, style = CaptionStyle)
        }
        Stage(height = 240.dp) {
            val (w, h) = when (dials.layout) { "wide" -> 200 to 110; "tall" -> 110 to 180; else -> 150 to 150 }
            val shape = RoundedCornerShape(dials.radius.dp)
            Box(
                Modifier
                    .size(w.dp, h.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = dials.details.opacity }
                    .shadow(dials.elevation.dp, shape)
                    .clip(shape)
                    .background(Color(dials.tint))
                    .then(if (dials.details.outline) Modifier.border(3.dp, Color.White, shape) else Modifier)
                    .clickable { pressed = !pressed },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(dials.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            }
        }
        Notes(
            "The panel below is part of this page: no entry button, no floating card.",
            "While this screen shows it, the floating panel no longer lists \"Page panel\".",
            "Pickers float above the page; Back closes an open picker first.",
            "The panel grows with its controls and the page scrolls them.",
        )
        DialKitPanel(dials, Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
    }
}

/** Opens a plain Activity whose bottom half is a fixed-height DialPanelView that scrolls inside. */
@Composable
internal fun ViewsPageDemo(demo: Demo, onBack: () -> Unit) {
    val context = LocalContext.current
    DemoScreen(demo, onBack, onOpenPanel = null) {
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(
                    "A View-based screen (android.app.Activity, no Compose) whose lower half is a DialPanelView with a " +
                        "fixed height: the title and toolbar stay, the controls scroll inside.",
                    style = BodyStyle,
                )
                PillButton("Open the Views screen", onClick = { context.startActivity(Intent(context, EmbeddedViewsActivity::class.java)) })
            }
        }
    }
}
