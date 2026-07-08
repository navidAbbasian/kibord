package com.navidabbasian.kibord.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import kotlin.math.abs
import kotlin.math.sin

/**
 * شکل سنگریزه‌ای ایستا: گوشه‌های نامتقارنِ برآمده از seed —
 * نه دایره‌ی کامل، نه مستطیل؛ هر seed یک فرم یکتا می‌دهد.
 */
fun blobShape(seed: Int): Shape {
    fun pct(k: Int): Int = 34 + abs((seed * 31 + k * 17) % 29) // ۳۴ تا ۶۲ درصد
    return RoundedCornerShape(
        topStartPercent = pct(1),
        topEndPercent = pct(2),
        bottomEndPercent = pct(3),
        bottomStartPercent = pct(4),
    )
}

/**
 * شکل بلاب زنده: گوشه‌ها آرام دگردیسی می‌شوند — دفرمه و یکتا.
 * برای عناصر برجسته (حباب‌ها، آواتارها) استفاده کنید.
 */
@Composable
fun rememberMorphingBlobShape(
    phase: Float = 0f,
    periodMs: Int = 5600,
): Shape {
    val transition = rememberInfiniteTransition(label = "blob")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "blob_t"
    )
    val a = (48 + 13 * sin(t + phase)).toInt()
    val b = (48 - 12 * sin(t * 0.8f + phase + 1.3f)).toInt()
    val c = (48 + 11 * sin(t * 1.15f + phase + 2.7f)).toInt()
    val d = (48 - 13 * sin(t * 0.9f + phase + 4.2f)).toInt()
    return RoundedCornerShape(
        topStartPercent = a.coerceIn(30, 62),
        topEndPercent = b.coerceIn(30, 62),
        bottomEndPercent = c.coerceIn(30, 62),
        bottomStartPercent = d.coerceIn(30, 62),
    )
}

/**
 * فیلد ورودی بلابی: ظرف ارگانیکِ در حال دگردیسی + آواتار سنگریزه‌ای رنگی.
 * جایگزین فانتزی همه‌ی فرم‌های ورود نام و کلمه.
 */
@Composable
fun BlobTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    color: Color = LocalGameAccent.current,
    badge: String? = null,
    tilt: Float = 0f,
    phase: Float = 0f,
    textStyle: TextStyle? = null,
) {
    val extras = kiExtras
    val shape = rememberMorphingBlobShape(phase = phase)
    val style = textStyle ?: MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt }
            .background(extras.glassStrong, shape)
            .border(2.dp, color.copy(alpha = 0.65f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(lerp(color, Color.White, 0.22f), color),
                            center = Offset(0.3f, 0.25f),
                            radius = 140f
                        ),
                        blobShape(seed = badge.hashCode() + (phase * 10).toInt())
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(modifier = Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(color),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = style.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                )
            }
        }
    }
}
