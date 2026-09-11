package com.navidabbasian.kibord.games.shelem

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
import com.navidabbasian.kibord.core.net.lan.LanServer
import com.navidabbasian.kibord.core.ui.net.LobbySeat
import com.navidabbasian.kibord.core.ui.net.LobbySeatKind
import com.navidabbasian.kibord.core.ui.net.NetConnectionOverlays
import com.navidabbasian.kibord.core.ui.net.NetEntryScreen
import com.navidabbasian.kibord.core.ui.net.NetJoinScreen
import com.navidabbasian.kibord.core.ui.net.NetLobbyScreen
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.ui.ShelemMatchOptions
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.ui.ShelemSetupScreen
import com.navidabbasian.kibord.games.shelem.ui.ShelemTableScreen
import com.navidabbasian.kibord.games.shelem.ui.ShelemWinnerScreen

/** ریشه‌ی بازی شلم: تنظیمات → میز (شرط، ویدو، حکم، ۱۲ دست) → برنده */
@Composable
fun ShelemGame(
    onExitToHub: () -> Unit,
    viewModel: ShelemViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val net by viewModel.net.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }
    val leaveAndExit = { viewModel.backToSetup(); onExitToHub() }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                ShelemSoundEvent.DEAL -> sound?.playRoundStart()
                ShelemSoundEvent.BID -> sound?.playTurnStart()
                ShelemSoundEvent.PASS -> sound?.playWordSkip()
                ShelemSoundEvent.CARD -> sound?.playButtonClick()
                ShelemSoundEvent.TRICK_WON -> { sound?.playCorrectWord(); sound?.vibrate(30) }
                ShelemSoundEvent.TRICK_LOST -> sound?.playDorNextTurn()
                ShelemSoundEvent.HAND_END -> sound?.playRoundEnd()
                ShelemSoundEvent.MATCH_WON -> { sound?.playGameOver(); sound?.vibrate(200) }
                ShelemSoundEvent.MATCH_LOST -> sound?.playTimerEnd()
            }
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage != ShelemStage.Playing) sound?.switchMusic(MusicTrack.HUB) else sound?.stopBackgroundMusic()
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
            state.stage == ShelemStage.NetEntry -> "entry"
            state.stage == ShelemStage.NetJoin -> "join"
            state.stage == ShelemStage.NetLobby -> "lobby"
            state.stage == ShelemStage.Setup || game == null -> "setup"
            game.phase == ShelemPhase.MATCH_OVER && state.showFinal -> "winner"
            else -> "table"
        }
        PhaseTransition(key = screen) {
            when (screen) {
                "setup" -> {
                    BackHandler { onExitToHub() }
                    ShelemSetupScreen(
                        state = state,
                        onName = viewModel::setPlayerName,
                        onTarget = viewModel::setTarget,
                        onShelemBonus = viewModel::setShelemBonus,
                        onStart = viewModel::startMatch,
                        onNetwork = viewModel::chooseNetworkMode,
                    )
                }

                "entry" -> {
                    BackHandler { viewModel.backFromNetEntry() }
                    NetEntryScreen(
                        net = net,
                        emoji = "🂡",
                        title = "شلم چند گوشی",
                        onNameChanged = viewModel::setMyName,
                        onToggleOnline = viewModel::setOnline,
                        onHost = viewModel::hostGame,
                        onJoin = viewModel::openJoin,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                        subtitle = "هر کدوم با گوشی خودتون — میزبان میز رو می‌چینه؛ دوستِ دوم یارِ میزبانه و صندلی‌های خالی ربات می‌شن",
                        hostOptions = {
                            ShelemMatchOptions(state = state, onTarget = viewModel::setTarget, onShelemBonus = viewModel::setShelemBonus)
                        },
                    )
                }

                "join" -> {
                    BackHandler { viewModel.backFromJoin() }
                    NetJoinScreen(
                        net = net,
                        emoji = "🂡",
                        onJoin = { g -> viewModel.joinLan(g.address, g.port) },
                        onManualJoin = { address -> viewModel.joinLan(address, LanServer.BASE_PORT) },
                        onJoinOnline = viewModel::joinOnline,
                        hostLabel = { "شلمِ $it" },
                    )
                }

                "lobby" -> {
                    BackHandler { if (net.isHost) viewModel.cancelHosting() else viewModel.backFromJoin() }
                    NetLobbyScreen(
                        net = net,
                        emoji = "🂡",
                        title = "شلم",
                        seats = state.netSeats.mapIndexed { i, seat ->
                            LobbySeat(
                                name = seat.name,
                                kind = when (seat.kind) {
                                    ShelemNetSeatKind.HOST -> LobbySeatKind.HOST
                                    ShelemNetSeatKind.GUEST -> LobbySeatKind.GUEST
                                    ShelemNetSeatKind.BOT -> LobbySeatKind.BOT
                                    ShelemNetSeatKind.EMPTY -> LobbySeatKind.EMPTY
                                },
                                connected = seat.connected,
                                tag = if (i % 2 == 0) "تیم ۱" else "تیم ۲",
                            )
                        },
                        isHost = net.isHost,
                        canStart = state.netCanStart,
                        onStart = viewModel::startNetGame,
                        summary = "تا ${state.target.toPersianDigits()} امتیاز" + if (state.shelemBonus) " · با پاداش شلم" else "",
                    )
                }

                "winner" -> {
                    BackHandler { leaveAndExit() }
                    ShelemWinnerScreen(
                        state = state,
                        game = game!!,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = leaveAndExit,
                    )
                }

                else -> {
                    BackHandler { pendingExit = true }
                    ShelemTableScreen(state = state, game = game!!, viewModel = viewModel)
                }
            }
        }
        NetConnectionOverlays(
            net = net,
            showAwayBanner = state.stage == ShelemStage.Playing,
            onReconnect = viewModel::reconnectOnline,
            onLeave = leaveAndExit,
        )
    }
}
