package com.navidabbasian.kibord.games.ludo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** موقعیت خال‌های هر وجه تاس در مختصات واحد ۰ تا ۱ */
private fun pipPositions(value: Int): List<Offset> {
    val c = Offset(0.5f, 0.5f)
    val tl = Offset(0.27f, 0.27f)
    val tr = Offset(0.73f, 0.27f)
    val bl = Offset(0.27f, 0.73f)
    val br = Offset(0.73f, 0.73f)
    val ml = Offset(0.27f, 0.5f)
    val mr = Offset(0.73f, 0.5f)
    return when (value) {
        1 -> listOf(c)
        2 -> listOf(tl, br)
        3 -> listOf(tl, c, br)
        4 -> listOf(tl, tr, bl, br)
        5 -> listOf(tl, tr, c, bl, br)
        else -> listOf(tl, tr, ml, mr, bl, br)
    }
}

/**
 * تاس منچ: با هر پرتاب تازه (rollKey عوض می‌شود) می‌چرخد، بالا می‌پرد، وجه‌هایش
 * تند عوض می‌شود و آخرش فنری روی عدد واقعی می‌نشیند. هاله‌ی رنگی = رنگ بازیکن نوبت.
 */
@Composable
fun LudoDie(
    value: Int?,
    rollKey: Int,
    rolling: Boolean,
    glow: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 68.dp,
) {
    var flash by remember { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(1f) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        // فقط پرتاب تازه انیمیشن دارد؛ بعد از چرخش صفحه تاس سر جایش می‌ماند
        if (rollKey == 0 || !rolling) return@LaunchedEffect
        progress.snapTo(0f)
        bounce.snapTo(1f)
        val flashJob = launch {
            while (progress.value < 0.82f) {
                flash = Random.nextInt(1, 7)
                delay(65)
            }
        }
        progress.animateTo(1f, animationSpec = tween(620, easing = FastOutSlowInEasing))
        flashJob.cancel()
        flash = null
        bounce.snapTo(1.2f)
        bounce.animateTo(1f, animationSpec = spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMedium))
    }
    val p = progress.value
    val tumbling = p < 1f
    val shown = if (tumbling) flash ?: (value ?: 6) else (value ?: 6)
    val hop = sin(p * Math.PI.toFloat()) * 30f
    val scale = (0.74f + 0.26f * p) * bounce.value
    val empty = value == null && !tumbling

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = (1f - p) * (1f - p) * 540f
                rotationX = (1f - p) * 300f
                scaleX = scale
                scaleY = scale
                translationY = -hop * density
                cameraDistance = 16f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        val s = this.size.minDimension
        val body = Size(s * 0.94f, s * 0.94f)
        val corner = CornerRadius(s * 0.22f, s * 0.22f)
        // هاله‌ی رنگ بازیکن
        drawRoundRect(
            color = glow.copy(alpha = if (enabled) 0.55f else 0.3f),
            topLeft = Offset(-s * 0.03f, s * 0.02f),
            size = Size(s, s),
            cornerRadius = CornerRadius(s * 0.26f, s * 0.26f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(s * 0.06f, s * 0.10f),
            size = body,
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFE), Color(0xFFF3F0E7), Color(0xFFD8D4C6)),
                start = Offset.Zero,
                end = Offset(s, s),
            ),
            size = body,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0xFFB9B5A6),
            size = body,
            cornerRadius = corner,
            style = Stroke(width = s * 0.03f),
        )
        if (!empty) {
            pipPositions(shown).forEach { pos ->
                val cpt = Offset(pos.x * body.width, pos.y * body.height)
                drawCircle(Color(0xFF191919), radius = s * 0.082f, center = cpt)
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = s * 0.024f,
                    center = cpt + Offset(-s * 0.02f, -s * 0.02f),
                )
            }
        } else {
            // تاس خالی: علامت سؤال کوچک (سه نقطه‌ی رنگی) یعنی «بریز!»
            for (i in 0 until 3) {
                drawCircle(
                    color = glow.copy(alpha = 0.8f),
                    radius = s * 0.06f,
                    center = Offset(body.width * (0.3f + 0.2f * i), body.height * 0.5f),
                )
            }
        }
    }
}
