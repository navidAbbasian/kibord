package com.navidabbasian.kibord.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras

enum class KButtonStyle { Primary, Glass, Outline, Danger }

/**
 * دکمه‌ی استاندارد اپ — گوشه‌ی کاملاً گرد، انیمیشن فنری لمس و صدای کلیک خودکار.
 * حالت Primary با گرادیان اکسنت بازی فعال پر می‌شود.
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
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press_scale"
    )
    val clickWithSound: () -> Unit = { sound?.playButtonClick(); onClick() }
    val shape = RoundedCornerShape(28.dp)
    val animModifier = modifier
        .fillMaxWidth()
        .height(56.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }

    when (style) {
        KButtonStyle.Primary -> {
            val gradient = Brush.horizontalGradient(
                listOf(accent, lerp(accent, Color.Black, 0.25f))
            )
            Button(
                onClick = clickWithSound,
                modifier = animModifier,
                enabled = enabled,
                shape = shape,
                interactionSource = interaction,
                contentPadding = PaddingValues(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradient, shape)
                        .then(if (!enabled) Modifier.background(Color.Black.copy(alpha = 0.35f), shape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = text, fontSize = 18.sp, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
                }
            }
        }
        KButtonStyle.Glass, KButtonStyle.Danger -> {
            val container = if (style == KButtonStyle.Danger) extras.danger.copy(alpha = 0.85f) else extras.glassStrong
            val content = if (style == KButtonStyle.Danger) Color.White else MaterialTheme.colorScheme.onSurface
            Button(
                onClick = clickWithSound,
                modifier = animModifier,
                enabled = enabled,
                shape = shape,
                interactionSource = interaction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = content,
                    disabledContainerColor = container.copy(alpha = container.alpha * 0.4f),
                    disabledContentColor = content.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(text = text, fontSize = 18.sp, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
            }
        }
        KButtonStyle.Outline -> {
            OutlinedButton(
                onClick = clickWithSound,
                modifier = animModifier,
                enabled = enabled,
                shape = shape,
                interactionSource = interaction,
                border = BorderStroke(1.5.dp, if (enabled) extras.glassBorderStrong else extras.glassBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = text, fontSize = 18.sp, style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp))
            }
        }
    }
}
