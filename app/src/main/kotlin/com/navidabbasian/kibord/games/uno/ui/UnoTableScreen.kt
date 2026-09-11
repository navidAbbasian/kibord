package com.navidabbasian.kibord.games.uno.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.UnoUiState
import com.navidabbasian.kibord.games.uno.UnoViewModel
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoEngine
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoPhase
import com.navidabbasian.kibord.games.uno.engine.UnoState

/** میز اونو: حریف‌ها بالا، دسته و رد وسط، بادبزن دست پایین */
@Composable
fun UnoTableScreen(
    state: UnoUiState,
    game: UnoState,
    viewModel: UnoViewModel,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    /** صندلی خودم — در بازی محلی ۰، در چندگوشی صندلی‌ای که میزبان داده */
    val me = state.mySeat
    val humanTurn = game.phase == UnoPhase.PLAYING && game.turn == me && !state.dealing

    // وایلدی که انسان لمس کرده و منتظر انتخاب رنگ است
    var pendingWild by remember { mutableStateOf<UnoCard?>(null) }
    var pickingDrawnColor by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---------------- حریف‌ها ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 56.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                state.opponentSeats(game.players).forEach { seat ->
                    OpponentChip(
                        name = state.seatName(seat),
                        count = game.hands[seat].size,
                        total = game.totals[seat],
                        active = game.turn == seat && game.phase == UnoPhase.PLAYING,
                        thinking = state.thinkingSeat == seat,
                        unoBubble = state.unoBubbleSeat == seat,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ---------------- نشانگر جهت و مدل ----------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (game.direction == 1) "⟳" else "⟲",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (game.settings.mode) {
                        UnoMode.CLASSIC -> "کلاسیک"
                        UnoMode.SEVEN_ZERO -> "هفت-صفر"
                        UnoMode.MERCILESS -> "بی‌رحم"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- وسط میز ----------------
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // دسته‌ی کشیدن
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (humanTurn && game.drawnCard == null) {
                                        Modifier.breathing(intensity = 0.03f, periodMs = 1800)
                                    } else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = humanTurn && game.drawnCard == null,
                                ) { viewModel.humanDrawTap() },
                        ) {
                            UnoCardBack(width = 72.dp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = game.drawPile.size.toPersianDigits(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(28.dp))

                    // دسته‌ی رد + هاله‌ی رنگ فعال
                    Box(contentAlignment = Alignment.Center) {
                        val wildOnTop = game.topCard.isWild && game.currentColor != null
                        if (wildOnTop) {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .breathing(intensity = 0.04f, periodMs = 1600)
                                    .border(5.dp, unoColorOf(game.currentColor!!).copy(alpha = 0.75f), CircleShape),
                            )
                        }
                        // ردهای قبلی با چرخش خفیف برای حس تاریخچه
                        game.discard.takeLast(3).dropLast(1).forEachIndexed { i, card ->
                            UnoCardFace(
                                card = card,
                                width = 96.dp,
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = if ((card.id + i) % 2 == 0) -9f else 7f
                                    alpha = 0.8f
                                },
                            )
                        }
                        UnoCardFace(
                            card = game.topCard,
                            width = 100.dp,
                            modifier = Modifier.graphicsLayer { rotationZ = if (game.topCard.id % 2 == 0) 3f else -4f },
                        )

                        // شمارنده‌ی جریمه‌ی در حال رشد
                        if (game.pendingDraw > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-24).dp)
                                    .breathing(intensity = 0.08f, periodMs = 900)
                                    .background(extras.danger, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "+${game.pendingDraw.toPersianDigits()}!",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                )
                            }
                        }
                    }
                }

            }

            // ---------------- ناحیه‌ی انسان ----------------
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.seatName(me),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = if (humanTurn) accent else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${game.totals[me].toPersianDigits()} امتیاز",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (humanTurn) {
                    Text(
                        text = if (game.pendingDraw > 0) "سوار کن یا بکش! 🔥" else "نوبت توئه!",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1500),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            HumanHand(
                game = game,
                me = me,
                enabled = humanTurn && game.drawnCard == null,
                onPlay = { card ->
                    if (card.isWild) pendingWild = card else viewModel.humanPlay(card)
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        GameHelpButton(gameId = "uno", modifier = Modifier.align(Alignment.TopStart))

        // پیام گذرای رویدادها
        AnimatedVisibility(
            visible = state.toast != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 96.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(extras.glassStrong, RoundedCornerShape(18.dp))
                    .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = state.toast ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // ---------------- دکمه‌ی «اونو!» ----------------
        if (game.unoPending == me) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .size(84.dp)
                    .breathing(intensity = 0.10f, periodMs = 550)
                    .background(unoColorOf(UnoColor.RED), CircleShape)
                    .border(4.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { viewModel.humanCallUno() },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "اونو!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }

        // ---------------- انیمیشن پرواز دست‌ها (هفت-صفر) ----------------
        state.handAnim?.let { anim -> HandFlyOverlay(anim.id, anim.moves, game.players, me = me) }

        // ---------------- انتخاب رنگ ----------------
        val startColorNeeded = game.phase == UnoPhase.CHOOSE_COLOR && game.turn == me && !state.dealing
        if (startColorNeeded || pendingWild != null || pickingDrawnColor) {
            ColorPickerDialog(
                title = if (startColorNeeded) "برگ شروع وایلده — رنگ رو تو انتخاب کن!" else "چه رنگی بشه؟",
                onPick = { color ->
                    when {
                        startColorNeeded -> viewModel.humanChooseStartColor(color)
                        pickingDrawnColor -> {
                            viewModel.humanPlayDrawn(color)
                            pickingDrawnColor = false
                        }
                        else -> {
                            pendingWild?.let { viewModel.humanPlay(it, color) }
                            pendingWild = null
                        }
                    }
                },
                onDismiss = {
                    pendingWild = null
                    pickingDrawnColor = false
                },
                cancelable = !startColorNeeded,
            )
        }

        // ---------------- برگ تازه‌کشیده: بازی کن یا نگه دار ----------------
        val drawn = game.drawnCard
        if (drawn != null && game.turn == me && !pickingDrawnColor) {
            Dialog(onDismissRequest = {}) {
                GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "این برگ رو کشیدی — قابل بازیه!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        UnoCardFace(card = drawn, width = 96.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "بازی کن یا نگه دار",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        KButton(
                            text = "بازی کن! 🎯",
                            onClick = {
                                if (drawn.isWild) pickingDrawnColor = true else viewModel.humanPlayDrawn()
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        KButton(text = "نگه دار", style = KButtonStyle.Glass, onClick = { viewModel.humanKeepDrawn() })
                    }
                }
            }
        }

        // ---------------- انتخاب هم‌بازی برای تعویض (۷) ----------------
        if (game.phase == UnoPhase.CHOOSE_SWAP && game.swapSeat == me) {
            Dialog(onDismissRequest = {}) {
                GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BobbingEmoji(emoji = "🔄", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "۷ زدی! دستت رو با کی عوض می‌کنی؟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.opponentSeats(game.players).forEach { seat ->
                                ChoiceBubble(
                                    main = state.seatName(seat).take(6),
                                    sub = "${game.hands[seat].size.toPersianDigits()} برگ",
                                    size = 88.dp,
                                    mainFontSize = 16.sp,
                                    accent = unoColorOf(UnoColor.entries[seat % 4]),
                                    onClick = { viewModel.humanChooseSwap(seat) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- خلاصه‌ی دست / پایان مسابقه ----------------
        if (game.phase == UnoPhase.ROUND_OVER || (game.phase == UnoPhase.MATCH_OVER && !state.showFinal)) {
            RoundOverOverlay(state = state, game = game, viewModel = viewModel)
        }
    }
}

// ----------------------------------------------------------------------
// قطعات
// ----------------------------------------------------------------------

/** چیپ حریف: حرف اول + نشان تعداد برگ + هاله‌ی نوبت + حباب «اونو!» */
@Composable
private fun OpponentChip(
    name: String,
    count: Int,
    total: Int,
    active: Boolean,
    thinking: Boolean,
    unoBubble: Boolean,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .then(if (active) Modifier.breathing(intensity = 0.06f, periodMs = 1400) else Modifier)
                    .background(if (active) accent else extras.glassStrong, CircleShape)
                    .border(
                        2.5.dp,
                        if (active) Color.White.copy(alpha = 0.85f) else extras.glassBorderStrong,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
            // نشان تعداد برگ
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 4.dp)
                    .size(24.dp)
                    .background(extras.danger, CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toPersianDigits(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            if (unoBubble) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-10).dp)
                        .background(unoColorOf(UnoColor.RED), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(text = "اونو!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = if (thinking) "$name…" else name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = "${total.toPersianDigits()} امتیاز",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/**
 * بادبزن دست انسان: برگ‌های قانونی بالا و روشن، بقیه کدر.
 *
 * تا ۹ برگ یک ردیف است؛ بیشتر که شد (بی‌رحم می‌تواند ۱۵+ برگ بریزد)
 * دو ردیف می‌شود: ردیف عقب بالا و ردیف جلو رویش با همپوشانی ~۴۰٪ ارتفاع.
 * گام دیدنیِ هر برگ دست‌کم ~۳۴dp نگه داشته می‌شود و کارت‌ها در حالت
 * «فشرده» رندر می‌شوند تا فقط نشان گوشه‌شان در نوار دیدنی بیفتد.
 */
@Composable
private fun HumanHand(
    game: UnoState,
    me: Int,
    enabled: Boolean,
    onPlay: (UnoCard) -> Unit,
) {
    val legal = remember(game, me) { UnoEngine.legalPlays(game, me).map { it.id }.toSet() }
    val hand = game.hands[me]
    if (hand.isEmpty()) return
    val twoRows = hand.size > 9

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val avail = maxWidth
        // کارت‌های دست کوچک‌تر می‌شوند تا گام هر برگ زیر ~۳۴dp نرود
        val cardW = when {
            !twoRows -> 64.dp
            hand.size <= 18 -> 56.dp
            else -> 48.dp
        }
        val cardH = cardW * 1.5f
        val lift = 10.dp
        // ردیف جلو ~۴۰٪ ارتفاع ردیف عقب را می‌پوشاند
        val rowShift = cardH * 0.6f
        val rows: List<List<UnoCard>> =
            if (twoRows) listOf(hand.take(hand.size / 2), hand.drop(hand.size / 2))
            else listOf(hand)
        val blockH = lift + cardH + (if (twoRows) rowShift else 0.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(blockH),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                val rowY = lift + (if (rowIndex == 1) rowShift else 0.dp)
                val step =
                    if (row.size <= 1) 0.dp
                    else minOf((avail - cardW) / (row.size - 1), cardW + 6.dp)
                val fanW = cardW + step * (row.size - 1)
                val leading = (avail - fanW) / 2
                row.forEachIndexed { i, card ->
                    key(card.id) {
                        val playable = enabled && card.id in legal
                        UnoCardFace(
                            card = card,
                            width = cardW,
                            compact = true,
                            dimmed = enabled && !playable,
                            modifier = Modifier
                                .offset(
                                    x = leading + step * i,
                                    y = rowY - (if (playable) lift else 0.dp),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = playable,
                                ) { onPlay(card) },
                        )
                    }
                }
            }
        }
    }
}

/** انتخاب یکی از چهار رنگ با حباب‌های رنگی */
@Composable
private fun ColorPickerDialog(
    title: String,
    onPick: (UnoColor) -> Unit,
    onDismiss: () -> Unit,
    cancelable: Boolean,
) {
    Dialog(onDismissRequest = { if (cancelable) onDismiss() }) {
        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    UnoColor.entries.forEach { color ->
                        ChoiceBubble(
                            main = unoColorName(color),
                            size = 64.dp,
                            mainFontSize = 14.sp,
                            accent = unoColorOf(color),
                            onClick = { onPick(color) },
                        )
                    }
                }
                if (cancelable) {
                    Spacer(modifier = Modifier.height(14.dp))
                    KButton(text = "بی‌خیال", style = KButtonStyle.Glass, onClick = onDismiss)
                }
            }
        }
    }
}

/** پرواز کارت‌های بسته بین صندلی‌ها (تعویض و چرخش هفت-صفر) */
@Composable
private fun HandFlyOverlay(animId: Int, moves: List<Pair<Int, Int>>, players: Int, me: Int) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        fun anchor(seat: Int): Pair<Float, Float> =
            if (seat == me) {
                0.5f to 0.86f
            } else {
                val k = players - 1
                val slot = (seat - me + players) % players
                (slot.toFloat() / (k + 1)) to 0.10f
            }

        val progress = remember(animId) { Animatable(0f) }
        LaunchedEffect(animId) { progress.animateTo(1f, tween(durationMillis = 850)) }
        val t = progress.value

        moves.forEach { (from, to) ->
            val (fx, fy) = anchor(from)
            val (tx, ty) = anchor(to)
            val x = w * (fx + (tx - fx) * t) - 22.dp
            // کمی قوس تا پرواز طبیعی به نظر برسد
            val arc = -0.08f * (1f - (2f * t - 1f) * (2f * t - 1f))
            val y = h * (fy + (ty - fy) * t + arc)
            UnoCardBack(
                width = 44.dp,
                modifier = Modifier
                    .offset(x = x, y = y)
                    .graphicsLayer { rotationZ = 360f * t * if (from % 2 == 0) 1f else -1f },
            )
        }
    }
}

/** خلاصه‌ی پایان دست: امتیاز دست + جمع کل + دکمه‌ی ادامه */
@Composable
private fun RoundOverOverlay(
    state: UnoUiState,
    game: UnoState,
    viewModel: UnoViewModel,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val winner = game.roundWinner ?: return
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
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            strong = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BobbingEmoji(emoji = if (winner == state.mySeat) "🎉" else "🃏", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(
                    text = "${state.seatName(winner)} دستش رو خالی کرد!",
                    fontSize = 20.sp,
                    rotation = -1.5f,
                )
                Spacer(modifier = Modifier.height(16.dp))
                (0 until game.players).forEach { seat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.seatName(seat),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (seat == winner) accent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "+${(game.roundScores?.get(seat) ?: 0).toPersianDigits()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = if (seat == winner) extras.success else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "جمع: ${game.totals[seat].toPersianDigits()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (game.phase == UnoPhase.MATCH_OVER) {
                    KButton(text = "نتیجه‌ی مسابقه 🏆", onClick = { viewModel.showFinal() })
                } else {
                    KButton(text = "دست بعدی 🎴", onClick = { viewModel.nextRound() })
                }
            }
        }
    }
}
