package com.navidabbasian.kibord.games.taboo

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
import com.navidabbasian.kibord.games.taboo.model.TabooPhase
import com.navidabbasian.kibord.games.taboo.model.TabooSoundEvent
import com.navidabbasian.kibord.games.taboo.ui.TabooSettingsScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooTeamCountScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooTeamNamesScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooTurnEndScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooTurnReadyScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooTurnScreen
import com.navidabbasian.kibord.games.taboo.ui.TabooWinnerScreen
import com.navidabbasian.kibord.games.taboo.viewmodel.TabooViewModel

/** ریشه‌ی تابو — ماشین فاز، موسیقی و رویدادهای صوتی */
@Composable
fun TabooGame(
    onExitToHub: () -> Unit,
    viewModel: TabooViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                TabooSoundEvent.TICK -> sound?.playTimerTick()
                TabooSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                TabooSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                TabooSoundEvent.CORRECT -> sound?.playCorrectWord()
                TabooSoundEvent.FOUL -> {
                    sound?.playWordSkip()
                    sound?.vibrate(120)
                }
                TabooSoundEvent.SKIP -> sound?.playWordSkip()
                TabooSoundEvent.GAME_OVER -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            TabooPhase.TeamCount, TabooPhase.TeamNames, TabooPhase.Settings -> MusicTrack.HUB
            else -> MusicTrack.DOR
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == TabooPhase.TeamCount || state.phase == TabooPhase.TeamNames || state.phase == TabooPhase.Settings) {
            GameHelpButton(gameId = "taboo", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                TabooPhase.TeamCount -> {
                    BackHandler { onExitToHub() }
                    TabooTeamCountScreen(onSelected = viewModel::setTeamCount)
                }

                TabooPhase.TeamNames -> {
                    BackHandler { viewModel.navigateBack() }
                    TabooTeamNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames,
                    )
                }

                TabooPhase.Settings -> {
                    BackHandler { viewModel.navigateBack() }
                    TabooSettingsScreen(
                        state = state,
                        onSeconds = viewModel::setTurnSeconds,
                        onRounds = viewModel::setTotalRounds,
                        onStart = viewModel::startGame,
                    )
                }

                is TabooPhase.TurnReady -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    TabooTurnReadyScreen(
                        state = state,
                        team = phase.team,
                        onStart = viewModel::startTurn,
                    )
                }

                TabooPhase.Turn -> {
                    BackHandler { }
                    TabooTurnScreen(
                        state = state,
                        onCorrect = viewModel::markCorrect,
                        onFoul = viewModel::markFoul,
                        onSkip = viewModel::skipCard,
                    )
                }

                is TabooPhase.TurnEnd -> {
                    BackHandler { viewModel.proceedAfterTurn() }
                    TabooTurnEndScreen(
                        state = state,
                        team = phase.team,
                        correct = phase.correct,
                        foul = phase.foul,
                        onAdjust = viewModel::adjustTurnScore,
                        onProceed = viewModel::proceedAfterTurn,
                    )
                }

                TabooPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    TabooWinnerScreen(
                        state = state,
                        winners = viewModel.winners(),
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.playAgain()
                            onExitToHub()
                        },
                    )
                }
            }
        }
    }
}
