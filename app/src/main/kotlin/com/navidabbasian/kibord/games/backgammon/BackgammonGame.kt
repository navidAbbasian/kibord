package com.navidabbasian.kibord.games.backgammon

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.net.online.StoredOnlineRoom
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.OnlineIdentityField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
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
import com.navidabbasian.kibord.games.backgammon.engine.BgGameEnd
import com.navidabbasian.kibord.games.backgammon.engine.BgMatch
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.net.BgDiscoveredGame

/** اسم فارسی هر روش برای تیترها و لابی */
private fun variantName(v: BgVariant?): String = when (v) {
    BgVariant.DUTCH -> "تخته‌نرد هلندی"
    BgVariant.HYPER -> "هایپرگامون"
    BgVariant.IRANI -> "تخته‌نرد ایرانی"
    else -> "تخته‌نرد کلاسیک"
}

/** اسم فارسی نتیجه: تکی، مارس، مارس کامل — در ایرانی امتیاز ۳ سگ‌مارس است */
private fun resultName(score: Int, variant: BgVariant?): String = when (score) {
    3 -> if (variant == BgVariant.IRANI) "سگ‌مارس" else "مارس کامل"
    2 -> "مارس"
    else -> "تکی"
}

/** شرح پایان یک دست: چطور تمام شد و چند امتیاز داشت */
private fun bgGameEndLabel(match: BgMatch, game: BgState): String {
    val points = if (match.lastGamePoints > 0) match.lastGamePoints else game.resultScore
    val how = when (match.lastGameEnd) {
        BgGameEnd.DROP -> "حریف دوبل رو رد کرد"
        BgGameEnd.RESIGN -> "حریف تسلیم شد"
        BgGameEnd.TIMEOUT -> "وقت حریف تموم شد"
        BgGameEnd.BEAR_OFF -> resultName(game.resultScore, game.rules.variant)
    }
    return "$how (${points.toPersianDigits()} امتیاز)"
}

/** ریشه‌ی بازی تخته‌نرد — سه روش؛ روی یک گوشی، شبکه‌ی محلی یا اینترنتی */
@Composable
fun BackgammonGame(
    onExitToHub: () -> Unit,
    viewModel: BackgammonViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current

    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                BgSoundEvent.DICE -> {
                    sound?.playDorNextTurn()
                    sound?.vibrate(40)
                }
                BgSoundEvent.MOVE -> sound?.playButtonClick()
                BgSoundEvent.HIT -> {
                    sound?.playWordSkip()
                    sound?.vibrate(80)
                }
                BgSoundEvent.BEAR_OFF -> sound?.playCorrectWord()
                BgSoundEvent.SKIP -> sound?.playTimerEnd()
                BgSoundEvent.WIN -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
                BgSoundEvent.DOUBLE -> {
                    sound?.playTurnStart()
                    sound?.vibrate(60)
                }
                BgSoundEvent.CHAT -> sound?.playButtonClick()
                BgSoundEvent.TIMEOUT -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(150)
                }
            }
        }
    }

    LaunchedEffect(Unit) { sound?.stopBackgroundMusic() }

    val leaveAndExit = {
        viewModel.backToVariants()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        val game = state.game
        PhaseTransition(key = state.stage to state.matchOver) {
            when (state.stage) {
                BgStage.VariantSelect -> {
                    BackHandler { onExitToHub() }
                    BgVariantSelectScreen(
                        state = state,
                        onPick = viewModel::chooseVariant,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                    )
                }

                BgStage.ModeSelect -> {
                    BackHandler { viewModel.backFromModeSelect() }
                    BgModeSelectScreen(
                        state = state,
                        onMatchLength = viewModel::setMatchLength,
                        onClockMinutes = viewModel::setClockMinutes,
                        onLocal = viewModel::chooseLocalMode,
                        onNetwork = viewModel::chooseNetworkMode,
                    )
                }

                BgStage.NetEntry -> {
                    BackHandler { viewModel.backFromNetEntry() }
                    BgNetEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onToggleOnline = viewModel::setOnlineMode,
                        onHost = {
                            if (state.onlineMode) viewModel.startHostingOnline()
                            else viewModel.startHosting()
                        },
                        onJoin = viewModel::openJoinScreen,
                        onResume = viewModel::resumeOnline,
                        onDiscardResume = viewModel::discardResume,
                    )
                }

                BgStage.NetJoin -> {
                    BackHandler { viewModel.backFromJoin() }
                    BgNetJoinScreen(
                        state = state,
                        onJoin = { g -> viewModel.joinGame(g.address, g.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                        onJoinOnline = viewModel::joinOnlineRoom,
                    )
                }

                BgStage.NetLobby -> {
                    BackHandler { viewModel.cancelHosting() }
                    BgNetLobbyScreen(state = state)
                }

                BgStage.Playing -> when {
                    game == null -> Unit

                    // مسابقه واقعاً تمام شد — صفحه‌ی برنده‌ی نهایی با امتیاز مسابقه
                    state.matchOver -> {
                        BackHandler { leaveAndExit() }
                        BgWinnerScreen(
                            state = state,
                            game = game,
                            onPlayAgain = viewModel::playAgain,
                            onExitToHub = leaveAndExit,
                        )
                    }

                    // وسط بازی، یا دست تمام شده و پرده‌ی «دست بعدی» روی همین صفحه است
                    else -> {
                        BackHandler { pendingExit = { leaveAndExit() } }
                        BgPlayScreen(
                            state = state,
                            game = game,
                            viewModel = viewModel,
                            onRequestExit = { pendingExit = { leaveAndExit() } },
                        )
                    }
                }
            }
        }

        // ---- میزبان لحظه‌ای غایب شده (سمت مهمان اینترنتی): بنر کوچک، بازی نمی‌ایستد ----
        if (state.hostAway && !state.lostConnection && state.stage == BgStage.Playing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                BgHostAwayBanner()
            }
        }

        // ---- ارتباط با میزبان قطع شد (سمت مهمان) ----
        if (state.lostConnection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { },
                contentAlignment = Alignment.Center,
            ) {
                TicketCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    tilt = -1.5f,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
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
                            text = if (state.onlineMode) {
                                "اینترنت رو چک کن؛ اتاق هنوز هست — می‌تونی با همون اسم دوباره وصل شی"
                            } else {
                                "وای‌فای رو چک کنید؛ اگر میزبان برگشت، دوباره با همون اسم بپیوندید"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        state.connectError?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelLarge,
                                color = kiExtras.danger,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.onlineMode && state.roomCode.isNotBlank()) {
                            KButton(
                                text = if (state.reconnecting) "یه لحظه…" else "دوباره وصل شو 🔁",
                                enabled = !state.reconnecting,
                                onClick = viewModel::reconnectOnline,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            KButton(text = "ترک بازی", style = KButtonStyle.Glass, onClick = leaveAndExit)
                        } else {
                            KButton(text = "باشه", onClick = leaveAndExit)
                        }
                    }
                }
            }
        }
    }
}

/** صفحه‌ی انتخاب روش: کلاسیک، هلندی، هایپرگامون (+ پیشنهاد ادامه‌ی بازی اینترنتیِ نیمه‌کاره) */
@Composable
private fun BgVariantSelectScreen(
    state: BgUiState,
    onPick: (BgVariant) -> Unit,
    onResume: () -> Unit,
    onDiscardResume: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "backgammon", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            BobbingEmoji(emoji = "🎲", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = "تخته‌نرد")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کدوم روش رو بازی می‌کنید؟",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            state.resumable?.let { stored ->
                // بازی اینترنتی نیمه‌کاره: همین اول کار پیشنهاد ادامه بده
                BgResumeCard(
                    stored = stored,
                    busy = state.connecting,
                    error = state.connectError,
                    onResume = onResume,
                    onDiscard = onDiscardResume,
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
            BgVariantCard(
                emoji = "🏛️",
                title = "تخته‌نرد کلاسیک",
                desc = "همون تخته‌ی همیشگی: چیدمان استاندارد، زدن و بستن و مارس!",
                onClick = { onPick(BgVariant.STANDARD) },
            )
            Spacer(modifier = Modifier.height(14.dp))
            BgVariantCard(
                emoji = "🌷",
                title = "تخته‌نرد هلندی",
                desc = "صفحه خالیه! اول باید هر ۱۵ مهره رو وارد کنی و تا مهره‌ای به خونه‌ت نرسه، حق زدن نداری.",
                onClick = { onPick(BgVariant.DUTCH) },
            )
            Spacer(modifier = Modifier.height(14.dp))
            BgVariantCard(
                emoji = "⚡",
                title = "هایپرگامون",
                desc = "فقط ۳ مهره برای هر نفر — کوتاه، تند و پرهیجان!",
                onClick = { onPick(BgVariant.HYPER) },
            )
            Spacer(modifier = Modifier.height(14.dp))
            BgVariantCard(
                emoji = "🏺",
                title = "تخته‌نرد ایرانی",
                desc = "بدون دوبل، دست به مهره — سگ‌مارس ۳ امتیازه!",
                onClick = { onPick(BgVariant.IRANI) },
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BgVariantCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        onClick = { sound?.playButtonClick(); onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** کارتِ «بازی اینترنتی نیمه‌کاره داری» — ادامه بده یا بی‌خیال */
@Composable
private fun BgResumeCard(
    stored: StoredOnlineRoom,
    busy: Boolean,
    error: String?,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "🔁 بازی اینترنتی نیمه‌کاره داری",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "اتاق ${stored.code} — ${if (stored.isHost) "میزبان بودی" else "مهمان بودی"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = kiExtras.danger,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KButton(
                    text = if (busy) "یه لحظه…" else "ادامه بده",
                    enabled = !busy,
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                )
                KButton(
                    text = "بی‌خیال",
                    style = KButtonStyle.Glass,
                    enabled = !busy,
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** بنر کوچکِ «میزبان لحظه‌ای قطع شده» — سمت مهمان اینترنتی، بازی را نمی‌بندد */
@Composable
private fun BgHostAwayBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(kiExtras.glassStrong, RoundedCornerShape(14.dp))
            .border(1.dp, kiExtras.glassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.breathing(intensity = 0.06f, periodMs = 1400)) {
            Text(text = "⏳", fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "میزبان لحظه‌ای قطع شده — منتظر برگشتش…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** انتخاب راه بازی و تنظیم مسابقه: طول مسابقه، ساعت، و همین گوشی یا شبکه */
@Composable
private fun BgModeSelectScreen(
    state: BgUiState,
    onMatchLength: (Int) -> Unit,
    onClockMinutes: (Int) -> Unit,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        BobbingEmoji(emoji = "🎲", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = variantName(state.variant), fontSize = 26.sp)
        Spacer(modifier = Modifier.height(18.dp))

        // ---- مسابقه تا چند امتیاز؟ ----
        BgOptionChips(
            title = "تا چند امتیاز؟",
            options = BG_MATCH_LENGTHS,
            selected = state.matchLength,
            label = { if (it == 1) "تک‌دست" else it.toPersianDigits() },
            onSelect = onMatchLength,
        )
        // نرد ایرانی ساعت ندارد — وقت بازیکن‌ها آزاد است
        if (state.variant != BgVariant.IRANI) {
            Spacer(modifier = Modifier.height(12.dp))
            // ---- ساعت هر بازیکن ----
            BgOptionChips(
                title = "زمان هر بازیکن",
                options = BG_CLOCK_MINUTES,
                selected = state.clockMinutes,
                label = { if (it == 0) "بدون ساعت" else "${it.toPersianDigits()} دقیقه" },
                onSelect = onClockMinutes,
            )
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "چطوری بازی می‌کنید؟",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
        ) {
            ChoiceBubble(
                main = "همین گوشی",
                sub = "دو نفر، نوبتی\nروی یک گوشی",
                emoji = "🤝",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = onLocal,
            )
            ChoiceBubble(
                main = "دو گوشی",
                sub = "شبکه‌ی محلی\nیا اینترنتی",
                emoji = "📶",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = onNetwork,
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/** یک ردیف گزینه‌ی چیپی: عنوان و چند انتخاب که یکی روشن است */
@Composable
private fun BgOptionChips(
    title: String,
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                val on = opt == selected
                Text(
                    text = label(opt),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            if (on) accent else kiExtras.glassStrong,
                            RoundedCornerShape(14.dp),
                        )
                        .border(
                            1.dp,
                            if (on) accent else kiExtras.glassBorder,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            sound?.playButtonClick()
                            onSelect(opt)
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** ورود شبکه‌ای: اسم خودت را بگو، بعد میزبان شو یا بپیوند */
@Composable
private fun BgNetEntryScreen(
    state: BgUiState,
    onNameChanged: (String) -> Unit,
    onToggleOnline: (Boolean) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onResume: () -> Unit,
    onDiscardResume: () -> Unit,
) {
    // هشدار تم‌دار وقتی بدون نوشتن اسم روی دکمه‌ها بزند
    var showNameError by remember { mutableStateOf(false) }
    val guardName: (() -> Unit) -> Unit = { action ->
        if (state.myName.isBlank()) showNameError = true else action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "🎲", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = variantName(state.variant), rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "هر کدوم با گوشی خودتون — یکی میزبان می‌شه و اون یکی بهش می‌پیونده",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))

        state.resumable?.let { stored ->
            // بازی اینترنتی نیمه‌کاره: بالای اسم، پیشنهاد ادامه
            BgResumeCard(
                stored = stored,
                busy = state.connecting,
                error = null,
                onResume = onResume,
                onDiscard = onDiscardResume,
            )
            Spacer(modifier = Modifier.height(18.dp))
        }

        if (state.onlineMode) {
            OnlineIdentityField(username = state.myName)
        } else BlobTextField(
            value = state.myName,
            onValueChange = {
                onNameChanged(it)
                if (it.isNotBlank()) showNameError = false
            },
            placeholder = "اسمت چیه؟ (شناسه‌ی تو در بازی)",
            badge = "👤",
            tilt = -1f,
        )
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
        ) {
            ChoiceBubble(
                main = "میزبان شو",
                sub = "تخته رو بچین و\nحریف رو دعوت کن",
                emoji = "👑",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = { guardName(onHost) },
            )
            ChoiceBubble(
                main = "بپیوند",
                sub = "به تخته‌ی ساخته‌شده\nوصل شو",
                emoji = "🚪",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = { guardName(onJoin) },
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
        // ---- سوییچ راهِ بازی: وای‌فای محلی یا اینترنت با کد اتاق ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(kiExtras.glassStrong, RoundedCornerShape(18.dp))
                .border(
                    1.5.dp,
                    if (state.onlineMode) LocalGameAccent.current else kiExtras.glassBorder,
                    RoundedCornerShape(18.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggleOnline(!state.onlineMode) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = if (state.onlineMode) "🌐" else "📶", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "بازی اینترنتی",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.onlineMode) {
                        "با کد اتاق — حریفت هر جای دنیا باشه"
                    } else {
                        "الان: وای‌فای یا هات‌اسپات مشترک"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = if (state.onlineMode) "✅" else "⬜", fontSize = 20.sp)
        }

        if (showNameError && state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1100)) {
                StickerTitle(
                    text = "✋ اول اسمت رو بنویس!",
                    accent = kiExtras.danger,
                    rotation = -2f,
                    fontSize = 22.sp,
                )
            }
        }
        state.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** پیوستن: بازی‌های پیداشده در شبکه + اتصال دستی، یا کد اتاق اینترنتی */
@Composable
private fun BgNetJoinScreen(
    state: BgUiState,
    onJoin: (BgDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
    onJoinOnline: (String) -> Unit,
) {
    var roomCode by rememberSaveable { mutableStateOf("") }

    if (state.onlineMode) {
        // ---- پیوستن اینترنتی: فقط کد اتاق ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            BobbingEmoji(emoji = "🌐", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "کد اتاق رو بزن", rotation = 2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "میزبان بعد از ساختن بازی یه کد ${OnlineRooms.CODE_LENGTH.toPersianDigits()} حرفی می‌بینه — ازش بگیر و همین‌جا بنویس",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            BlobTextField(
                value = roomCode,
                onValueChange = { roomCode = it.uppercase().take(OnlineRooms.CODE_LENGTH) },
                placeholder = "مثلاً H7KQ2M",
                badge = "🔑",
                tilt = -1f,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(modifier = Modifier.height(14.dp))
            KButton(
                text = if (state.connecting) "در حال اتصال…" else "برو تو اتاق!",
                enabled = !state.connecting && roomCode.length == OnlineRooms.CODE_LENGTH,
                onClick = { onJoinOnline(roomCode) },
            )
            state.connectError?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = kiExtras.danger,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "📡", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "به کدوم تخته بپیوندیم؟", rotation = 2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.discovered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                        Text(text = "🔎", fontSize = 30.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دنبال بازی می‌گردم…\nمیزبان باید بازی رو ساخته باشه و روی همین شبکه باشید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(count = state.discovered.size, key = { state.discovered[it].hostName }) { i ->
                    val game = state.discovered[i]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        strong = true,
                        tilt = if (i % 2 == 0) -1f else 1f,
                        onClick = { onJoin(game) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "🎲", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تخته‌ی ${game.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "لمس کن تا وصل شی",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(text = "🚪", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- اتصال دستی وقتی کشف خودکار جواب نداد ----
        Text(
            text = "پیدا نشد؟ آدرسِ نمایش‌داده‌شده در لابی میزبان رو بزن:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            placeholder = "مثلاً 192.168.1.5",
            badge = "🔗",
            tilt = 0.8f,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(
            text = if (state.connecting) "در حال اتصال…" else "اتصال دستی",
            enabled = !state.connecting && manualAddress.isNotBlank(),
            onClick = { onManualJoin(manualAddress.trim()) },
        )
        state.connectError?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = kiExtras.danger,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** لابی میزبان: منتظر تنها حریف — با اتصال او بازی خودکار شروع می‌شود */
@Composable
private fun BgNetLobbyScreen(state: BgUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        BobbingEmoji(emoji = "🪑", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))
        StickerTitle(text = "لابی ${variantName(state.variant)}", rotation = -2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(18.dp))

        if (state.roomCode.isNotBlank()) {
            // ---- بازی اینترنتی: کد اتاق را بلند و خوانا نشان بده ----
            Text(
                text = "این کد رو به حریفت بگو:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.roomCode,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                color = LocalGameAccent.current,
                modifier = Modifier
                    .background(kiExtras.glassStrong, RoundedCornerShape(14.dp))
                    .border(1.5.dp, LocalGameAccent.current.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
            Spacer(modifier = Modifier.height(18.dp))
        } else if (state.hostAddress.isNotBlank()) {
            Text(
                text = "اتصال دستی حریف: ${state.hostAddress}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                    Text(text = "⏳", fontSize = 30.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.roomCode.isNotBlank()) {
                        "منتظر حریفیم…\nتا با کد وارد شد، بازی خودش شروع می‌شه"
                    } else {
                        "منتظر حریفیم…\nروی یک وای‌فای یا هات‌اسپات باشید تا پیدات کنه"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** صفحه‌ی برنده: نتیجه (تکی/مارس/مارس کامل)، پز دادن و بازی دوباره */
@Composable
private fun BgWinnerScreen(
    state: BgUiState,
    game: BgState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val winner = state.match.matchWinner ?: game.winner ?: return
    val winnerName = state.displayName(winner)
    val match = state.match
    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            BobbingEmoji(emoji = "🏆", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = if (match.length > 1) "$winnerName مسابقه رو برد!" else "$winnerName برد!")
            Spacer(modifier = Modifier.height(12.dp))
            if (match.length > 1) {
                // مسابقه‌ی چندامتیازی: نتیجه‌ی کل مسابقه
                Text(
                    text = "نتیجه‌ی مسابقه تا ${match.length.toPersianDigits()}: " +
                        "${match.score(winner).toPersianDigits()} – ${match.score(winner.opponent).toPersianDigits()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "دست آخر: ${bgGameEndLabel(match, game)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "نتیجه: ${bgGameEndLabel(match, game)}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            if (state.isNetPlay) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (state.myPlayer == winner) "دمت گرم، بردی! 🎉" else "این دست مالِ حریف بود — تلافی کن!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = "backgammon",
                gameTitle = "تخته‌نرد",
                gameEmoji = "🎲",
                winnerText = winnerName,
                scoreLines = listOf(BgPlayer.WHITE, BgPlayer.BLACK).map { p ->
                    state.displayName(p) to if (match.length > 1) {
                        "${match.score(p).toPersianDigits()} امتیاز"
                    } else {
                        "${game.borneOff(p).toPersianDigits()} مهره خارج"
                    }
                },
                winnerNames = listOf(winnerName),
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(
                text = if (state.netRole == BgNetRole.CLIENT) "دوباره بازی؟ از میزبان بخواه 🔁" else "دوباره بازی 🔁",
                onClick = onPlayAgain,
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
