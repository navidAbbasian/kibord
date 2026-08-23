package com.navidabbasian.kibord.games.dooz

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.audio.LocalSoundManager
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
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.dooz.engine.DoozBoard
import com.navidabbasian.kibord.games.dooz.engine.DoozDifficulty
import com.navidabbasian.kibord.games.dooz.engine.DoozMark
import kotlin.math.min

private const val GAME_ID = "dooz"

/** رنگ هر مهره: ضربدر قرمزِ اپ، دایره آبیِ دوز */
@Composable
private fun markColor(mark: DoozMark): Color = when (mark) {
    DoozMark.X -> kiExtras.danger
    DoozMark.O -> LocalGameAccent.current
}

private fun DoozMark.emoji(): String = if (this == DoozMark.X) "❌" else "⭕"

private fun DoozDifficulty.label(): String = when (this) {
    DoozDifficulty.EASY -> "آسون"
    DoozDifficulty.MEDIUM -> "معمولی"
    DoozDifficulty.HARD -> "سخت"
}

private fun DoozDifficulty.emoji(): String = when (this) {
    DoozDifficulty.EASY -> "🙂"
    DoozDifficulty.MEDIUM -> "😎"
    DoozDifficulty.HARD -> "🤖"
}

/** ریشه‌ی دوز — تنظیمات، صفحه‌ی بازی و قهرمانِ سِری */
@Composable
fun DoozGame(
    onExitToHub: () -> Unit,
    viewModel: DoozViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                DoozSoundEvent.TAP -> {
                    sound?.playButtonClick()
                    sound?.vibrate(18)
                }
                DoozSoundEvent.ROUND_WIN -> {
                    sound?.playRoundEnd()
                    sound?.vibrate(90)
                }
                DoozSoundEvent.DRAW -> {
                    sound?.playWordSkip()
                    sound?.vibrate(40)
                }
                DoozSoundEvent.SERIES_WIN -> {
                    sound?.playGameOver()
                    sound?.vibrate(220)
                }
            }
        }
    }

    LaunchedEffect(Unit) { sound?.stopBackgroundMusic() }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        PhaseTransition(key = state.phase) {
            when (state.phase) {
                DoozPhase.Setup -> {
                    BackHandler { onExitToHub() }
                    DoozSetupScreen(
                        state = state,
                        onMode = viewModel::setMode,
                        onDifficulty = viewModel::setDifficulty,
                        onTarget = viewModel::setTargetWins,
                        onName = viewModel::setName,
                        onStart = viewModel::startSeries,
                    )
                }

                DoozPhase.Play -> {
                    BackHandler { pendingExit = { viewModel.backToSetup(); onExitToHub() } }
                    DoozPlayScreen(
                        state = state,
                        onTap = viewModel::tapCell,
                        onNextRound = viewModel::nextRound,
                    )
                }

                DoozPhase.SeriesOver -> {
                    BackHandler { viewModel.backToSetup() }
                    DoozWinnerScreen(
                        state = state,
                        onPlayAgain = { Analytics.gameReplay(); viewModel.playAgain() },
                        onSettings = viewModel::backToSetup,
                        onExitToHub = { viewModel.backToSetup(); onExitToHub() },
                    )
                }
            }
        }
        if (state.phase != DoozPhase.SeriesOver) {
            GameHelpButton(gameId = GAME_ID, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

// ================= تنظیمات =================

@Composable
private fun DoozSetupScreen(
    state: DoozUiState,
    onMode: (DoozMode) -> Unit,
    onDifficulty: (DoozDifficulty) -> Unit,
    onTarget: (Int) -> Unit,
    onName: (Int, String) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val muted = lerp(accent, Color(0xFF8A93A8), 0.78f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        BobbingEmoji(emoji = "⭕", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "دوز", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "سه‌تا مهره‌ت رو توی یه خط بچین — سطر، ستون یا قطر!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))

        // ---- حالت بازی ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            ChoiceBubble(
                main = "دو نفره",
                sub = "روی همین گوشی",
                emoji = "👥",
                size = 128.dp,
                mainFontSize = 22.sp,
                accent = if (state.mode == DoozMode.PVP) accent else muted,
                tilt = -3f,
                onClick = { onMode(DoozMode.PVP) },
            )
            ChoiceBubble(
                main = "با ربات",
                sub = "حریف هوشمند",
                emoji = "🤖",
                size = 128.dp,
                mainFontSize = 22.sp,
                accent = if (state.mode == DoozMode.BOT) accent else muted,
                tilt = 3f,
                phase = 1.4f,
                modifier = Modifier.offset(y = 12.dp),
                onClick = { onMode(DoozMode.BOT) },
            )
        }
        Spacer(modifier = Modifier.height(22.dp))

        // ---- سختی ربات ----
        AnimatedVisibility(visible = state.isBotGame) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SectionLabel("ربات چقدر زرنگ باشه؟")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DoozDifficulty.entries.forEach { d ->
                        DoozPill(
                            text = d.label(),
                            emoji = d.emoji(),
                            selected = state.difficulty == d,
                            onClick = { onDifficulty(d) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        // ---- اسم‌ها ----
        SectionLabel("کی‌ها بازی می‌کنن؟")
        Spacer(modifier = Modifier.height(6.dp))
        BlobTextField(
            value = state.names[0],
            onValueChange = { onName(0, it.take(14)) },
            placeholder = "بازیکن ۱",
            color = extras.danger,
            badge = "❌",
            tilt = -1f,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (state.isBotGame) {
            GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), cornerRadius = 22.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "⭕", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ربات",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "🤖 ${state.difficulty.label()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            BlobTextField(
                value = state.names[1],
                onValueChange = { onName(1, it.take(14)) },
                placeholder = "بازیکن ۲",
                color = accent,
                badge = "⭕",
                tilt = 1f,
                phase = 1.3f,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(18.dp))

        // ---- تا چند برد ----
        SectionLabel("تا چند برد؟")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DoozViewModel.TARGETS.forEach { t ->
                DoozPill(
                    text = "${t.toPersianDigits()} برد",
                    emoji = if (t == 1) "⚡" else if (t == 3) "🏅" else "🏆",
                    selected = state.targetWins == t,
                    onClick = { onTarget(t) },
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            KButton(
                text = if (state.isBotGame) "بزن بریم سراغ ربات! 🤖" else "بزن بریم! 🎯",
                onClick = {
                    sound?.playButtonClick()
                    onStart()
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** قرص انتخاب: شیشه‌ای وقتی آزاد، پررنگ با رنگ بازی وقتی انتخاب شده */
@Composable
private fun DoozPill(
    text: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Color = LocalGameAccent.current,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pill_scale",
    )
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) accent else extras.glass, shape)
            .border(2.dp, if (selected) lerp(accent, Color.White, 0.35f) else extras.glassBorder, shape)
            .clickable {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ================= صفحه‌ی بازی =================

@Composable
private fun DoozPlayScreen(
    state: DoozUiState,
    onTap: (Int) -> Unit,
    onNextRound: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val result = state.roundResult

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "دوز", rotation = -2f, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "دست ${state.roundNo.toPersianDigits()} · تا ${state.targetWins.toPersianDigits()} برد",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ---- نوار امتیاز ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScoreCard(
                    mark = DoozMark.X,
                    name = state.displayName(DoozMark.X),
                    wins = state.xWins,
                    active = result == null && state.turn == DoozMark.X,
                )
                Column(
                    modifier = Modifier.widthIn(min = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "🤝", fontSize = 18.sp)
                    Text(
                        text = state.draws.toPersianDigits(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "مساوی",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreCard(
                    mark = DoozMark.O,
                    name = state.displayName(DoozMark.O),
                    wins = state.oWins,
                    active = result == null && state.turn == DoozMark.O,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- صفحه ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                DoozBoardCanvas(
                    board = state.board,
                    roundNo = state.roundNo,
                    winLine = result?.line,
                    enabled = state.humanCanMove,
                    onTap = onTap,
                    modifier = Modifier.fillMaxSize(),
                )
                // شانه بالا انداختن برای مساوی
                androidx.compose.animation.AnimatedVisibility(
                    visible = result != null && result.winner == null,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Text(
                        text = "🤷",
                        fontSize = 96.sp,
                        modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1400),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- نوبت ----
            TurnLine(state = state)
            Spacer(modifier = Modifier.height(18.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }

        // ---- کارت نتیجه‌ی دست ----
        AnimatedVisibility(
            visible = result != null && state.resultShown && state.seriesWinner == null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                initialOffsetY = { it },
            ) + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(180), targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            val winner = result?.winner
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true, cornerRadius = 26.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = if (winner == null) "🤝" else "🎉", fontSize = 34.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (winner) {
                            null -> "مساوی شد!"
                            else -> "${winner.emoji()} ${state.displayName(winner)} این دست رو برد!"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (winner == null) MaterialTheme.colorScheme.onSurface else markColor(winner),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.displayName(DoozMark.X)} ${state.xWins.toPersianDigits()} — ${state.oWins.toPersianDigits()} ${state.displayName(DoozMark.O)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    KButton(text = "دست بعدی 👉", onClick = onNextRound, accent = accent)
                }
            }
        }
    }
}

/** کارت هر بازیکن در نوار امتیاز؛ کارتِ نوبت‌دار با رنگ مهره می‌درخشد */
@Composable
private fun RowScope.ScoreCard(
    mark: DoozMark,
    name: String,
    wins: Int,
    active: Boolean,
) {
    val extras = kiExtras
    val color = markColor(mark)
    val shape = RoundedCornerShape(22.dp)
    val borderAlpha by animateFloatAsState(targetValue = if (active) 1f else 0f, label = "score_border")
    Column(
        modifier = Modifier
            .weight(1f)
            .then(if (active) Modifier.breathing(intensity = 0.025f, periodMs = 1600) else Modifier)
            .background(if (active) color.copy(alpha = 0.16f) else extras.glass, shape)
            .border(2.dp, lerp(extras.glassBorder, color, borderAlpha), shape)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = mark.emoji(), fontSize = 20.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = wins.toPersianDigits(),
            style = MaterialTheme.typography.headlineMedium,
            color = color,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun TurnLine(state: DoozUiState) {
    val result = state.roundResult
    val text: String
    val color: Color
    when {
        result != null && result.winner != null -> {
            text = "${result.winner.emoji()} ${state.displayName(result.winner)} سه‌تا رو چید!"
            color = markColor(result.winner)
        }
        result != null -> {
            text = "هیچ‌کی نبرد — مساوی! 🤷"
            color = MaterialTheme.colorScheme.onSurface
        }
        state.botThinking -> {
            text = "ربات داره فکر می‌کنه… 🤔"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> {
            text = "نوبت ${state.turn.emoji()} ${state.displayName(state.turn)}"
            color = markColor(state.turn)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = if (state.botThinking && result == null) Modifier.breathing(intensity = 0.03f, periodMs = 900) else Modifier,
    )
}

// ================= صفحه‌ی ۳×۳ =================

/**
 * صفحه‌ی دوز با Canvas: خطوط شبکه، مهره‌های فنری، خط برنده‌ی انیمیت‌شده.
 * مختصات صفحه قرینه است، پس راست‌چین بودن اپ تاثیری ندارد.
 */
@Composable
private fun DoozBoardCanvas(
    board: DoozBoard,
    roundNo: Int,
    winLine: List<Int>?,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = kiExtras
    val xColor = markColor(DoozMark.X)
    val oColor = markColor(DoozMark.O)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (extras.isDark) 0.28f else 0.22f)
    val boardFill = extras.glassStrong
    val boardBorder = extras.glassBorderStrong
    val lineColor = extras.gold

    // پاپ فنری هر مهره: از صفر تا یک وقتی گذاشته می‌شود، برگشت به صفر وقتی صفحه پاک می‌شود
    val scales = List(9) { i ->
        animateFloatAsState(
            targetValue = if (board[i] != null) 1f else 0f,
            animationSpec = if (board[i] != null)
                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
            else tween(180),
            label = "cell_$i",
        )
    }

    // ورود شبکه در شروع هر دست
    val gridProgress = remember { Animatable(0f) }
    LaunchedEffect(roundNo) {
        gridProgress.snapTo(0f)
        gridProgress.animateTo(1f, tween(420))
    }

    // خط برنده
    val lineProgress = remember { Animatable(0f) }
    LaunchedEffect(winLine) {
        if (winLine == null) lineProgress.snapTo(0f)
        else {
            lineProgress.snapTo(0f)
            lineProgress.animateTo(1f, tween(520))
        }
    }
    // محو شدن مهره‌های غیر برنده
    val dim by animateFloatAsState(targetValue = if (winLine != null) 0.3f else 1f, label = "dim")

    Canvas(
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures { offset ->
                val s = min(size.width, size.height).toFloat()
                val left = (size.width - s) / 2f
                val top = (size.height - s) / 2f
                val col = ((offset.x - left) / (s / 3f)).toInt()
                val row = ((offset.y - top) / (s / 3f)).toInt()
                if (col in 0..2 && row in 0..2) onTap(row * 3 + col)
            }
        },
    ) {
        val s = min(size.width, size.height)
        val left = (size.width - s) / 2f
        val top = (size.height - s) / 2f
        val cell = s / 3f
        val corner = s * 0.07f

        // زمینه‌ی شیشه‌ای
        drawRoundRect(
            color = boardFill,
            topLeft = Offset(left, top),
            size = Size(s, s),
            cornerRadius = CornerRadius(corner, corner),
        )
        drawRoundRect(
            color = boardBorder,
            topLeft = Offset(left, top),
            size = Size(s, s),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = s * 0.008f),
        )

        // خطوط شبکه (از وسط باز می‌شوند)
        val gp = gridProgress.value
        val gridW = s * 0.02f
        val inset = s * 0.05f
        for (i in 1..2) {
            val half = (s - 2 * inset) / 2f * gp
            val mid = top + s / 2f
            val x = left + cell * i
            drawLine(gridColor, Offset(x, mid - half), Offset(x, mid + half), gridW, StrokeCap.Round)
            val midX = left + s / 2f
            val y = top + cell * i
            drawLine(gridColor, Offset(midX - half, y), Offset(midX + half, y), gridW, StrokeCap.Round)
        }

        // مهره‌ها
        for (i in 0 until 9) {
            val mark = board[i] ?: continue
            val sc = scales[i].value.coerceAtLeast(0f)
            if (sc <= 0.001f) continue
            val cx = left + (i % 3) * cell + cell / 2f
            val cy = top + (i / 3) * cell + cell / 2f
            val onLine = winLine?.contains(i) == true
            val alpha = if (winLine != null && !onLine) dim else 1f
            when (mark) {
                DoozMark.X -> drawX(Offset(cx, cy), cell * 0.58f * sc, cell * 0.13f, xColor.copy(alpha = alpha))
                DoozMark.O -> drawO(Offset(cx, cy), cell * 0.29f * sc, cell * 0.13f, oColor.copy(alpha = alpha))
            }
        }

        // خط برنده
        if (winLine != null && lineProgress.value > 0f) {
            val a = winLine.first()
            val b = winLine.last()
            val start = Offset(left + (a % 3) * cell + cell / 2f, top + (a / 3) * cell + cell / 2f)
            val endFull = Offset(left + (b % 3) * cell + cell / 2f, top + (b / 3) * cell + cell / 2f)
            // کمی از دو سر بیرون بزند
            val dir = endFull - start
            val len = kotlin.math.hypot(dir.x, dir.y)
            val unit = Offset(dir.x / len, dir.y / len)
            val ext = cell * 0.32f
            val s0 = start - unit * ext
            val e0 = endFull + unit * ext
            val e = s0 + (e0 - s0) * lineProgress.value
            drawLine(lineColor.copy(alpha = 0.35f), s0, e, cell * 0.26f, StrokeCap.Round)
            drawLine(lineColor, s0, e, cell * 0.12f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawX(center: Offset, size: Float, width: Float, color: Color) {
    val h = size / 2f
    // هاله‌ی نرم زیر مهره
    drawLine(color.copy(alpha = color.alpha * 0.22f), center + Offset(-h, -h), center + Offset(h, h), width * 1.9f, StrokeCap.Round)
    drawLine(color.copy(alpha = color.alpha * 0.22f), center + Offset(h, -h), center + Offset(-h, h), width * 1.9f, StrokeCap.Round)
    drawLine(color, center + Offset(-h, -h), center + Offset(h, h), width, StrokeCap.Round)
    drawLine(color, center + Offset(h, -h), center + Offset(-h, h), width, StrokeCap.Round)
}

private fun DrawScope.drawO(center: Offset, radius: Float, width: Float, color: Color) {
    drawCircle(color.copy(alpha = color.alpha * 0.22f), radius = radius, center = center, style = Stroke(width * 1.9f))
    drawCircle(color, radius = radius, center = center, style = Stroke(width))
}

// ================= قهرمان سِری =================

@Composable
private fun DoozWinnerScreen(
    state: DoozUiState,
    onPlayAgain: () -> Unit,
    onSettings: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val winner = state.seriesWinner ?: DoozMark.X
    val winnerName = state.displayName(winner)
    val loser = winner.other
    val botWon = state.isBot(winner)
    val color = markColor(winner)

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
            Spacer(modifier = Modifier.height(44.dp))
            BobbingEmoji(emoji = if (botWon) "🤖" else "🏆", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = "$winnerName برد!", accent = color)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "${winner.emoji()} ${state.wins(winner).toPersianDigits()} — ${state.wins(loser).toPersianDigits()} ${loser.emoji()}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "${state.roundNo.toPersianDigits()} دست · ${state.draws.toPersianDigits()} مساوی",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    botWon -> "این دفعه ربات زرنگ‌تر بود — تلافی کن! 😤"
                    state.isBotGame -> "دمت گرم، ربات رو حریف شدی! 🎉"
                    else -> "${state.displayName(loser)} نوبت بعد حالشو بگیر! 😏"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = GAME_ID,
                gameTitle = "دوز",
                gameEmoji = "⭕",
                winnerText = winnerName,
                scoreLines = listOf(
                    state.displayName(DoozMark.X) to "${state.xWins.toPersianDigits()} برد",
                    state.displayName(DoozMark.O) to "${state.oWins.toPersianDigits()} برد",
                    "مساوی" to state.draws.toPersianDigits(),
                ),
                winnerNames = listOf(winnerName),
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "تغییر تنظیمات", style = KButtonStyle.Outline, onClick = onSettings)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
