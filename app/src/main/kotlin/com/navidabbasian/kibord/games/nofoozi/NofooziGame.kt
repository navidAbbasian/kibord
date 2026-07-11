package com.navidabbasian.kibord.games.nofoozi

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
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.games.nofoozi.model.NfPhase
import com.navidabbasian.kibord.games.nofoozi.ui.NfDiscussionScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfEntryScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfJoinScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfLobbyScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfRevealScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfRoundResultScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfVoteScreen
import com.navidabbasian.kibord.games.nofoozi.ui.NfWinnerScreen
import com.navidabbasian.kibord.games.nofoozi.viewmodel.NfLocalScreen
import com.navidabbasian.kibord.games.nofoozi.viewmodel.NofooziViewModel

/** ریشه‌ی کلمه‌ی نفوذی — بازی چندگوشی روی شبکه‌ی محلی */
@Composable
fun NofooziGame(
    onExitToHub: () -> Unit,
    viewModel: NofooziViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    LaunchedEffect(state.localScreen, snapshot.phase) {
        val track = when {
            state.localScreen != NfLocalScreen.IN_GAME -> MusicTrack.HUB
            snapshot.phase == NfPhase.LOBBY -> MusicTrack.HUB
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
        PhaseTransition(key = state.localScreen to snapshot.phase) {
            when (state.localScreen) {
                NfLocalScreen.ENTRY -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    NfEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onHost = viewModel::startHosting,
                        onJoin = viewModel::openJoinScreen,
                    )
                }

                NfLocalScreen.JOIN -> {
                    BackHandler { viewModel.backToEntryFromJoin() }
                    NfJoinScreen(
                        state = state,
                        onJoin = { game -> viewModel.joinGame(game.address, game.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                    )
                }

                NfLocalScreen.IN_GAME -> {
                    BackHandler { pendingExit = { leaveAndExit() } }
                    when (snapshot.phase) {
                        NfPhase.LOBBY -> NfLobbyScreen(
                            state = state,
                            onTotalRounds = viewModel::setTotalRounds,
                            onStart = viewModel::startGame,
                        )

                        NfPhase.REVEAL -> NfRevealScreen(
                            state = state,
                            onSeen = viewModel::markSeen,
                        )

                        NfPhase.DISCUSSION -> NfDiscussionScreen(
                            state = state,
                            onStartVoting = viewModel::startVoting,
                        )

                        NfPhase.VOTE -> NfVoteScreen(
                            state = state,
                            onVote = viewModel::vote,
                        )

                        NfPhase.ROUND_RESULT -> NfRoundResultScreen(
                            state = state,
                            onProceed = viewModel::proceedFromResult,
                        )

                        NfPhase.GAME_OVER -> {
                            LaunchedEffect(Unit) { sound?.playGameOver() }
                            NfWinnerScreen(
                                state = state,
                                winners = viewModel.winners(),
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
