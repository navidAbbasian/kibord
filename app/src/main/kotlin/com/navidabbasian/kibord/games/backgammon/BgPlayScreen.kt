package com.navidabbasian.kibord.games.backgammon

import android.os.SystemClock
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.util.formatMillisAsClock
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.backgammon.engine.BgGameEnd
import com.navidabbasian.kibord.games.backgammon.engine.BgMatchRules
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.hub.gameGuides
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

// ---- پالت «اتاق بازی» تاریک برگرفته از عکس مرجع ----
private val RoomBg = Color(0xFF2A2320)
private val TopButtonBg = Color(0xFF3A302A)
private val PanelDark = Color(0xFF1F1A17)
private val AccentGold = Color(0xFFF2A93B)
private val DotGreen = Color(0xFF3BD16F)
private val DotGrey = Color(0xFF6B6259)
private val ClockBlue = Color(0xFF3C9BE8)
private val LineBlue = Color(0xFF2F9BF0)
private val LineDim = Color(0xFF473C34)
private val TextDim = Color(0xFFC9BEB4)
private val DangerRed = Color(0xFFE85B5B)
private val DieCream = Color(0xFFF3E7C8)
private val DiePip = Color(0xFF3A2A1F)

/** جمله‌های آماده‌ی چت سریع */
private val chatPhrases = listOf("سلام!", "خوب بازی کردی!", "عجله کن 😄", "چه تاسی!", "دست بعدی؟", "GG 👋")
private val chatEmojis = listOf("😂", "😮", "😤", "👏", "🔥", "🎲")

/**
 * صفحه‌ی بازی تخته‌نرد به سبک اتاق تاریک: نوار بالایی با آواتار و امتیاز
 * مسابقه و ساعت‌ها، خط آبی نوبت، تخته‌ی تمام‌عرض با تاس روی خودش،
 * ناحیه‌ی فرمان زیر تخته و نوار چت سریع پایین (فقط بازی شبکه‌ای).
 */
@Composable
internal fun BgPlayScreen(
    state: BgUiState,
    game: BgState,
    viewModel: BackgammonViewModel,
    onRequestExit: () -> Unit,
) {
    val sound = LocalSoundManager.current

    // نبض نمایش ساعت‌ها — فقط وقتی ساعتی واقعاً می‌دود
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.clocksTicking) {
        if (!state.clocksTicking) return@LaunchedEffect
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var resignConfirm by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RoomBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ---- نوار بالایی: برگشت، دو بازیکن، منو ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BgRoundButton(symbol = "→", onClick = onRequestExit)
                BgPlayerHeader(
                    player = BgPlayer.WHITE,
                    state = state,
                    game = game,
                    now = now,
                    avatarAtEnd = true,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "vs",
                    color = TextDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                BgPlayerHeader(
                    player = BgPlayer.BLACK,
                    state = state,
                    game = game,
                    now = now,
                    avatarAtEnd = false,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    BgRoundButton(symbol = "⋮", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("🏳️ تسلیم") },
                            // پیش از مشخص‌شدن نفر اول، تسلیمِ محلی معنا ندارد
                            enabled = game.phase != BgPhase.FINISHED &&
                                (state.isNetPlay || game.turn != null),
                            onClick = { menuOpen = false; resignConfirm = true },
                        )
                        DropdownMenuItem(
                            text = { Text("📖 راهنما") },
                            onClick = { menuOpen = false; helpOpen = true },
                        )
                        DropdownMenuItem(
                            text = { Text("🚪 خروج") },
                            onClick = { menuOpen = false; onRequestExit() },
                        )
                    }
                }
            }

            // حریفِ شبکه‌ای وسط بازی رفت — میزبان خبردار می‌شود
            if (state.netRole == BgNetRole.HOST && state.room.guestName.isNotBlank() && !state.room.guestConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📴 ارتباط حریف قطع شد — با همون اسم برگرده، بازی ادامه پیدا می‌کنه",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- خط آبی نوبت/زمان درست بالای تخته ----
            BgTurnLine(state = state, game = game, now = now)

            // ---- تخته + تاس‌های روی آن ----
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val boardW = maxWidth
                val geo = BgBoardGeometry(Size(boardW.value, boardW.value / BG_BOARD_ASPECT))
                BackgammonBoard(
                    state = game,
                    sourcesAbs = state.sourcesAbs,
                    selectedAbs = state.selectedSource?.let { sel ->
                        if (sel == BgMove.ENTRY) null
                        else game.turn?.let { com.navidabbasian.kibord.games.backgammon.engine.relToAbs(it, sel) }
                    },
                    destsAbs = state.destsAbs,
                    offIsDest = state.offIsDest,
                    cubeValue = state.match.cubeValue,
                    cubeOwner = state.match.cubeOwner,
                    crawford = state.match.crawford,
                    cubeGlow = state.canOfferDouble,
                    showCube = state.cubeAllowed,
                    onTapPoint = viewModel::tapPoint,
                    onTapEntry = viewModel::tapEntry,
                    onTapOff = viewModel::tapOff,
                    onTapCube = viewModel::offerDouble,
                )
                // تاس‌ها با مختصات چپ‌به‌راستِ خودِ بوم جانمایی می‌شوند
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.matchParentSize()) {
                        BgBoardDice(state = state, game = game, geo = geo)
                    }
                }
            }

            // ---- ناحیه‌ی فرمان زیر تخته ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BgActionArea(state = state, game = game, viewModel = viewModel)
            }

            // ---- نوار چت سریع — فقط بازی شبکه‌ای ----
            if (state.isNetPlay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelDark)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            sound?.playButtonClick()
                            viewModel.setChatOpen(true)
                        }
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "💬", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "یه چیزی بگو…",
                        color = TextDim,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(text = "➤", color = TextDim, fontSize = 16.sp)
                }
            } else {
                Spacer(modifier = Modifier.navigationBarsPadding().height(4.dp))
            }
        }

        // ---- حباب‌های چت بالای آواتارها ----
        state.chatBubbles.forEach { (player, bubble) ->
            Box(
                modifier = Modifier
                    .align(if (player == BgPlayer.WHITE) Alignment.TopStart else Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 106.dp, start = 14.dp, end = 14.dp),
            ) {
                Text(
                    text = bubble.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(max = 170.dp)
                        .background(TopButtonBg, RoundedCornerShape(14.dp))
                        .border(1.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        // ---- پنل چت سریع ----
        if (state.chatOpen) {
            BgChatPanel(
                onPick = viewModel::sendChat,
                onDismiss = { viewModel.setChatOpen(false) },
            )
        }

        // ---- دست تمام شد ولی مسابقه ادامه دارد: پرده‌ی «دست بعدی» ----
        if (state.gameOverMatchContinues) {
            BgGameOverOverlay(state = state, game = game, onNext = viewModel::nextGame)
        }
    }

    // ---- پیشنهاد دوبل: پاسخ‌دهنده تصمیم می‌گیرد ----
    if (state.mustAnswerDouble && !state.gameOverMatchContinues) {
        BgDoubleDialog(state = state, onAnswer = viewModel::answerDouble)
    }

    // ---- تایید تسلیم ----
    if (resignConfirm) {
        BgDarkDialog(onDismiss = { resignConfirm = false }) {
            Text(text = "🏳️", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            val who = if (state.isNetPlay) "تسلیم می‌شی؟" else "${state.displayName(game.turn ?: BgPlayer.WHITE)} تسلیم می‌شه؟"
            Text(
                text = who,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.cubeAllowed) {
                    "حریف این دست رو با ارزش فعلی مکعب (${state.match.cubeValue.toPersianDigits()} برابرِ تکی) می‌بره."
                } else {
                    "حریف این دست رو تکی می‌بره."
                },
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BgPillButton(text = "آره، تسلیم", primary = false) {
                    resignConfirm = false
                    viewModel.resign()
                }
                BgPillButton(text = "نه، ادامه", primary = true) { resignConfirm = false }
            }
        }
    }

    // ---- راهنما ----
    if (helpOpen) {
        BgHelpDialog(onDismiss = { helpOpen = false })
    }
}

/** دکمه‌ی گرد تیره‌ی نوار بالا (برگشت و منو) */
@Composable
private fun BgRoundButton(symbol: String, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(TopButtonBg, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                sound?.playButtonClick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** سرصفحه‌ی یک بازیکن: «N مونده»، آواتار حلقه‌دار، اسم با چراغ اتصال و ساعت */
@Composable
private fun BgPlayerHeader(
    player: BgPlayer,
    state: BgUiState,
    game: BgState,
    now: Long,
    avatarAtEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val isTurn = game.turn == player && game.phase != BgPhase.OPENING_ROLL && game.phase != BgPhase.FINISHED
    val name = state.displayName(player)
    val away = state.match.away(player)
    val awayText = "${away.coerceAtLeast(1).toPersianDigits()} مونده"

    val connected = when {
        !state.isNetPlay -> true
        state.myPlayer == player -> true
        state.netRole == BgNetRole.HOST -> state.room.guestConnected
        else -> !state.lostConnection && !state.hostAway
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (avatarAtEnd) {
                BgAwayText(awayText)
                Spacer(modifier = Modifier.width(8.dp))
                BgAvatar(player = player, name = name, isTurn = isTurn, isNet = state.isNetPlay)
            } else {
                BgAvatar(player = player, name = name, isTurn = isTurn, isNet = state.isNetPlay)
                Spacer(modifier = Modifier.width(8.dp))
                BgAwayText(awayText)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (connected) DotGreen else DotGrey, CircleShape),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = name + if (state.isNetPlay && state.myPlayer == player) " (تو)" else "",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (state.match.hasClocks) {
            val remaining = state.clockRemaining(player, now)
            Text(
                text = formatMillisAsClock(remaining),
                color = if (remaining < 30_000L) DangerRed else ClockBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** متن بزرگ «N مونده» */
@Composable
private fun BgAwayText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
    )
}

/** آواتار گرد با حلقه: طلایی برای بازیکنِ نوبت، وگرنه رنگ مهره‌اش */
@Composable
private fun BgAvatar(player: BgPlayer, name: String, isTurn: Boolean, isNet: Boolean) {
    val ring = when {
        isTurn -> AccentGold
        player == BgPlayer.WHITE -> BgCheckerWhite
        else -> BgCheckerBlackRing
    }
    val label = if (isNet) name.trim().take(1).ifBlank { "🙂" } else "🙂"
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(2.5.dp, ring, CircleShape)
            .padding(4.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF8A5A34), Color(0xFF5E3A1E))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * خط آبی بالای تخته: با ساعت روشن، نوار پیشرفتِ زمانِ بازیکنِ در حال
 * بازی است و کوتاه می‌شود؛ بدون ساعت، در نوبت خودت پر و آبی است و در
 * نوبت حریف کم‌رنگ.
 */
@Composable
private fun BgTurnLine(state: BgUiState, game: BgState, now: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(LineDim),
    ) {
        if (state.match.hasClocks) {
            val running = state.match.clockRunning
            if (running != null && game.phase != BgPhase.FINISHED) {
                val total = state.match.clockTotalMs.coerceAtLeast(1L)
                val frac = (state.clockRemaining(running, now).toFloat() / total).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(3.dp)
                        .background(LineBlue),
                )
            }
        } else if (state.isMyTurn && game.phase != BgPhase.FINISHED && game.phase != BgPhase.OPENING_ROLL) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(LineBlue),
            )
        }
    }
}

// ================= ناحیه‌ی فرمان زیر تخته =================

@Composable
private fun BgActionArea(state: BgUiState, game: BgState, viewModel: BackgammonViewModel) {
    val turnName = state.displayName(game.turn ?: BgPlayer.WHITE)
    when {
        state.skipMessage != null -> {
            Text(
                text = if (state.isNetPlay && !state.isMyTurn) {
                    "$turnName حرکتی نداره — نوبتش می‌سوزه"
                } else {
                    state.skipMessage
                },
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (state.isMyTurn) {
                Spacer(modifier = Modifier.height(8.dp))
                BgPillButton(text = "باشه، نوبت بعدی", primary = true, onClick = viewModel::confirmSkip)
            }
        }

        game.phase == BgPhase.OPENING_ROLL -> {
            Text(
                text = when {
                    game.openingTie -> "مساوی شد! دوباره تاس بریزید"
                    else -> "هر بازیکن یه تاس می‌ندازه — بالاتر شروع می‌کنه"
                },
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (state.netRole == BgNetRole.CLIENT) {
                Text(text = "میزبان تاس شروع رو می‌ندازه…", color = TextDim, fontSize = 13.sp)
            } else {
                BgPillButton(text = "تاس بریز 🎲", primary = true, onClick = viewModel::rollOpening)
            }
        }

        game.phase == BgPhase.ROLLING && state.match.doubleOfferedBy != null -> {
            // پیشنهاد دوبل معلق: پاسخ‌دهنده دیالوگ دارد، پیشنهاددهنده منتظر است
            if (!state.mustAnswerDouble) {
                Text(
                    text = "پیشنهاد دوبل رفت — منتظر جواب حریف…",
                    color = AccentGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        game.phase == BgPhase.ROLLING -> {
            if (state.isMyTurn) {
                Text(
                    text = if (state.isNetPlay) "نوبت توئه!" else "نوبت $turnName",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.canOfferDouble) {
                        BgPillButton(
                            text = "دوبل ✖️${(state.match.cubeValue * 2).toPersianDigits()}",
                            primary = false,
                            onClick = viewModel::offerDouble,
                        )
                    }
                    BgPillButton(text = "تاس بریز 🎲", primary = true, onClick = viewModel::rollDice)
                }
            } else {
                Text(text = "منتظر تاسِ $turnName…", color = TextDim, fontSize = 14.sp)
            }
        }

        game.phase == BgPhase.MOVING -> {
            Text(
                text = when {
                    state.isNetPlay && !state.isMyTurn -> "$turnName داره حرکت می‌کنه…"
                    state.entryIsSource -> "باید مهره وارد کنی — یه خونه‌ی طلایی رو لمس کن"
                    else -> "یه مهره‌ت رو انتخاب کن، بعد خونه‌ی طلایی رو بزن"
                },
                color = TextDim,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            // راهنمای یک‌باره‌ی روش ایرانی: انتخاب مهره قفل می‌شود
            if (state.touchMoveActive && !state.touchMoveHintSeen && state.isMyTurn) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "دست به مهره! مهره‌ای که لمس کنی باید بازی بشه",
                    color = AccentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** دکمه‌ی قرصی تیره/طلایی هماهنگ با اتاق تاریک */
@Composable
private fun BgPillButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    Text(
        text = text,
        color = if (primary) Color(0xFF2A2013) else Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(if (primary) AccentGold else TopButtonBg, RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (primary) AccentGold.lighten(0.2f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

// ================= تاس‌های روی تخته =================

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
 * تاس‌ها روی خودِ تخته می‌افتند: روی نیمه‌ی بازیکنی که انداخته
 * (سفید نیمه‌ی راست، سیاه نیمه‌ی چپ) با چرخش کج و انیمیشن غلت.
 * تک‌تاس‌های پرتاب شروع هم هرکدام روی نیمه‌ی صاحبش می‌نشیند.
 */
@Composable
private fun BgBoardDice(state: BgUiState, game: BgState, geo: BgBoardGeometry) {
    data class Die(val value: Int, val used: Boolean, val x: Float, val y: Float, val rot: Float)

    val dieS = geo.w * 0.075f
    val dice: List<Die> = when {
        game.phase == BgPhase.OPENING_ROLL && game.openingDieWhite != null && game.openingDieBlack != null -> listOf(
            Die(game.openingDieWhite!!, false, geo.rightHalfCx(), geo.h * 0.513f, -8f),
            Die(game.openingDieBlack!!, false, geo.leftHalfCx(), geo.h * 0.513f, 12f),
        )

        game.dice.isNotEmpty() && game.turn != null -> {
            val cx = if (game.turn == BgPlayer.WHITE) geo.rightHalfCx() else geo.leftHalfCx()
            val faces = if (game.dice.size == 2 && game.dice[0] == game.dice[1]) List(4) { game.dice[0] } else game.dice
            val remainingPool = game.remainingDice.toMutableList()
            val dx = geo.w * 0.058f
            val positions = when (faces.size) {
                4 -> listOf(
                    Offset(cx - dx, geo.h * 0.452f), Offset(cx + dx, geo.h * 0.472f),
                    Offset(cx - dx, geo.h * 0.556f), Offset(cx + dx, geo.h * 0.576f),
                )
                else -> listOf(Offset(cx - dx, geo.h * 0.482f), Offset(cx + dx, geo.h * 0.544f))
            }
            faces.mapIndexed { i, v ->
                val used = game.phase == BgPhase.MOVING && !remainingPool.remove(v)
                val p = positions.getOrElse(i) { positions.last() }
                Die(v, used, p.x, p.y, if (i % 2 == 0) -8f else 12f)
            }
        }

        else -> emptyList()
    }
    if (dice.isEmpty()) return

    // انیمیشن غلت مشترک — با هر پرتاب تازه (rollNonce) از نو کوک می‌شود
    var flash by remember { mutableStateOf<List<Int>?>(null) }
    val progress = remember { Animatable(1f) }
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(state.rollNonce) {
        progress.snapTo(0f)
        bounce.snapTo(1f)
        val flashJob = launch {
            while (progress.value < 0.8f) {
                flash = List(6) { Random.nextInt(1, 7) }
                delay(70)
            }
        }
        progress.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        flashJob.cancel()
        flash = null
        bounce.snapTo(1.15f)
        bounce.animateTo(1f, animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium))
    }
    val p = progress.value
    val tumbling = p < 1f

    dice.forEachIndexed { i, die ->
        val dir = if (i % 2 == 0) 1f else -1f
        val shown = if (tumbling) flash?.getOrNull(i % 6) ?: die.value else die.value
        BgCreamDie(
            value = shown,
            used = die.used && !tumbling,
            sizeDp = dieS.dp,
            baseRotation = die.rot,
            tumbleRotation = dir * (1f - p) * (1f - p) * 540f,
            scale = (0.72f + 0.28f * p) * bounce.value,
            hop = sin(p * Math.PI.toFloat()) * 20f,
            modifier = Modifier.offset(x = (die.x - dieS / 2f).dp, y = (die.y - dieS / 2f).dp),
        )
    }
}

/** یک تاس کرمِ کج با خال‌های قهوه‌ای تیره و سایه — مثل عکس مرجع */
@Composable
private fun BgCreamDie(
    value: Int,
    used: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp,
    baseRotation: Float,
    tumbleRotation: Float = 0f,
    scale: Float = 1f,
    hop: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(sizeDp)
            .graphicsLayer {
                rotationZ = baseRotation + tumbleRotation
                scaleX = scale
                scaleY = scale
                translationY = -hop * density
                alpha = if (used) 0.4f else 1f
            },
    ) {
        val s = size.minDimension
        val body = Size(s * 0.92f, s * 0.92f)
        val corner = CornerRadius(s * 0.22f, s * 0.22f)
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.30f),
            topLeft = Offset(s * 0.07f, s * 0.11f),
            size = body,
            cornerRadius = corner,
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(DieCream.lighten(0.15f), DieCream, DieCream.darken(0.10f)),
                start = Offset.Zero,
                end = Offset(s, s),
            ),
            size = body,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = DieCream.darken(0.28f),
            size = body,
            cornerRadius = corner,
            style = Stroke(width = s * 0.03f),
        )
        pipPositions(value).forEach { pos ->
            drawCircle(
                color = DiePip,
                radius = s * 0.085f,
                center = Offset(pos.x * body.width, pos.y * body.height),
            )
        }
    }
}

// ================= پرده‌ها و دیالوگ‌های تیره =================

/** قاب دیالوگ تیره‌ی هماهنگ با اتاق بازی */
@Composable
private fun BgDarkDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(RoomBg, RoundedCornerShape(24.dp))
                .border(1.5.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/** دیالوگ پیشنهاد دوبل: قبول یا رد؛ در حالت محلی به صندلی پاسخ‌دهنده خطاب می‌شود */
@Composable
private fun BgDoubleDialog(state: BgUiState, onAnswer: (Boolean) -> Unit) {
    val offerer = state.match.doubleOfferedBy ?: return
    val newValue = (state.match.cubeValue * 2).coerceAtMost(BgMatchRules.MAX_CUBE)
    val offererName = state.displayName(offerer)
    val responderName = state.displayName(offerer.opponent)
    BgDarkDialog(onDismiss = {}) {
        Text(text = "🎲✖️${newValue.toPersianDigits()}", fontSize = 34.sp, fontWeight = FontWeight.Black, color = AccentGold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$offererName دوبل کرد!",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (state.isNetPlay) {
                "قبول کنی، ارزش این دست ${newValue.toPersianDigits()} برابر می‌شه و مکعب مال تو می‌شه.\nرد کنی، همین حالا دست رو با ${state.match.cubeValue.toPersianDigits()} امتیازِ مکعب واگذار می‌کنی."
            } else {
                "$responderName، تصمیم با توئه:\nقبول یعنی دست ${newValue.toPersianDigits()} برابر و مکعب مال تو؛ رد یعنی واگذاری همین حالا."
            },
            color = TextDim,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BgPillButton(text = "رد می‌کنم", primary = false) { onAnswer(false) }
            BgPillButton(text = "قبول! ✖️${newValue.toPersianDigits()}", primary = true) { onAnswer(true) }
        }
    }
}

/** پرده‌ی پایان دست وقتی مسابقه ادامه دارد: نتیجه، امتیازها و «دست بعدی» */
@Composable
private fun BgGameOverOverlay(state: BgUiState, game: BgState, onNext: () -> Unit) {
    val match = state.match
    val winner = match.lastGameWinner ?: game.winner ?: return
    val winnerName = state.displayName(winner)
    val points = if (match.lastGamePoints > 0) match.lastGamePoints else game.resultScore
    val how = when (match.lastGameEnd) {
        BgGameEnd.DROP -> "حریف دوبل رو رد کرد"
        BgGameEnd.RESIGN -> "حریف تسلیم شد"
        BgGameEnd.TIMEOUT -> "وقت حریف تموم شد"
        BgGameEnd.BEAR_OFF -> when (game.resultScore) {
            3 -> if (game.rules.variant == BgVariant.IRANI) "سگ‌مارس!" else "مارس کامل!"
            2 -> "مارس!"
            else -> "بردِ تکی"
        }
    }
    val nextCrawford = BgMatchRules.beginGame(match).crawford && state.cubeAllowed
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .background(RoomBg, RoundedCornerShape(24.dp))
                .border(1.5.dp, AccentGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "🏁", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$winnerName دست رو برد: ${points.toPersianDigits()} امتیاز",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = how, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "مسابقه تا ${match.length.toPersianDigits()}: " +
                    "${state.displayName(BgPlayer.WHITE)} ${match.scoreWhite.toPersianDigits()} — " +
                    "${state.displayName(BgPlayer.BLACK)} ${match.scoreBlack.toPersianDigits()}",
                color = AccentGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (nextCrawford) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "دست بعد «کرافورد»ه — دوبل نداره",
                    color = TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            BgPillButton(
                text = if (state.netRole == BgNetRole.CLIENT) "دست بعدی 🎲" else "دست بعدی 🎲",
                primary = true,
                onClick = onNext,
            )
        }
    }
}

/** پنل چت سریع: جمله‌های آماده و ایموجی — انتخاب یعنی فرستادن */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BgChatPanel(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDark, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "چت سریع 💬", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chatPhrases.forEach { phrase ->
                    Text(
                        text = phrase,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .background(TopButtonBg, RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(phrase) }
                            .padding(horizontal = 13.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chatEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .background(TopButtonBg, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onPick(emoji) }
                            .padding(8.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** راهنمای بازی از بانک راهنمای اپ — نسخه‌ی تیره برای اتاق بازی */
@Composable
private fun BgHelpDialog(onDismiss: () -> Unit) {
    val guide = remember { gameGuides.find { it.gameId == "backgammon" } } ?: return
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .background(RoomBg, RoundedCornerShape(24.dp))
                .border(1.5.dp, AccentGold.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📖", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "چطور بازی کنیم؟",
                    color = AccentGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            guide.steps.forEachIndexed { i, (title, body) ->
                Row(modifier = Modifier.padding(vertical = 5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(AccentGold, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (i + 1).toPersianDigits(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2A2013),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = body, color = TextDim, fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BgPillButton(text = "فهمیدم! 👍", primary = true, onClick = onDismiss)
            }
        }
    }
}
