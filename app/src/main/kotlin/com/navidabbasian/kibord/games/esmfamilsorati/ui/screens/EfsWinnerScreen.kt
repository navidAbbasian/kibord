package com.navidabbasian.kibord.games.esmfamilsorati.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.formatMillisAsClock
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsPlayer
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsTopic

/** صفحه‌ی برنده‌ی اسم فامیل سرعتی */
@Composable
fun EfsWinnerScreen(
    winner: EfsPlayer?,
    topic: EfsTopic?,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val extras = kiExtras
    val color = winner?.let { extras.teamColors.getOrElse(it.id) { extras.teamColors[0] } }
        ?: MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "trophy")
    val trophyScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "trophy_scale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🏆", fontSize = 88.sp, modifier = Modifier.scale(trophyScale))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = winner?.name ?: "هیچ‌کس!",
                style = MaterialTheme.typography.displayMedium,
                color = color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (winner != null) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "با ⏱️ ${formatMillisAsClock(winner.remainingTimeMillis)} ذخیره برنده شد!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        topic?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "موضوع بازی: ${it.emoji} ${it.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            ShareWinButton(
                gameId = "esm_famil_sorati",
                gameTitle = "اسم فامیل سرعتی",
                gameEmoji = "⚡",
                winnerText = winner?.name ?: "هیچ‌کس!",
                scoreLines = winner?.let {
                    listOf("زمان ذخیره" to formatMillisAsClock(it.remainingTimeMillis))
                } ?: emptyList(),
                winnerNames = winner?.let { listOf(it.name) } ?: emptyList(),
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
