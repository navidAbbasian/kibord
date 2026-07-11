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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
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
 * تب خانه‌ی هاب — همان کلاژ آشنا (قهرمان + شبکه‌ی پلکانی + بنر پهن)
 * اما با ارتفاع‌های کشسان تا هر شش بازی بدون اسکرول در یک صفحه جا شوند.
 */
@Composable
fun HubScreen(onOpenGame: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ---- هدر فشرده ----
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BobbingEmoji(emoji = "✨", fontSize = 15.sp, phase = 2.1f)
            Spacer(modifier = Modifier.width(8.dp))
            BobbingEmoji(emoji = "🏆", fontSize = 32.sp)
            Spacer(modifier = Modifier.width(8.dp))
            StickerTitle(text = "کی برد؟", accent = VioletPrimary, rotation = -2.5f, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            BobbingEmoji(emoji = "✨", fontSize = 15.sp, phase = 4.4f)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "امشب کدومو بازی کنیم؟",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))

        // ---- کلاژ کشسان ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // کارت قهرمان: کلمز تمام‌عرض
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.23f)
            ) {
                AppearWrap(index = 0) {
                    GameTile(game = gameCatalog[0], index = 0, emojiSize = 30.sp, onOpenGame = onOpenGame)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // شبکه‌ی پلکانی دوستونه با ارتفاع‌های ناهمسان
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.54f)
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(0.54f)) {
                        AppearWrap(index = 1) {
                            GameTile(game = gameCatalog[1], index = 1, onOpenGame = onOpenGame)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().weight(0.46f)) {
                        AppearWrap(index = 3) {
                            GameTile(game = gameCatalog[3], index = 3, onOpenGame = onOpenGame)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().weight(0.46f)) {
                        AppearWrap(index = 2) {
                            GameTile(game = gameCatalog[2], index = 2, onOpenGame = onOpenGame)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().weight(0.54f)) {
                        AppearWrap(index = 4) {
                            GameTile(game = gameCatalog[4], index = 4, onOpenGame = onOpenGame)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // بنر پهن اسم فامیل
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.23f)
            ) {
                AppearWrap(index = 5) {
                    GameTile(game = gameCatalog[5], index = 5, emojiSize = 30.sp, taglineMaxLines = 1, onOpenGame = onOpenGame)
                }
            }
        }

        // ---- جای نوار ناوبری شناور پایین ----
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(92.dp)
        )
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
 * کاشی بازی: گرادیان اکسنت، درخشش لغزان، تنفس آرام و ایموجی معلق —
 * ارتفاعش را از والد می‌گیرد تا شش کاشی همیشه در یک صفحه جا شوند.
 */
@Composable
private fun GameTile(
    game: GameInfo,
    index: Int,
    onOpenGame: (String) -> Unit,
    emojiSize: androidx.compose.ui.unit.TextUnit = 26.sp,
    taglineMaxLines: Int = 2,
) {
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    val tilt = if (index % 2 == 0) -1.4f else 1.4f
    val shape = RoundedCornerShape(26.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
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
                .size(96.dp)
                .offset(x = (-18).dp, y = 120.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
                .background(Color.White.copy(alpha = 0.12f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BobbingEmoji(emoji = game.emoji, fontSize = emojiSize, phase = index * 1.1f)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = game.tagline,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = taglineMaxLines
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "👥 ${game.players}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -tilt * 1.5f }
                    .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }
    }
}
