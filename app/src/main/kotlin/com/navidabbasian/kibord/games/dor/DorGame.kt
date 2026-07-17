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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
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

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    // رساندن رویدادهای صوتی موتور بازی به مدیر صدا
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                DorSoundEvent.TICK_NORMAL -> sound?.playDorTickNormal()
                DorSoundEvent.TICK_FAST -> sound?.playDorTickFast()
                // به خواست ارباب: ثانیه‌های آخر فقط تیک تند — لوپ تنش حذف شد
                DorSoundEvent.START_TENSION -> Unit
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

    // موسیقی: منو در مراحل راه‌اندازی؛ حین بازی فقط تیک‌تاک و افکت‌ها — بدون موسیقی
    LaunchedEffect(state.phase) {
        when (state.phase) {
            DorPhase.PlayerCount, DorPhase.PlayerNames, DorPhase.Categories, DorPhase.Mode ->
                sound?.switchMusic(MusicTrack.HUB)
            else -> sound?.stopBackgroundMusic()
        }
    }

    // پاک‌سازی موسیقی تنش هنگام خروج از بازی
    DisposableEffect(Unit) {
        onDispose { sound?.stopBombTension() }
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == DorPhase.PlayerCount || state.phase == DorPhase.PlayerNames || state.phase == DorPhase.Categories || state.phase == DorPhase.Mode) {
            GameHelpButton(gameId = "dor", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
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
                                viewModel.leaveGame()
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
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
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
