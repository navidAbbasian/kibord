package com.navidabbasian.kibord.games.shelem.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.SHELEM_HUMAN
import com.navidabbasian.kibord.games.shelem.ShelemUiState
import com.navidabbasian.kibord.games.shelem.engine.ShelemState

/** صفحه‌ی پایان مسابقه: برنده، امتیازها، خلاصه‌ی دست‌ها، اشتراک و دوباره بازی */
@Composable
fun ShelemWinnerScreen(
    state: ShelemUiState,
    game: ShelemState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val winner = game.matchWinner ?: return
    val extras = kiExtras
    val weWon = winner == 0
    val humanName = state.seatName(SHELEM_HUMAN)
    val teamNames = listOf(
        "$humanName و ${state.seatName(2)}",
        "${state.seatName(1)} و ${state.seatName(3)}",
    )
    val winnerText = if (weWon) "تیم ما" else "تیم ${teamNames[1]}"
    val winnerNames = if (weWon) listOf(humanName) else listOf(state.seatName(1), state.seatName(3))
    val madeCount = game.history.count { it.made }
    val shelemCount = game.history.count { it.shelem }

    Box(modifier = Modifier.fillMaxSize()) {
        if (weWon) ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            BobbingEmoji(emoji = if (weWon) "🏆" else "🫠", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = if (weWon) "تیم ما برد!" else "حریف‌ها بردن!")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (weWon) "دمت گرم $humanName! تو و ${state.seatName(2)} ترکوندین 🎉"
                else "این بار ${state.seatName(1)} و ${state.seatName(3)} بردن — تلافی کن!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    for (team in 0..1) {
                        val color = shelemTeamColor(team)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(color.copy(alpha = if (team == winner) 0.22f else 0.08f), RoundedCornerShape(14.dp))
                                .border(1.dp, color.copy(alpha = if (team == winner) 0.8f else 0.3f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (if (team == winner) "🏅 " else "") + "${state.teamName(team)}: ${teamNames[team]}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = game.scores[team].toPersianDigits(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(label = "دست", value = game.history.size.toPersianDigits())
                        Stat(label = "شرطِ گرفته", value = madeCount.toPersianDigits())
                        Stat(label = "شلم", value = shelemCount.toPersianDigits())
                        Stat(label = "تا", value = game.settings.targetScore.toPersianDigits())
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ShareWinButton(
                gameId = "shelem",
                gameTitle = "شلم",
                gameEmoji = "♠️",
                winnerText = winnerText,
                scoreLines = listOf(
                    teamNames[0] to "${game.scores[0].toPersianDigits()} امتیاز",
                    teamNames[1] to "${game.scores[1].toPersianDigits()} امتیاز",
                ),
                winnerNames = winnerNames,
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.navigationBarsPadding().height(28.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
