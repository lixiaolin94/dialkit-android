package io.github.lixiaolin94.dialkit.embedded

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The app's own screen: the card as users see it, and the way into the Tuning screens. No floating panel. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dials = rememberCardDials()
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF5F5F7)).safeDrawingPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                BasicText("DialKit Embedded", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111)))
                BasicText(
                    "This app has no floating panel (floatingPanel = false). The card's dials are tuned on the app's " +
                        "own Tuning screens, and the values are saved across restarts.",
                    style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, color = Color(0xFF6B6B6B)),
                )
                HomeCard(dials)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Pill("Tune (Compose)", onClick = { startActivity(Intent(this@MainActivity, TuningComposeActivity::class.java)) })
                    Pill("Tune (Views)", onClick = { startActivity(Intent(this@MainActivity, TuningViewsActivity::class.java)) })
                }
            }
        }
    }
}
