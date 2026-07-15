package com.navidabbasian.kibord.hub

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.shineSweep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary

/** گنجه‌ی «بازی‌های بیشتر»: فهرست اسکرولی بقیه‌ی بازی‌ها با همان کاشی‌های آشنا */
@Composable
fun MoreGamesScreen(onOpenGame: (String) -> Unit) {
    KiBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 14.dp, bottom = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BobbingEmoji(emoji = "🎲", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        StickerTitle(text = "بازی‌های بیشتر", accent = VioletPrimary, rotation = -2f, fontSize = 26.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "این گنجه هی پرتر می‌شه! 🎁",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    // آیکون تیم‌چین در همان جایگاهِ هابِ اصلی
                    TeamPickerButton(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = { onOpenGame(Routes.TEAM_PICKER) },
                    )
                }
            }
            items(count = moreGamesCatalog.size, key = { moreGamesCatalog[it].id }) { i ->
                MoreGameCard(game = moreGamesCatalog[i], index = i, onOpenGame = onOpenGame)
            }
        }
    }
}

/** کارت پهن هر بازی در گنجه — هم‌خانواده‌ی کاشی‌های هاب */
@Composable
private fun MoreGameCard(
    game: GameInfo,
    index: Int,
    onOpenGame: (String) -> Unit,
) {
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "more_card_press"
    )
    val tilt = if (index % 2 == 0) -1.2f else 1.2f
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .breathing(intensity = 0.010f, periodMs = 3000 + index * 350, phase = index * 1.7f)
            .graphicsLayer {
                rotationZ = tilt
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(game.accent, Color.White, 0.10f),
                        game.accent,
                        game.accentDark,
                    )
                )
            )
            .border(2.5.dp, Color.White.copy(alpha = 0.4f), shape)
            .shineSweep(periodMs = 3600 + index * 500, phase = index * 0.45f)
            .clickable(interactionSource = interaction, indication = null) {
                sound?.playButtonClick()
                game.route?.let(onOpenGame)
            }
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-16).dp, y = 16.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BobbingEmoji(emoji = game.emoji, fontSize = 34.sp, phase = index * 1.1f)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = game.tagline,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.92f),
                    lineHeight = 15.sp,
                    maxLines = 2,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "👥 ${game.players}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
    }
}
