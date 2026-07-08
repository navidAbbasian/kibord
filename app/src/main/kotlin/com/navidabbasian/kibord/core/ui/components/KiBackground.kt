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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import kotlin.math.cos
import kotlin.math.sin

private data class Orb(val x: Float, val y: Float, val radius: Float, val colorIndex: Int, val phase: Float)
private data class Sparkle(val x: Float, val y: Float, val size: Float, val phase: Float)

// چیدمان ثابت و دست‌چین — تصادفی نیست تا بین فریم‌ها و اجراها پایدار بماند
private val ORBS = listOf(
    Orb(0.12f, 0.10f, 0.24f, 0, 0.0f),
    Orb(0.90f, 0.22f, 0.30f, 1, 1.6f),
    Orb(0.06f, 0.52f, 0.20f, 2, 3.1f),
    Orb(0.94f, 0.66f, 0.26f, 3, 4.4f),
    Orb(0.25f, 0.90f, 0.30f, 4, 2.2f),
    Orb(0.78f, 0.95f, 0.22f, 5, 5.3f),
)

private val SPARKLES = listOf(
    Sparkle(0.20f, 0.20f, 7f, 0.0f),
    Sparkle(0.82f, 0.12f, 9f, 1.1f),
    Sparkle(0.68f, 0.34f, 6f, 2.3f),
    Sparkle(0.10f, 0.38f, 8f, 3.4f),
    Sparkle(0.46f, 0.08f, 6f, 4.2f),
    Sparkle(0.90f, 0.48f, 7f, 5.0f),
    Sparkle(0.30f, 0.62f, 9f, 0.8f),
    Sparkle(0.74f, 0.72f, 6f, 2.9f),
    Sparkle(0.14f, 0.80f, 8f, 4.7f),
    Sparkle(0.55f, 0.88f, 7f, 1.9f),
)

/**
 * پس‌زمینه‌ی فانتزی همه‌ی صفحات: گرادیان تم‌آگاه + گوی‌های پاستلی شناور
 * که آرام نفس می‌کشند و ستاره‌های چهارپر چشمک‌زن.
 */
@Composable
fun KiBackground(
    modifier: Modifier = Modifier,
    accent: Color = LocalGameAccent.current,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val extras = kiExtras
    val dark = extras.isDark
    val bottom = lerp(background, accent, if (dark) 0.30f else 0.16f)
    val glow = accent.copy(alpha = if (dark) 0.34f else 0.18f)

    val orbColors = extras.teamColors
    val orbAlpha = if (dark) 0.10f else 0.16f
    val sparkleColor = if (dark) Color.White else accent

    val transition = rememberInfiniteTransition(label = "bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "drift"
    )
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "twinkle"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, bottom)))
            .background(
                Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent),
                    center = Offset(0.5f, 0f),
                    radius = 1400f
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // ---- گوی‌های شناور ----
            ORBS.forEach { orb ->
                val wobbleX = sin(drift + orb.phase) * w * 0.03f
                val wobbleY = cos(drift * 0.7f + orb.phase) * h * 0.02f
                val breathe = 1f + 0.08f * sin(drift * 1.3f + orb.phase)
                val color = orbColors[orb.colorIndex % orbColors.size]
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = orbAlpha), Color.Transparent),
                        center = Offset(orb.x * w + wobbleX, orb.y * h + wobbleY),
                        radius = orb.radius * w * breathe
                    ),
                    radius = orb.radius * w * breathe,
                    center = Offset(orb.x * w + wobbleX, orb.y * h + wobbleY)
                )
            }

            // ---- ستاره‌های چهارپر چشمک‌زن ----
            SPARKLES.forEach { s ->
                val a = (0.5f + 0.5f * sin(twinkle * 1.5f + s.phase)).coerceIn(0f, 1f)
                if (a > 0.15f) {
                    val cx = s.x * w
                    val cy = s.y * h
                    val r = s.size * density * (0.7f + 0.3f * a)
                    val path = Path().apply {
                        moveTo(cx, cy - r)
                        quadraticTo(cx + r * 0.18f, cy - r * 0.18f, cx + r, cy)
                        quadraticTo(cx + r * 0.18f, cy + r * 0.18f, cx, cy + r)
                        quadraticTo(cx - r * 0.18f, cy + r * 0.18f, cx - r, cy)
                        quadraticTo(cx - r * 0.18f, cy - r * 0.18f, cx, cy - r)
                        close()
                    }
                    drawPath(path, sparkleColor.copy(alpha = a * if (dark) 0.5f else 0.35f))
                }
            }
        }

        content()
    }
}
