package com.navidabbasian.kibord.games.pantomime.rival

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.KiBackground
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
        when (state.phase) {
            RivalPhase.TeamCount -> {
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
                BackHandler { onExitToHub() }
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
                BackHandler {
                    viewModel.playAgain()
                    onExitToHub()
                }
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
