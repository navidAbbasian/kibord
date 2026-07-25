package com.navidabbasian.kibord.games.esmfamilsorati

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsPhase
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsSoundEvent
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsEliminatedScreen
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsGameScreen
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsPlayerCountScreen
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsPlayerNamesScreen
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsTopicSelectScreen
import com.navidabbasian.kibord.games.esmfamilsorati.ui.screens.EfsWinnerScreen
import com.navidabbasian.kibord.games.esmfamilsorati.viewmodel.EfsViewModel

/** ریشه‌ی اسم فامیل سرعتی — ماشین فاز، موسیقی، رویدادهای صوتی و مدیریت بازگشت */
@Composable
fun EfsGame(
    onExitToHub: () -> Unit,
    viewModel: EfsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    // رساندن رویدادهای صوتی موتور بازی به مدیر صدا — صداها از بازی دور قرض گرفته شده‌اند
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                EfsSoundEvent.TICK_NORMAL -> sound?.playDorTickNormal()
                EfsSoundEvent.TICK_FAST -> sound?.playDorTickFast()
                EfsSoundEvent.EXPLOSION -> sound?.playDorExplosion()
                EfsSoundEvent.WORD_CORRECT -> sound?.playDorWordCorrect()
                EfsSoundEvent.NEXT_TURN -> sound?.playDorNextTurn()
                // پاس جریمه است — فقط لرزش کوتاه، بدون صدای مثبت
                EfsSoundEvent.PASS_LETTER -> sound?.vibrate(50)
                EfsSoundEvent.PLAYER_ELIMINATED -> sound?.playDorTeamEliminated()
                EfsSoundEvent.GAME_OVER -> sound?.playDorGameOver()
                EfsSoundEvent.VIBRATE_LONG -> sound?.vibratePattern(longArrayOf(0, 120, 60, 320))
                EfsSoundEvent.VIBRATE_SHORT -> sound?.vibrate(50)
            }
        }
    }

    // موسیقی: منو در مراحل راه‌اندازی؛ حین بازی فقط تیک‌تاک و افکت‌ها
    LaunchedEffect(state.phase) {
        when (state.phase) {
            EfsPhase.PlayerCount, EfsPhase.PlayerNames, EfsPhase.TopicSelect ->
                sound?.switchMusic(MusicTrack.HUB)
            else -> sound?.stopBackgroundMusic()
        }
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == EfsPhase.PlayerCount || state.phase == EfsPhase.PlayerNames || state.phase == EfsPhase.TopicSelect) {
            GameHelpButton(gameId = "esm_famil_sorati", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                EfsPhase.PlayerCount -> {
                    BackHandler { onExitToHub() }
                    EfsPlayerCountScreen(onPlayerCountSelected = viewModel::setPlayerCount)
                }

                EfsPhase.PlayerNames -> {
                    BackHandler { viewModel.navigateBack() }
                    EfsPlayerNamesScreen(
                        playerNames = state.playerNames,
                        onNameChanged = viewModel::updatePlayerName,
                        onConfirm = viewModel::confirmNames
                    )
                }

                EfsPhase.TopicSelect -> {
                    BackHandler { viewModel.navigateBack() }
                    EfsTopicSelectScreen(
                        topics = state.topics,
                        selected = state.topic,
                        firstPlayerName = state.players.firstOrNull()?.name ?: "",
                        onSelect = viewModel::selectTopic,
                        onStart = viewModel::startGame
                    )
                }

                EfsPhase.Playing -> {
                    BackHandler { viewModel.pauseGame() }
                    EfsGameScreen(
                        state = state,
                        onCenterTap = viewModel::onCenterTap,
                        onPass = viewModel::passLetter,
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

                is EfsPhase.PlayerEliminated -> {
                    BackHandler { viewModel.continueAfterElimination() }
                    EfsEliminatedScreen(
                        player = phase.player,
                        nextPlayerName = state.currentPlayer?.name ?: "",
                        onContinue = viewModel::continueAfterElimination
                    )
                }

                is EfsPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    EfsWinnerScreen(
                        winner = phase.player,
                        topic = state.topic,
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
