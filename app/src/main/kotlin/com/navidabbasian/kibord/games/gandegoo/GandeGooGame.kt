package com.navidabbasian.kibord.games.gandegoo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import com.navidabbasian.kibord.games.gandegoo.model.GgPhase
import com.navidabbasian.kibord.games.gandegoo.model.GgSoundEvent
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgBidScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgBoardScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgPlayScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgResultScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgReviewScreen
import com.navidabbasian.kibord.games.gandegoo.ui.screens.GgModeScreen
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

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
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
            GgPhase.TeamCount, GgPhase.TeamNames, GgPhase.Mode, GgPhase.Setup -> MusicTrack.HUB
            else -> MusicTrack.GANDEGOO
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == GgPhase.TeamCount || state.phase == GgPhase.TeamNames || state.phase == GgPhase.Mode || state.phase == GgPhase.Setup) {
            GameHelpButton(gameId = "gandegoo", modifier = Modifier.align(Alignment.TopStart))
        }
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
    
                GgPhase.Mode -> {
                    BackHandler { viewModel.navigateBack() }
                    GgModeScreen(onModeSelected = viewModel::setMode)
                }

                GgPhase.Setup -> {
                    BackHandler { viewModel.navigateBack() }
                    GgSetupScreen(
                        availableCategories = state.availableCategories,
                        chosenIds = state.chosenCategoryIds,
                        cellsPerCategory = state.mode.tiers.size,
                        onToggleCategory = viewModel::toggleCategory,
                        onStart = viewModel::startGame
                    )
                }
    
                GgPhase.Board -> {
                    BackHandler { pendingExit = { onExitToHub() } }
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
                        onSwapQuestion = {
                            sound?.playWordSkip()
                            viewModel.swapQuestion()
                        },
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
                        },
                        onStartVideoCheck = {
                            sound?.playButtonClick()
                            sound?.vibrate(60)
                            viewModel.startVideoCheck()
                        },
                        onEndVideoCheck = {
                            sound?.playButtonClick()
                            viewModel.endVideoCheck()
                        }
                    )
                }

                GgPhase.Review -> {
                    BackHandler { }
                    GgReviewScreen(
                        state = state,
                        onIncrement = {
                            sound?.playCorrectWord()
                            viewModel.incrementCount()
                        },
                        onDecrement = {
                            sound?.playWordSkip()
                            viewModel.decrementCount()
                        },
                        onConfirm = viewModel::confirmReview
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
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
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
