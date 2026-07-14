package com.navidabbasian.kibord.games.mafia

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.games.mafia.model.MfPhase
import com.navidabbasian.kibord.games.mafia.ui.MfDayAnnounceScreen
import com.navidabbasian.kibord.games.mafia.ui.MfDayResultScreen
import com.navidabbasian.kibord.games.mafia.ui.MfDayVoteScreen
import com.navidabbasian.kibord.games.mafia.ui.MfEntryScreen
import com.navidabbasian.kibord.games.mafia.ui.MfGameOverScreen
import com.navidabbasian.kibord.games.mafia.ui.MfJoinScreen
import com.navidabbasian.kibord.games.mafia.ui.MfLobbyScreen
import com.navidabbasian.kibord.games.mafia.ui.MfNightScreen
import com.navidabbasian.kibord.games.mafia.ui.MfRoleRevealScreen
import com.navidabbasian.kibord.games.mafia.viewmodel.MafiaViewModel
import com.navidabbasian.kibord.games.mafia.viewmodel.MfLocalScreen

/** ریشه‌ی شب مافیا — بازی چندگوشی روی شبکه‌ی محلی، بدون گرداننده */
@Composable
fun MafiaGame(
    onExitToHub: () -> Unit,
    viewModel: MafiaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    LaunchedEffect(state.localScreen, snapshot.phase) {
        val track = when {
            state.localScreen != MfLocalScreen.IN_GAME -> MusicTrack.HUB
            snapshot.phase == MfPhase.LOBBY -> MusicTrack.HUB
            else -> MusicTrack.ESM_FAMIL
        }
        sound?.switchMusic(track)
    }

    val leaveAndExit = {
        viewModel.leaveGame()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.localScreen != MfLocalScreen.IN_GAME || state.snapshot.phase == MfPhase.LOBBY) {
            GameHelpButton(gameId = "mafia", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.localScreen to snapshot.phase) {
            when (state.localScreen) {
                MfLocalScreen.ENTRY -> {
                    BackHandler { onExitToHub() }
                    MfEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onHost = viewModel::startHosting,
                        onJoin = viewModel::openJoinScreen,
                    )
                }

                MfLocalScreen.JOIN -> {
                    BackHandler { viewModel.backToEntryFromJoin() }
                    MfJoinScreen(
                        state = state,
                        onJoin = { game -> viewModel.joinGame(game.address, game.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                    )
                }

                MfLocalScreen.IN_GAME -> {
                    BackHandler { pendingExit = { leaveAndExit() } }
                    when (snapshot.phase) {
                        MfPhase.LOBBY -> MfLobbyScreen(
                            state = state,
                            onStart = viewModel::startGame,
                        )

                        MfPhase.ROLE_REVEAL -> MfRoleRevealScreen(
                            state = state,
                            onSeen = viewModel::markSeen,
                        )

                        MfPhase.NIGHT -> MfNightScreen(
                            state = state,
                            onAction = viewModel::nightAction,
                        )

                        MfPhase.DAY_ANNOUNCE -> MfDayAnnounceScreen(
                            state = state,
                            onStartVote = viewModel::startDayVote,
                        )

                        MfPhase.DAY_VOTE -> MfDayVoteScreen(
                            state = state,
                            onVote = viewModel::dayVote,
                        )

                        MfPhase.DAY_RESULT -> MfDayResultScreen(
                            state = state,
                            onProceed = viewModel::proceedFromDayResult,
                        )

                        MfPhase.GAME_OVER -> {
                            LaunchedEffect(Unit) { sound?.playGameOver() }
                            MfGameOverScreen(
                                state = state,
                                onPlayAgain = viewModel::playAgain,
                                onExit = leaveAndExit,
                            )
                        }
                    }
                }
            }
        }

        // ---- ارتباط با میزبان قطع شد ----
        if (state.lostConnection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                contentAlignment = Alignment.Center
            ) {
                TicketCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    tilt = -1.5f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BobbingEmoji(emoji = "📴", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ارتباط با میزبان قطع شد!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "وای‌فای رو چک کنید؛ اگر میزبان برگشت، دوباره با همون اسم بپیوندید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        KButton(text = "باشه", onClick = leaveAndExit)
                    }
                }
            }
        }
    }
}
