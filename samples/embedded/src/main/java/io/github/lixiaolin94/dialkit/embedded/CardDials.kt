package io.github.lixiaolin94.dialkit.embedded

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.DialGroup
import io.github.lixiaolin94.dialkit.DialPersist
import io.github.lixiaolin94.dialkit.compose.rememberDialKit
import io.github.lixiaolin94.dialkit.compose.toAnimationSpec

/** The app's one group. Every screen registers it under the same id, so they all share one set of values. */
class CardDials : DialGroup() {
    val title by text("Good morning")
    val subtitle by text("3 meetings · 2 reminders")
    val tint by color("#6366f1")
    val radius by slider(24f, 0f..48f)
    val elevation by slider(12f, 0f..32f)
    val padding by slider(20f, 8f..40f)
    val compact by toggle(false)
    val press by spring(visualDuration = 0.3f, bounce = 0.3f)
}

const val CARD_NAME = "Card"
const val CARD_ID = "card"

/** Values and versions survive restarts, like settings. */
val CardPersist = DialPersist()

@Composable
fun rememberCardDials(): CardDials = rememberDialKit(CARD_NAME, id = CARD_ID, persist = CardPersist) { CardDials() }

/** The card the dials shape; press it to see the press spring. */
@Composable
fun HomeCard(dials: CardDials, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, dials.press.toAnimationSpec(), label = "press")
    val shape = RoundedCornerShape(dials.radius.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(dials.elevation.dp, shape)
            .clip(shape)
            .background(Color(dials.tint))
            .clickable(interactionSource = source, indication = null) { }
            .padding(dials.padding.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (dials.compact) 2.dp else 8.dp),
    ) {
        BasicText(dials.title, style = TextStyle(fontSize = if (dials.compact) 18.sp else 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
        BasicText(dials.subtitle, style = TextStyle(fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)))
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BasicText(
        text,
        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF4F46E5)),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE0E7FF))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
