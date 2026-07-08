package com.navidabbasian.kibord.core.ui.components

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius as GeoCornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

/**
 * حباب انتخاب: گزینه‌ی دایره‌ای بزرگ با گرادیان اکسنت، تنفس و پرش لمسی —
 * جایگزین فانتزیِ کارت‌های مستطیلی گزینه.
 */
@Composable
fun ChoiceBubble(
    main: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    emoji: String? = null,
    size: Dp = 132.dp,
    accent: Color = LocalGameAccent.current,
    tilt: Float = 0f,
    phase: Float = 0f,
    mainFontSize: TextUnit = 34.sp,
) {
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bubble_press"
    )
    // سنگریزه‌ی دفرمه‌ی در حال دگردیسی — نه دایره، نه مربع
    val blob = rememberMorphingBlobShape(phase = phase)

    Box(
        modifier = modifier
            .size(size)
            .breathing(intensity = 0.02f, periodMs = 2800, phase = phase)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationZ = tilt
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(lerp(accent, Color.White, 0.18f), accent, lerp(accent, Color.Black, 0.12f)),
                    center = Offset(0.35f, 0.25f),
                    radius = 500f
                ),
                blob
            )
            .border(3.dp, Color.White.copy(alpha = 0.45f), blob)
            .clickable(interactionSource = interaction, indication = null) {
                sound?.playButtonClick()
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (emoji != null) {
                Text(text = emoji, fontSize = 22.sp)
            }
            Text(
                text = main,
                fontSize = mainFontSize,
                fontWeight = FontWeight.Black,
                color = Color.White,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = mainFontSize)
            )
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * سکه‌ی امتیاز: ژتون دایره‌ای براق برای خانه‌های جدول.
 * حالت طلایی برای موضوع ویژه و حالت خاکستری برای خانه‌ی سوخته.
 */
@Composable
fun PointCoin(
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    accent: Color = LocalGameAccent.current,
    used: Boolean = false,
    golden: Boolean = false,
    size: Dp = 58.dp,
    phase: Float = 0f,
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "coin_press"
    )

    val base = when {
        used -> extras.glass
        golden -> extras.gold
        else -> accent
    }
    val body = if (used) {
        Brush.radialGradient(listOf(extras.glass, extras.glass))
    } else {
        Brush.radialGradient(
            colors = listOf(lerp(base, Color.White, 0.28f), base, lerp(base, Color.Black, 0.15f)),
            center = Offset(0.3f, 0.25f),
            radius = 220f
        )
    }
    val borderColor = when {
        used -> extras.glassBorder
        golden -> Color.White.copy(alpha = 0.75f)
        else -> Color.White.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (!used) Modifier.breathing(intensity = 0.03f, periodMs = 2400, phase = phase)
                else Modifier
            )
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background(body, CircleShape)
            .border(2.5.dp, borderColor, CircleShape)
            // حلقه‌ی داخلی سکه
            .padding(5.dp)
            .border(
                1.5.dp,
                if (used) extras.glassBorder.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.35f),
                CircleShape
            )
            .then(
                if (onClick != null && !used) {
                    Modifier.clickable(interactionSource = interaction, indication = null) {
                        sound?.playButtonClick()
                        onClick()
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (golden && !used) {
                Text(text = "⭐", fontSize = 13.sp)
            }
            Text(
                text = value,
                fontSize = if (golden) 15.sp else 19.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    used -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    golden -> Color(0xFF5C4400)
                    else -> Color.White
                }
            )
        }
    }
}

/** مدال امتیاز تیم: دایره‌ی رنگی با امتیاز داخلش، نام زیرش و تاج معلق برای تیم برجسته */
@Composable
fun TeamMedallions(
    count: Int,
    nameOf: (Int) -> String,
    scoreOf: (Int) -> Int,
    highlight: Int = -1,
    modifier: Modifier = Modifier,
    crownOnLeader: Boolean = true,
) {
    val teamColors = kiExtras.teamColors
    val scores = (0 until count).map(scoreOf)
    val max = scores.maxOrNull() ?: 0
    val leaders = if (crownOnLeader && max > 0) {
        scores.withIndex().filter { it.value == max }.map { it.index }.toSet()
    } else emptySet()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(count) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            val active = highlight < 0 || i == highlight
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .then(
                                if (i == highlight) Modifier.breathing(intensity = 0.05f, periodMs = 1600)
                                else Modifier
                            )
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        lerp(color, Color.White, 0.25f),
                                        color,
                                        lerp(color, Color.Black, if (active) 0.12f else 0.3f)
                                    ),
                                    center = Offset(0.3f, 0.25f),
                                    radius = 240f
                                ),
                                CircleShape
                            )
                            .border(
                                2.5.dp,
                                Color.White.copy(alpha = if (active) 0.55f else 0.2f),
                                CircleShape
                            )
                            .graphicsLayer { alpha = if (active) 1f else 0.62f },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scoreOf(i).toPersianDigits(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    if (i in leaders) {
                        BobbingEmoji(
                            emoji = "👑",
                            fontSize = 18.sp,
                            phase = i * 1.4f,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer { translationY = -34f }
                        )
                    }
                }
                Text(
                    text = nameOf(i),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) color else color.copy(alpha = 0.6f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** شکل بلیت: مستطیل گرد با دو بریدگی نیم‌دایره در میانه‌ی پهلوها */
class TicketShape(
    private val cornerRadius: Dp = 22.dp,
    private val notchRadius: Dp = 12.dp,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val corner = with(density) { cornerRadius.toPx() }
        val notch = with(density) { notchRadius.toPx() }
        val rect = Path().apply {
            addRoundRect(
                RoundRect(
                    Rect(0f, 0f, size.width, size.height),
                    GeoCornerRadius(corner, corner)
                )
            )
        }
        val notches = Path().apply {
            addOval(Rect(Offset(0f, size.height / 2f), notch))
            addOval(Rect(Offset(size.width, size.height / 2f), notch))
        }
        return Outline.Generic(Path.combine(PathOperation.Difference, rect, notches))
    }
}

/** کارت بلیتی: برای نمایش سوال و کلمه — با خط‌چین وسط مثل بلیت واقعی */
@Composable
fun TicketCard(
    modifier: Modifier = Modifier,
    accent: Color = LocalGameAccent.current,
    golden: Boolean = false,
    tilt: Float = -1.2f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val extras = kiExtras
    val shape = TicketShape()
    val borderColor = if (golden) extras.gold else accent.copy(alpha = 0.65f)

    Column(
        modifier = modifier
            .graphicsLayer { rotationZ = tilt }
            .background(extras.glassStrong, shape)
            .border(2.dp, borderColor, shape)
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            )
            .padding(2.dp),
        content = content
    )
}
