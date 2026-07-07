package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.kiExtras

/** نشان «به زودی» برای بازی‌های در راه */
@Composable
fun ComingSoonBadge(modifier: Modifier = Modifier) {
    Text(
        text = "به زودی",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF3D2E00),
        modifier = modifier
            .background(kiExtras.warning, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
