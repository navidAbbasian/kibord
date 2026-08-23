package com.navidabbasian.kibord.games.shelem.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.HandFan
import com.navidabbasian.kibord.core.cards.HiddenHand
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.color
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.SHELEM_HUMAN
import com.navidabbasian.kibord.games.shelem.ShelemUiState
import com.navidabbasian.kibord.games.shelem.ShelemViewModel
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemState

/** رنگ تیم: «ما» نعنایی، «اون‌ها» گلبهی */
@Composable
fun shelemTeamColor(team: Int): Color = if (team == 0) kiExtras.teamColors[2] else kiExtras.teamColors[0]

/** میز بازی: سه دستِ بسته دور میز، دستِ باز انسان پایین، دستِ جاری وسط، پنل‌ها و ورقه‌ها */
@Composable
fun ShelemTableScreen(
    state: ShelemUiState,
    game: ShelemState,
    viewModel: ShelemViewModel,
) {
    val extras = kiExtras
    val humanTurn = !state.dealing && when (game.phase) {
        ShelemPhase.BIDDING -> game.bidTurn == SHELEM_HUMAN
        ShelemPhase.DISCARDING, ShelemPhase.TRUMP -> game.declarer == SHELEM_HUMAN
        ShelemPhase.PLAYING -> game.turn == SHELEM_HUMAN && game.trickWinner == null
        else -> false
    }
    val legal = remember(game) { ShelemEngine.legalPlaysFor(game, SHELEM_HUMAN).toSet() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // ---------------- نوار بالا: امتیازها و قرارداد ----------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShelemScoreboard(state = state, game = game, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                ShelemTrumpBadge(game = game, state = state)
            }

            // ---------------- میز ----------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                // یار (بالا)
                SeatBadge(
                    state = state,
                    game = game,
                    seat = 2,
                    modifier = Modifier.align(Alignment.TopCenter),
                    horizontal = true,
                )
                // حریف راست (صندلی ۱) و حریف چپ (صندلی ۳) — نوبت پادساعتگرد
                SeatBadge(
                    state = state,
                    game = game,
                    seat = 1,
                    modifier = Modifier.align(Alignment.CenterEnd).offset(y = (-24).dp),
                    horizontal = false,
                )
                SeatBadge(
                    state = state,
                    game = game,
                    seat = 3,
                    modifier = Modifier.align(Alignment.CenterStart).offset(y = (-24).dp),
                    horizontal = false,
                )
                // دستِ جاری وسط میز
                TrickArea(
                    game = game,
                    modifier = Modifier.align(Alignment.Center).offset(y = 6.dp),
                )
                // امتیاز زنده‌ی این دست
                LivePointsRow(state = state, game = game, modifier = Modifier.align(Alignment.BottomCenter))
                // پخش کارت
                PopVisibility(
                    visible = state.dealing,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .background(extras.glassStrong, RoundedCornerShape(20.dp))
                            .border(1.dp, extras.glassBorderStrong, RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "🎴 دارم کارت پخش می‌کنم…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ---------------- بخش انسان ----------------
            HumanRow(state = state, game = game, humanTurn = humanTurn)
            HumanHand(
                state = state,
                game = game,
                legal = legal,
                humanTurn = humanTurn,
                onPlay = viewModel::humanPlay,
                onToggleDiscard = viewModel::toggleDiscard,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .heightIn(min = 92.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShelemActionPanel(state = state, game = game, humanTurn = humanTurn, viewModel = viewModel)
            }
        }

        GameHelpButton(gameId = "shelem", modifier = Modifier.align(Alignment.TopStart))

        // ---------------- ورقه‌ها ----------------
        if (game.phase == ShelemPhase.TRUMP && game.declarer == SHELEM_HUMAN) {
            ShelemTrumpSheet(hand = game.hands[SHELEM_HUMAN], onPick = viewModel::humanChooseTrump)
        }
        if ((game.phase == ShelemPhase.HAND_OVER || game.phase == ShelemPhase.MATCH_OVER) && game.handResult != null) {
            ShelemHandEndOverlay(
                state = state,
                game = game,
                onNext = if (game.phase == ShelemPhase.HAND_OVER) viewModel::nextHand else viewModel::showFinal,
            )
        }
    }
}

/** ظاهر/محو شدن با پرش کوچک — بیرون از هر اسکوپ تا با نسخه‌های ستونی قاطی نشود */
@Composable
private fun PopVisibility(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 1.1f),
    ) { content() }
}

// ----------------------------------------------------------------------
// نوار بالا
// ----------------------------------------------------------------------

@Composable
private fun ShelemScoreboard(state: ShelemUiState, game: ShelemState, modifier: Modifier = Modifier) {
    val extras = kiExtras
    Column(
        modifier = modifier
            .background(extras.glassStrong, RoundedCornerShape(18.dp))
            .border(1.dp, extras.glassBorderStrong, RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScorePill(label = state.teamName(0), score = game.scores[0], color = shelemTeamColor(0))
            Spacer(modifier = Modifier.width(8.dp))
            ScorePill(label = state.teamName(1), score = game.scores[1], color = shelemTeamColor(1))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "تا ${game.settings.targetScore.toPersianDigits()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = contractLine(state, game),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun contractLine(state: ShelemUiState, game: ShelemState): String = when (game.phase) {
    ShelemPhase.BIDDING -> {
        val high = game.highBid
        val bidder = game.highBidder
        if (high == null || bidder == null) "دست ${game.handNumber.toPersianDigits()} · شرط‌بندی — هنوز کسی شرط نبسته"
        else "دست ${game.handNumber.toPersianDigits()} · بالاترین شرط: ${high.toPersianDigits()} (${state.seatName(bidder)})"
    }
    else -> {
        val d = game.declarer ?: return ""
        val who = if (d == SHELEM_HUMAN) "حاکم تویی" else "حاکم: ${state.seatName(d)}"
        "دست ${game.handNumber.toPersianDigits()} · $who · شرط ${game.contract.toPersianDigits()}" +
            (if (game.forcedBid) " (اجباری)" else "")
    }
}

@Composable
private fun ScorePill(label: String, score: Int, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = score.toPersianDigits(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** نشان حکم: خال درشت؛ قبل از انتخاب حکم، علامت سوال */
@Composable
private fun ShelemTrumpBadge(game: ShelemState, state: ShelemUiState) {
    val extras = kiExtras
    val trump = game.trump
    Column(
        modifier = Modifier
            .size(width = 58.dp, height = 56.dp)
            .background(if (trump != null) Color(0xFFFFFDF7) else extras.glassStrong, RoundedCornerShape(16.dp))
            .border(1.5.dp, if (trump != null) (trump.color.copy(alpha = 0.6f)) else extras.glassBorderStrong, RoundedCornerShape(16.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (trump != null) {
            Text(text = trump.symbol, color = trump.color, fontSize = 26.sp, lineHeight = 28.sp)
            Text(text = "حکم", color = Color(0xFF26262E), fontSize = 10.sp, lineHeight = 11.sp)
        } else {
            Text(text = "🃏", fontSize = 22.sp, lineHeight = 24.sp)
            Text(
                text = if (game.phase == ShelemPhase.BIDDING) "شرط؟" else "حکم؟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ----------------------------------------------------------------------
// صندلی‌ها
// ----------------------------------------------------------------------

/** برچسب یک ربات: اسم + تاج حاکم + دست بسته + حباب شرط / نشانگر فکر کردن */
@Composable
private fun SeatBadge(
    state: ShelemUiState,
    game: ShelemState,
    seat: Int,
    modifier: Modifier = Modifier,
    horizontal: Boolean,
) {
    val count = game.hands[seat].size
    val bubble = state.bidBubbles[seat]
    val thinking = state.thinkingSeat == seat
    val isTurn = when (game.phase) {
        ShelemPhase.BIDDING -> game.bidTurn == seat
        ShelemPhase.DISCARDING, ShelemPhase.TRUMP -> game.declarer == seat
        ShelemPhase.PLAYING -> game.turn == seat && game.trickWinner == null
        else -> false
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (horizontal) {
            NameChip(state = state, game = game, seat = seat, active = isTurn)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HiddenHand(count = count, width = 30.dp)
                Spacer(modifier = Modifier.width(6.dp))
                SeatBubble(bubble = bubble, thinking = thinking)
            }
        } else {
            NameChip(state = state, game = game, seat = seat, active = isTurn)
            Spacer(modifier = Modifier.height(4.dp))
            HiddenHand(count = count, width = 26.dp)
            Spacer(modifier = Modifier.height(4.dp))
            SeatBubble(bubble = bubble, thinking = thinking)
        }
    }
}

@Composable
private fun NameChip(state: ShelemUiState, game: ShelemState, seat: Int, active: Boolean) {
    val extras = kiExtras
    val color = shelemTeamColor(ShelemRules.teamOf(seat))
    val isDeclarer = game.declarer == seat && game.phase != ShelemPhase.BIDDING
    val isDealer = game.dealer == seat
    val borderAlpha by animateFloatAsState(if (active) 1f else 0.35f, label = "chip")
    Row(
        modifier = Modifier
            .then(if (active) Modifier.breathing(intensity = 0.04f) else Modifier)
            .background(if (active) color.copy(alpha = 0.30f) else extras.glassStrong, RoundedCornerShape(14.dp))
            .border(if (active) 2.dp else 1.dp, color.copy(alpha = borderAlpha), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = state.seatName(seat),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isDeclarer) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "👑", fontSize = 13.sp)
        } else if (isDealer) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "🎴", fontSize = 11.sp)
        }
    }
}

/** حباب کنار صندلی: «۱۲۰»، «پاس» یا سه نقطه‌ی فکر کردن */
@Composable
private fun SeatBubble(bubble: String?, thinking: Boolean) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    AnimatedVisibility(
        visible = bubble != null || thinking,
        enter = fadeIn() + scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        exit = fadeOut(),
    ) {
        val isPass = bubble == "پاس"
        val text = when {
            thinking -> "…"
            bubble == null -> ""
            isPass -> "پاس"
            else -> bubble.toPersianDigits()
        }
        Box(
            modifier = Modifier
                .background(
                    when {
                        thinking -> extras.glassStrong
                        isPass -> extras.glass
                        else -> accent.copy(alpha = 0.85f)
                    },
                    RoundedCornerShape(12.dp),
                )
                .border(1.dp, if (thinking || isPass) extras.glassBorderStrong else accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (thinking || isPass) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            )
        }
    }
}

// ----------------------------------------------------------------------
// دستِ جاری وسط میز
// ----------------------------------------------------------------------

/** چهار جایگاه کارت دور مرکز: پایین=۰، راست=۱، بالا=۲، چپ=۳ — کارت‌ها از سمت صندلی پرواز می‌کنند */
@Composable
private fun TrickArea(game: ShelemState, modifier: Modifier = Modifier) {
    val cardW = 54.dp
    val spread = 44.dp
    Box(modifier = modifier.size(cardW * 2 + spread * 2, cardW * 1.4f + spread * 2)) {
        for (seat in 0 until ShelemRules.PLAYERS) {
            val card = game.trickCardOf(seat)
            val winner = game.trickWinner == seat
            val (dx, dy) = when (seat) {
                0 -> 0.dp to spread
                1 -> spread * 1.4f to 0.dp
                2 -> 0.dp to -spread
                else -> -spread * 1.4f to 0.dp
            }
            FlyingTrickCard(
                card = card,
                seat = seat,
                width = cardW,
                highlighted = winner,
                modifier = Modifier.align(Alignment.Center).offset(x = dx, y = dy),
            )
        }
    }
}

@Composable
private fun FlyingTrickCard(card: Card?, seat: Int, width: androidx.compose.ui.unit.Dp, highlighted: Boolean, modifier: Modifier) {
    if (card == null) {
        Box(modifier = modifier.size(width, width * 1.4f))
        return
    }
    val progress = remember(card.id) { Animatable(0f) }
    LaunchedEffect(card.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val winScale by animateFloatAsState(if (highlighted) 1.12f else 1f, animationSpec = tween(250), label = "win")
    // جهت پرواز: از سمت صندلی به مرکز
    val (fx, fy) = when (seat) {
        0 -> 0f to 1f
        1 -> 1f to 0f
        2 -> 0f to -1f
        else -> -1f to 0f
    }
    val tilt = when (seat) { 1 -> 8f; 3 -> -8f; 2 -> 3f; else -> -3f }
    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            translationX = fx * (1f - p) * 260f
            translationY = fy * (1f - p) * 320f
            scaleX = (0.7f + 0.3f * p) * winScale
            scaleY = (0.7f + 0.3f * p) * winScale
            alpha = (0.2f + 0.8f * p).coerceAtMost(1f)
        },
    ) {
        PlayingCard(card = card, width = width, rotation = tilt, highlighted = highlighted)
    }
}

/** امتیاز زنده‌ی دو تیم در این دست (شامل ویدو برای تیم حاکم بعد از خواباندن) */
@Composable
private fun LivePointsRow(state: ShelemUiState, game: ShelemState, modifier: Modifier = Modifier) {
    if (game.phase < ShelemPhase.PLAYING) return
    // ویدو فقط وقتی به حساب می‌آید که خودِ انسان حاکم باشد (خوابیده‌های ربات مخفی است)
    val includeKitty = game.declarer == SHELEM_HUMAN
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        LivePill(label = state.teamName(0), pts = game.livePoints(0, includeKitty), color = shelemTeamColor(0), tricks = game.tricksTaken[0])
        Spacer(modifier = Modifier.width(14.dp))
        LivePill(label = state.teamName(1), pts = game.livePoints(1, includeKitty), color = shelemTeamColor(1), tricks = game.tricksTaken[1])
    }
}

@Composable
private fun LivePill(label: String, pts: Int, color: Color, tricks: Int) {
    val extras = kiExtras
    Row(
        modifier = Modifier
            .background(extras.glassStrong, RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "${pts.toPersianDigits()} امتیاز",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "(${tricks.toPersianDigits()} دست)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ----------------------------------------------------------------------
// انسان
// ----------------------------------------------------------------------

@Composable
private fun HumanRow(state: ShelemUiState, game: ShelemState, humanTurn: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NameChip(state = state, game = game, seat = SHELEM_HUMAN, active = humanTurn)
        Spacer(modifier = Modifier.width(8.dp))
        SeatBubble(bubble = state.bidBubbles[SHELEM_HUMAN], thinking = false)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = humanHint(state, game, humanTurn),
            style = MaterialTheme.typography.labelLarge,
            color = if (humanTurn) LocalGameAccent.current else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (humanTurn) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun humanHint(state: ShelemUiState, game: ShelemState, humanTurn: Boolean): String = when {
    state.dealing -> ""
    game.phase == ShelemPhase.BIDDING -> if (humanTurn) "نوبت شرطِ توئه!" else "${state.seatName(game.bidTurn)} داره فکر می‌کنه…"
    game.phase == ShelemPhase.DISCARDING -> if (humanTurn) "۴ کارت انتخاب کن بخوابونی" else "${state.seatName(game.declarer!!)} داره می‌خوابونه…"
    game.phase == ShelemPhase.TRUMP -> if (humanTurn) "حکم رو انتخاب کن" else "${state.seatName(game.declarer!!)} داره حکم می‌کنه…"
    game.phase == ShelemPhase.PLAYING -> when {
        game.trickWinner != null -> "دست رو ${state.seatName(game.trickWinner)} برد" + if (ShelemRules.teamOf(game.trickWinner) == 0) " 🎉" else ""
        humanTurn -> if (game.trick.isEmpty()) "نوبت توئه — شروع کن!" else "نوبت توئه!"
        else -> "نوبت ${state.seatName(game.turn)}"
    }
    else -> ""
}

/** دست باز انسان: بادبزن معمولی هنگام بازی؛ بادبزن انتخابی هنگام خواباندن */
@Composable
private fun HumanHand(
    state: ShelemUiState,
    game: ShelemState,
    legal: Set<Card>,
    humanTurn: Boolean,
    onPlay: (Card) -> Unit,
    onToggleDiscard: (Card) -> Unit,
) {
    val hand = game.hands[SHELEM_HUMAN]
    // انیمیشن پخش: کارت‌ها یکی‌یکی ظاهر می‌شوند
    val dealProgress = remember { Animatable(1f) }
    LaunchedEffect(state.dealing, game.handNumber) {
        if (state.dealing) {
            dealProgress.snapTo(0f)
            dealProgress.animateTo(1f, tween(1250))
        } else {
            dealProgress.snapTo(1f)
        }
    }
    val visibleCount = if (state.dealing) (hand.size * dealProgress.value).toInt().coerceIn(0, hand.size) else hand.size
    val shown = hand.take(visibleCount)

    val discarding = game.phase == ShelemPhase.DISCARDING && game.declarer == SHELEM_HUMAN
    if (discarding) {
        SelectableFan(
            cards = hand,
            selected = state.selectedDiscards,
            fresh = game.kitty.toSet(),
            onCardClick = onToggleDiscard,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    } else {
        HandFan(
            cards = shown,
            modifier = Modifier.padding(horizontal = 8.dp),
            playable = if (game.phase == ShelemPhase.PLAYING && humanTurn) legal else null,
            maxCardWidth = 66.dp,
            onCardClick = if (game.phase == ShelemPhase.PLAYING && humanTurn) onPlay else null,
        )
    }
}

/**
 * بادبزن با انتخاب چندتایی برای خواباندن: کارت‌های انتخاب‌شده بالا می‌آیند و
 * چهار کارتِ تازه‌ی ویدو با نشان کوچک مشخص‌اند.
 */
@Composable
private fun SelectableFan(
    cards: List<Card>,
    selected: Set<Card>,
    fresh: Set<Card>,
    onCardClick: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalGameAccent.current
    val maxCardWidth = 62.dp
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(maxCardWidth * 1.4f + 22.dp),
    ) {
        val n = cards.size
        val cardW = minOf(maxCardWidth, maxWidth / 4)
        val step = if (n <= 1) 0.dp else minOf(cardW * 0.62f, (maxWidth - cardW) / (n - 1))
        val totalW = cardW + step * (n - 1)
        val startX = (maxWidth - totalW) / 2
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            cards.forEachIndexed { i, card ->
                val mid = (n - 1) / 2f
                val tilt = if (n > 1) (i - mid) * (8f / maxOf(1f, mid)) else 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = startX + step * i, y = 0.dp),
                ) {
                    PlayingCard(
                        card = card,
                        width = cardW,
                        highlighted = card in selected,
                        rotation = tilt * 0.6f,
                        onClick = { onCardClick(card) },
                    )
                    if (card in fresh) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-6).dp)
                                .size(16.dp)
                                .background(accent, CircleShape)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "✦", color = Color.White, fontSize = 9.sp, lineHeight = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
