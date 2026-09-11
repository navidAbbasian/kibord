package com.navidabbasian.kibord.games.hokm

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
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
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import com.navidabbasian.kibord.games.hokm.engine.MordabadiRules

/** ریشه‌ی بازی حکم: تنظیمات → آس‌کِشی → میز → برنده */
@Composable
fun HokmGame(
    onExitToHub: () -> Unit,
    viewModel: HokmViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val net by viewModel.net.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { sound?.stopBackgroundMusic() }
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                HokmSoundEvent.CARD -> sound?.playWordSkip()
                HokmSoundEvent.TRUMP -> {
                    sound?.playTurnStart()
                    sound?.vibrate(40)
                }
                HokmSoundEvent.TRICK_WON -> sound?.playCorrectWord()
                HokmSoundEvent.TRICK_LOST -> sound?.playDorNextTurn()
                HokmSoundEvent.HAND_WON -> {
                    sound?.playRoundEnd()
                    sound?.vibrate(80)
                }
                HokmSoundEvent.HAND_LOST -> sound?.playTimerEnd()
                HokmSoundEvent.MATCH_OVER -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
                HokmSoundEvent.YOUR_TURN -> sound?.vibrate(25)
            }
        }
    }

    val leaveToHub = {
        viewModel.backToSetup()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit,
            onConfirm = { pendingExit = false; leaveToHub() },
            onDismiss = { pendingExit = false },
        )
        PhaseTransition(key = state.stage) {
            when (state.stage) {
                HokmStage.Setup -> {
                    BackHandler { onExitToHub() }
                    HokmSetupScreen(state = state, viewModel = viewModel)
                }

                HokmStage.NetEntry -> {
                    BackHandler { viewModel.backFromNetEntry() }
                    NetEntryScreen(
                        net = net,
                        emoji = "🃏",
                        title = "حکم چند گوشی",
                        onNameChanged = viewModel::setMyName,
                        onToggleOnline = viewModel::setOnline,
                        onHost = viewModel::hostGame,
                        onJoin = viewModel::openJoin,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                        subtitle = "هر کدوم با گوشی خودتون — میزبان میز رو می‌چینه؛ در چهار نفره دوستِ دوم یارِ میزبانه و صندلی‌های خالی ربات می‌شن",
                        hostOptions = { HokmMatchOptions(state = state, viewModel = viewModel) },
                    )
                }

                HokmStage.NetJoin -> {
                    BackHandler { viewModel.backFromJoin() }
                    NetJoinScreen(
                        net = net,
                        emoji = "🃏",
                        onJoin = { g -> viewModel.joinLan(g.address, g.port) },
                        onManualJoin = { address -> viewModel.joinLan(address, LanServer.BASE_PORT) },
                        onJoinOnline = viewModel::joinOnline,
                        hostLabel = { "حکمِ $it" },
                    )
                }

                HokmStage.NetLobby -> {
                    BackHandler { if (net.isHost) viewModel.cancelHosting() else viewModel.backFromJoin() }
                    NetLobbyScreen(
                        net = net,
                        emoji = "🃏",
                        title = "حکم",
                        seats = state.netSeats.mapIndexed { i, seat ->
                            LobbySeat(
                                name = seat.name,
                                kind = when (seat.kind) {
                                    HokmNetSeatKind.HOST -> LobbySeatKind.HOST
                                    HokmNetSeatKind.GUEST -> LobbySeatKind.GUEST
                                    HokmNetSeatKind.BOT -> LobbySeatKind.BOT
                                    HokmNetSeatKind.EMPTY -> LobbySeatKind.EMPTY
                                },
                                connected = seat.connected,
                                tag = if (state.variant == HokmVariant.FOUR) (if (i % 2 == 0) "تیم ۱" else "تیم ۲") else null,
                            )
                        },
                        isHost = net.isHost,
                        canStart = state.netCanStart,
                        onStart = viewModel::startNetGame,
                        summary = when (state.variant) {
                            HokmVariant.FOUR -> "چهار نفره · تا ${state.target.toPersianDigits()} امتیاز"
                            HokmVariant.THREE -> "مردابادی · سقف بدهی ${state.debtLimit.toPersianDigits()}"
                            HokmVariant.TWO -> "دو نفره · تا ${state.target.toPersianDigits()} امتیاز"
                        },
                    )
                }

                HokmStage.AceDeal -> {
                    BackHandler { pendingExit = true }
                    HokmAceDealScreen(state = state, onSkip = viewModel::skipAceDeal)
                }

                HokmStage.Playing -> {
                    BackHandler { pendingExit = true }
                    HokmPlayScreen(state = state, viewModel = viewModel)
                }

                HokmStage.MatchOver -> {
                    BackHandler { leaveToHub() }
                    HokmWinnerScreen(
                        state = state,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = leaveToHub,
                    )
                }
            }
        }
        NetConnectionOverlays(
            net = net,
            showAwayBanner = state.stage == HokmStage.Playing || state.stage == HokmStage.AceDeal,
            onReconnect = viewModel::reconnectOnline,
            onLeave = leaveToHub,
        )
    }
}

// ---------------------------------------------------------------- تنظیمات

@Composable
private fun HokmSetupScreen(state: HokmUiState, viewModel: HokmViewModel) {
    val accent = LocalGameAccent.current
    val teamColors = kiExtras.teamColors
    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "hokm", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(28.dp))
            BobbingEmoji(emoji = "🃏", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "حکم")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "حاکم حکم می‌کنه، هفت دست می‌بره!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            BlobTextField(
                value = state.playerName,
                onValueChange = viewModel::setPlayerName,
                placeholder = "اسمت چیه؟",
                badge = "😎",
                tilt = -1f,
            )

            Spacer(modifier = Modifier.height(22.dp))
            HokmMatchOptions(state = state, viewModel = viewModel)
            Spacer(modifier = Modifier.height(18.dp))
            NetModeCard(onClick = viewModel::chooseNetworkMode)
            Spacer(modifier = Modifier.height(30.dp))
            KButton(text = "بزن بریم! 🃏", onClick = viewModel::startMatch)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


/** گزینه‌های مسابقه: روش (۴/۳/۲ نفره)، هدف یا سقف بدهی — هم در تنظیمات محلی، هم برای میزبان چندگوشی */
@Composable
internal fun HokmMatchOptions(state: HokmUiState, viewModel: HokmViewModel) {
    val accent = LocalGameAccent.current
    val teamColors = kiExtras.teamColors
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("چند نفره؟")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            HokmVariant.entries.forEachIndexed { i, v ->
                val selected = state.variant == v
                ChoiceBubble(
                    main = v.playerCount.toPersianDigits(),
                    sub = "نفره",
                    emoji = when (v) {
                        HokmVariant.FOUR -> "👥"
                        HokmVariant.THREE -> "🔺"
                        HokmVariant.TWO -> "🤝"
                    },
                    size = 104.dp,
                    mainFontSize = 30.sp,
                    accent = if (selected) accent else teamColors.teamColorFor(i + 2).copy(alpha = 0.55f),
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.3f,
                    modifier = Modifier.offset(y = if (i == 1) 12.dp else 0.dp),
                    onClick = { viewModel.setVariant(v) },
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (state.variant) {
                HokmVariant.FOUR -> "تو و ${HokmUiState.BOT_NAMES[1]} یه تیم، ${HokmUiState.BOT_NAMES[0]} و ${HokmUiState.BOT_NAMES[2]} تیم مقابل"
                HokmVariant.THREE -> "مردابادی! سهمیه‌های ۳/۵/۹ — یه دو از بازی بیرونه، طلب و بدهی رد و بدل می‌شه"
                HokmVariant.TWO -> "تک به تک با ${HokmUiState.BOT_NAMES[0]} — نصف دسته کنار می‌مونه"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(22.dp))
        if (state.variant == HokmVariant.THREE) {
            // مردابادی پایانش حذفی است: به جای امتیاز، سقف بدهی انتخاب می‌شود
            SectionLabel("سقف بدهی چند؟")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                MordabadiRules.DEBT_LIMITS.forEachIndexed { i, t ->
                    val selected = state.debtLimit == t
                    ChoiceBubble(
                        main = t.toPersianDigits(),
                        sub = "بدهی",
                        size = 96.dp,
                        mainFontSize = 28.sp,
                        accent = if (selected) accent else teamColors.teamColorFor(i + 5).copy(alpha = 0.55f),
                        tilt = if (i % 2 == 0) 3f else -3f,
                        phase = i * 1.1f + 0.5f,
                        modifier = Modifier.offset(y = if (i == 1) 12.dp else 0.dp),
                        onClick = { viewModel.setDebtLimit(t) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "جمع بدهیت به سقف برسه حذف می‌شی — دو بازمانده دوئل می‌کنن!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            SectionLabel("تا چند امتیاز؟")
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            ) {
                listOf(3, 5, 7).forEachIndexed { i, t ->
                    val selected = state.target == t
                    ChoiceBubble(
                        main = t.toPersianDigits(),
                        sub = "امتیاز",
                        size = 96.dp,
                        mainFontSize = 28.sp,
                        accent = if (selected) accent else teamColors.teamColorFor(i + 5).copy(alpha = 0.55f),
                        tilt = if (i % 2 == 0) 3f else -3f,
                        phase = i * 1.1f + 0.5f,
                        modifier = Modifier.offset(y = if (i == 1) 12.dp else 0.dp),
                        onClick = { viewModel.setTarget(t) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

// ---------------------------------------------------------------- آس‌کِشی

/** آس‌کِشی: کارت‌ها یکی‌یکی جلوی هر نفر رو می‌شود؛ اولین آس حاکم را تعیین می‌کند */
@Composable
private fun HokmAceDealScreen(state: HokmUiState, onSkip: () -> Unit) {
    val deal = state.aceDeal ?: return
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val revealed = deal.cards.take(state.aceRevealed)
    val done = state.aceRevealed >= deal.cards.size
    val n = state.variant.playerCount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSkip() },
    ) {
        GameHelpButton(gameId = "hokm", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            BobbingEmoji(emoji = "👑", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(8.dp))
            StickerTitle(text = "کی حاکم می‌شه؟", fontSize = 26.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "کارت‌ها رو می‌شه — اولین آس، حاکم!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (seat in 0 until n) {
                    val mine = revealed.filter { it.seat == seat }
                    val last = mine.lastOrNull()
                    val isHakem = done && deal.hakem == seat
                    Column(
                        modifier = Modifier.width(84.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier.height(112.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            // کارت‌های قبلی کمی زیر هم
                            mine.dropLast(1).takeLast(3).forEachIndexed { i, tc ->
                                PlayingCard(
                                    card = tc.card,
                                    width = 60.dp,
                                    modifier = Modifier.offset(y = (-(3 - i) * 6).dp),
                                    rotation = (i - 1) * 3f,
                                )
                            }
                            if (last != null) {
                                androidx.compose.runtime.key(last.card.id) {
                                    val pop = remember { Animatable(0.4f) }
                                    LaunchedEffect(Unit) {
                                        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                    PlayingCard(
                                        card = last.card,
                                        width = 64.dp,
                                        highlighted = isHakem,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = pop.value
                                            scaleY = pop.value
                                            alpha = pop.value.coerceIn(0f, 1f)
                                        },
                                    )
                                }
                            } else {
                                PlayingCard(card = null, faceUp = false, width = 60.dp, dimmed = true)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = (if (isHakem) "👑 " else "") + state.nameOf(seat),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isHakem) accent else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                        Text(
                            text = "${mine.size.toPersianDigits()} کارت",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            AnimatedVisibility(visible = done, enter = fadeIn() + scaleIn(initialScale = 0.8f)) {
                GlassCard(strong = true, borderColor = accent.copy(alpha = 0.6f)) {
                    Text(
                        text = "${state.nameOf(deal.hakem)} آس آورد — حاکم شد! 👑",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
            if (!done) {
                Text(
                    text = "برای رد شدن ضربه بزن",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        // نشانه‌ی کوچک گوشه: تعداد کارت‌های روشده
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .background(extras.glass, CircleShape)
                .border(1.dp, extras.glassBorder, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "${state.aceRevealed.toPersianDigits()} کارت رو شد",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- برنده

@Composable
private fun HokmWinnerScreen(
    state: HokmUiState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val game = state.game ?: return
    val mordabadi = state.mordabadiGame
    val duelSeats = state.duelSeats
    if (state.variant == HokmVariant.THREE && mordabadi != null && duelSeats != null) {
        MordabadiWinnerScreen(
            state = state,
            duel = game,
            final = mordabadi,
            duelSeats = duelSeats,
            onPlayAgain = onPlayAgain,
            onExitToHub = onExitToHub,
        )
        return
    }
    val winnerTeam = game.matchWinnerTeam ?: return
    val humanWon = winnerTeam == state.humanTeam
    val variant = state.variant

    val winnerText = when {
        variant == HokmVariant.FOUR && humanWon -> "تیم شما برد!"
        variant == HokmVariant.FOUR -> "تیم ${state.nameOf(1)} و ${state.nameOf(3)} برد!"
        humanWon -> "${state.nameOf(state.mySeatInGame)} برد!"
        else -> "${state.nameOf(winnerTeam)} برد!"
    }
    val winnerNames = when {
        humanWon -> listOf(state.nameOf(state.mySeatInGame))
        variant == HokmVariant.FOUR -> listOf(state.nameOf(1), state.nameOf(3))
        else -> listOf(state.nameOf(winnerTeam))
    }
    val scoreLines = (0 until variant.teamCount).map { t ->
        val label = when (variant) {
            HokmVariant.FOUR -> if (t == 0) "${state.nameOf(0)} و ${state.nameOf(2)}" else "${state.nameOf(1)} و ${state.nameOf(3)}"
            else -> state.nameOf(t)
        }
        label to "${game.scores[t].toPersianDigits()} امتیاز"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (humanWon) ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            BobbingEmoji(emoji = if (humanWon) "🏆" else "🃏", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = winnerText, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (humanWon) "دمت گرم! حکمِ تمیزی بود 🎉" else "این بار مالِ اونا بود — تلافی کن!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    scoreLines.forEachIndexed { i, (name, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (if (i == winnerTeam) "🏆 " else "") + name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = score,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (i == winnerTeam) LocalGameAccent.current else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${game.handNumber.toPersianDigits()} دست بازی شد — تا ${state.target.toPersianDigits()} امتیاز",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            ShareWinButton(
                gameId = "hokm",
                gameTitle = "حکم",
                gameEmoji = "🃏",
                winnerText = winnerText,
                scoreLines = scoreLines,
                winnerNames = winnerNames,
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** صفحه‌ی برنده‌ی مردابادی: نتیجه‌ی دوئل + ترازها و جمعِ بدهی‌های نهایی */
@Composable
private fun MordabadiWinnerScreen(
    state: HokmUiState,
    duel: HokmState,
    final: HokmState,
    duelSeats: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val duelWinnerTeam = duel.matchWinnerTeam ?: return
    val winnerSeat = duelSeats.getOrElse(duelWinnerTeam) { 0 }
    val loserSeat = duelSeats.getOrElse(1 - duelWinnerTeam) { 1 }
    val humanWon = winnerSeat == state.mySeat
    val eliminated = final.lastResult?.eliminatedSeat
    val duelTricks = duel.lastResult?.teamTricks ?: duel.teamTricksAll

    val winnerText = "${state.mordabadiNameOf(winnerSeat)} قهرمان مردابادی شد!"
    val scoreLines = (0..2).map { seat ->
        val label = state.mordabadiNameOf(seat) + if (eliminated == seat) " (حذف 🚫)" else ""
        val balance = final.balances.getOrElse(seat) { 0 }
        val balanceText = when {
            balance > 0 -> "طلب ${balance.toPersianDigits()}"
            balance < 0 -> "بدهی ${(-balance).toPersianDigits()}"
            else -> "تراز ۰"
        }
        label to "$balanceText • جمع بدهی ${final.totalDebts.getOrElse(seat) { 0 }.toPersianDigits()}"
    } + listOf(
        "دوئل نهایی ⚔️" to "${state.mordabadiNameOf(winnerSeat)} ${duelTricks.getOrElse(duelWinnerTeam) { 0 }.toPersianDigits()}" +
            " – ${duelTricks.getOrElse(1 - duelWinnerTeam) { 0 }.toPersianDigits()} ${state.mordabadiNameOf(loserSeat)}",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (humanWon) ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            BobbingEmoji(emoji = if (humanWon) "🏆" else "🃏", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = winnerText, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    humanWon -> "دمت گرم! هم حساب و کتابت جمع بود هم دوئل رو بردی 🎉"
                    eliminated == state.mySeat -> "بدهی امونت نداد و حذف شدی — دفعه‌ی بعد سهمیه‌تو بگیر!"
                    else -> "تا دوئل رفتی ولی آخرش نشد — تلافی کن!"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    scoreLines.forEachIndexed { i, (name, score) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (if (i == winnerSeat) "🏆 " else "") + name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = score,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = if (i == winnerSeat) LocalGameAccent.current else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${final.handNumber.toPersianDigits()} دست مردابادی — سقف بدهی ${final.debtLimit.toPersianDigits()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            ShareWinButton(
                gameId = "hokm",
                gameTitle = "حکم مردابادی",
                gameEmoji = "🃏",
                winnerText = winnerText,
                scoreLines = scoreLines,
                winnerNames = listOf(state.mordabadiNameOf(winnerSeat)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** رنگ هر تیم روی میز: تیم بازیکن با اکسنت بازی، بقیه از پالت تیم‌ها */
@Composable
internal fun hokmTeamColor(team: Int, humanTeam: Int): Color {
    val accent = LocalGameAccent.current
    return if (team == humanTeam) accent else kiExtras.teamColors.teamColorFor(team + 3)
}

/** مستطیل گرد شیشه‌ای کوچک برای چیپ‌ها */
internal val ChipShape = RoundedCornerShape(14.dp)
