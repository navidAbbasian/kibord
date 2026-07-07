package com.navidabbasian.kibord.games.kalamz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.KiBackground
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
    val sound = LocalSoundManager.current

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
                RoundIntroScreen(
                    round = phase.round,
                    onStartRound = { viewModel.startRound() }
                )
            }

            is GamePhase.TurnReady, is GamePhase.TurnActive, is GamePhase.TurnEnd -> {
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
                BackHandler {
                    viewModel.resetGame()
                    onExitToHub()
                }
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
