package com.navidabbasian.kibord.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras

enum class KButtonStyle { Primary, Glass, Outline, Danger }

/**
 * دکمه‌ی بازی‌گونه: بدنه‌ی حجیم با لبه‌ی سه‌بعدیِ پایین که با لمس فرو می‌رود.
 * صدای کلیک خودکار دارد؛ Primary و Danger با گرادیان رنگی، Glass و Outline شیشه‌ای.
 */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KButtonStyle = KButtonStyle.Primary,
    enabled: Boolean = true,
    accent: Color = LocalGameAccent.current,
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val edgeHeight = 6.dp
    val press by animateDpAsState(
        targetValue = if (pressed && enabled) edgeHeight - 1.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press_depth"
    )

    val shape = RoundedCornerShape(26.dp)

    val (bodyBrush, edgeColor, contentColor) = when (style) {
        KButtonStyle.Primary -> Triple(
            Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.12f), accent)),
            lerp(accent, Color.Black, 0.32f),
            Color.White
        )
        KButtonStyle.Danger -> Triple(
            Brush.verticalGradient(listOf(lerp(extras.danger, Color.White, 0.10f), extras.danger)),
            lerp(extras.danger, Color.Black, 0.32f),
            Color.White
        )
        KButtonStyle.Glass -> Triple(
            Brush.verticalGradient(listOf(extras.glassStrong, extras.glassStrong)),
            extras.glassBorderStrong,
            MaterialTheme.colorScheme.onSurface
        )
        KButtonStyle.Outline -> Triple(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
            extras.glassBorder,
            MaterialTheme.colorScheme.onSurface
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        // لبه‌ی سه‌بعدی پایین
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = edgeHeight)
                .background(edgeColor.copy(alpha = if (enabled) edgeColor.alpha else edgeColor.alpha * 0.4f), shape)
        )
        // بدنه — با فشار به سمت لبه فرو می‌رود
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(bottom = edgeHeight)
                .offset(y = press)
                .background(bodyBrush, shape)
                .then(
                    if (style == KButtonStyle.Outline) {
                        Modifier.border(2.dp, extras.glassBorderStrong, shape)
                    } else Modifier
                )
                .then(if (!enabled) Modifier.background(Color.Black.copy(alpha = 0.25f), shape) else Modifier)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = {
                        sound?.playButtonClick()
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp),
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
        }
    }
}
