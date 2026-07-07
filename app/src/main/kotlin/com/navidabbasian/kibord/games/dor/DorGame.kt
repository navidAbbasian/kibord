package com.navidabbasian.kibord.games.dor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.games.dor.model.DorPhase
import com.navidabbasian.kibord.games.dor.model.DorSoundEvent
import com.navidabbasian.kibord.games.dor.ui.screens.DorCategoryScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorEliminatedScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorGameScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorModeScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorPlayerCountScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorPlayerNamesScreen
import com.navidabbasian.kibord.games.dor.ui.screens.DorWinnerScreen
import com.navidabbasian.kibord.games.dor.viewmodel.DorViewModel

/** ریشه‌ی بازی دور — ماشین فاز، موسیقی، رویدادهای صوتی و مدیریت بازگشت */
@Composable
fun DorGame(
    onExitToHub: () -> Unit,
    viewModel: DorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current

    // رساندن رویدادهای صوتی موتور بازی به مدیر صدا
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                DorSoundEvent.TICK_NORMAL -> sound?.playDorTickNormal()
                DorSoundEvent.TICK_FAST -> sound?.playDorTickFast()
                DorSoundEvent.START_TENSION -> sound?.startBombTension()
                DorSoundEvent.STOP_TENSION -> sound?.stopBombTension()
                DorSoundEvent.EXPLOSION -> sound?.playDorExplosion()
                DorSoundEvent.VIBRATE_LONG -> sound?.vibratePattern(longArrayOf(0, 120, 60, 320))
                DorSoundEvent.VIBRATE_SHORT -> sound?.vibrate(50)
                DorSoundEvent.WORD_CORRECT -> sound?.playDorWordCorrect()
                DorSoundEvent.NEXT_TURN -> sound?.playDorNextTurn()
                DorSoundEvent.TEAM_ELIMINATED -> sound?.playDorTeamEliminated()
                DorSoundEvent.GAME_OVER -> sound?.playDorGameOver()
            }
        }
    }

    // موسیقی: منو در مراحل راه‌اندازی، موسیقی دور حین بازی
    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            DorPhase.PlayerCount, DorPhase.PlayerNames, DorPhase.Categories, DorPhase.Mode -> MusicTrack.HUB
            else -> MusicTrack.DOR
        }
        sound?.switchMusic(track)
    }

    // پاک‌سازی موسیقی تنش هنگام خروج از بازی
    DisposableEffect(Unit) {
        onDispose { sound?.stopBombTension() }
    }

    KiBackground {
        when (val phase = state.phase) {
            DorPhase.PlayerCount -> {
                BackHandler { onExitToHub() }
                DorPlayerCountScreen(onPlayerCountSelected = viewModel::setPlayerCount)
            }

            DorPhase.PlayerNames -> {
                BackHandler { viewModel.navigateBack() }
                DorPlayerNamesScreen(
                    playerNames = state.playerNames,
                    onNameChanged = viewModel::updatePlayerName,
                    onConfirm = viewModel::confirmNames
                )
            }

            DorPhase.Categories -> {
                BackHandler { viewModel.navigateBack() }
                DorCategoryScreen(
                    categories = state.categories,
                    selectedIds = state.selectedCategoryIds,
                    onToggle = viewModel::toggleCategory,
                    onSelectAll = viewModel::selectAllCategories,
                    onAddWord = viewModel::addCustomWord,
                    onConfirm = viewModel::confirmCategories
                )
            }

            DorPhase.Mode -> {
                BackHandler { viewModel.navigateBack() }
                DorModeScreen(onModeSelected = viewModel::selectMode)
            }

            DorPhase.Playing -> {
                BackHandler { viewModel.pauseGame() }
                DorGameScreen(
                    state = state,
                    nextPlayerName = viewModel.nextPlayerName(),
                    onCenterTap = viewModel::onCenterTap,
                    onSkip = viewModel::skipWord,
                    onPause = viewModel::pauseGame
                )

                if (state.showPauseDialog) {
                    PauseDialog(
                        onResume = viewModel::resumeGame,
                        onEndGame = {
                            viewModel.playAgain()
                            onExitToHub()
                        }
                    )
                }
            }

            is DorPhase.TeamEliminated -> {
                BackHandler { viewModel.continueAfterElimination() }
                DorEliminatedScreen(
                    team = phase.team,
                    onContinue = viewModel::continueAfterElimination
                )
            }

            is DorPhase.Winner -> {
                BackHandler {
                    viewModel.playAgain()
                    onExitToHub()
                }
                DorWinnerScreen(
                    winner = phase.team,
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

@Composable
private fun PauseDialog(
    onResume: () -> Unit,
    onEndGame: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "⏸️ بازی متوقف شد",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KButton(text = "ادامه‌ی بازی", onClick = onResume)
                Spacer(modifier = Modifier.height(10.dp))
                KButton(text = "پایان بازی", onClick = onEndGame, style = KButtonStyle.Danger)
            }
        },
        confirmButton = {}
    )
}
