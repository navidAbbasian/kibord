package com.navidabbasian.kibord.core.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.navidabbasian.kibord.core.ui.theme.kiExtras

/** رنگ متن کارت: خال‌های قرمز یا مشکی */
val Suit.color: Color get() = if (isRed) Color(0xFFD93B4B) else Color(0xFF26262E)

private val CardBackTop = Color(0xFF6A5AE0)
private val CardBackBottom = Color(0xFF3E2F9E)

/**
 * یک کارت بازی به سبک «کی برد؟»: گوشه‌های گرد، سایه‌ی نرم، عددِ بزرگ و خال
 * در گوشه و وسط. اندازه با [width] مشخص می‌شود (نسبت ۱:۱.۴).
 *
 * - [faceUp] = false → پشت کارت
 * - [highlighted] → حاشیه‌ی روشن و کمی بالا آمده (کارتِ قابل بازی)
 * - [dimmed] → کم‌رنگ (کارتِ غیرمجاز)
 */
@Composable
fun PlayingCard(
    card: Card?,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    faceUp: Boolean = true,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    rotation: Float = 0f,
    onClick: (() -> Unit)? = null,
) {
    val extras = kiExtras
    val height = width * 1.4f
    val lift by animateDpAsState(if (highlighted) (-10).dp else 0.dp, label = "lift")
    val alpha by animateFloatAsState(if (dimmed) 0.45f else 1f, label = "alpha")
    val shape = RoundedCornerShape(width * 0.14f)
    val borderColor = if (highlighted) Color(0xFFFFC83D) else extras.glassBorderStrong

    // کارت‌ها همیشه چپ‌به‌راست چیده می‌شوند (عدد گوشه‌ی بالا-چپ)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .offset(y = lift)
                .graphicsLayer { this.alpha = alpha; rotationZ = rotation }
                .shadow(if (highlighted) 10.dp else 4.dp, shape, clip = false)
                .size(width, height)
                .background(
                    if (faceUp && card != null) Color(0xFFFFFDF7)
                    else Color.Transparent,
                    shape,
                )
                .border(if (highlighted) 2.5.dp else 1.2.dp, borderColor, shape)
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = MutableInteractionSource(),
                        indication = null,
                        onClick = onClick,
                    ) else Modifier,
                ),
        ) {
            if (!faceUp || card == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(CardBackTop, CardBackBottom)), shape)
                        .padding(width * 0.1f)
                        .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(width * 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "🎴", fontSize = (width.value * 0.34f).sp)
                }
            } else {
                val c = card.suit.color
                // گوشه‌ی بالا-چپ
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = width * 0.09f, top = width * 0.05f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = card.rank.label,
                        color = c,
                        fontSize = (width.value * 0.30f).sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = (width.value * 0.32f).sp,
                    )
                    Text(
                        text = card.suit.symbol,
                        color = c,
                        fontSize = (width.value * 0.26f).sp,
                        lineHeight = (width.value * 0.28f).sp,
                    )
                }
                // خال بزرگ وسط
                Text(
                    text = card.suit.symbol,
                    color = c.copy(alpha = 0.9f),
                    fontSize = (width.value * 0.62f).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = width * 0.12f),
                )
                // گوشه‌ی پایین-راست (وارونه)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = width * 0.09f, bottom = width * 0.05f)
                        .rotate(180f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = card.rank.label,
                        color = c,
                        fontSize = (width.value * 0.22f).sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = (width.value * 0.24f).sp,
                    )
                }
            }
        }
    }
}

/**
 * بادبزنِ دستِ بازیکن: کارت‌ها روی هم می‌خوابند و با انتخاب بالا می‌آیند.
 * [playable] کارت‌های مجاز این لحظه‌اند (بقیه کم‌رنگ)؛ اگر null باشد همه مجازند.
 * پهنای هر کارت از فضای موجود حساب می‌شود تا ۱۳ کارت هم جا بگیرد.
 */
@Composable
fun HandFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    playable: Set<Card>? = null,
    selected: Card? = null,
    faceUp: Boolean = true,
    maxCardWidth: Dp = 72.dp,
    onCardClick: ((Card) -> Unit)? = null,
) {
    if (cards.isEmpty()) {
        Box(modifier = modifier.height(maxCardWidth * 1.4f + 12.dp))
        return
    }
    // کل بادبزن چپ‌به‌راست چیده می‌شود (جهتِ خودِ Box هم) تا offset از چپ حساب شود
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(maxCardWidth * 1.4f + 14.dp)) {
        val n = cards.size
        // فاصله‌ی هر کارت از بعدی؛ آخرین کارت کامل دیده می‌شود
        val cardW = minOf(maxCardWidth, maxWidth / 4)
        val step = if (n == 1) 0.dp else minOf(cardW * 0.62f, (maxWidth - cardW) / (n - 1))
        val totalW = cardW + step * (n - 1)
        val startX = (maxWidth - totalW) / 2
        run {
            cards.forEachIndexed { i, card ->
                val ok = playable == null || card in playable
                val mid = (n - 1) / 2f
                val tilt = if (n > 1) (i - mid) * (8f / maxOf(1f, mid)) else 0f
                PlayingCard(
                    card = card,
                    width = cardW,
                    faceUp = faceUp,
                    highlighted = selected == card,
                    dimmed = faceUp && !ok,
                    rotation = tilt * 0.6f,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = startX + step * i, y = 0.dp),
                    onClick = if (onCardClick != null && ok) ({ onCardClick(card) }) else null,
                )
            }
        }
    }
    }
}

/** دسته‌ی بسته‌ی کارت (پشت) برای نشان دادن دست حریف‌ها — با شمارنده */
@Composable
fun HiddenHand(count: Int, modifier: Modifier = Modifier, width: Dp = 34.dp) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.width(width + 8.dp * minOf(count, 4).coerceAtLeast(1)).height(width * 1.4f)) {
            repeat(minOf(count, 4)) { i ->
                PlayingCard(card = null, faceUp = false, width = width, modifier = Modifier.offset(x = 5.dp * i))
            }
        }
    }
}
