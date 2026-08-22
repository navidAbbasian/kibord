package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras

/**
 * جای فیلدِ «اسمت چیه؟» در حالت اینترنتی: اسم بازیکن همان یوزرنیم حسابش
 * است و دستی عوض نمی‌شود — تا آمار و لیدربورد به آدم درست بچسبد.
 */
@Composable
fun OnlineIdentityField(username: String, modifier: Modifier = Modifier) {
    val extras = kiExtras
    val shape = rememberMorphingBlobShape(phase = 0.7f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(extras.glassStrong, shape)
            .border(1.5.dp, LocalGameAccent.current.copy(alpha = 0.6f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🎖️", fontSize = 22.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "\u200E@$username",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "توی بازی اینترنتی با یوزرنیم حسابت شناخته می‌شی",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
