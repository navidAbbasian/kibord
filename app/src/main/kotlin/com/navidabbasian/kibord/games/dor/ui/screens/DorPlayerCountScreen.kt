package com.navidabbasian.kibord.games.dor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import androidx.compose.foundation.layout.offset

/** انتخاب تعداد بازیکنان دور: ۴ / ۶ / ۸ / ۱۰ / ۱۲ */
@Composable
fun DorPlayerCountScreen(onPlayerCountSelected: (Int) -> Unit) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(text = "💣", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند نفرید؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "تیم‌های دو نفره خودکار ساخته می‌شن",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        val options = listOf(4, 6, 8, 10, 12)
        options.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally)
            ) {
                row.forEachIndexed { colIndex, count ->
                    val zig = (rowIndex + colIndex) % 2 == 0
                    val index = rowIndex * 2 + colIndex
                    ChoiceBubble(
                        main = count.toPersianDigits(),
                        sub = "نفر — ${(count / 2).toPersianDigits()} تیم",
                        size = 136.dp,
                        // همان نگاشت رنگی کلمز: ۴→۱ ... ۱۲→۵
                        accent = teamColors[(index + 1) % teamColors.size],
                        tilt = if (zig) -3f else 3f,
                        phase = index * 1.3f,
                        modifier = Modifier.offset(y = if (zig) 0.dp else 22.dp),
                        onClick = { onPlayerCountSelected(count) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
