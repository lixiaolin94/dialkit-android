package io.github.lixiaolin94.dialkit.embedded

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lixiaolin94.dialkit.compose.DialKitPanel

/** A Tuning screen in Compose: a live preview, then the panel in the page's scrolling column. */
class TuningComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dials = rememberCardDials()
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF5F5F7)).safeDrawingPadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                BasicText(
                    "Tuning",
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111)),
                    modifier = Modifier.padding(start = 20.dp, top = 20.dp),
                )
                HomeCard(dials, Modifier.padding(horizontal = 20.dp))
                DialKitPanel(dials, Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
