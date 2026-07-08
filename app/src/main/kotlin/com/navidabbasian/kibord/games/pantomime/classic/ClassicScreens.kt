package com.navidabbasian.kibord.games.pantomime.classic

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
import com.navidabbasian.kibord.games.pantomime.model.PCategory

/** ورود نام دو تیم */
@Composable
fun ClassicTeamNamesScreen(
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
                .padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🤫", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "دو گروه بشید!",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "نام دو تیم را وارد کنید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        repeat(2) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = teamNames.getOrElse(i) { "" },
                    onValueChange = { onNameChanged(i, it.take(20)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "تیم ${(i + 1).toPersianDigits()}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = kiExtras.glassBorder,
                        focusedContainerColor = kiExtras.glass,
                        unfocusedContainerColor = kiExtras.glass,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = color,
                    )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "ادامه", onClick = onConfirm)
        }
    }
}

/** انتخاب تعداد راندها */
@Composable
fun ClassicRoundsScreen(onRoundsSelected: (Int) -> Unit) {
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
        Text(text = "🔁", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "چند راند؟",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = "در هر راند، هر تیم یک اجرا دارد",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        listOf(
            Triple(3, "کوتاه", "۶ اجرا — حدود ۱۵ دقیقه"),
            Triple(5, "متوسط", "۱۰ اجرا — حدود ۲۵ دقیقه"),
            Triple(7, "طولانی", "۱۴ اجرا — حدود ۳۵ دقیقه"),
        ).forEachIndexed { i, (rounds, title, subtitle) ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                tilt = if (i % 2 == 0) -1.4f else 1.4f,
                onClick = {
                    sound?.playButtonClick()
                    onRoundsSelected(rounds)
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = rounds.toPersianDigits(),
                        style = MaterialTheme.typography.displayMedium,
                        color = accent
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

/** انتخاب کتگوری و رده‌ی امتیازی (یا ریسک طلایی) توسط تیم اجراکننده */
@Composable
fun ClassicPickScreen(
    state: ClassicUiState,
    hasWords: (PCategory, Int) -> Boolean,
    hasGolden: (PCategory) -> Boolean,
    onPickWord: (PCategory, Int) -> Unit,
    onPickGolden: (PCategory) -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val teamColors = extras.teamColors
    val performerColor = teamColors.getOrElse(state.performingTeam) { teamColors[0] }
    val goldenAvailable = !state.goldenUsed[state.performingTeam]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- امتیازها و راند ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            repeat(2) { i ->
                val color = teamColors.getOrElse(i) { teamColors[0] }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            color.copy(alpha = if (i == state.performingTeam) 1f else 0.55f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = state.teamDisplayName(i),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        text = state.scores[i].toPersianDigits(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }
            }
        }

        Text(
            text = "راند ${state.currentRound.toPersianDigits()} از ${state.totalRounds.toPersianDigits()} — اجرای ${state.teamDisplayName(state.performingTeam)}",
            style = MaterialTheme.typography.titleMedium,
            color = performerColor,
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 4, 6).forEach { points ->
                                val available = hasWords(category, points)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (available) accent.copy(alpha = 0.85f) else extras.glass)
                                        .then(
                                            if (available) Modifier.clickable {
                                                sound?.playButtonClick()
                                                onPickWord(category, points)
                                            } else Modifier
                                        )
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = points.toPersianDigits(),
                                        color = if (available) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            if (goldenAvailable && hasGolden(category)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(extras.gold)
                                        .clickable {
                                            sound?.playButtonClick()
                                            onPickGolden(category)
                                        }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🏆${30.toPersianDigits()}",
                                        color = Color(0xFF3D2E00),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** شکست در ریسک طلایی: حذف فوری */
@Composable
fun ClassicGoldenLossScreen(
    state: ClassicUiState,
    loserTeam: Int,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors
    val winnerTeam = 1 - loserTeam

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "💀", fontSize = 84.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "ریسک طلایی جواب نداد!",
            style = MaterialTheme.typography.displayMedium,
            color = extras.danger,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${state.teamDisplayName(loserTeam)} حذف شد",
            style = MaterialTheme.typography.titleLarge,
            color = teamColors.getOrElse(loserTeam) { teamColors[0] },
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), borderColor = extras.gold.copy(alpha = 0.7f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🏆", fontSize = 44.sp)
                Text(
                    text = "برنده: ${state.teamDisplayName(winnerTeam)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = teamColors.getOrElse(winnerTeam) { teamColors[0] },
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
        Spacer(modifier = Modifier.height(12.dp))
        KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
    }
}

/** برنده‌ی پایان راندها */
@Composable
fun ClassicWinnerScreen(
    state: ClassicUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val extras = kiExtras
    val teamColors = extras.teamColors

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
                text = if (winners.size > 1) "مساوی!" else state.teamDisplayName(winners.firstOrNull() ?: 0),
                style = MaterialTheme.typography.displayMedium,
                color = teamColors.getOrElse(winners.firstOrNull() ?: 0) { teamColors[0] },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    repeat(2) { i ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = if (i in winners) "🥇" else "🥈", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = state.teamDisplayName(i),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = teamColors.getOrElse(i) { teamColors[0] }
                                )
                            }
                            Text(
                                text = state.scores[i].toPersianDigits(),
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
