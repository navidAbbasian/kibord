package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras

/**
 * پس‌زمینه‌ی گرادیانی تم‌آگاه: از رنگ زمینه به سمت ته‌رنگِ اکسنت بازی فعال،
 * به‌علاوه‌ی یک هاله‌ی نرم بالای صفحه.
 */
@Composable
fun KiBackground(
    modifier: Modifier = Modifier,
    accent: Color = LocalGameAccent.current,
    content: @Composable BoxScope.() -> Unit
) {
    val background = MaterialTheme.colorScheme.background
    val dark = kiExtras.isDark
    val bottom = lerp(background, accent, if (dark) 0.28f else 0.14f)
    val glow = accent.copy(alpha = if (dark) 0.32f else 0.16f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, bottom)))
            .background(
                Brush.radialGradient(
                    colors = listOf(glow, Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0.5f, 0f),
                    radius = 1400f
                )
            ),
        content = content
    )
}
