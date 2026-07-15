package com.navidabbasian.kibord.games.pantomime.rival

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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

/** ریشه‌ی پانتومیم رقابتی */
@Composable
fun RivalPantomimeGame(
    onExitToHub: () -> Unit,
    viewModel: RivalViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val teamColors = kiExtras.teamColors

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

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
                PantoSoundEvent.GOLDEN_FAIL -> {}
                PantoSoundEvent.GAME_OVER -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            RivalPhase.TeamCount, RivalPhase.TeamNames -> MusicTrack.HUB
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
        if (state.phase == RivalPhase.TeamCount || state.phase == RivalPhase.TeamNames) {
            GameHelpButton(gameId = "pantomime_rival", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (state.phase) {
                RivalPhase.TeamCount -> {
                    // صفحه‌ی اولِ راه‌اندازی: هنوز بازی شروع نشده، پس بی‌تاییدْ خروج
                    BackHandler { onExitToHub() }
                    RivalTeamCountScreen(onTeamCountSelected = viewModel::setTeamCount)
                }
    
                RivalPhase.TeamNames -> {
                    BackHandler { viewModel.navigateBack() }
                    RivalTeamNamesScreen(
                        teamCount = state.teamCount,
                        teamNames = state.teamNames,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames
                    )
                }
    
                RivalPhase.Board -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    RivalBoardScreen(
                        state = state,
                        onCellSelected = viewModel::selectCell
                    )
                }
    
                RivalPhase.Reveal -> {
                    BackHandler { }
                    state.attempt?.let { attempt ->
                        PantoWordRevealScreen(
                            attempt = attempt,
                            performerTeamName = state.teamDisplayName(state.pickingTeam),
                            teamColor = teamColors.getOrElse(state.pickingTeam) { teamColors[0] },
                            onStartPerform = viewModel::startPerform
                        )
                    }
                }
    
                RivalPhase.Perform -> {
                    BackHandler { }
                    state.attempt?.let { attempt ->
                        PantoPerformScreen(
                            attempt = attempt,
                            timeLeftMillis = state.timeLeftMillis,
                            performerTeamName = state.teamDisplayName(state.pickingTeam),
                            teamColor = teamColors.getOrElse(state.pickingTeam) { teamColors[0] },
                            onSuccess = viewModel::markSuccess,
                            onFail = viewModel::markFail
                        )
                    }
                }
    
                RivalPhase.Result -> {
                    BackHandler { viewModel.proceedAfterResult() }
                    state.lastResult?.let { result ->
                        PantoResultScreen(
                            result = result,
                            performerTeamName = state.teamDisplayName(result.performingTeam),
                            teamColor = teamColors.getOrElse(result.performingTeam) { teamColors[0] },
                            scoresContent = {
                                TeamScoreChips(
                                    count = state.teamCount,
                                    nameOf = state::teamDisplayName,
                                    scoreOf = { state.scores[it] },
                                )
                            },
                            onProceed = viewModel::proceedAfterResult,
                            proceedLabel = if (state.allCellsPlayed) "کی برد؟ 🏆" else "ادامه"
                        )
                    }
                }
    
                RivalPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    RivalWinnerScreen(
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
