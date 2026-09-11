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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.GlowChip
import com.navidabbasian.kibord.core.cards.HandFan
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.SideBackFan
import com.navidabbasian.kibord.core.cards.TableGlowCyan
import com.navidabbasian.kibord.core.cards.TablePill
import com.navidabbasian.kibord.core.cards.TablePillBrown
import com.navidabbasian.kibord.core.cards.TablePillCream
import com.navidabbasian.kibord.core.cards.TablePillGold
import com.navidabbasian.kibord.core.cards.TopBackFan
import com.navidabbasian.kibord.core.cards.WonTrickPile
import com.navidabbasian.kibord.core.cards.WoodPill
import com.navidabbasian.kibord.core.cards.WoodPlankBackground
import com.navidabbasian.kibord.core.cards.color
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.ShelemUiState
import com.navidabbasian.kibord.games.shelem.ShelemViewModel
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemState

/** رنگ تیم: «ما» نعنایی، «اون‌ها» گلبهی */
@Composable
fun shelemTeamColor(team: Int): Color = if (team == 0) kiExtras.teamColors[2] else kiExtras.teamColors[0]

/**
 * میز بازی شلم — همان میز چوبی گرم حکم: بادبزن پشتِ کارت یار بالا، دو ربات
 * کناری با قرص عمودی، چیپ درخشان حکم/شرط بالا، دستِ جاری وسط و دستِ دو ردیفه‌ی
 * انسان پایین. پنل‌های شرط/خواباندن روی چوب می‌نشینند.
 */
@Composable
fun ShelemTableScreen(
    state: ShelemUiState,
    game: ShelemState,
    viewModel: ShelemViewModel,
) {
    /** صندلی خودم — در بازی محلی ۰، در چندگوشی صندلی‌ای که میزبان داده */
    val me = state.mySeat
    val humanTurn = !state.dealing && when (game.phase) {
        ShelemPhase.BIDDING -> game.bidTurn == me
        ShelemPhase.DISCARDING, ShelemPhase.TRUMP -> game.declarer == me
        ShelemPhase.PLAYING -> game.turn == me && game.trickWinner == null
        else -> false
    }
    val legal = remember(game, me) { ShelemEngine.legalPlaysFor(game, me).toSet() }

    Box(modifier = Modifier.fillMaxSize()) {
        WoodPlankBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ---------------- نوار باریک بالا: قرص‌های چوبی امتیاز ----------------
            TopStrip(
                state = state,
                game = game,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 14.dp, top = 6.dp),
            )

            // ---------------- میز ----------------
            ShelemTableArea(
                state = state,
                game = game,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .heightIn(min = 92.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShelemActionPanel(state = state, game = game, humanTurn = humanTurn, viewModel = viewModel)
            }
        }

        GameHelpButton(gameId = "shelem", modifier = Modifier.align(Alignment.TopStart))

        // ---------------- ورقه‌ها ----------------
        if (game.phase == ShelemPhase.TRUMP && game.declarer == me) {
            ShelemTrumpSheet(hand = game.hands[me], onPick = viewModel::humanChooseTrump)
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
private fun TopStrip(state: ShelemUiState, game: ShelemState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WoodPill(
            text = "${state.teamName(0)} ${game.scores[0].toPersianDigits()} – " +
                "${state.teamName(1)} ${game.scores[1].toPersianDigits()}",
        )
        WoodPill(
            text = "تا ${game.settings.targetScore.toPersianDigits()} • دست ${game.handNumber.toPersianDigits()}",
        )
    }
}

// ----------------------------------------------------------------------
// میز
// ----------------------------------------------------------------------

@Composable
private fun ShelemTableArea(state: ShelemUiState, game: ShelemState, modifier: Modifier = Modifier) {
    // چیدمان میز چپ‌به‌راست است تا راست/چپ واقعی باشند؛ متن‌ها خودشان راست‌چین می‌شوند
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.padding(vertical = 2.dp)) {
            // چیپ درخشان بالا: شرط → حکم
            ShelemTrumpChip(
                game = game,
                modifier = Modifier
                    .align(BiasAlignment(0f, -0.98f))
                    .padding(top = 2.dp),
            )

            // صندلی‌ها نسبت به خودم: یار روبه‌رو (بالا)، حریف‌ها راست و چپ
            val me = state.mySeat
            val top = (me + 2) % 4
            val right = (me + 1) % 4
            val left = (me + 3) % 4
            // یار (بالا): بادبزن پشتِ کارت + قرص افقی
            TopBackFan(
                count = game.hands[top].size,
                modifier = Modifier.align(BiasAlignment(0f, -0.60f)),
            )
            Column(
                modifier = Modifier.align(BiasAlignment(0f, -0.26f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SeatPill(state = state, game = game, seat = top)
                Spacer(modifier = Modifier.height(3.dp))
                SeatBubble(bubble = state.bidBubbles[top], thinking = state.thinkingSeat == top)
            }

            // حریف راست (صندلی ۱) و حریف چپ (صندلی ۳) — بادبزن عمودی لبه + قرص عمودی
            SideBackFan(
                count = game.hands[right].size,
                rightSide = true,
                modifier = Modifier.align(BiasAlignment(1f, -0.35f)),
            )
            Column(
                modifier = Modifier.align(BiasAlignment(0.94f, 0.28f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SeatPill(state = state, game = game, seat = right, vertical = true, rotate = -90f)
                Spacer(modifier = Modifier.height(3.dp))
                SeatBubble(bubble = state.bidBubbles[right], thinking = state.thinkingSeat == right)
            }
            SideBackFan(
                count = game.hands[left].size,
                rightSide = false,
                modifier = Modifier.align(BiasAlignment(-1f, -0.35f)),
            )
            Column(
                modifier = Modifier.align(BiasAlignment(-0.94f, 0.28f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SeatPill(state = state, game = game, seat = left, vertical = true, rotate = 90f)
                Spacer(modifier = Modifier.height(3.dp))
                SeatBubble(bubble = state.bidBubbles[left], thinking = state.thinkingSeat == left)
            }

            // دسته‌های دستِ برده — یکی برای هر تیم، کنار همان تیم
            if (game.phase >= ShelemPhase.PLAYING) {
                WonTrickPile(
                    count = game.tricksTaken[state.myTeam],
                    modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)),
                )
                WonTrickPile(
                    count = game.tricksTaken[1 - state.myTeam],
                    modifier = Modifier.align(BiasAlignment(0.92f, -0.86f)),
                )
            }

            // دستِ جاری وسط میز
            TrickArea(
                game = game,
                me = me,
                modifier = Modifier.align(BiasAlignment(0f, 0.22f)),
            )

            // پخش کارت
            PopVisibility(
                visible = state.dealing,
                modifier = Modifier.align(BiasAlignment(0f, 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .background(TablePillBrown, RoundedCornerShape(18.dp))
                        .border(1.dp, TablePillGold, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "🎴 دارم کارت پخش می‌کنم…",
                        style = MaterialTheme.typography.titleSmall,
                        color = TablePillCream,
                    )
                }
            }
        }
    }
}

/** چیپ درخشان بالای میز: «در انتظار شرط…» → «در انتظار حکم…» → «حکـم : ♥» */
@Composable
private fun ShelemTrumpChip(game: ShelemState, modifier: Modifier = Modifier) {
    val trump = game.trump
    GlowChip(glowing = trump != null, modifier = modifier) {
        when {
            trump != null -> {
                Text(
                    text = "حکــم :",
                    style = MaterialTheme.typography.titleSmall,
                    color = TablePillGold,
                    fontWeight = FontWeight.Black,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = trump.symbol,
                        fontSize = 17.sp,
                        color = trump.color,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            game.phase == ShelemPhase.BIDDING -> Text(
                text = "در انتظار شرط…",
                style = MaterialTheme.typography.titleSmall,
                color = TablePillCream.copy(alpha = 0.7f),
            )

            else -> Text(
                text = "در انتظار حکم…",
                style = MaterialTheme.typography.titleSmall,
                color = TablePillCream.copy(alpha = 0.7f),
            )
        }
    }
}

// ----------------------------------------------------------------------
// صندلی‌ها
// ----------------------------------------------------------------------

private fun seatIsTurn(state: ShelemUiState, game: ShelemState, seat: Int): Boolean =
    !state.dealing && when (game.phase) {
        ShelemPhase.BIDDING -> game.bidTurn == seat
        ShelemPhase.DISCARDING, ShelemPhase.TRUMP -> game.declarer == seat
        ShelemPhase.PLAYING -> game.turn == seat && game.trickWinner == null
        else -> false
    }

/** متن قرص یک صندلی: اسم + (برای حاکم) شرطش */
private fun seatPillName(state: ShelemUiState, game: ShelemState, seat: Int): String {
    val base = if (seat == state.mySeat) {
        if (state.netMode) state.seatName(seat) else state.playerName.trim().ifBlank { "شما" }
    } else if (seat == ShelemRules.partnerOf(state.mySeat)) {
        "یار: ${state.seatName(seat)}"
    } else {
        state.seatName(seat)
    }
    val declared = game.declarer == seat && game.phase != ShelemPhase.BIDDING && game.contract > 0
    return if (declared) "$base · شرط ${game.contract.toPersianDigits()}" else base
}

/** قرص چوبی یک صندلی با نشانِ امتیازِ زنده‌ی تیمش (به رنگ تیم) */
@Composable
private fun SeatPill(
    state: ShelemUiState,
    game: ShelemState,
    seat: Int,
    vertical: Boolean = false,
    rotate: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val team = ShelemRules.teamOf(seat)
    // ویدو فقط وقتی به حساب می‌آید که خودِ انسان حاکم باشد (خوابیده‌های ربات مخفی است)
    val pts = game.livePoints(team, includeKitty = game.declarer == state.mySeat)
    TablePill(
        name = seatPillName(state, game, seat),
        crowned = game.declarer == seat && game.phase != ShelemPhase.BIDDING,
        badge = pts.toPersianDigits(),
        glowing = seatIsTurn(state, game, seat),
        vertical = vertical,
        rotate = rotate,
        badgeColor = shelemTeamColor(team).copy(alpha = 0.92f),
        modifier = modifier,
    )
}

/** حباب کنار صندلی: «۱۲۰»، «پاس» یا سه نقطه‌ی فکر کردن — قرص چوبی کوچک */
@Composable
private fun SeatBubble(bubble: String?, thinking: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = bubble != null || thinking,
        modifier = modifier,
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
        val border = when {
            thinking -> TablePillGold.copy(alpha = 0.5f)
            isPass -> TablePillGold.copy(alpha = 0.7f)
            else -> TableGlowCyan
        }
        Box(
            modifier = Modifier
                .background(TablePillBrown, RoundedCornerShape(12.dp))
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (thinking || isPass) TablePillCream.copy(alpha = 0.8f) else Color.White,
            )
        }
    }
}

// ----------------------------------------------------------------------
// دستِ جاری وسط میز
// ----------------------------------------------------------------------

/** جای کارت هر صندلی وسط میز (نسبت به مرکز، dp): پایین=۰، راست=۱، بالا=۲، چپ=۳ */
private fun shelemSlotOffset(seat: Int): Pair<Float, Float> = when (seat) {
    0 -> 0f to 56f
    1 -> 66f to -8f
    2 -> 0f to -66f
    else -> -66f to -8f
}

private fun shelemSlotRotation(seat: Int): Float = when (seat) {
    0 -> -8f
    1 -> 10f
    2 -> 6f
    else -> -6f
}

/** چهار جایگاه کارت دور مرکز — کارت‌ها از سمت صندلی پرواز می‌کنند */
@Composable
private fun TrickArea(game: ShelemState, me: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(280.dp, 240.dp), contentAlignment = Alignment.Center) {
        for (seat in 0 until ShelemRules.PLAYERS) {
            val card = game.trickCardOf(seat)
            val winner = game.trickWinner == seat
            // جای کارت نسبت به خودم: پایین=۰، راست=۱، بالا=۲، چپ=۳
            val slot = (seat - me + ShelemRules.PLAYERS) % ShelemRules.PLAYERS
            val (dx, dy) = shelemSlotOffset(slot)
            FlyingTrickCard(
                card = card,
                seat = slot,
                width = 92.dp,
                highlighted = winner,
                modifier = Modifier.align(Alignment.Center).offset(x = dx.dp, y = dy.dp),
            )
        }
    }
}

@Composable
private fun FlyingTrickCard(card: Card?, seat: Int, width: Dp, highlighted: Boolean, modifier: Modifier) {
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
        PlayingCard(card = card, width = width, rotation = shelemSlotRotation(seat), highlighted = highlighted)
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
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeatPill(state = state, game = game, seat = state.mySeat)
        Spacer(modifier = Modifier.width(6.dp))
        SeatBubble(bubble = state.bidBubbles[state.mySeat], thinking = false)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = humanHint(state, game, humanTurn),
            style = MaterialTheme.typography.labelLarge,
            color = if (humanTurn) Color.White else TablePillCream.copy(alpha = 0.9f),
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
        game.trickWinner != null -> "دست رو ${state.seatName(game.trickWinner)} برد" + if (ShelemRules.teamOf(game.trickWinner) == state.myTeam) " 🎉" else ""
        humanTurn -> if (game.trick.isEmpty()) "نوبت توئه — شروع کن!" else "نوبت توئه!"
        else -> "نوبت ${state.seatName(game.turn)}"
    }
    else -> ""
}

/** دست باز انسان: بادبزن دو ردیفه هنگام بازی؛ بادبزن انتخابی هنگام خواباندن */
@Composable
private fun HumanHand(
    state: ShelemUiState,
    game: ShelemState,
    legal: Set<Card>,
    humanTurn: Boolean,
    onPlay: (Card) -> Unit,
    onToggleDiscard: (Card) -> Unit,
) {
    val hand = game.hands[state.mySeat]
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

    val discarding = game.phase == ShelemPhase.DISCARDING && game.declarer == state.mySeat
    if (discarding) {
        SelectableFan(
            cards = hand,
            selected = state.selectedDiscards,
            fresh = game.kitty.toSet(),
            onCardClick = onToggleDiscard,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    } else {
        HandFan(
            cards = shown,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            playable = if (game.phase == ShelemPhase.PLAYING && humanTurn) legal else null,
            maxCardWidth = 72.dp,
            onCardClick = if (game.phase == ShelemPhase.PLAYING && humanTurn) onPlay else null,
        )
    }
}

/**
 * بادبزن با انتخاب چندتایی برای خواباندن: کارت‌های انتخاب‌شده بالا می‌آیند و
 * چهار کارتِ تازه‌ی ویدو با نشان کوچک مشخص‌اند. ۱۶ کارت خودکار دو ردیفه می‌شود
 * (همان قاعده‌ی بادبزن اصلی) تا کارت‌ها روی هم نیفتند.
 */
@Composable
private fun SelectableFan(
    cards: List<Card>,
    selected: Set<Card>,
    fresh: Set<Card>,
    onCardClick: (Card) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = TableGlowCyan
    val maxCardWidth = 66.dp
    val maxPerRow = 9
    val twoRows = cards.size > maxPerRow
    val fanHeight = if (twoRows) maxCardWidth * 1.4f * 1.6f + 22.dp else maxCardWidth * 1.4f + 22.dp
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(fanHeight),
    ) {
        val cardW = minOf(maxCardWidth, maxWidth / 4)
        val rows: List<Pair<List<Card>, Dp>> = if (!twoRows) {
            listOf(cards to 0.dp)
        } else {
            val upper = cards.take((cards.size + 1) / 2)
            listOf(
                upper to -(cardW * 1.4f * 0.6f),
                cards.drop(upper.size) to 0.dp,
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            rows.forEach { (rowCards, rowY) ->
                val n = rowCards.size
                val step = if (n <= 1) 0.dp else minOf(cardW * 0.62f, (maxWidth - cardW) / (n - 1))
                val totalW = cardW + step * (n - 1)
                val startX = (maxWidth - totalW) / 2
                rowCards.forEachIndexed { i, card ->
                    val mid = (n - 1) / 2f
                    val tilt = if (n > 1) (i - mid) * (8f / maxOf(1f, mid)) else 0f
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = startX + step * i, y = rowY),
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
}
