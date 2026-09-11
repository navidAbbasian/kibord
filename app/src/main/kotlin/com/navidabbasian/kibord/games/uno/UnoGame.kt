package com.navidabbasian.kibord.games.uno

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
import com.navidabbasian.kibord.games.uno.ui.UnoSetupScreen
import com.navidabbasian.kibord.games.uno.ui.UnoTableScreen
import com.navidabbasian.kibord.games.uno.ui.UnoWinnerScreen

/** ریشه‌ی بازی اونو: تنظیمات → میز (سه مدل) → برنده */
@Composable
fun UnoGame(
    onExitToHub: () -> Unit,
    viewModel: UnoViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                UnoSoundEvent.PLAY -> sound?.playButtonClick()
                UnoSoundEvent.DRAW -> sound?.playWordSkip()
                UnoSoundEvent.HIT -> {
                    sound?.playDorNextTurn()
                    sound?.vibrate(150)
                }
                UnoSoundEvent.UNO_CALL -> sound?.playCorrectWord()
                UnoSoundEvent.CAUGHT -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                UnoSoundEvent.ROUND_END -> sound?.playRoundEnd()
                UnoSoundEvent.MATCH_WON -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
                UnoSoundEvent.MATCH_LOST -> sound?.playTimerEnd()
            }
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage == UnoStage.Setup) sound?.switchMusic(MusicTrack.HUB) else sound?.stopBackgroundMusic()
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
            state.stage == UnoStage.Setup || game == null -> "setup"
            state.showFinal && game.matchWinner != null -> "winner"
            else -> "table"
        }
        PhaseTransition(key = screen) {
            when (screen) {
                "setup" -> {
                    BackHandler { onExitToHub() }
                    UnoSetupScreen(
                        state = state,
                        onName = viewModel::setPlayerName,
                        onMode = viewModel::setMode,
                        onPlayers = viewModel::setPlayers,
                        onTarget = viewModel::setTarget,
                        onStart = viewModel::startMatch,
                    )
                }

                "winner" -> {
                    BackHandler {
                        viewModel.backToSetup()
                        onExitToHub()
                    }
                    UnoWinnerScreen(
                        state = state,
                        game = game!!,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.backToSetup()
                            onExitToHub()
                        },
                    )
                }

                else -> {
                    BackHandler { pendingExit = true }
                    UnoTableScreen(state = state, game = game!!, viewModel = viewModel)
                }
            }
        }
    }
}
