package io.github.lixiaolin94.dialkit.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lixiaolin94.dialkit.DialKit
import io.github.lixiaolin94.dialkit.catalog.demos.catalogSections

/** One catalog entry. [content] gets a callback that returns to the list. */
internal class Demo(
    val id: String,
    val title: String,
    val summary: String,
    val content: @Composable (demo: Demo, onBack: () -> Unit) -> Unit,
)

internal class CatalogSection(val title: String, val demos: List<Demo>)

/** Whether the DialKit panel is expanded, fed by `DialKitConfig.onOpenChange` (read by the controller demo). */
internal object PanelState {
    var open by mutableStateOf(false)
}

class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Start-up options are read when this Activity first resumes, so configuring here is in time.
        DialKit.configure {
            entryVisibleByDefault = true
            onOpenChange = { open -> PanelState.open = open }
        }
        setContent { Catalog() }
    }
}

@Composable
private fun Catalog() {
    val sections = catalogSections
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = currentId?.let { id -> sections.flatMap { it.demos }.firstOrNull { it.id == id } }
    BackHandler(enabled = current != null) { currentId = null }
    if (current != null) {
        current.content(current) { currentId = null }
    } else {
        Home(sections) { currentId = it.id }
    }
}

@Composable
private fun Home(sections: List<CatalogSection>, onOpen: (Demo) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("DialKit Catalog", style = TitleStyle)
                BasicText(
                    "Every control and panel feature of DialKit for Android, one screen each. Each screen registers " +
                        "its own panel; leaving the screen removes it.",
                    style = CaptionStyle,
                )
                BasicText(
                    "Show the entry with a three-finger double tap, Ctrl+Shift+D on a hardware keyboard, or the " +
                        "button below. Tap the entry to expand the panel.",
                    style = CaptionStyle,
                )
                PillButton("Show entry", onClick = { DialKit.showEntry() })
            }
        }
        sections.forEach { section ->
            item { BasicText(section.title.uppercase(), style = HeadingStyle, modifier = Modifier.padding(start = 16.dp, top = 12.dp)) }
            items(section.demos, key = { it.id }) { demo -> DemoRow(demo) { onOpen(demo) } }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun DemoRow(demo: Demo, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BasicText(demo.title, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
        BasicText(demo.summary, style = CaptionStyle)
    }
}
