package com.navidabbasian.kibord.games.esmfamil

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.games.esmfamil.model.EfPhase
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfCountdownScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfEntryScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfJoinScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfJudgeScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfLetterScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfLobbyScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfPlayScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfReviewScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfRoundResultScreen
import com.navidabbasian.kibord.games.esmfamil.ui.screens.EfWinnerScreen
import com.navidabbasian.kibord.games.esmfamil.viewmodel.EfLocalScreen
import com.navidabbasian.kibord.games.esmfamil.viewmodel.EsmFamilViewModel

/** ریشه‌ی اسم فامیل — بازی چندگوشی روی شبکه‌ی محلی */
@Composable
fun EsmFamilGame(
    onExitToHub: () -> Unit,
    viewModel: EsmFamilViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    LaunchedEffect(state.localScreen, snapshot.phase) {
        val track = when {
            state.localScreen != EfLocalScreen.IN_GAME -> MusicTrack.HUB
            snapshot.phase == EfPhase.LOBBY -> MusicTrack.HUB
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
        if (state.localScreen != EfLocalScreen.IN_GAME || state.snapshot.phase == EfPhase.LOBBY) {
            GameHelpButton(gameId = "esm_famil", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.localScreen to snapshot.phase::class) {
            when (state.localScreen) {
                EfLocalScreen.ENTRY -> {
                    BackHandler { onExitToHub() }
                    EfEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onHost = viewModel::startHosting,
                        onJoin = viewModel::openJoinScreen,
                    )
                }

                EfLocalScreen.JOIN -> {
                    BackHandler { viewModel.backToEntryFromJoin() }
                    EfJoinScreen(
                        state = state,
                        onJoin = { game -> viewModel.joinGame(game.address, game.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                    )
                }

                EfLocalScreen.IN_GAME -> {
                    BackHandler { pendingExit = { leaveAndExit() } }
                    when (snapshot.phase) {
                        EfPhase.LOBBY -> EfLobbyScreen(
                            state = state,
                            onToggleTopic = viewModel::toggleTopic,
                            onAddTopic = viewModel::addCustomTopic,
                            onRoundSeconds = viewModel::setRoundSeconds,
                            onTotalRounds = viewModel::setTotalRounds,
                            onStart = viewModel::startGame,
                        )

                        EfPhase.LETTER_PICK -> EfLetterScreen(
                            state = state,
                            onPickLetter = viewModel::pickLetter,
                        )

                        EfPhase.COUNTDOWN -> EfCountdownScreen(state = state)

                        EfPhase.PLAYING -> EfPlayScreen(
                            state = state,
                            onAnswerChanged = viewModel::updateAnswer,
                            onStop = {
                                sound?.playRoundEnd()
                                viewModel.pressStop()
                            },
                        )

                        EfPhase.REVIEW -> EfReviewScreen(
                            state = state,
                            onVote = viewModel::voteReject,
                            onDone = viewModel::markReviewDone,
                        )

                        EfPhase.JUDGE -> EfJudgeScreen(
                            state = state,
                            onSetScore = viewModel::judgeSetScore,
                            onProceed = viewModel::proceedFromJudge,
                        )

                        EfPhase.ROUND_RESULT -> EfRoundResultScreen(
                            state = state,
                            onProceed = viewModel::proceedFromResult,
                        )

                        EfPhase.GAME_OVER -> {
                            LaunchedEffect(Unit) { sound?.playGameOver() }
                            EfWinnerScreen(
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
