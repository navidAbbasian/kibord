package com.navidabbasian.kibord.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import kotlin.math.sin

/** ایموجی معلق: بالا و پایین می‌رود و نرم تاب می‌خورد */
@Composable
fun BobbingEmoji(
    emoji: String,
    fontSize: TextUnit = 56.sp,
    modifier: Modifier = Modifier,
    phase: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "bob")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "bob_t"
    )
    Text(
        text = emoji,
        fontSize = fontSize,
        modifier = modifier.graphicsLayer {
            translationY = 10f * sin(t + phase)
            rotationZ = 5f * sin(t * 0.8f + phase)
        }
    )
}

/** تیتر برچسبی: متن درشت روی استیکر رنگیِ کج */
@Composable
fun StickerTitle(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = LocalGameAccent.current,
    rotation: Float = -2.5f,
    fontSize: TextUnit = 30.sp,
) {
    Box(
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .background(accent, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.displayMedium.copy(fontSize = fontSize),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

private data class ConfettiPiece(
    val x: Float,       // موقعیت افقی نسبی
    val delay: Float,   // فاز شروع
    val size: Float,
    val colorIndex: Int,
    val sway: Float,    // دامنه‌ی تاب افقی
)

private val CONFETTI = List(36) { i ->
    // شبه‌تصادفی ثابت از روی اندیس — بدون Random تا بین فریم‌ها پایدار بماند
    val fx = ((i * 37) % 100) / 100f
    ConfettiPiece(
        x = fx,
        delay = ((i * 53) % 100) / 100f,
        size = 6f + ((i * 29) % 8),
        colorIndex = i % 8,
        sway = 14f + ((i * 17) % 22),
    )
}

/** بارش کاغذرنگی برای صفحات برنده — روی کل صفحه بکشید */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    colors: List<Color> = com.navidabbasian.kibord.core.ui.theme.teamColorsOnDark,
) {
    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "confetti_t"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        CONFETTI.forEach { p ->
            val progress = (t + p.delay) % 1f
            val y = progress * (h + 80f) - 40f
            val x = p.x * w + p.sway * density * sin(progress * 12f + p.delay * 6f)
            val alpha = when {
                progress < 0.08f -> progress / 0.08f
                progress > 0.85f -> (1f - progress) / 0.15f
                else -> 1f
            }
            val color = colors[p.colorIndex % colors.size]
            rotate(degrees = progress * 720f + p.delay * 360f, pivot = androidx.compose.ui.geometry.Offset(x, y)) {
                drawRoundRect(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = androidx.compose.ui.geometry.Offset(x - p.size * density / 2f, y - p.size * density / 4f),
                    size = androidx.compose.ui.geometry.Size(p.size * density, p.size * density / 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * density)
                )
            }
        }
    }
}
