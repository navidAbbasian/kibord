package com.navidabbasian.kibord.games.gandegoo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.gandegoo.model.GgPhase
import com.navidabbasian.kibord.games.gandegoo.model.GgSoundEvent
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgBidScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgBoardScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgPlayScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgResultScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgSetupScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgTeamCountScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgTeamNamesScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgWinnerScreen
import com.navidabbasian.kibord.games.gandegoo.viewmodel.GandeGooViewModel

/** ریشه‌ی بازی گنده‌گو — ماشین فاز، موسیقی و رویدادهای صوتی */
@Composable
fun GandeGooGame(
    onExitToHub: () -> Unit,
    viewModel: GandeGooViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                GgSoundEvent.TICK -> sound?.playTimerTick()
                GgSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                GgSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                GgSoundEvent.FULL_SUCCESS -> {
                    sound?.playRoundEnd()
                    sound?.vibrate(120)
                }
                GgSoundEvent.GAME_OVER -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            GgPhase.TeamCount, GgPhase.TeamNames, GgPhase.Setup -> MusicTrack.HUB
            else -> MusicTrack.GANDEGOO
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        PhaseTransition(key = state.phase::class) {
            when (state.phase) {
                GgPhase.TeamCount -> {
                    BackHandler { onExitToHub() }
                    GgTeamCountScreen(onTeamCountSelected = viewModel::setTeamCount)
                }
    
                GgPhase.TeamNames -> {
                    BackHandler { viewModel.navigateBack() }
                    GgTeamNamesScreen(
                        teamCount = state.teamCount,
                        teamNames = state.teamNames,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames
                    )
                }
    
                GgPhase.Setup -> {
                    BackHandler { viewModel.navigateBack() }
                    GgSetupScreen(onStart = viewModel::startGame)
                }
    
                GgPhase.Board -> {
                    BackHandler { onExitToHub() }
                    GgBoardScreen(
                        state = state,
                        onCellSelected = viewModel::selectCell
                    )
                }
    
                GgPhase.Bid -> {
                    BackHandler { viewModel.cancelBid() }
                    GgBidScreen(
                        state = state,
                        onClaimingTeamChanged = viewModel::setClaimingTeam,
                        onClaimChanged = viewModel::setClaim,
                        onStart = viewModel::startAttempt,
                        onCancel = viewModel::cancelBid
                    )
                }
    
                GgPhase.Play -> {
                    // حین شمارش، بازگشت غیرفعال است تا دست خراب نشود
                    BackHandler { }
                    GgPlayScreen(
                        state = state,
                        onCount = {
                            sound?.playCorrectWord()
                            viewModel.incrementCount()
                        },
                        onUndo = {
                            sound?.playWordSkip()
                            viewModel.decrementCount()
                        }
                    )
                }
    
                GgPhase.Result -> {
                    BackHandler { viewModel.proceedAfterResult() }
                    GgResultScreen(
                        state = state,
                        onProceed = viewModel::proceedAfterResult
                    )
                }
    
                GgPhase.Winner -> {
                    BackHandler {
                        viewModel.playAgain()
                        onExitToHub()
                    }
                    GgWinnerScreen(
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
