package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.games.kalamz.model.Player
import com.navidabbasian.kibord.games.kalamz.model.RoundType
import com.navidabbasian.kibord.games.kalamz.model.Team

// Round accents (theme-neutral, readable on both light and dark backgrounds)
private val BlueAccent = Color(0xFF64B5F6)
private val PurpleAccent = Color(0xFFBA68C8)

@Composable
fun TurnIntroScreen(
    currentPlayer: Player,
    team: Team,
    round: RoundType,
    onStartTurn: () -> Unit
) {
    val sound = LocalSoundManager.current
    LaunchedEffect(Unit) { sound?.playTurnStart() }

    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse), label = "p"
    )

    val teamColor = kiExtras.teamColors.teamColorFor(team.id)
    val roundAccent = when (round) {
        RoundType.DESCRIBE  -> kiExtras.gold
        RoundType.ONE_WORD  -> BlueAccent
        RoundType.PANTOMIME -> PurpleAccent
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
        ) {
            // Team badge
            Box(modifier = Modifier.size(100.dp).scale(pulse).background(teamColor.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(76.dp).background(teamColor.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = teamColor, modifier = Modifier.size(38.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Team name
            Surface(shape = RoundedCornerShape(20.dp), color = teamColor.copy(alpha = 0.25f)) {
                Text("تیم ${team.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = teamColor,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("نوبت", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(4.dp))
            Text(currentPlayer.name, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = kiExtras.gold, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(28.dp))

            // Round info card
            GlassCard(
                cornerRadius = 20.dp,
                containerColor = roundAccent.copy(alpha = 0.15f),
                borderColor = roundAccent.copy(alpha = 0.35f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("راند ${round.roundNumber}", fontSize = 13.sp, color = roundAccent.copy(alpha = 0.8f))
                    Text(round.persianTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = roundAccent)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(round.persianDescription, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scores summary
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Star, contentDescription = null, tint = kiExtras.gold, modifier = Modifier.size(18.dp).align(Alignment.CenterVertically))
                Text("امتیاز کل تیم: ${team.totalScore}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(44.dp))

            KButton(
                text = "شروع نوبت",
                onClick = onStartTurn,
                accent = roundAccent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
