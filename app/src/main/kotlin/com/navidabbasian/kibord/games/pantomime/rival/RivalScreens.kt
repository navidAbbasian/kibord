package com.navidabbasian.kibord.games.pantomime.rival

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.pantomime.ui.TeamScoreChips
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import androidx.compose.foundation.layout.offset
import com.navidabbasian.kibord.core.ui.components.PointCoin
import com.navidabbasian.kibord.core.ui.components.BlobTextField

/** انتخاب تعداد تیم‌ها */
@Composable
fun RivalTeamCountScreen(onTeamCountSelected: (Int) -> Unit) {
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
        Text(text = "⚔️", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند تیم هستید؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "پانتومیم با جدول امتیاز — رقابت نفس‌گیر",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            listOf(2, 3).forEachIndexed { i, count ->
                ChoiceBubble(
                    main = count.toPersianDigits(),
                    sub = "تیم",
                    size = 148.dp,
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.5f,
                    modifier = Modifier.offset(y = if (i % 2 == 0) 0.dp else 30.dp),
                    onClick = { onTeamCountSelected(count) }
                )
            }
        }
    }
}

/** ورود نام تیم‌ها */
@Composable
fun RivalTeamNamesScreen(
    teamCount: Int,
    teamNames: List<String>,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "نام تیم‌ها",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        repeat(teamCount) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            BlobTextField(
                value = teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = "تیم ${(i + 1).toPersianDigits()}",
                color = color,
                badge = (i + 1).toPersianDigits(),
                tilt = if (i % 2 == 0) -1.2f else 1.2f,
                phase = i * 1.6f,
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .offset(x = if (i % 2 == 0) (-6).dp else 6.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "شروع بازی", onClick = onConfirm)
        }
    }
}

/** جدول ۶ کتگوری × (۲/۴/۶) پانتومیم رقابتی */
@Composable
fun RivalBoardScreen(
    state: RivalUiState,
    onCellSelected: (RivalCell) -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val teamColors = extras.teamColors
    val pickerColor = teamColors.getOrElse(state.pickingTeam) { teamColors[0] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TeamScoreChips(
            count = state.teamCount,
            nameOf = state::teamDisplayName,
            scoreOf = { state.scores[it] },
            highlight = state.pickingTeam,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        Text(
            text = "نوبت اجرای: ${state.teamDisplayName(state.pickingTeam)}",
            style = MaterialTheme.typography.titleMedium,
            color = pickerColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(count = state.categories.size, key = { state.categories[it].id }) { catIndex ->
                val category = state.categories[catIndex]
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    tilt = if (catIndex % 2 == 0) -1f else 1f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = category.emoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)
                        ) {
                            category.tiers.forEachIndexed { qIndex, points ->
                                val cell = RivalCell(catIndex, points)
                                val used = cell in state.usedCells
                                PointCoin(
                                    value = points.toPersianDigits(),
                                    used = used,
                                    phase = (catIndex * 3 + qIndex) * 0.9f,
                                    onClick = { onCellSelected(cell) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** برنده‌ی پانتومیم رقابتی */
@Composable
fun RivalWinnerScreen(
    state: RivalUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

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
            Text(text = "🏆", fontSize = 84.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (winners.size > 1) {
                    "مساوی! ${winners.joinToString(" و ") { state.teamDisplayName(it) }}"
                } else {
                    state.teamDisplayName(winners.firstOrNull() ?: 0)
                },
                style = MaterialTheme.typography.displayMedium,
                color = teamColors.getOrElse(winners.firstOrNull() ?: 0) { teamColors[0] },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    (0 until state.teamCount)
                        .sortedByDescending { state.scores[it] }
                        .forEachIndexed { rank, team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (team in winners) "🥇" else "${(rank + 1).toPersianDigits()}",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = state.teamDisplayName(team),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = teamColors.getOrElse(team) { teamColors[0] }
                                    )
                                }
                                Text(
                                    text = state.scores[team].toPersianDigits(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }

    }
}
