package com.navidabbasian.kibord.games.backgammon

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.net.online.OnlineRooms
import com.navidabbasian.kibord.core.ui.components.BlobTextField
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
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.engine.relToAbs
import com.navidabbasian.kibord.games.backgammon.net.BgDiscoveredGame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** اسم فارسی هر روش برای تیترها و لابی */
private fun variantName(v: BgVariant?): String = when (v) {
    BgVariant.DUTCH -> "تخته‌نرد هلندی"
    BgVariant.HYPER -> "هایپرگامون"
    else -> "تخته‌نرد کلاسیک"
}

/** اسم فارسی نتیجه: تکی، مارس، مارس کامل */
private fun resultName(score: Int): String = when (score) {
    3 -> "مارس کامل"
    2 -> "مارس"
    else -> "تکی"
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
        PhaseTransition(key = state.stage to (game?.phase == BgPhase.FINISHED)) {
            when (state.stage) {
                BgStage.VariantSelect -> {
                    BackHandler { onExitToHub() }
                    BgVariantSelectScreen(onPick = viewModel::chooseVariant)
                }

                BgStage.ModeSelect -> {
                    BackHandler { viewModel.backFromModeSelect() }
                    BgModeSelectScreen(
                        variant = state.variant,
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

                    game.phase == BgPhase.FINISHED -> {
                        BackHandler { leaveAndExit() }
                        BgWinnerScreen(
                            state = state,
                            game = game,
                            onPlayAgain = viewModel::playAgain,
                            onExitToHub = leaveAndExit,
                        )
                    }

                    else -> {
                        BackHandler { pendingExit = { leaveAndExit() } }
                        BgPlayScreen(state = state, game = game, viewModel = viewModel)
                    }
                }
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

/** صفحه‌ی انتخاب روش: کلاسیک، هلندی، هایپرگامون */
@Composable
private fun BgVariantSelectScreen(onPick: (BgVariant) -> Unit) {
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

/** انتخاب راه بازی: دو نفر روی همین گوشی یا دو گوشی روی شبکه */
@Composable
private fun BgModeSelectScreen(
    variant: BgVariant?,
    onLocal: () -> Unit,
    onNetwork: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        BobbingEmoji(emoji = "🎲", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(12.dp))
        StickerTitle(text = variantName(variant), fontSize = 26.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "چطوری بازی می‌کنید؟",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(34.dp))
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

        BlobTextField(
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

/** صفحه‌ی اصلی بازی: نشان نوبت، صفحه‌ی تخته، تاس‌ها و پیام‌ها */
@Composable
private fun BgPlayScreen(
    state: BgUiState,
    game: BgState,
    viewModel: BackgammonViewModel,
) {
    val teamColors = kiExtras.teamColors
    val whiteColor = teamColors.getOrElse(0) { Color(0xFFF2E9DC) }
    val blackColor = teamColors.getOrElse(1) { Color(0xFF54423A) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ---- نشان بازیکن‌ها و نوبت ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BgPlayerChip(
                player = BgPlayer.WHITE,
                color = whiteColor,
                state = state,
                game = game,
                isTurn = game.turn == BgPlayer.WHITE && game.phase != BgPhase.OPENING_ROLL,
            )
            BgPlayerChip(
                player = BgPlayer.BLACK,
                color = blackColor,
                state = state,
                game = game,
                isTurn = game.turn == BgPlayer.BLACK && game.phase != BgPhase.OPENING_ROLL,
            )
        }

        // حریفِ شبکه‌ای وسط بازی رفت — میزبان خبردار می‌شود
        if (state.netRole == BgNetRole.HOST && state.room.guestName.isNotBlank() && !state.room.guestConnected) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "📴 ارتباط حریف قطع شد — اگه با همون اسم برگرده، بازی ادامه پیدا می‌کنه",
                style = MaterialTheme.typography.labelMedium,
                color = kiExtras.danger,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---- صفحه‌ی تخته ----
        BackgammonBoard(
            state = game,
            sourcesAbs = state.sourcesAbs,
            selectedAbs = state.selectedSource?.let { sel ->
                if (sel == BgMove.ENTRY) null else game.turn?.let { relToAbs(it, sel) }
            },
            destsAbs = state.destsAbs,
            offIsDest = state.offIsDest,
            whiteColor = whiteColor,
            blackColor = blackColor,
            onTapPoint = viewModel::tapPoint,
            onTapEntry = viewModel::tapEntry,
            onTapOff = viewModel::tapOff,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---- ناحیه‌ی تاس و پیام ----
        when {
            state.skipMessage != null -> {
                GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (state.isNetPlay && !state.isMyTurn) {
                                "${state.displayName(game.turn ?: BgPlayer.WHITE)} حرکتی نداره — نوبتش می‌سوزه"
                            } else {
                                state.skipMessage
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        if (state.isMyTurn) {
                            Spacer(modifier = Modifier.height(12.dp))
                            KButton(text = "باشه، نوبت بعدی", onClick = viewModel::confirmSkip)
                        }
                    }
                }
            }

            game.phase == BgPhase.OPENING_ROLL -> BgOpeningArea(state, game, viewModel)

            game.phase == BgPhase.ROLLING -> {
                val turnName = state.displayName(game.turn ?: BgPlayer.WHITE)
                if (state.isMyTurn) {
                    Text(
                        text = if (state.isNetPlay) "نوبت توئه — تاس بریز!" else "نوبت $turnName — تاس بریز!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    KButton(text = "تاس بریز 🎲", onClick = viewModel::rollDice)
                } else {
                    BgWaitingHint(text = "منتظر تاسِ $turnName…")
                }
            }

            game.phase == BgPhase.MOVING -> {
                BgDiceRow(dice = game.dice, remaining = game.remainingDice, rollKey = state.rollNonce)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        state.isNetPlay && !state.isMyTurn ->
                            "${state.displayName(game.turn ?: BgPlayer.WHITE)} داره حرکت می‌کنه…"
                        state.entryIsSource -> "باید مهره وارد کنی — یه خونه‌ی سبز رو لمس کن"
                        else -> "یه مهره‌ت رو انتخاب کن، بعد خونه‌ی سبز رو بزن"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** انتظار نرم برای حریف شبکه‌ای */
@Composable
private fun BgWaitingHint(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
            Text(text = "⏳", fontSize = 26.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** ناحیه‌ی پرتاب شروع: هر بازیکن یک تاس؛ مساوی یعنی تکرار — در شبکه دست میزبان است */
@Composable
private fun BgOpeningArea(state: BgUiState, game: BgState, viewModel: BackgammonViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val w = game.openingDieWhite
        val b = game.openingDieBlack
        if (w != null && b != null) {
            BgDiceRow(dice = listOf(w, b), remaining = null, rollKey = state.rollNonce)
            Spacer(modifier = Modifier.height(4.dp))
            // چیدمان راست‌به‌چپ: تاس اول (سفید) سمت راست می‌افتد
            Text(
                text = "راست: ${state.displayName(BgPlayer.WHITE)} — چپ: ${state.displayName(BgPlayer.BLACK)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        val message = when {
            game.openingTie -> "مساوی شد! دوباره تاس بریزید"
            else -> "هر بازیکن یه تاس می‌ندازه — بالاتر شروع می‌کنه"
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (state.netRole == BgNetRole.CLIENT) {
            BgWaitingHint(text = "میزبان تاس شروع رو می‌ندازه…")
        } else {
            KButton(text = "تاس بریز 🎲", onClick = viewModel::rollOpening)
        }
    }
}

/** نشان یک بازیکن: رنگ، اسم، مهره‌های بیرون و بار و خارج‌شده */
@Composable
private fun BgPlayerChip(
    player: BgPlayer,
    color: Color,
    state: BgUiState,
    game: BgState,
    isTurn: Boolean,
) {
    GlassCard(strong = isTurn, cornerRadius = 18.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                val youBadge = if (state.isNetPlay && state.myPlayer == player) " (تو)" else ""
                Text(
                    text = state.displayName(player) + youBadge + if (isTurn) " — نوبتشه" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val parts = buildList {
                if (game.offBoard(player) > 0) add("بیرون: ${game.offBoard(player).toPersianDigits()}")
                if (game.bar(player) > 0) add("بار: ${game.bar(player).toPersianDigits()}")
                add("خارج‌شده: ${game.borneOff(player).toPersianDigits()}")
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** موقعیت خال‌های هر وجه تاس در مختصات واحد ۰ تا ۱ */
private fun pipPositions(value: Int): List<Offset> {
    val c = Offset(0.5f, 0.5f)
    val tl = Offset(0.27f, 0.27f)
    val tr = Offset(0.73f, 0.27f)
    val bl = Offset(0.27f, 0.73f)
    val br = Offset(0.73f, 0.73f)
    val ml = Offset(0.27f, 0.5f)
    val mr = Offset(0.73f, 0.5f)
    return when (value) {
        1 -> listOf(c)
        2 -> listOf(tl, br)
        3 -> listOf(tl, c, br)
        4 -> listOf(tl, tr, bl, br)
        5 -> listOf(tl, tr, c, bl, br)
        else -> listOf(tl, tr, ml, mr, bl, br)
    }
}

/**
 * ردیف تاس‌ها با انیمیشن غلت خوردن: با هر پرتاب تازه (rollKey عوض می‌شود)
 * وجه‌ها تند عوض می‌شوند، تاس می‌چرخد و بالا می‌پرد و آخرش فنری روی
 * عدد واقعی می‌نشیند — روی گوشی مهمان هم با رسیدن وضعیت میزبان اجرا می‌شود.
 * جفت چهار تاس نشان می‌دهد و مصرف‌شده‌ها کم‌رنگ می‌شوند (remaining تهی = بدون کم‌رنگی).
 */
@Composable
private fun BgDiceRow(dice: List<Int>, remaining: List<Int>?, rollKey: Int) {
    if (dice.isEmpty()) return
    // چهارتایی‌شدن جفت فقط مال تاس نوبت است؛ تساویِ پرتاب شروع (remaining تهی) دو تاس می‌ماند
    val faces = if (remaining != null && dice.size == 2 && dice[0] == dice[1]) List(4) { dice[0] } else dice
    var flash by remember { mutableStateOf<List<Int>?>(null) }
    val progress = remember { Animatable(1f) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        progress.snapTo(0f)
        bounce.snapTo(1f)
        // در حین غلت، وجه‌ها هر چند فریم یک عدد شانسی نشان می‌دهند
        val flashJob = launch {
            while (progress.value < 0.8f) {
                flash = List(6) { Random.nextInt(1, 7) }
                delay(70)
            }
        }
        progress.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        flashJob.cancel()
        flash = null
        // فرود فنری روی عدد واقعی
        bounce.snapTo(1.18f)
        bounce.animateTo(1f, animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium))
    }
    val p = progress.value
    val tumbling = p < 1f
    val remainingPool = remaining?.toMutableList()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        faces.forEachIndexed { i, value ->
            val used = remainingPool != null && !remainingPool.remove(value)
            val dir = if (i % 2 == 0) 1f else -1f
            val shown = if (tumbling) flash?.getOrNull(i % 6) ?: value else value
            BgDie3D(
                value = shown,
                used = used && !tumbling,
                rotationZ = dir * (1f - p) * (1f - p) * 540f,
                rotationX = (1f - p) * 300f * dir,
                scale = (0.72f + 0.28f * p) * bounce.value,
                hop = sin(p * Math.PI.toFloat()) * 26f,
            )
        }
    }
}

/** یک تاس سه‌بعدی سفید با خال‌های مشکی — مثل عکس مرجع، با نور و سایه */
@Composable
private fun BgDie3D(
    value: Int,
    used: Boolean,
    rotationZ: Float = 0f,
    rotationX: Float = 0f,
    scale: Float = 1f,
    hop: Float = 0f,
) {
    Canvas(
        modifier = Modifier
            .size(54.dp)
            .graphicsLayer {
                this.rotationZ = rotationZ
                this.rotationX = rotationX
                scaleX = scale
                scaleY = scale
                translationY = -hop * density
                alpha = if (used) 0.35f else 1f
                cameraDistance = 16f * density
            },
    ) {
        val s = size.minDimension
        val body = Size(s * 0.94f, s * 0.94f)
        val corner = CornerRadius(s * 0.22f, s * 0.22f)
        // سایه‌ی نرم زیر تاس
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(s * 0.06f, s * 0.10f),
            size = body,
            cornerRadius = corner,
        )
        // بدنه‌ی سفید با نور از بالا-چپ
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFE), Color(0xFFF3F0E7), Color(0xFFD8D4C6)),
                start = Offset.Zero,
                end = Offset(s, s),
            ),
            size = body,
            cornerRadius = corner,
        )
        // لبه‌ی خاکستری گرم
        drawRoundRect(
            color = Color(0xFFB9B5A6),
            size = body,
            cornerRadius = corner,
            style = Stroke(width = s * 0.03f),
        )
        // خال‌های مشکی با برق ریز نور
        pipPositions(value).forEach { pos ->
            val cpt = Offset(pos.x * body.width, pos.y * body.height)
            drawCircle(Color(0xFF191919), radius = s * 0.082f, center = cpt)
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = s * 0.024f,
                center = cpt + Offset(-s * 0.02f, -s * 0.02f),
            )
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
    val winner = game.winner ?: return
    val winnerName = state.displayName(winner)
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
            StickerTitle(text = "$winnerName برد!")
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "نتیجه: ${resultName(game.resultScore)} (${game.resultScore.toPersianDigits()} امتیاز)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
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
                    state.displayName(p) to "${game.borneOff(p).toPersianDigits()} مهره خارج"
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
