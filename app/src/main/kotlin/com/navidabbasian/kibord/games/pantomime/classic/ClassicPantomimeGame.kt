package com.navidabbasian.kibord.games.pantomime.classic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.games.pantomime.model.PantoSoundEvent
import com.navidabbasian.kibord.games.pantomime.ui.PantoPerformScreen
import com.navidabbasian.kibord.games.pantomime.ui.PantoResultScreen
import com.navidabbasian.kibord.games.pantomime.ui.PantoWordRevealScreen
import com.navidabbasian.kibord.games.pantomime.ui.TeamScoreChips

/** ریشه‌ی پانتومیم کلاسیک */
@Composable
fun ClassicPantomimeGame(
    onExitToHub: () -> Unit,
    viewModel: ClassicViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val teamColors = kiExtras.teamColors

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                PantoSoundEvent.TICK -> sound?.playTimerTick()
                PantoSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                PantoSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                PantoSoundEvent.SUCCESS -> {
                    sound?.playCorrectWord()
                    sound?.vibrate(100)
                }
                PantoSoundEvent.GOLDEN_FAIL -> {
                    sound?.playDorExplosion()
                    sound?.vibratePattern(longArrayOf(0, 120, 60, 320))
                }
                PantoSoundEvent.GAME_OVER -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            ClassicPhase.TeamNames, ClassicPhase.Rounds -> MusicTrack.HUB
            else -> MusicTrack.PANTOMIME
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == ClassicPhase.TeamNames || state.phase == ClassicPhase.Rounds) {
            GameHelpButton(gameId = "pantomime_classic", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                ClassicPhase.TeamNames -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    ClassicTeamNamesScreen(
                        teamNames = state.teamNames,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames
                    )
                }
    
                ClassicPhase.Rounds -> {
                    BackHandler { viewModel.navigateBack() }
                    ClassicRoundsScreen(onRoundsSelected = viewModel::setRounds)
                }
    
                ClassicPhase.Pick -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    ClassicPickScreen(
                        state = state,
                        hasWords = viewModel::hasWords,
                        hasGolden = viewModel::hasGolden,
                        onPickWord = viewModel::pickWord,
                        onPickGolden = viewModel::pickGolden
                    )
                }
    
                ClassicPhase.Reveal -> {
                    // بازگشت غیرفعال: کلمه قرعه خورده و نباید بسوزد
                    BackHandler { }
                    state.attempt?.let { attempt ->
                        PantoWordRevealScreen(
                            attempt = attempt,
                            performerTeamName = state.teamDisplayName(state.performingTeam),
                            teamColor = teamColors.getOrElse(state.performingTeam) { teamColors[0] },
                            onStartPerform = viewModel::startPerform
                        )
                    }
                }
    
                ClassicPhase.Perform -> {
                    BackHandler { }
                    state.attempt?.let { attempt ->
                        PantoPerformScreen(
                            attempt = attempt,
                            timeLeftMillis = state.timeLeftMillis,
                            performerTeamName = state.teamDisplayName(state.performingTeam),
                            teamColor = teamColors.getOrElse(state.performingTeam) { teamColors[0] },
                            onSuccess = viewModel::markSuccess,
                            onFail = viewModel::markFail
                        )
                    }
                }
    
                ClassicPhase.Result -> {
                    BackHandler { viewModel.proceedAfterResult() }
                    state.lastResult?.let { result ->
                        PantoResultScreen(
                            result = result,
                            performerTeamName = state.teamDisplayName(result.performingTeam),
                            teamColor = teamColors.getOrElse(result.performingTeam) { teamColors[0] },
                            scoresContent = {
                                TeamScoreChips(
                                    count = 2,
                                    nameOf = state::teamDisplayName,
                                    scoreOf = { state.scores[it] },
                                )
                            },
                            onProceed = viewModel::proceedAfterResult
                        )
                    }
                }
    
                is ClassicPhase.GoldenLoss -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    ClassicGoldenLossScreen(
                        state = state,
                        loserTeam = phase.loserTeam,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.playAgain()
                            onExitToHub()
                        }
                    )
                }
    
                ClassicPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    ClassicWinnerScreen(
                        state = state,
                        winners = viewModel.winners(),
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.playAgain()
                            onExitToHub()
                        }
                    )
                }
            }
        }
    }
}
