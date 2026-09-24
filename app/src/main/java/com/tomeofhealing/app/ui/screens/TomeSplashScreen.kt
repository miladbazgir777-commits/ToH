package com.tomeofhealing.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tomeofhealing.app.ui.components.EmblemView
import com.tomeofhealing.app.ui.components.FantasyBackdrop
import com.tomeofhealing.app.ui.components.RuneDivider
import com.tomeofhealing.app.ui.components.TomeOfHealingMainEmblem
import kotlinx.coroutines.delay

@Composable
fun TomeSplashScreen(onFinished: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (entered) 1f else .86f, tween(650, easing = FastOutSlowInEasing), label = "crestScale")
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(520), label = "crestAlpha")

    LaunchedEffect(Unit) {
        entered = true
        delay(1500)
        onFinished()
    }

    FantasyBackdrop {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EmblemView(TomeOfHealingMainEmblem, size = 190.dp, animate = true, modifier = Modifier.scale(scale).alpha(alpha))
            Spacer(Modifier.height(18.dp))
            Text("Tome of Healing", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.alpha(alpha))
            Spacer(Modifier.height(8.dp))
            RuneDivider(Modifier.widthIn(max = 360.dp).alpha(alpha))
            Spacer(Modifier.height(8.dp))
            Text("A physician's living codex", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary.copy(alpha = .78f), modifier = Modifier.alpha(alpha))
        }
    }
}
