package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.games.kalamz.model.GamePhase
import com.navidabbasian.kibord.games.kalamz.model.GameUiState
import com.navidabbasian.kibord.games.kalamz.ui.components.TimerDisplay
import com.navidabbasian.kibord.games.kalamz.ui.components.WordCard

@Composable
fun TurnScreen(
    state: GameUiState,
    onStartTurn: () -> Unit,
    onCorrect: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProceed: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit,
    onRemoveWord: (String) -> Unit
) {
    // Keep the device awake while the turn is active
    val view = LocalView.current
    DisposableEffect(state.phase is GamePhase.TurnActive) {
        view.keepScreenOn = state.phase is GamePhase.TurnActive
        onDispose { view.keepScreenOn = false }
    }

    when (val phase = state.phase) {
        is GamePhase.TurnReady -> {
            val team = state.teams[phase.teamId]
            val currentPlayer = if (state.currentPlayerSlot == 1) team.player1 else team.player2
            TurnIntroScreen(
                currentPlayer = currentPlayer,
                team = team,
                round = state.currentRound,
                onStartTurn = onStartTurn
            )
        }
        is GamePhase.TurnActive -> TurnActiveContent(
            state = state,
            onCorrect = onCorrect,
            onPrevious = onPrevious,
            onNext = onNext,
            onPauseTimer = onPauseTimer,
            onResumeTimer = onResumeTimer
        )
        is GamePhase.TurnEnd -> TurnEndContent(
            correctCount = phase.correctCount,
            correctWords = phase.correctWords,
            onProceed = onProceed,
            onRemoveWord = onRemoveWord
        )
        else -> {}
    }
}

// ────────── TURN ACTIVE ──────────

@Composable
private fun TurnActiveContent(
    state: GameUiState,
    onCorrect: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPauseTimer: () -> Unit,
    onResumeTimer: () -> Unit
) {
    val sound = LocalSoundManager.current
    val teamColor = kiExtras.teamColors.teamColorFor(state.currentTeamIndex)

    // Timer-tick and warning sounds
    // تیک‌تاک روی تمام مدت نوبت پخش می‌شه؛ در ۵ ثانیه آخر به هشدار تبدیل می‌شه
    val timeLeft = state.timeLeftMillis
    LaunchedEffect(timeLeft / 1000) {
        if (!state.isTimerPaused && timeLeft > 0) {
            when {
                timeLeft <= 5_000 -> sound?.playTimerWarning()
                else              -> sound?.playTimerTick()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassCard(
                    cornerRadius = 14.dp,
                    containerColor = teamColor.copy(alpha = 0.2f),
                    borderColor = teamColor.copy(alpha = 0.4f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.teams[state.currentTeamIndex].name.ifBlank { "تیم ${state.currentTeamIndex + 1}" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = teamColor)
                        Text(state.currentRound.persianTitle.substringBefore(":"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBadge("${state.turnCorrectCount}", "درست", kiExtras.success)
                    StatBadge("${state.remainingWords.size}", "باقی", kiExtras.gold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TimerDisplay(timeLeftMillis = state.timeLeftMillis, totalTimeMillis = state.timerDurationMillis, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))

            state.currentWord?.let { word ->
                WordCard(word = word.text, onPrevious = onPrevious, onNext = onNext, canGoBack = state.canGoToPrevious, modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.weight(1f))

            // Pause / Resume button
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier.size(52.dp).background(kiExtras.glassStrong, CircleShape)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() },
                            onClick = if (state.isTimerPaused) { { sound?.playButtonClick(); onResumeTimer() } } else { { sound?.playButtonClick(); onPauseTimer() } }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isTimerPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (state.isTimerPaused) "ادامه" else "توقف",
                        tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            KButton(
                text = "درسته!",
                onClick = { sound?.playCorrectWord(); onCorrect() },
                accent = kiExtras.success,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isTimerPaused
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Pause overlay
        if (state.isTimerPaused) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("متوقف شده", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("برای ادامه دکمه پلی بزن", fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun StatBadge(value: String, label: String, color: Color) {
    GlassCard(
        cornerRadius = 14.dp,
        containerColor = color.copy(alpha = 0.18f),
        borderColor = color.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ────────── TURN END ──────────

@Composable
private fun TurnEndContent(correctCount: Int, correctWords: List<String>, onProceed: () -> Unit, onRemoveWord: (String) -> Unit) {
    val sound = LocalSoundManager.current
    LaunchedEffect(Unit) { sound?.playTimerEnd() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Icon(
                imageVector = if (correctCount > 0) Icons.Outlined.EmojiEvents else Icons.Default.SentimentNeutral,
                contentDescription = null, tint = if (correctCount > 0) kiExtras.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("تایم تموم شد!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(6.dp))

            GlassCard(
                cornerRadius = 20.dp,
                containerColor = kiExtras.gold.copy(alpha = 0.2f),
                borderColor = kiExtras.gold.copy(alpha = 0.5f)
            ) {
                Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = kiExtras.gold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$correctCount کلمه درست", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kiExtras.gold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("برای حذف کلمه اشتباه روی ایکس بزن", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(16.dp))

            if (correctWords.isNotEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    cornerRadius = 24.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("کلمات درست:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(10.dp))
                        correctWords.forEach { word ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = kiExtras.success, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(word, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(onClick = { sound?.playWordSkip(); onRemoveWord(word) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = kiExtras.danger, modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            KButton(
                text = "نفر بعدی",
                onClick = onProceed
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
