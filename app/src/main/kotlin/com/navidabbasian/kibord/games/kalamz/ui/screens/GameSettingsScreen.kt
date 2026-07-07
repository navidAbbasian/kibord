package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras

@Composable
fun GameSettingsScreen(
    onConfirmSettings: (wordsPerPlayer: Int, timerDurationMillis: Long) -> Unit
) {
    var wordsPerPlayer by remember { mutableIntStateOf(5) }
    var turnSeconds by remember { mutableIntStateOf(45) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "تنظیمات بازی",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "بازی رو سفارشی‌سازی کنید",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        GlassSettingCard(
            icon = Icons.Default.Edit,
            title = "کلمات هر بازیکن",
            value = "$wordsPerPlayer کلمه",
            onDecrease = { if (wordsPerPlayer > 2) wordsPerPlayer-- },
            onIncrease = { if (wordsPerPlayer < 15) wordsPerPlayer++ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        GlassSettingCard(
            icon = Icons.Default.Timer,
            title = "زمان هر نوبت",
            value = "$turnSeconds ثانیه",
            onDecrease = { if (turnSeconds > 15) turnSeconds -= 5 },
            onIncrease = { if (turnSeconds < 180) turnSeconds += 5 }
        )

        Spacer(modifier = Modifier.weight(1f))

        KButton(
            text = "شروع بازی",
            onClick = { onConfirmSettings(wordsPerPlayer, turnSeconds * 1000L) },
            style = KButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(32.dp)
        )
    }
}

@Composable
private fun GlassSettingCard(
    icon: ImageVector,
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    val sound = LocalSoundManager.current

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledIconButton(
                    onClick = { sound?.playButtonClick(); onDecrease() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = kiExtras.glassStrong,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) { Icon(Icons.Default.Remove, contentDescription = "کم کن", modifier = Modifier.size(22.dp)) }

                Text(
                    value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = kiExtras.gold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 110.dp)
                )

                FilledIconButton(
                    onClick = { sound?.playButtonClick(); onIncrease() },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = kiExtras.glassStrong,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
