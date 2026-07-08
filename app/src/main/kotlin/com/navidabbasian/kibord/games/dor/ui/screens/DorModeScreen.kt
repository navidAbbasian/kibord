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
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.dor.model.DorGameMode

/** انتخاب حالت بازی دور: سریع یا حرفه‌ای */
@Composable
fun DorModeScreen(onModeSelected: (DorGameMode) -> Unit) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Text(text = "⚙️", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "حالت بازی",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        ModeCard(
            emoji = "⚡",
            title = "سریع",
            subtitle = "برای دورهمی‌های پرهیجان",
            mode = DorGameMode.QUICK,
            onClick = {
                sound?.playButtonClick()
                onModeSelected(DorGameMode.QUICK)
            },
            tilt = -1.5f
        )
        Spacer(modifier = Modifier.height(16.dp))
        ModeCard(
            emoji = "🎯",
            title = "حرفه‌ای",
            subtitle = "نفس‌گیر و طولانی‌تر",
            mode = DorGameMode.PROFESSIONAL,
            onClick = {
                sound?.playButtonClick()
                onModeSelected(DorGameMode.PROFESSIONAL)
            },
            tilt = 1.5f
        )
    }
}

@Composable
private fun ModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    mode: DorGameMode,
    onClick: () -> Unit,
    tilt: Float = 0f,
) {
    val accent = LocalGameAccent.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tilt = tilt,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 40.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChip("⏱ زمان تیم", "${(mode.teamTimeMillis / 1000).toInt().toPersianDigits()} ثانیه")
                InfoChip("💣 بمب", "${(mode.bombTimeMillis / 1000).toInt().toPersianDigits()} ثانیه")
                InfoChip("⚠️ جریمه", "${(mode.penaltyMillis / 1000).toInt().toPersianDigits()} ثانیه")
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    GlassCard(cornerRadius = 14.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
