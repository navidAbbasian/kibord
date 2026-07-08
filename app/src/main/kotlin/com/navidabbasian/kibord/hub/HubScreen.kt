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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.shineSweep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary

/**
 * تب خانه‌ی هاب — چیدمان کلاژی فانتزی:
 * یک کارت قهرمانِ تمام‌عرض + شبکه‌ی پلکانی دوستونه با ارتفاع‌های ناهمسان.
 */
@Composable
fun HubScreen(onOpenGame: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 18.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BobbingEmoji(emoji = "✨", fontSize = 22.sp, phase = 2.1f)
                    Spacer(modifier = Modifier.width(10.dp))
                    BobbingEmoji(emoji = "🏆", fontSize = 54.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    BobbingEmoji(emoji = "✨", fontSize = 22.sp, phase = 4.4f)
                }
                Spacer(modifier = Modifier.height(6.dp))
                StickerTitle(text = "کی برد؟", accent = VioletPrimary, rotation = -2.5f, fontSize = 34.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "امشب کدومو بازی کنیم؟",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ---- کارت قهرمان: بازی اول ----
        item {
            AppearWrap(index = 0) {
                GameTile(
                    game = gameCatalog[0],
                    index = 0,
                    height = 218.dp,
                    emojiSize = 54.sp,
                    onOpenGame = onOpenGame,
                )
            }
        }

        // ---- شبکه‌ی پلکانی: چهار بازی بعدی، دوتا دوتا با ارتفاع ناهمسان ----
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    AppearWrap(index = 1) {
                        GameTile(gameCatalog[1], index = 1, height = 240.dp, onOpenGame = onOpenGame)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AppearWrap(index = 3) {
                        GameTile(gameCatalog[3], index = 3, height = 208.dp, onOpenGame = onOpenGame)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Spacer(modifier = Modifier.height(26.dp))
                    AppearWrap(index = 2) {
                        GameTile(gameCatalog[2], index = 2, height = 208.dp, onOpenGame = onOpenGame)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AppearWrap(index = 4) {
                        GameTile(gameCatalog[4], index = 4, height = 240.dp, onOpenGame = onOpenGame)
                    }
                }
            }
        }
    }
}

/** ورود فنری پلکانی برای هر کاشی */
@Composable
private fun AppearWrap(index: Int, content: @Composable () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(70L * index)
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.5f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "appear"
    )
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (appeared) 1f else 0f
        }
    ) {
        content()
    }
}

/**
 * کاشی بازی: گرادیان اکسنت، درخشش لغزان، تنفس آرام، ایموجی معلق بزرگ
 * و حباب‌های تزئینی — هر کاشی با کجی و فاز مخصوص خودش.
 */
@Composable
private fun GameTile(
    game: GameInfo,
    index: Int,
    height: Dp,
    onOpenGame: (String) -> Unit,
    emojiSize: androidx.compose.ui.unit.TextUnit = 40.sp,
) {
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    val tilt = if (index % 2 == 0) -1.6f else 1.6f
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .breathing(intensity = 0.012f, periodMs = 3000 + index * 350, phase = index * 1.7f)
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
        // حباب‌های تزئینی گوشه‌ها
        Box(
            modifier = Modifier
                .size(height * 0.55f)
                .offset(x = (-20).dp, y = height * 0.62f)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(height * 0.3f)
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-14).dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BobbingEmoji(emoji = game.emoji, fontSize = emojiSize, phase = index * 1.1f)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = game.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "👥 ${game.players}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -tilt * 1.5f }
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}
