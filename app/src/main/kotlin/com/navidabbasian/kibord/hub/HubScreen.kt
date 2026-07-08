package com.navidabbasian.kibord.hub

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary

/** تب خانه‌ی هاب — کارت‌های انتخاب بازی با چیدمان فانتزی */
@Composable
fun HubScreen(onOpenGame: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "🏆", fontSize = 52.sp)
                Spacer(modifier = Modifier.height(6.dp))
                StickerTitle(text = "کی برد؟", accent = VioletPrimary, rotation = -2.5f, fontSize = 34.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "✨ مجموعه بازی‌های دورهمی ✨",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        items(count = gameCatalog.size, key = { gameCatalog[it].id }) { index ->
            val game = gameCatalog[index]
            GameCard(
                game = game,
                index = index,
                onClick = { game.route?.let(onOpenGame) }
            )
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, index: Int, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    val enabled = game.route != null
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // ورود فنری: کارت‌ها یکی‌یکی با پرش ظاهر می‌شوند
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60L * index)
        appeared = true
    }
    val appearScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "appear"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "card_scale"
    )

    // نظم‌شکنی: هر کارت کمی کج و جابه‌جا در جهت متفاوت
    val tilt = if (index % 2 == 0) -1.4f else 1.4f
    val sideOffset = if (index % 2 == 0) (-5).dp else 5.dp

    val gradient = if (enabled) {
        Brush.linearGradient(listOf(game.accent, game.accentDark))
    } else {
        Brush.linearGradient(
            listOf(game.accent.copy(alpha = 0.35f), game.accentDark.copy(alpha = 0.35f))
        )
    }

    Card(
        onClick = { if (enabled) { sound?.playButtonClick(); onClick() } else sound?.vibrate(30) },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = sideOffset)
            .graphicsLayer {
                scaleX = appearScale * pressScale
                scaleY = appearScale * pressScale
                rotationZ = tilt
                alpha = if (appeared) 1f else 0f
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(2.dp, Color.White.copy(alpha = if (enabled) 0.35f else 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ایموجی بزرگ معلق با حباب کج
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { rotationZ = -tilt * 4f }
                    .background(Color.White.copy(alpha = if (enabled) 0.25f else 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                BobbingEmoji(emoji = game.emoji, fontSize = 38.sp, phase = index * 1.3f)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = game.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = if (enabled) 0.9f else 0.5f),
                    lineHeight = 20.sp
                )
                if (enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "👥 ${game.players}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = tilt * 1.5f }
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "🎮",
                fontSize = 20.sp,
                modifier = Modifier
                    .graphicsLayer { rotationZ = tilt * 6f }
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .padding(8.dp)
            )
        }
    }
}
