package com.navidabbasian.kibord.games.kalamz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.kalamz.model.GamePhase
import com.navidabbasian.kibord.games.kalamz.model.RoundType
import com.navidabbasian.kibord.games.kalamz.ui.screens.GameSettingsScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.PlayerCountScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.ResultsScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.RoundEndScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.RoundIntroScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.TeamSetupScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.TurnScreen
import com.navidabbasian.kibord.games.kalamz.ui.screens.WordEntryScreen
import com.navidabbasian.kibord.games.kalamz.viewmodel.GameViewModel

/** ریشه‌ی بازی کلمز — ماشین فاز کامل با موسیقی راند و مدیریت بازگشت */
@Composable
fun KalamzGame(
    onExitToHub: () -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    // موسیقی بر اساس فاز/راند جاری
    LaunchedEffect(state.phase, state.currentRound) {
        val track = when (state.phase) {
            is GamePhase.Setup,
            is GamePhase.TeamSetup,
            is GamePhase.CustomSettings,
            is GamePhase.WordEntry -> MusicTrack.HUB

            else -> when (state.currentRound) {
                RoundType.DESCRIBE -> MusicTrack.KALAMZ_ROUND_1
                RoundType.ONE_WORD -> MusicTrack.KALAMZ_ROUND_2
                RoundType.PANTOMIME -> MusicTrack.KALAMZ_ROUND_3
            }
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        // راهنما فقط روی صفحه‌های پیش از شروع بازی — تا با تایمر/امتیاز وسط بازی تداخل نکند
        val setupPhase = state.phase is GamePhase.Setup || state.phase is GamePhase.TeamSetup ||
            state.phase is GamePhase.CustomSettings || state.phase is GamePhase.WordEntry
        if (setupPhase) {
            GameHelpButton(gameId = "kalamz", modifier = Modifier.align(Alignment.TopStart))
        }
        // بک وسط بازی نباید بی‌هوا از بازی خارج شود — خروج نشست را پاک می‌کند
        val exitConfirm = { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                is GamePhase.Setup -> {
                    BackHandler { onExitToHub() }
                    PlayerCountScreen(
                        onPlayerCountSelected = { count -> viewModel.setPlayerCount(count) }
                    )
                }
    
                is GamePhase.TeamSetup -> {
                    BackHandler { viewModel.navigateBack() }
                    TeamSetupScreen(
                        teams = state.teams,
                        onPlayerNameChanged = { playerId, name -> viewModel.updatePlayerName(playerId, name) },
                        onTeamNameChanged = { teamId, name -> viewModel.updateTeamName(teamId, name) },
                        onConfirm = { viewModel.confirmTeams() }
                    )
                }
    
                is GamePhase.CustomSettings -> {
                    BackHandler { viewModel.navigateBack() }
                    GameSettingsScreen(
                        onConfirmSettings = { wordsPerPlayer, timerDurationMillis ->
                            viewModel.setGameSettings(wordsPerPlayer, timerDurationMillis)
                        }
                    )
                }
    
                is GamePhase.WordEntry -> {
                    BackHandler { viewModel.navigateBack() }
                    val playerIndex = phase.currentPlayerIndex
                    val player = state.allPlayers[playerIndex]
                    WordEntryScreen(
                        player = player,
                        wordsPerPlayer = state.wordsPerPlayer,
                        currentPlayerIndex = playerIndex,
                        totalPlayers = state.allPlayers.size,
                        onSubmitWords = { idx, words -> viewModel.submitWordsForPlayer(idx, words) }
                    )
                }
    
                is GamePhase.RoundIntro -> {
                    BackHandler { exitConfirm() }
                    RoundIntroScreen(
                        round = phase.round,
                        onStartRound = { viewModel.startRound() }
                    )
                }

                is GamePhase.TurnReady, is GamePhase.TurnActive, is GamePhase.TurnEnd -> {
                    BackHandler { exitConfirm() }
                    TurnScreen(
                        state = state,
                        onStartTurn = { viewModel.startTurn() },
                        onCorrect = { viewModel.markCorrect() },
                        onPrevious = { viewModel.previousWord() },
                        onNext = { viewModel.nextWord() },
                        onProceed = { viewModel.proceedAfterTurn() },
                        onPauseTimer = { viewModel.pauseTimer() },
                        onResumeTimer = { viewModel.resumeTimer() },
                        onRemoveWord = { word -> viewModel.removeCorrectWord(word) }
                    )
                }
    
                is GamePhase.RoundEnd -> {
                    BackHandler { exitConfirm() }
                    val roundIndex = phase.round.roundNumber - 1
                    val teamScores = state.teams.map { team -> Pair(team.id, team.scoresPerRound[roundIndex]) }
                    RoundEndScreen(
                        round = phase.round,
                        teams = state.teams,
                        teamScores = teamScores,
                        onProceed = { viewModel.proceedToNextRound() }
                    )
                }
    
                is GamePhase.GameOver -> {
                    BackHandler { pendingExit = { viewModel.resetGame(); onExitToHub() } }
                    ResultsScreen(
                        teams = state.teams,
                        onPlayAgain = { viewModel.resetGame() },
                        onExitToHub = {
                            viewModel.resetGame()
                            onExitToHub()
                        }
                    )
                }
            }
        }
    }
}
