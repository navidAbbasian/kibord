package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.hub.gameGuides

/**
 * دکمه‌ی راهنمای شناورِ داخل بازی: یک «؟» کوچک گوشه‌ی صفحه که با لمس،
 * قدم‌های همان بازی را (از همان بانک راهنمای اپ) در یک کارت نشان می‌دهد.
 * روی همه‌ی فازها شناور می‌ماند تا وسط بازی کسی گیر نکند.
 */
@Composable
fun GameHelpButton(
    gameId: String,
    modifier: Modifier = Modifier,
) {
    val guide = remember(gameId) { gameGuides.find { it.gameId == gameId } } ?: return
    val accent = LocalGameAccent.current
    val sound = LocalSoundManager.current
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(8.dp)
            .size(38.dp)
            .background(kiExtras.glassStrong, CircleShape)
            .border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                sound?.playButtonClick()
                open = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "؟", fontSize = 20.sp, fontWeight = FontWeight.Black, color = accent)
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            TicketCard(modifier = Modifier.fillMaxWidth(), accent = accent, tilt = -1.5f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📖", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "چطور بازی کنیم؟",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = accent,
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    guide.steps.forEachIndexed { i, (title, body) ->
                        Row(modifier = Modifier.padding(vertical = 5.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(accent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (i + 1).toPersianDigits(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp,
                                )
                            }
                        }
                    }

                    if (guide.tips.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        guide.tips.forEach { tip ->
                            Text(
                                text = "💡 $tip",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    KButton(text = "فهمیدم! 👍", onClick = { open = false })
                }
            }
        }
    }
}
