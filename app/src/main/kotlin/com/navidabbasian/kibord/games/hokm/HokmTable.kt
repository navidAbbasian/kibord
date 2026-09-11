package com.navidabbasian.kibord.games.hokm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.GlowChip
import com.navidabbasian.kibord.core.cards.HandFan
import com.navidabbasian.kibord.core.cards.OrnateCardBack
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.SideBackFan
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.TableGlowCyan
import com.navidabbasian.kibord.core.cards.TablePill
import com.navidabbasian.kibord.core.cards.TablePillBrown
import com.navidabbasian.kibord.core.cards.TablePillCream
import com.navidabbasian.kibord.core.cards.TablePillGold
import com.navidabbasian.kibord.core.cards.TableBadgeBlue
import com.navidabbasian.kibord.core.cards.TopBackFan
import com.navidabbasian.kibord.core.cards.WonTrickPile
import com.navidabbasian.kibord.core.cards.WoodPill
import com.navidabbasian.kibord.core.cards.WoodPlankBackground
import com.navidabbasian.kibord.core.cards.color
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.hokm.engine.HokmPhase
import com.navidabbasian.kibord.games.hokm.engine.HokmRules
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import com.navidabbasian.kibord.games.hokm.engine.MordabadiRules
import com.navidabbasian.kibord.games.hokm.engine.TrickCard
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- رنگ‌های میز چوبی (مشترک در TableUi)

private val PillBrown = TablePillBrown
private val PillGold = TablePillGold
private val PillCream = TablePillCream
private val BadgeBlue = TableBadgeBlue
private val GlowCyan = TableGlowCyan
private val CreditGreen = Color(0xFF6FE3A5)
private val DebtRed = Color(0xFFFF9B8E)

/** جای هر صندلی دور میز */
private enum class TablePos { BOTTOM, RIGHT, TOP, LEFT }

/** جای صندلی [seat] وقتی صندلی [me] پایینِ صفحه نشسته (منفی = تماشاگر، از دید صندلی ۰) */
private fun tablePos(variant: HokmVariant, seat: Int, me: Int): TablePos {
    val n = variant.playerCount
    val anchor = if (me in 0 until n) me else 0
    val idx = ((seat - anchor) % n + n) % n
    return when (variant) {
        HokmVariant.FOUR -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.TOP, TablePos.LEFT)[idx]
        HokmVariant.THREE -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.LEFT)[idx]
        HokmVariant.TWO -> listOf(TablePos.BOTTOM, TablePos.TOP)[idx]
    }
}

/** جای کارت هر صندلی وسط میز (نسبت به مرکز، dp) */
private fun slotOffset(pos: TablePos): Pair<Float, Float> = when (pos) {
    TablePos.BOTTOM -> 0f to 56f
    TablePos.RIGHT -> 66f to -8f
    TablePos.TOP -> 0f to -66f
    TablePos.LEFT -> -66f to -8f
}

/** از کجا کارت به میز پرواز می‌کند / به کجا جمع می‌شود */
private fun farOffset(pos: TablePos): Pair<Float, Float> = when (pos) {
    TablePos.BOTTOM -> 0f to 320f
    TablePos.RIGHT -> 250f to -60f
    TablePos.TOP -> 0f to -260f
    TablePos.LEFT -> -250f to -60f
}

private fun slotRotation(pos: TablePos): Float = when (pos) {
    TablePos.BOTTOM -> -8f
    TablePos.RIGHT -> 10f
    TablePos.TOP -> 6f
    TablePos.LEFT -> -6f
}

// ---------------------------------------------------------------- صفحه‌ی میز

/** صفحه‌ی میز حکم — میز چوبی گرم با بادبزن‌های پشتِ کارت */
@Composable
internal fun HokmPlayScreen(state: HokmUiState, viewModel: HokmViewModel) {
    val game = state.game ?: return
    /** صندلی خودم در وضعیت جاری؛ منفی یعنی فقط تماشا می‌کنم (دوئلِ بقیه) */
    val me = state.mySeatInGame
    val humanPlays = me >= 0
    val myHand = game.hands.getOrElse(me) { emptyList() }
    val myTurn = game.phase == HokmPhase.PLAYING && game.turn == me && humanPlays &&
        !game.trickComplete && state.exchangeFx == null && !state.collectionBanner
    val legal = remember(game, myTurn) { if (myTurn) HokmRules.legalMoves(game, me).toSet() else null }
    val debtorPicking = state.debtorPick != null
    val mordabadi = game.isMordabadi && game.quotas.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        WoodPlankBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TopStrip(
                state = state,
                game = game,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 14.dp, top = 6.dp),
            )

            HokmTableArea(
                state = state,
                game = game,
                humanPlays = humanPlays,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            HumanRow(state = state, game = game, myTurn = myTurn, mordabadi = mordabadi)
            HandFan(
                cards = myHand,
                playable = when {
                    debtorPicking && state.debtorPick?.debtor == state.mySeat -> myHand.toSet()
                    else -> legal
                },
                selected = state.receivedCard,
                maxCardWidth = if (mordabadi) 72.dp else 74.dp,
                onCardClick = when {
                    debtorPicking && state.debtorPick?.debtor == state.mySeat -> ({ card -> viewModel.giveDebtCard(card) })
                    myTurn -> ({ card -> viewModel.playCard(card) })
                    else -> null
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        GameHelpButton(gameId = "hokm", modifier = Modifier.align(Alignment.TopStart))

        // ---- پیام گذرا (اعلام حکم / وصول طلب) ----
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 78.dp),
        ) {
            AnimatedVisibility(
                visible = state.notice != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut(),
            ) {
                val text = state.notice ?: ""
                Box(
                    modifier = Modifier
                        .background(PillBrown, RoundedCornerShape(18.dp))
                        .border(1.dp, PillGold, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = text, color = PillCream, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // ---- بنر شروع فاز وصول (مردابادی) ----
        AnimatedVisibility(
            visible = state.collectionBanner,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            GlowChip(glowing = true) {
                Text(
                    text = "💰 وصول طلب‌ها",
                    style = MaterialTheme.typography.titleMedium,
                    color = PillCream,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // ---- انتخاب حکم توسط بازیکن ----
        if (game.phase == HokmPhase.CHOOSE_TRUMP && game.hakem == me && humanPlays) {
            TrumpChoiceSheet(game = game, cards = myHand, onChoose = viewModel::chooseTrump)
        }

        // ---- وصول طلب (مردابادی): نوبت طلبکارِ انسانی ----
        if (game.phase == HokmPhase.COLLECTION && MordabadiRules.collector(game) == state.mySeat &&
            !debtorPicking && !state.collectionBanner && state.exchangeFx == null
        ) {
            CollectionSheet(state = state, game = game, me = state.mySeat, onExchange = viewModel::humanExchange)
        }

        // ---- پایان دست ----
        if (game.phase == HokmPhase.HAND_OVER) {
            if (game.isMordabadi) {
                MordabadiHandOverOverlay(state = state, game = game, onNext = viewModel::nextHand)
            } else {
                HandOverOverlay(state = state, game = game, onNext = viewModel::nextHand)
            }
        }
    }
}

// ---------------------------------------------------------------- نوار بالا

/** نوار باریک بالای میز: امتیاز مسابقه و شماره‌ی دست به شکل قرص‌های چوبی */
@Composable
private fun TopStrip(state: HokmUiState, game: HokmState, modifier: Modifier = Modifier) {
    val mordabadi = game.isMordabadi && game.quotas.isNotEmpty()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mordabadi) {
            WoodPill(text = "سقف بدهی ${game.debtLimit.toPersianDigits()}")
            WoodPill(text = "دست ${game.handNumber.toPersianDigits()}")
        } else {
            WoodPill(
                text = (0 until game.variant.teamCount).joinToString(" – ") {
                    "${state.teamName(it)} ${game.scores[it].toPersianDigits()}"
                },
            )
            WoodPill(text = "تا ${game.target.toPersianDigits()} • دست ${game.handNumber.toPersianDigits()}")
        }
    }
}

// ---------------------------------------------------------------- میز

@Composable
private fun HokmTableArea(state: HokmUiState, game: HokmState, humanPlays: Boolean, modifier: Modifier = Modifier) {
    val variant = game.variant
    val mordabadi = game.isMordabadi && game.quotas.isNotEmpty()
    // چیدمان میز چپ‌به‌راست است تا راست/چپ واقعی باشند؛ متن‌ها خودشان راست‌چین می‌شوند
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.padding(vertical = 2.dp)) {
            // چیپ حکم بالای میز
            TrumpChip(
                trump = game.trump,
                modifier = Modifier
                    .align(BiasAlignment(0f, -0.98f))
                    .padding(top = 2.dp),
            )

            // نشانگر کوچکِ ماندگار فاز وصول
            if (game.phase == HokmPhase.COLLECTION) {
                WoodPill(
                    text = "💰 وصول طلب‌ها…",
                    modifier = Modifier.align(BiasAlignment(0f, -0.74f)),
                )
            }

            // حریف‌ها: بادبزنِ پشتِ کارت + قرص اسم + ستون دست‌های برده
            val me = state.mySeatInGame
            for (seat in 0 until variant.playerCount) {
                if (seat == me) continue
                val pos = tablePos(variant, seat, me)
                OpponentSide(
                    state = state,
                    game = game,
                    seat = seat,
                    pos = pos,
                    mordabadi = mordabadi,
                    boxScope = this,
                )
            }

            // دسته‌های دستِ برده
            TrickPiles(state = state, game = game, mordabadi = mordabadi, boxScope = this)

            // وسط میز
            TrickArea(
                state = state,
                game = game,
                humanPlays = humanPlays,
                modifier = Modifier.align(BiasAlignment(0f, 0.22f)),
            )

            // تبادلِ وصول: دو کارت بین دو صندلی پرواز می‌کنند
            state.exchangeFx?.let { fx ->
                ExchangeFlight(
                    fx = fx,
                    variant = variant,
                    me = state.mySeat,
                    modifier = Modifier.align(BiasAlignment(0f, 0.22f)),
                )
            }
        }
    }
}

/** یک حریف: بادبزن پشتِ کارت‌ها کنار لبه + قرص اسم (عمودی در کناره‌ها) */
@Composable
private fun OpponentSide(
    state: HokmUiState,
    game: HokmState,
    seat: Int,
    pos: TablePos,
    mordabadi: Boolean,
    boxScope: androidx.compose.foundation.layout.BoxScope,
) {
    val isTurn = game.phase == HokmPhase.PLAYING && game.turn == seat && !game.trickComplete
    val me = state.mySeatInGame
    val isPartner = me >= 0 && game.variant.partnerOf(me) == seat
    val name = if (isPartner) "شریک شما" else state.nameOf(seat)
    val crowned = game.hakem == seat
    val count = game.hands[seat].size
    val badge = if (mordabadi) {
        "${game.tricksWon[seat].toPersianDigits()} از ${game.quotaOf(seat).toPersianDigits()}"
    } else {
        game.tricksWon[seat].toPersianDigits()
    }
    with(boxScope) {
        when (pos) {
            TablePos.TOP -> {
                TopBackFan(
                    count = count,
                    modifier = Modifier.align(BiasAlignment(0f, -0.62f)),
                )
                TablePill(
                    name = name,
                    crowned = crowned,
                    badge = badge,
                    glowing = isTurn,
                    modifier = Modifier.align(BiasAlignment(0f, -0.30f)),
                )
            }

            TablePos.RIGHT, TablePos.LEFT -> {
                val edge = if (pos == TablePos.RIGHT) 1f else -1f
                SideBackFan(
                    count = count,
                    rightSide = pos == TablePos.RIGHT,
                    modifier = Modifier.align(BiasAlignment(edge, -0.35f)),
                )
                TablePill(
                    name = name,
                    crowned = crowned,
                    badge = badge,
                    glowing = isTurn,
                    vertical = true,
                    rotate = if (pos == TablePos.RIGHT) -90f else 90f,
                    modifier = Modifier.align(BiasAlignment(edge * 0.94f, 0.28f)),
                )
            }

            TablePos.BOTTOM -> Unit
        }
    }
}

/** چیپ‌های مردابادی زیر قرص اسم: تراز (طلب سبز / بدهی قرمز) + جمعِ بدهی — فقط برای خودِ بازیکن */
@Composable
private fun MordabadiChips(game: HokmState, seat: Int, modifier: Modifier = Modifier) {
    val balance = game.balances.getOrElse(seat) { 0 }
    val totalDebt = game.totalDebts.getOrElse(seat) { 0 }
    val (text, color) = when {
        balance > 0 -> "طلب ${balance.toPersianDigits()}" to CreditGreen
        balance < 0 -> "بدهی ${(-balance).toPersianDigits()}" to DebtRed
        else -> "تراز ۰" to PillCream
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(PillBrown, ChipShape)
                .border(1.dp, color.copy(alpha = 0.8f), ChipShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .background(PillBrown, ChipShape)
                .border(1.dp, PillGold.copy(alpha = 0.5f), ChipShape)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "جمع بدهی ${totalDebt.toPersianDigits()}",
                style = MaterialTheme.typography.labelSmall,
                color = PillCream,
                maxLines = 1,
            )
        }
    }
}

/** چیپ حکم: قرص قهوه‌ای با حاشیه‌ی درخشان فیروزه‌ای و خال داخل دایره‌ی سفید */
@Composable
private fun TrumpChip(trump: Suit?, modifier: Modifier = Modifier) {
    val declared = trump != null
    GlowChip(glowing = declared, modifier = modifier) {
        if (declared && trump != null) {
            Text(
                text = "حکــم :",
                style = MaterialTheme.typography.titleSmall,
                color = PillGold,
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
        } else {
            Text(
                text = "در انتظار حکم…",
                style = MaterialTheme.typography.titleSmall,
                color = PillCream.copy(alpha = 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------- دسته‌های دستِ برده

/** جای دسته‌ها: چهار نفره یکی برای هر تیم؛ دو/سه نفره برای هر بازیکن */
@Composable
private fun TrickPiles(
    state: HokmUiState,
    game: HokmState,
    mordabadi: Boolean,
    boxScope: androidx.compose.foundation.layout.BoxScope,
) {
    if (game.phase != HokmPhase.PLAYING && game.phase != HokmPhase.HAND_OVER) return
    with(boxScope) {
        when (game.variant) {
            HokmVariant.FOUR -> {
                val myTeam = state.myTeam
                WonTrickPile(count = game.teamTricks(myTeam), modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)))
                WonTrickPile(count = game.teamTricks(1 - myTeam), modifier = Modifier.align(BiasAlignment(0.92f, -0.86f)))
            }

            HokmVariant.TWO -> {
                val me = state.mySeatInGame.coerceIn(0, 1)
                WonTrickPile(count = game.tricksWon[me], modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)))
                WonTrickPile(count = game.tricksWon[1 - me], modifier = Modifier.align(BiasAlignment(0.92f, -0.86f)))
            }

            HokmVariant.THREE -> {
                val me = state.mySeatInGame
                for (seat in 0..2) {
                    val bias = when (tablePos(HokmVariant.THREE, seat, me)) {
                        TablePos.BOTTOM -> BiasAlignment(-0.92f, 0.94f)
                        TablePos.RIGHT -> BiasAlignment(0.70f, 0.62f)
                        else -> BiasAlignment(-0.70f, 0.62f)
                    }
                    WonTrickPile(count = game.tricksWon[seat], modifier = Modifier.align(bias))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ردیف بازیکن

/** ردیف اطلاعات بازیکن: قرص «شما» با تاج و نشانِ دست‌ها + چیپ نوبت */
@Composable
private fun HumanRow(state: HokmUiState, game: HokmState, myTurn: Boolean, mordabadi: Boolean) {
    val me = state.mySeatInGame
    if (me < 0) {
        Text(
            text = "حذف شدی — دوئل بقیه رو تماشا می‌کنی 👀",
            style = MaterialTheme.typography.labelLarge,
            color = PillCream,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        return
    }
    val name = if (state.netMode || state.duelSeats != null) state.nameOf(me) else state.playerName.trim().ifBlank { "شما" }
    val badge = if (mordabadi) {
        "${game.tricksWon[me].toPersianDigits()} از ${game.quotaOf(me).toPersianDigits()}"
    } else {
        game.tricksWon[me].toPersianDigits()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TablePill(
                name = name,
                crowned = game.hakem == me,
                badge = badge,
                glowing = myTurn,
            )
            if (mordabadi) {
                Spacer(modifier = Modifier.width(6.dp))
                MordabadiChips(game = game, seat = me)
            }
        }
        AnimatedVisibility(visible = myTurn, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .breathing(intensity = 0.04f, periodMs = 1200)
                    .background(BadgeBlue, ChipShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), ChipShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "نوبت توئه! ☝️",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- کارت‌های وسط

@Composable
private fun TrickArea(state: HokmUiState, game: HokmState, humanPlays: Boolean, modifier: Modifier = Modifier) {
    val variant = game.variant
    val winnerSeat = if (state.sweeping) game.trickLeader else null
    Box(modifier = modifier.size(280.dp, 240.dp), contentAlignment = Alignment.Center) {
        // راهنمای وسط میز وقتی خالی است
        if (game.trick.isEmpty() && game.phase == HokmPhase.PLAYING &&
            state.exchangeFx == null && !state.collectionBanner
        ) {
            CenterHint(state = state, game = game, humanPlays = humanPlays)
        }
        val me = state.mySeatInGame
        if (game.phase == HokmPhase.CHOOSE_TRUMP && game.hakem != me) {
            WaitingBubble(text = "${state.nameOf(game.hakem)} داره حکم می‌کنه… 🤔")
        }
        if (game.phase == HokmPhase.COLLECTION) {
            val c = MordabadiRules.collector(game)
            if (state.debtorPick != null) {
                WaitingBubble(text = "یه کارت بده جای بدهیت 👇")
            } else if (c != null && c != state.mySeat && state.exchangeFx == null && !state.collectionBanner) {
                WaitingBubble(text = "${state.nameOf(c)} داره طلبش رو وصول می‌کنه… 💰")
            }
        }
        game.trick.forEach { tc ->
            key(tc.card.id, game.handNumber) {
                FlyingTrickCard(
                    tc = tc,
                    pos = tablePos(variant, tc.seat, me),
                    sweepTo = winnerSeat?.let { tablePos(variant, it, me) },
                )
            }
        }
    }
}

/** یک کارت روی میز: از سمت صاحبش پرواز می‌کند و آخر به سمت برنده جمع می‌شود */
@Composable
private fun FlyingTrickCard(tc: TrickCard, pos: TablePos, sweepTo: TablePos?) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
    val sweep by animateFloatAsState(
        targetValue = if (sweepTo != null) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "sweep",
    )
    val (sx, sy) = slotOffset(pos)
    val (fx, fy) = farOffset(pos)
    val (wx, wy) = sweepTo?.let { farOffset(it) } ?: (sx to sy)
    val e = enter.value
    // مسیر: از دور → جای خودش → (موقع جمع‌کردن) به سمت برنده
    val baseX = fx + (sx - fx) * e
    val baseY = fy + (sy - fy) * e
    val x = baseX + (wx - baseX) * sweep
    val y = baseY + (wy - baseY) * sweep
    PlayingCard(
        card = tc.card,
        width = 92.dp,
        rotation = slotRotation(pos) * (1f - sweep * 0.5f),
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .alpha((1f - sweep * 0.9f).coerceIn(0.1f, 1f)),
    )
}

// ---------------------------------------------------------------- پرواز تبادل وصول

/**
 * انیمیشن یک تبادلِ وصول: دو کارت بین صندلی طلبکار و بدهکار پرواز می‌کنند.
 * کارت‌ها پشت‌به‌بالا هستند مگر خودِ بازیکن یک طرف تبادل باشد (کارتِ خودش رو دیده می‌شود).
 */
@Composable
private fun ExchangeFlight(
    fx: ExchangeFx,
    variant: HokmVariant,
    me: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(280.dp, 240.dp), contentAlignment = Alignment.Center) {
        FlyingSwapCard(
            from = tablePos(variant, fx.collector, me),
            to = tablePos(variant, fx.debtor, me),
            face = fx.collectorFace?.takeIf { fx.collector == me },
            fxId = fx.id,
            startDelayMs = 0,
        )
        FlyingSwapCard(
            from = tablePos(variant, fx.debtor, me),
            to = tablePos(variant, fx.collector, me),
            face = fx.debtorFace?.takeIf { fx.debtor == me },
            fxId = fx.id,
            startDelayMs = 320,
        )
    }
}

@Composable
private fun FlyingSwapCard(from: TablePos, to: TablePos, face: Card?, fxId: Long, startDelayMs: Int) {
    val t = remember(fxId, startDelayMs) { Animatable(0f) }
    LaunchedEffect(fxId, startDelayMs) {
        t.snapTo(0f)
        delay(startDelayMs.toLong())
        t.animateTo(1f, tween(1150, easing = FastOutSlowInEasing))
    }
    val (ax, ay) = farOffset(from)
    val (bx, by) = farOffset(to)
    val p = t.value
    val x = ax + (bx - ax) * p
    val y = ay + (by - ay) * p
    val a = when {
        p < 0.1f -> p / 0.1f
        p > 0.88f -> ((1f - p) / 0.12f)
        else -> 1f
    }
    Box(modifier = Modifier.offset(x = x.dp, y = y.dp).alpha(a.coerceIn(0f, 1f))) {
        if (face != null) {
            PlayingCard(card = face, width = 58.dp)
        } else {
            OrnateCardBack(width = 44.dp)
        }
    }
}

@Composable
private fun CenterHint(state: HokmUiState, game: HokmState, humanPlays: Boolean) {
    val me = state.mySeatInGame
    val text = when {
        game.turn == me && humanPlays ->
            if (game.hakem == me && game.played.isEmpty()) "تو حاکمی — شروع کن! 👑" else "نوبت توئه — یه کارت بنداز"
        else -> "${state.nameOf(game.turn)} داره فکر می‌کنه…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (game.turn == me && humanPlays) Color.White else PillCream.copy(alpha = 0.9f),
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(PillBrown.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun WaitingBubble(text: String) {
    val transition = rememberInfiniteTransition(label = "wait")
    val a by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "wait_a",
    )
    Box(
        modifier = Modifier
            .alpha(a)
            .background(PillBrown, RoundedCornerShape(18.dp))
            .border(1.dp, PillGold, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = PillCream,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- انتخاب حکم

@Composable
private fun TrumpChoiceSheet(game: HokmState, cards: List<Card>, onChoose: (Suit) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val cardWidth = if (cards.size > 6) 34.dp else 54.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        TicketCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            accent = accent,
            tilt = -1f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StickerTitle(text = "تو حاکمی! حکم چیه؟", fontSize = 22.sp, rotation = -1.5f)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (game.isMordabadi) "از روی این نُه کارت تصمیم بگیر" else "از روی این پنج کارت تصمیم بگیر",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        cards.forEachIndexed { i, card ->
                            PlayingCard(card = card, width = cardWidth, rotation = (i - cards.size / 2) * 2f)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Suit.entries.forEach { suit ->
                        val count = cards.count { it.suit == suit }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .breathing(intensity = 0.03f, periodMs = 2200, phase = suit.ordinal * 0.9f)
                                    .background(Color(0xFFFFFDF7), CircleShape)
                                    .border(2.dp, accent.copy(alpha = 0.7f), CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        sound?.playButtonClick()
                                        onChoose(suit)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = suit.symbol, fontSize = 32.sp, color = suit.color, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = suit.persian + if (count > 0) " (${count.toPersianDigits()})" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (game.isMordabadi) {
                        "بعدش بقیه‌ی کارت‌ها پخش می‌شه و طلبکارها وصول می‌کنن"
                    } else {
                        "بعدش بقیه‌ی کارت‌ها پخش می‌شه و تو شروع می‌کنی"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.warning,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- وصول طلب (مردابادی)

/** شیت وصول برای طلبکار انسانی: انتخاب بدهکار + خال (یا حکم‌خواهی با ۳ طلب) */
@Composable
private fun CollectionSheet(state: HokmUiState, game: HokmState, me: Int, onExchange: (Int, Suit) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val debtors = MordabadiRules.debtors(game)
    val suits = MordabadiRules.availableSuits(game, me)
    var debtor by remember(game) { mutableStateOf(debtors.minByOrNull { game.balances[it] } ?: -1) }
    if (debtor !in debtors && debtors.isNotEmpty()) debtor = debtors.first()
    val trump = game.trump
    val canTrump = debtor >= 0 && MordabadiRules.canDemandTrump(game, me, debtor)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        TicketCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            accent = accent,
            tilt = 1f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StickerTitle(text = "طلبت رو وصول کن! 💰", fontSize = 22.sp, rotation = -1.5f)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "طلب تو: ${game.balances[me].toPersianDigits()} — " +
                        "یه خال انتخاب کن: پایین‌ترینش رو می‌دی و بالاترینش رو می‌گیری",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // انتخاب بدهکار (وقتی دو بدهکار هست) — بدون عدد؛ بدهی بقیه محرمانه است
                if (debtors.size > 1) {
                    Text(
                        text = "از کی بگیری؟",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        debtors.forEach { d ->
                            val selected = d == debtor
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (selected) accent else extras.glassStrong,
                                        RoundedCornerShape(14.dp),
                                    )
                                    .border(1.dp, extras.glassBorderStrong, RoundedCornerShape(14.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { debtor = d }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "${state.nameOf(d)} (بدهکار)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // خال‌های غیرحکم
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Suit.entries.filter { it != trump }.forEach { suit ->
                        val enabled = suit in suits && debtor >= 0
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .alpha(if (enabled) 1f else 0.35f)
                                    .background(Color(0xFFFFFDF7), CircleShape)
                                    .border(2.dp, accent.copy(alpha = 0.7f), CircleShape)
                                    .then(
                                        if (enabled) {
                                            Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                sound?.playButtonClick()
                                                onExchange(debtor, suit)
                                            }
                                        } else Modifier,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = suit.symbol, fontSize = 30.sp, color = suit.color, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = suit.persian,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // حکم‌خواهی
                if (trump != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .alpha(if (canTrump) 1f else 0.4f)
                            .background(if (canTrump) accent else extras.glassStrong, RoundedCornerShape(16.dp))
                            .border(1.dp, extras.glassBorderStrong, RoundedCornerShape(16.dp))
                            .then(
                                if (canTrump) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        sound?.playButtonClick()
                                        onExchange(debtor, trump)
                                    }
                                } else Modifier,
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "حکم ${trump.symbol} (۳ طلب)",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (canTrump) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "اگه بدهکار اون خال رو نداشته باشه، هر کارتی که بخواد می‌ده",
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.warning,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- پایان دست

@Composable
private fun HandOverOverlay(state: HokmUiState, game: HokmState, onNext: () -> Unit) {
    val result = game.lastResult ?: return
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val variant = game.variant
    val plural = variant == HokmVariant.FOUR
    val myTeam = state.humanTeam
    val won = result.winnerTeam == myTeam
    val tie = result.winnerTeam == null

    val title = when {
        tie -> "مساوی شد!"
        won -> if (plural) "دست رو بردید!" else "دست رو بردی!"
        else -> if (plural) "دست رو باختید" else "دست رو باختی"
    }
    val emoji = when {
        tie -> "🤝"
        won && result.kot -> "🔥"
        won -> "🎉"
        result.hakemKot -> "💥"
        else -> "😬"
    }
    val winnerName = result.winnerTeam?.let { state.teamName(it) }
    val subtitle = when {
        tie -> "کسی به هفت نرسید و دست‌ها برابر شد — امتیازی رد و بدل نشد"
        result.hakemKot -> "حاکم‌کُت! $winnerName هفت‌هیچ حاکم رو کُت کرد — ${result.points.toPersianDigits()} امتیاز"
        result.kot -> "کُت! هفت‌هیچ — ${result.points.toPersianDigits()} امتیاز برای $winnerName"
        else -> "${result.points.toPersianDigits()} امتیاز برای $winnerName"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        TicketCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            accent = accent,
            golden = won && result.kot,
            tilt = 1.2f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = emoji, fontSize = 44.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = title, fontSize = 24.sp, rotation = -2f)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ResultLine(
                    label = "دست‌ها",
                    value = (0 until variant.teamCount).joinToString(" – ") {
                        "${state.teamName(it)} ${result.teamTricks[it].toPersianDigits()}"
                    },
                )
                ResultLine(
                    label = "امتیاز",
                    value = (0 until variant.teamCount).joinToString(" – ") {
                        "${state.teamName(it)} ${game.scores[it].toPersianDigits()}"
                    },
                )
                ResultLine(
                    label = "حاکم بعدی",
                    value = "👑 " + state.nameOf(result.hakemAfter) +
                        if (result.hakemAfter == result.hakemBefore) " (می‌مونه)" else "",
                )
                Spacer(modifier = Modifier.height(16.dp))
                KButton(text = "دست بعدی 🃏", onClick = onNext)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "تا ${game.target.toPersianDigits()} امتیاز",
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.warning,
                )
            }
        }
    }
}

/** پایان دستِ مردابادی: تسویه‌ی سهمیه‌ها؛ تراز و جمع‌بدهی فقط برای خودِ بازیکن نمایش داده می‌شود */
@Composable
private fun MordabadiHandOverOverlay(state: HokmUiState, game: HokmState, onNext: () -> Unit) {
    val result = game.lastResult ?: return
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val eliminated = result.eliminatedSeat
    val me = state.mySeat
    val myDelta = result.deltas.getOrElse(me) { 0 }

    val title = when {
        eliminated == me -> "حذف شدی! 🚫"
        eliminated != null -> "${state.mordabadiNameOf(eliminated)} حذف شد!"
        myDelta > 0 -> "طلبکار شدی!"
        myDelta < 0 -> "بدهکار شدی…"
        else -> "سر به سر!"
    }
    val emoji = when {
        eliminated != null -> "⚔️"
        myDelta > 0 -> "🤑"
        myDelta < 0 -> "😬"
        else -> "😌"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        TicketCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            accent = accent,
            golden = eliminated != null && eliminated != me,
            tilt = 1.2f,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = emoji, fontSize = 44.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = title, fontSize = 24.sp, rotation = -2f)
                Spacer(modifier = Modifier.height(12.dp))
                for (seat in 0..2) {
                    val delta = result.deltas.getOrElse(seat) { 0 }
                    val deltaText = when {
                        delta > 0 -> "+${delta.toPersianDigits()} طلب"
                        delta < 0 -> "${(-delta).toPersianDigits()}− بدهی"
                        else -> "سر به سر"
                    }
                    // تراز و جمع بدهی فقط برای خودِ بازیکن — حساب بقیه محرمانه است
                    val privateTail = if (seat == me) {
                        " • تراز ${game.balances.getOrElse(seat) { 0 }.toPersianDigits()}" +
                            " • جمع بدهی ${game.totalDebts.getOrElse(seat) { 0 }.toPersianDigits()}"
                    } else ""
                    ResultLine(
                        label = state.mordabadiNameOf(seat) + if (eliminated == seat) " 🚫" else "",
                        value = "${result.teamTricks[seat].toPersianDigits()} دست → $deltaText" + privateTail,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (eliminated == null) {
                    ResultLine(
                        label = "حاکم بعدی",
                        value = "👑 ${state.mordabadiNameOf(result.hakemAfter)} (۹دستی شد)",
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                KButton(
                    text = if (eliminated != null) "دوئل نهایی ⚔️" else "دست بعدی 🃏",
                    onClick = onNext,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (eliminated != null) {
                        "دو بازمانده یه دست حکم دو نفره می‌زنن — برنده‌ش قهرمانه!"
                    } else {
                        "سقف بدهی: ${game.debtLimit.toPersianDigits()} — سهمیه‌ها می‌چرخن (۳→۵→۹→۳)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.warning,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}
