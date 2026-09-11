package com.navidabbasian.kibord.games.ludo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.net.lan.LanServer
import com.navidabbasian.kibord.core.ui.net.LobbySeat
import com.navidabbasian.kibord.core.ui.net.LobbySeatKind
import com.navidabbasian.kibord.core.ui.net.NetConnectionOverlays
import com.navidabbasian.kibord.core.ui.net.NetEntryScreen
import com.navidabbasian.kibord.core.ui.net.NetJoinScreen
import com.navidabbasian.kibord.core.ui.net.NetLobbyScreen
import com.navidabbasian.kibord.core.ui.net.NetModeCard
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoEngine
import com.navidabbasian.kibord.games.ludo.engine.LudoPhase
import com.navidabbasian.kibord.games.ludo.engine.LudoSeatKind
import com.navidabbasian.kibord.games.ludo.engine.LudoState
import com.navidabbasian.kibord.games.ludo.ui.LudoBoard
import com.navidabbasian.kibord.games.ludo.ui.LudoDie
import com.navidabbasian.kibord.games.ludo.ui.paint

/** ریشه‌ی بازی منچ: تنظیم صندلی‌ها → بازی روی یک گوشی (با ربات) → صفحه‌ی برنده */
@Composable
fun LudoGame(
    onExitToHub: () -> Unit,
    viewModel: LudoViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val net by viewModel.net.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                LudoSoundEvent.DICE -> {
                    sound?.playDorNextTurn()
                    sound?.vibrate(35)
                }
                LudoSoundEvent.HOP -> sound?.playButtonClick()
                LudoSoundEvent.ENTER -> sound?.playTurnStart()
                LudoSoundEvent.CAPTURE -> {
                    sound?.playWordSkip()
                    sound?.vibrate(90)
                }
                LudoSoundEvent.GOAL -> sound?.playCorrectWord()
                LudoSoundEvent.SKIP -> sound?.playTimerEnd()
                LudoSoundEvent.TURN -> sound?.vibrate(18)
                LudoSoundEvent.BONUS -> sound?.playDorWordCorrect()
                LudoSoundEvent.WIN -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
            }
        }
    }
    LaunchedEffect(Unit) { sound?.stopBackgroundMusic() }

    val leaveAndExit = {
        viewModel.backToSetup()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit,
            onConfirm = { pendingExit = false; leaveAndExit() },
            onDismiss = { pendingExit = false },
        )
        val game = state.game
        PhaseTransition(key = state.stage to (game?.phase == LudoPhase.FINISHED)) {
            when {
                state.stage == LudoStage.Setup -> {
                    BackHandler { onExitToHub() }
                    LudoSetupScreen(state = state, viewModel = viewModel)
                }

                state.stage == LudoStage.NetEntry -> {
                    BackHandler { viewModel.backFromNetEntry() }
                    NetEntryScreen(
                        net = net,
                        emoji = "🎯",
                        title = "منچ چند گوشی",
                        onNameChanged = viewModel::setMyName,
                        onToggleOnline = viewModel::setOnline,
                        onHost = viewModel::hostGame,
                        onJoin = viewModel::openJoin,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                        subtitle = "هر کدوم با گوشی خودتون — میزبان قرمزه، دوستات به ترتیب سبز و زرد و آبی می‌شن؛ صندلی‌های خالی ربات",
                        hostOptions = {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LudoToggleRow(
                                    emoji = "🎲",
                                    label = "سه تا شش پشت هم = سوختن نوبت",
                                    checked = state.tripleSixRule,
                                    index = 0,
                                    onCheckedChange = { sound?.playButtonClick(); viewModel.setTripleSixRule(it) },
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LudoToggleRow(
                                    emoji = "⭐",
                                    label = "زدن مهره روی خانه‌ی شروع ممنوع",
                                    checked = state.safeStart,
                                    index = 1,
                                    onCheckedChange = { sound?.playButtonClick(); viewModel.setSafeStart(it) },
                                )
                            }
                        },
                    )
                }

                state.stage == LudoStage.NetJoin -> {
                    BackHandler { viewModel.backFromJoin() }
                    NetJoinScreen(
                        net = net,
                        emoji = "🎯",
                        onJoin = { g -> viewModel.joinLan(g.address, g.port) },
                        onManualJoin = { address -> viewModel.joinLan(address, LanServer.BASE_PORT) },
                        onJoinOnline = viewModel::joinOnline,
                        hostLabel = { "منچِ $it" },
                    )
                }

                state.stage == LudoStage.NetLobby -> {
                    BackHandler { if (net.isHost) viewModel.cancelHosting() else viewModel.backFromJoin() }
                    NetLobbyScreen(
                        net = net,
                        emoji = "🎯",
                        title = "منچ",
                        seats = LudoColor.entries.map { c ->
                            val seat = state.netSeats[c.ordinal]
                            LobbySeat(
                                name = seat.name,
                                kind = when (seat.kind) {
                                    LudoNetSeatKind.HOST -> LobbySeatKind.HOST
                                    LudoNetSeatKind.GUEST -> LobbySeatKind.GUEST
                                    LudoNetSeatKind.BOT -> LobbySeatKind.BOT
                                    LudoNetSeatKind.EMPTY -> LobbySeatKind.EMPTY
                                },
                                connected = seat.connected,
                                tag = "${ludoColorEmoji(c)} ${c.persianName}",
                            )
                        },
                        isHost = net.isHost,
                        canStart = state.netCanStart,
                        onStart = viewModel::startNetGame,
                        hint = if (net.isHost) "دست‌کم یکی از دوستات باید وصل باشه؛ صندلی خالی رو لمس کن تا ربات بشینه" else null,
                        onSeatTap = viewModel::toggleLobbySeat,
                    )
                }

                game == null -> {
                    BackHandler { onExitToHub() }
                    LudoSetupScreen(state = state, viewModel = viewModel)
                }

                game.phase == LudoPhase.FINISHED -> {
                    BackHandler { leaveAndExit() }
                    LudoWinnerScreen(
                        state = state,
                        game = game,
                        onContinue = viewModel::continueForOthers,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = leaveAndExit,
                    )
                }

                else -> {
                    BackHandler { pendingExit = true }
                    LudoPlayScreen(state = state, game = game, viewModel = viewModel)
                }
            }
        }
        NetConnectionOverlays(
            net = net,
            showAwayBanner = state.stage == LudoStage.Playing,
            onReconnect = viewModel::reconnectOnline,
            onLeave = leaveAndExit,
        )
    }
}

/** نقطه‌ی رنگی هر مهره برای لابی و برچسب‌ها */
private fun ludoColorEmoji(color: LudoColor): String = when (color) {
    LudoColor.RED -> "🔴"
    LudoColor.GREEN -> "🟢"
    LudoColor.YELLOW -> "🟡"
    LudoColor.BLUE -> "🔵"
}

// ---------------------------------------------------------------------------
// صفحه‌ی تنظیم
// ---------------------------------------------------------------------------

@Composable
private fun LudoSetupScreen(state: LudoUiState, viewModel: LudoViewModel) {
    val sound = LocalSoundManager.current
    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "ludo", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            BobbingEmoji(emoji = "🎯", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "منچ")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "برای هر رنگ بگو کی بازی می‌کنه: آدم، ربات یا خالی",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            NetModeCard(onClick = viewModel::chooseNetworkMode)
            Spacer(modifier = Modifier.height(18.dp))

            LudoColor.entries.forEach { color ->
                val cfg = state.seatConfigs[color.ordinal]
                LudoSeatCard(
                    color = color,
                    config = cfg,
                    index = color.ordinal,
                    onKind = { viewModel.setSeatKind(color, it) },
                    onName = { viewModel.setSeatName(color, it) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            LudoToggleRow(
                emoji = "🎲",
                label = "سه تا شش پشت هم = سوختن نوبت",
                checked = state.tripleSixRule,
                index = 0,
                onCheckedChange = { sound?.playButtonClick(); viewModel.setTripleSixRule(it) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            LudoToggleRow(
                emoji = "⭐",
                label = "زدن مهره روی خانه‌ی شروع ممنوع",
                checked = state.safeStart,
                index = 1,
                onCheckedChange = { sound?.playButtonClick(); viewModel.setSafeStart(it) },
            )

            Spacer(modifier = Modifier.height(22.dp))
            if (!state.canStart) {
                Text(
                    text = "دست‌کم دو تا صندلی باید پر باشه",
                    style = MaterialTheme.typography.labelLarge,
                    color = kiExtras.warning,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            KButton(
                text = "بزن بریم! 🎯",
                enabled = state.canStart,
                onClick = viewModel::startGame,
            )
            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

/** کارت یک صندلی: نقطه‌ی رنگ، اسم و انتخاب آدم/ربات/خالی */
@Composable
private fun LudoSeatCard(
    color: LudoColor,
    config: LudoSeatConfig,
    index: Int,
    onKind: (LudoSeatKind) -> Unit,
    onName: (String) -> Unit,
) {
    val paint = color.paint()
    val active = config.kind != LudoSeatKind.EMPTY
    val alpha by animateFloatAsState(targetValue = if (active) 1f else 0.55f, label = "seat_alpha")
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .animateContentSize(),
        strong = active,
        borderColor = if (active) paint.copy(alpha = 0.7f) else null,
        tilt = if (index % 2 == 0) -0.6f else 0.6f,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            Brush.radialGradient(listOf(lerp(paint, Color.White, 0.35f), paint)),
                            CircleShape,
                        )
                        .border(2.dp, lerp(paint, Color.Black, 0.35f), CircleShape),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = color.persianName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(44.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LudoKindChip("آدم 🙂", config.kind == LudoSeatKind.HUMAN, paint) { onKind(LudoSeatKind.HUMAN) }
                    LudoKindChip("ربات 🤖", config.kind == LudoSeatKind.BOT, paint) { onKind(LudoSeatKind.BOT) }
                    LudoKindChip("خالی", config.kind == LudoSeatKind.EMPTY, paint) { onKind(LudoSeatKind.EMPTY) }
                }
            }
            if (active) {
                Spacer(modifier = Modifier.height(10.dp))
                BlobTextField(
                    value = config.name,
                    onValueChange = onName,
                    placeholder = if (config.kind == LudoSeatKind.BOT) "ربات" else "بازیکن ${(index + 1).toPersianDigits()}",
                    color = paint,
                    badge = if (config.kind == LudoSeatKind.BOT) "🤖" else "🙂",
                    tilt = 0f,
                    phase = index * 1.3f,
                )
            }
        }
    }
}

@Composable
private fun RowScope.LudoKindChip(
    label: String,
    selected: Boolean,
    paint: Color,
    onClick: () -> Unit,
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(targetValue = if (selected) 1.04f else 1f, label = "chip_scale")
    Box(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (selected) paint else extras.glass, shape)
            .border(1.5.dp, if (selected) lerp(paint, Color.Black, 0.25f) else extras.glassBorder, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { sound?.playButtonClick(); onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** ردیف کلید روشن/خاموش شیشه‌ای */
@Composable
private fun LudoToggleRow(
    emoji: String,
    label: String,
    checked: Boolean,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val shape = RoundedCornerShape(20.dp)
    val knob by animateFloatAsState(targetValue = if (checked) 1f else 0f, label = "toggle_knob")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = if (index % 2 == 0) -0.5f else 0.5f }
            .background(if (checked) extras.glassStrong else extras.glass, shape)
            .border(1.5.dp, if (checked) accent.copy(alpha = 0.6f) else extras.glassBorder, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // کلید لغزنده‌ی کوچک
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(30.dp)
                .background(if (checked) accent else extras.glass, CircleShape)
                .border(1.5.dp, if (checked) Color.White.copy(alpha = 0.4f) else extras.glassBorder, CircleShape)
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(BiasAlignment(1f - 2f * knob, 0f))
                    .size(24.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Text(text = "✓", fontSize = 12.sp, fontWeight = FontWeight.Black, color = accent)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// صفحه‌ی بازی
// ---------------------------------------------------------------------------

@Composable
private fun LudoPlayScreen(state: LudoUiState, game: LudoState, viewModel: LudoViewModel) {
    val turnPaint = game.turn.paint()
    val turnName = state.displayName(game.turn)
    val isBotTurn = game.currentSeat.isBot
    /** نوبتِ آدمِ همین گوشی (در چندگوشی: فقط رنگ خودم) */
    val isHumanTurn = state.isLocalHumanTurn(game)
    val canRoll = isHumanTurn && game.phase == LudoPhase.ROLLING && !state.busy
    val turnDisconnected = state.isDisconnected(game.turn)

    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "ludo", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ---- نشان بازیکن‌ها ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                game.activeColors.forEach { c ->
                    LudoPlayerChip(
                        color = c,
                        name = state.displayName(c),
                        isBot = game.seat(c).isBot,
                        inGoal = game.inGoal(c),
                        rank = game.finished.indexOf(c).takeIf { it >= 0 }?.plus(1),
                        isTurn = c == game.turn,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- بنر نوبت ----
            LudoTurnBanner(paint = turnPaint, name = if (isHumanTurn && state.netMode) "$turnName (تو)" else turnName, isBot = isBotTurn)

            Spacer(modifier = Modifier.height(8.dp))

            // ---- صفحه ----
            LudoBoard(
                game = game,
                anim = state.anim,
                legalTokens = state.legalTokens,
                autoToken = state.autoToken,
                onTapToken = viewModel::tapToken,
            )

            // ---- تاس و پیام ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier.then(if (canRoll) Modifier.breathing(intensity = 0.06f, periodMs = 1400) else Modifier),
                    ) {
                        LudoDie(
                            value = game.die,
                            rollKey = state.rollNonce,
                            rolling = state.rolling,
                            glow = turnPaint,
                            enabled = canRoll,
                            onClick = viewModel::rollDice,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val hint = when {
                        turnDisconnected -> "📴 $turnName قطع شده — با همون اسم برگرده، بازی ادامه پیدا می‌کنه"
                        state.message != null -> state.message
                        state.rolling -> "تاس داره می‌چرخه…"
                        isBotTurn -> "$turnName داره فکر می‌کنه… 🤖"
                        !isHumanTurn -> "نوبت $turnName — منتظر بمون…"
                        game.phase == LudoPhase.ROLLING -> "نوبت $turnName — تاس بریز!"
                        state.autoToken != null -> "فقط یه حرکت داری — خودش می‌ره!"
                        else -> "یه مهره‌ی روشن رو لمس کن"
                    }
                    Text(
                        text = hint ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AnimatedVisibility(visible = isHumanTurn, enter = fadeIn(), exit = fadeOut()) {
                        KButton(
                            text = "تاس بریز 🎲",
                            enabled = canRoll,
                            accent = turnPaint,
                            onClick = viewModel::rollDice,
                            modifier = Modifier.padding(horizontal = 36.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** بنر رنگیِ نوبت — برای بازی دست‌به‌دست روی یک گوشی */
@Composable
private fun LudoTurnBanner(paint: Color, name: String, isBot: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .breathing(intensity = 0.02f, periodMs = 2200)
            .background(
                Brush.horizontalGradient(listOf(lerp(paint, Color.White, 0.15f), paint)),
                shape,
            )
            .border(2.dp, Color.White.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (isBot) "نوبت $name 🤖" else "نوبت $name",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** نشان کوچک هر بازیکن: رنگ، اسم، تعداد مهره‌های رسیده و رتبه */
@Composable
private fun LudoPlayerChip(
    color: LudoColor,
    name: String,
    isBot: Boolean,
    inGoal: Int,
    rank: Int?,
    isTurn: Boolean,
    modifier: Modifier = Modifier,
) {
    val paint = color.paint()
    val extras = kiExtras
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(targetValue = if (isTurn) 1.04f else 1f, label = "chip_turn")
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (isTurn) paint.copy(alpha = 0.32f) else extras.glass, shape)
            .border(if (isTurn) 2.dp else 1.dp, if (isTurn) paint else extras.glassBorder, shape)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(paint, CircleShape),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = when {
                rank != null -> "🏆 رتبه‌ی ${rank.toPersianDigits()}"
                else -> "🏁 ${inGoal.toPersianDigits()} از ۴" + if (isBot) " 🤖" else ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// صفحه‌ی برنده
// ---------------------------------------------------------------------------

@Composable
private fun LudoWinnerScreen(
    state: LudoUiState,
    game: LudoState,
    onContinue: () -> Unit,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val ranking = remember(game) { LudoEngine.ranking(game) }
    val winner = ranking.first()
    val winnerName = state.displayName(winner)
    val justFinished = game.finished.lastOrNull() ?: winner
    val justFinishedName = state.displayName(justFinished)
    val canContinue = !game.gameOver
    val remaining = game.playingColors.size

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            BobbingEmoji(emoji = if (justFinished == winner) "🏆" else "🎉", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(
                text = if (justFinished == winner) "$winnerName برد!" else "$justFinishedName رسید!",
                accent = justFinished.paint(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (justFinished == winner) {
                    "هر چهار مهره رو رسوند خونه — دمش گرم! 🎯"
                } else {
                    "رتبه‌ی ${game.finished.size.toPersianDigits()} مال $justFinishedName شد"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            TicketCard(modifier = Modifier.fillMaxWidth(), accent = winner.paint(), golden = true) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text(
                        text = "رده‌بندی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ranking.forEachIndexed { i, c ->
                        val done = c in game.finished
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (i) {
                                    0 -> "🥇"
                                    1 -> "🥈"
                                    2 -> "🥉"
                                    else -> "🎲"
                                },
                                fontSize = 20.sp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(c.paint(), CircleShape),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = state.displayName(c),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (done) "رسید ✅" else "${game.inGoal(c).toPersianDigits()} از ۴ مهره",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            // پز دادن و ثبت آمار فقط یک بار: همان لحظه‌ای که برنده مشخص می‌شود
            if (game.finished.size <= 1) {
                ShareWinButton(
                    gameId = "ludo",
                    gameTitle = "منچ",
                    gameEmoji = "🎯",
                    winnerText = winnerName,
                    scoreLines = ranking.mapIndexed { i, c ->
                        state.displayName(c) to if (c in game.finished) "رتبه‌ی ${(i + 1).toPersianDigits()}" else "${game.inGoal(c).toPersianDigits()} مهره رسیده"
                    },
                    winnerNames = listOf(winnerName),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (canContinue && remaining >= 2) {
                KButton(text = "ادامه بازی برای بقیه 🎲", onClick = onContinue)
                Spacer(modifier = Modifier.height(10.dp))
                KButton(text = "دوباره از اول 🔁", style = KButtonStyle.Glass, onClick = onPlayAgain)
            } else {
                KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            }
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
