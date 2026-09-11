package com.navidabbasian.kibord.games.uno

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
import com.navidabbasian.kibord.games.uno.ui.UnoMatchOptions
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.games.uno.ui.UnoSetupScreen
import com.navidabbasian.kibord.games.uno.ui.UnoTableScreen
import com.navidabbasian.kibord.games.uno.ui.UnoWinnerScreen

/** ریشه‌ی بازی اونو: تنظیمات → میز (سه مدل) → برنده */
@Composable
fun UnoGame(
    onExitToHub: () -> Unit,
    viewModel: UnoViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val net by viewModel.net.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }
    val leaveAndExit = { viewModel.backToSetup(); onExitToHub() }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                UnoSoundEvent.PLAY -> sound?.playButtonClick()
                UnoSoundEvent.DRAW -> sound?.playWordSkip()
                UnoSoundEvent.HIT -> {
                    sound?.playDorNextTurn()
                    sound?.vibrate(150)
                }
                UnoSoundEvent.UNO_CALL -> sound?.playCorrectWord()
                UnoSoundEvent.CAUGHT -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                UnoSoundEvent.ROUND_END -> sound?.playRoundEnd()
                UnoSoundEvent.MATCH_WON -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
                UnoSoundEvent.MATCH_LOST -> sound?.playTimerEnd()
            }
        }
    }

    LaunchedEffect(state.stage) {
        if (state.stage != UnoStage.Playing) sound?.switchMusic(MusicTrack.HUB) else sound?.stopBackgroundMusic()
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
            state.stage == UnoStage.NetEntry -> "entry"
            state.stage == UnoStage.NetJoin -> "join"
            state.stage == UnoStage.NetLobby -> "lobby"
            state.stage == UnoStage.Setup || game == null -> "setup"
            state.showFinal && game.matchWinner != null -> "winner"
            else -> "table"
        }
        PhaseTransition(key = screen) {
            when (screen) {
                "setup" -> {
                    BackHandler { onExitToHub() }
                    UnoSetupScreen(
                        state = state,
                        onName = viewModel::setPlayerName,
                        onMode = viewModel::setMode,
                        onPlayers = viewModel::setPlayers,
                        onTarget = viewModel::setTarget,
                        onStart = viewModel::startMatch,
                        onNetwork = viewModel::chooseNetworkMode,
                    )
                }

                "entry" -> {
                    BackHandler { viewModel.backFromNetEntry() }
                    NetEntryScreen(
                        net = net,
                        emoji = "🌈",
                        title = "اونو چند گوشی",
                        onNameChanged = viewModel::setMyName,
                        onToggleOnline = viewModel::setOnline,
                        onHost = viewModel::hostGame,
                        onJoin = viewModel::openJoin,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                        subtitle = "هر کدوم با گوشی خودتون — میزبان میز رو می‌چینه، دوستات می‌پیوندن؛ صندلی‌های خالی ربات می‌شن",
                        hostOptions = {
                            UnoMatchOptions(
                                state = state,
                                onMode = viewModel::setMode,
                                onPlayers = viewModel::setPlayers,
                                onTarget = viewModel::setTarget,
                            )
                        },
                    )
                }

                "join" -> {
                    BackHandler { viewModel.backFromJoin() }
                    NetJoinScreen(
                        net = net,
                        emoji = "🌈",
                        onJoin = { g -> viewModel.joinLan(g.address, g.port) },
                        onManualJoin = { address -> viewModel.joinLan(address, LanServer.BASE_PORT) },
                        onJoinOnline = viewModel::joinOnline,
                        hostLabel = { "اونوی $it" },
                    )
                }

                "lobby" -> {
                    BackHandler { if (net.isHost) viewModel.cancelHosting() else viewModel.backFromJoin() }
                    NetLobbyScreen(
                        net = net,
                        emoji = "🌈",
                        title = "اونو",
                        seats = state.netSeats.map { seat ->
                            LobbySeat(
                                name = seat.name,
                                kind = when (seat.kind) {
                                    UnoNetSeatKind.HOST -> LobbySeatKind.HOST
                                    UnoNetSeatKind.GUEST -> LobbySeatKind.GUEST
                                    UnoNetSeatKind.BOT -> LobbySeatKind.BOT
                                    UnoNetSeatKind.EMPTY -> LobbySeatKind.EMPTY
                                },
                                connected = seat.connected,
                            )
                        },
                        isHost = net.isHost,
                        canStart = state.netCanStart,
                        onStart = viewModel::startNetGame,
                        summary = "${state.players.toPersianDigits()} نفره · " + when (state.mode) {
                            UnoMode.CLASSIC -> "کلاسیک"
                            UnoMode.SEVEN_ZERO -> "هفت-صفر"
                            UnoMode.MERCILESS -> "بی‌رحم"
                        } + " · " + if (state.target == 0) "تک‌دست" else "تا ${state.target.toPersianDigits()} امتیاز",
                    )
                }

                "winner" -> {
                    BackHandler { leaveAndExit() }
                    UnoWinnerScreen(
                        state = state,
                        game = game!!,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = leaveAndExit,
                    )
                }

                else -> {
                    BackHandler { pendingExit = true }
                    UnoTableScreen(state = state, game = game!!, viewModel = viewModel)
                }
            }
        }
        NetConnectionOverlays(
            net = net,
            showAwayBanner = state.stage == UnoStage.Playing,
            onReconnect = viewModel::reconnectOnline,
            onLeave = leaveAndExit,
        )
    }
}
