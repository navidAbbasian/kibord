package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.games.kalamz.model.RoundType
import com.navidabbasian.kibord.games.kalamz.model.Team
import com.navidabbasian.kibord.games.kalamz.ui.components.TeamScoreCard

private val BlueAccent = Color(0xFF64B5F6)
private val PurpleAccent = Color(0xFFBA68C8)

@Composable
fun ResultsScreen(teams: List<Team>, onPlayAgain: () -> Unit, onExitToHub: () -> Unit) {
    val sound = LocalSoundManager.current
    LaunchedEffect(Unit) { sound?.playGameOver() }

    val extras = kiExtras
    val sortedTeams = teams.sortedByDescending { it.totalScore }
    val winnerScore = sortedTeams.firstOrNull()?.totalScore ?: 0

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(52.dp))

            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = extras.gold, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(8.dp))
            Text("نتایج نهایی", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = extras.gold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("برنده: ${sortedTeams.firstOrNull()?.name?.ifBlank { "تیم ۱" } ?: ""}",
                    fontSize = 18.sp, color = extras.gold, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sortedTeams.forEachIndexed { index, team ->
                    val isWinner = team.totalScore == winnerScore
                    TeamScoreCard(team = team, rank = index + 1, isWinner = isWinner)

                    var expanded by remember { mutableStateOf(false) }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp,
                        onClick = { expanded = !expanded }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("جزئیات", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (expanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val rounds = listOf(RoundType.DESCRIBE, RoundType.ONE_WORD, RoundType.PANTOMIME)
                                rounds.forEachIndexed { rIdx, round ->
                                    val score = team.scoresPerRound.getOrElse(rIdx) { 0 }
                                    val words = team.correctWordsPerRound.getOrElse(rIdx) { emptyList() }
                                    val roundAccent = when (round) { RoundType.DESCRIBE -> extras.gold; RoundType.ONE_WORD -> BlueAccent; else -> PurpleAccent }
                                    if (rIdx > 0) HorizontalDivider(color = extras.glassBorder, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 6.dp))
                                    Text("${round.persianTitle.substringBefore(":")}: $score کلمه", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = roundAccent, modifier = Modifier.padding(top = 4.dp))
                                    if (words.isNotEmpty()) {
                                        words.forEach { word ->
                                            Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = extras.success, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(word, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    } else {
                                        Text("  —", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            KButton(text = "بازی دوباره", onClick = onPlayAgain, style = KButtonStyle.Primary)

            Spacer(modifier = Modifier.height(10.dp))

            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
