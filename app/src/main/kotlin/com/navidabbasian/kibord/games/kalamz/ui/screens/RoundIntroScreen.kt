package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.games.kalamz.model.RoundType

private val BlueAccent = Color(0xFF64B5F6)
private val PurpleAccent = Color(0xFFBA68C8)

@Composable
fun RoundIntroScreen(round: RoundType, onStartRound: () -> Unit) {
    val sound = LocalSoundManager.current
    LaunchedEffect(Unit) { sound?.playRoundStart() }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "s"
    )

    val roundEmoji = when (round) {
        RoundType.DESCRIBE   -> "🗣️"
        RoundType.ONE_WORD   -> "☝️"
        RoundType.PANTOMIME  -> "🎭"
    }
    val roundAccent = when (round) {
        RoundType.DESCRIBE  -> kiExtras.gold
        RoundType.ONE_WORD  -> BlueAccent
        RoundType.PANTOMIME -> PurpleAccent
    }

    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated emoji badge for each round
            Box(
                modifier = Modifier.size(140.dp).scale(scale).background(roundAccent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(110.dp).background(roundAccent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = roundEmoji, fontSize = 56.sp, textAlign = TextAlign.Center)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = roundAccent.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, roundAccent.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Text(
                    text = "راند ${round.roundNumber} از ۳",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = roundAccent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(round.persianTitle.substringAfter(": ").ifBlank { round.persianTitle },
                fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = kiExtras.glass),
                border = BorderStroke(1.dp, kiExtras.glassBorder), elevation = CardDefaults.cardElevation(0.dp)) {
                Text(round.persianDescription, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, lineHeight = 28.sp, modifier = Modifier.padding(16.dp))
            }

            Spacer(modifier = Modifier.height(48.dp))

            KButton(
                text = "شروع", onClick = onStartRound,
                style = KButtonStyle.Primary, accent = roundAccent,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }
    }
}
