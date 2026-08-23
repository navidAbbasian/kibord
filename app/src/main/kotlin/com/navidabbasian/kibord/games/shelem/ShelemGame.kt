package com.navidabbasian.kibord.games.shelem

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
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
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.ui.ShelemSetupScreen
import com.navidabbasian.kibord.games.shelem.ui.ShelemTableScreen
import com.navidabbasian.kibord.games.shelem.ui.ShelemWinnerScreen

/** ریشه‌ی بازی شلم: تنظیمات → میز (شرط، ویدو، حکم، ۱۲ دست) → برنده */
@Composable
fun ShelemGame(
    onExitToHub: () -> Unit,
    viewModel: ShelemViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                ShelemSoundEvent.DEAL -> sound?.playRoundStart()
                ShelemSoundEvent.BID -> sound?.playTurnStart()
                ShelemSoundEvent.PASS -> sound?.playWordSkip()
                ShelemSoundEvent.CARD -> sound?.playButtonClick()
                ShelemSoundEvent.TRICK_WON -> { sound?.playCorrectWord(); sound?.vibrate(30) }
                ShelemSoundEvent.TRICK_LOST -> sound?.playDorNextTurn()
                ShelemSoundEvent.HAND_END -> sound?.playRoundEnd()
                ShelemSoundEvent.MATCH_WON -> { sound?.playGameOver(); sound?.vibrate(200) }
                ShelemSoundEvent.MATCH_LOST -> sound?.playTimerEnd()
            }
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage == ShelemStage.Setup) sound?.switchMusic(MusicTrack.HUB) else sound?.stopBackgroundMusic()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit,
            onConfirm = {
                pendingExit = false
                viewModel.backToSetup()
                onExitToHub()
            },
            onDismiss = { pendingExit = false },
        )
        val game = state.game
        val screen = when {
            state.stage == ShelemStage.Setup || game == null -> "setup"
            game.phase == ShelemPhase.MATCH_OVER && state.showFinal -> "winner"
            else -> "table"
        }
        PhaseTransition(key = screen) {
            when (screen) {
                "setup" -> {
                    BackHandler { onExitToHub() }
                    ShelemSetupScreen(
                        state = state,
                        onName = viewModel::setPlayerName,
                        onTarget = viewModel::setTarget,
                        onShelemBonus = viewModel::setShelemBonus,
                        onStart = viewModel::startMatch,
                    )
                }

                "winner" -> {
                    BackHandler { viewModel.backToSetup(); onExitToHub() }
                    ShelemWinnerScreen(
                        state = state,
                        game = game!!,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = { viewModel.backToSetup(); onExitToHub() },
                    )
                }

                else -> {
                    BackHandler { pendingExit = true }
                    ShelemTableScreen(state = state, game = game!!, viewModel = viewModel)
                }
            }
        }
    }
}
