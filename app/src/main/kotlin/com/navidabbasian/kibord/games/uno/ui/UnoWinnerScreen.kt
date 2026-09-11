package com.navidabbasian.kibord.games.uno.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.UNO_HUMAN
import com.navidabbasian.kibord.games.uno.UnoUiState
import com.navidabbasian.kibord.games.uno.engine.UnoState

/** صفحه‌ی پایان مسابقه‌ی اونو: قهرمان، جدول امتیازها، اشتراک و دوباره بازی */
@Composable
fun UnoWinnerScreen(
    state: UnoUiState,
    game: UnoState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val winner = game.matchWinner ?: return
    val winnerName = state.seatName(winner)
    val humanWon = winner == UNO_HUMAN

    ConfettiOverlay(modifier = Modifier.fillMaxSize())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        BobbingEmoji(emoji = if (humanWon) "🏆" else "🌈", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(12.dp))
        StickerTitle(
            text = if (humanWon) "قهرمان اونو شدی!" else "$winnerName برد!",
            rotation = -2f,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (humanWon) "همه رو رنگ به رنگ جا گذاشتی 😎" else "دفعه‌ی بعد مچشو بگیر!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(22.dp))
        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.padding(18.dp)) {
                val ranked = (0 until game.players).sortedByDescending { game.totals[it] }
                ranked.forEachIndexed { rank, seat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when (rank) {
                                0 -> "🥇"
                                1 -> "🥈"
                                2 -> "🥉"
                                else -> "🎖️"
                            },
                            fontSize = 20.sp,
                        )
                        Spacer(modifier = Modifier.padding(start = 10.dp))
                        Text(
                            text = state.seatName(seat),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (seat == winner) accent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${game.totals[seat].toPersianDigits()} امتیاز",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = if (seat == winner) extras.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        ShareWinButton(
            gameId = "uno",
            gameTitle = "اونو",
            gameEmoji = "🌈",
            winnerText = if (humanWon) "$winnerName قهرمان اونو شد! 🏆" else "$winnerName برنده‌ی اونو شد!",
            scoreLines = (0 until game.players).map { seat ->
                state.seatName(seat) to "${game.totals[seat].toPersianDigits()} امتیاز"
            },
            winnerNames = listOf(winnerName),
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(text = "دوباره بازی! 🌈", onClick = onPlayAgain)
        Spacer(modifier = Modifier.height(10.dp))
        KButton(text = "برگرد به خونه", style = KButtonStyle.Glass, onClick = onExitToHub)
        Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
    }
}
