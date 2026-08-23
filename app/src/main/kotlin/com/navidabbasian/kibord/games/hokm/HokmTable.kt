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
import androidx.compose.runtime.remember
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
import com.navidabbasian.kibord.core.cards.HandFan
import com.navidabbasian.kibord.core.cards.HiddenHand
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.color
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
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
import com.navidabbasian.kibord.games.hokm.engine.TrickCard

/** جای هر صندلی دور میز */
private enum class TablePos { BOTTOM, RIGHT, TOP, LEFT }

private fun tablePos(variant: HokmVariant, seat: Int): TablePos = when (variant) {
    HokmVariant.FOUR -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.TOP, TablePos.LEFT)[seat]
    HokmVariant.THREE -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.LEFT)[seat]
    HokmVariant.TWO -> listOf(TablePos.BOTTOM, TablePos.TOP)[seat]
}

/** جای کارت هر صندلی وسط میز (نسبت به مرکز، dp) */
private fun slotOffset(pos: TablePos): Pair<Float, Float> = when (pos) {
    TablePos.BOTTOM -> 0f to 44f
    TablePos.RIGHT -> 60f to -6f
    TablePos.TOP -> 0f to -56f
    TablePos.LEFT -> -60f to -6f
}

/** از کجا کارت به میز پرواز می‌کند / به کجا جمع می‌شود */
private fun farOffset(pos: TablePos): Pair<Float, Float> = when (pos) {
    TablePos.BOTTOM -> 0f to 300f
    TablePos.RIGHT -> 230f to -60f
    TablePos.TOP -> 0f to -240f
    TablePos.LEFT -> -230f to -60f
}

private fun slotRotation(pos: TablePos): Float = when (pos) {
    TablePos.BOTTOM -> -3f
    TablePos.RIGHT -> 9f
    TablePos.TOP -> 4f
    TablePos.LEFT -> -9f
}

/** صفحه‌ی میز حکم */
@Composable
internal fun HokmPlayScreen(state: HokmUiState, viewModel: HokmViewModel) {
    val game = state.game ?: return
    val accent = LocalGameAccent.current
    val myTurn = game.phase == HokmPhase.PLAYING && game.turn == 0 && !game.trickComplete
    val legal = remember(game) { if (myTurn) HokmRules.legalMoves(game, 0).toSet() else null }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ---- نوار امتیاز ----
            Box(modifier = Modifier.fillMaxWidth()) {
                ScoreStrip(
                    state = state,
                    game = game,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp, end = 14.dp, top = 6.dp),
                )
            }

            // ---- میز ----
            HokmTable(
                state = state,
                game = game,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // ---- من ----
            MyRow(state = state, game = game, myTurn = myTurn)
            HandFan(
                cards = game.hands[0],
                playable = legal,
                maxCardWidth = 74.dp,
                onCardClick = if (myTurn) ({ card -> viewModel.playCard(card) }) else null,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        GameHelpButton(gameId = "hokm", modifier = Modifier.align(Alignment.TopStart))

        // ---- پیام گذرا (اعلام حکم) ----
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
                        .background(accent, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(text = text, color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // ---- انتخاب حکم توسط بازیکن ----
        if (game.phase == HokmPhase.CHOOSE_TRUMP && game.hakem == 0) {
            TrumpChoiceSheet(game = game, onChoose = viewModel::chooseTrump)
        }

        // ---- پایان دست ----
        if (game.phase == HokmPhase.HAND_OVER) {
            HandOverOverlay(state = state, game = game, onNext = viewModel::nextHand)
        }
    }
}

// ---------------------------------------------------------------- نوار امتیاز

@Composable
private fun ScoreStrip(state: HokmUiState, game: HokmState, modifier: Modifier = Modifier) {
    val extras = kiExtras
    GlassCard(modifier = modifier, cornerRadius = 18.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (team in 0 until game.variant.teamCount) {
                val color = hokmTeamColor(team, state.humanTeam)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.teamName(team),
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = game.scores[team].toPersianDigits(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (team == (if (game.variant == HokmVariant.THREE) 1 else 0)) {
                    // وسط: حکم و هدف
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TrumpBadge(trump = game.trump)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "تا ${game.target.toPersianDigits()} • دست ${game.handNumber.toPersianDigits()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // شمارنده‌ی دست‌های این دور
        if (game.phase == HokmPhase.PLAYING || game.phase == HokmPhase.HAND_OVER) {
            Text(
                text = "دست‌های این دور: " + (0 until game.variant.teamCount).joinToString(" – ") {
                    "${state.teamName(it)} ${game.teamTricks(it).toPersianDigits()}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extras.glass)
                    .padding(vertical = 3.dp),
                maxLines = 1,
            )
        }
    }
}

/** چیپ حکم: نماد خال با رنگ خودش */
@Composable
private fun TrumpBadge(trump: Suit?) {
    val extras = kiExtras
    Row(
        modifier = Modifier
            .background(extras.glassStrong, ChipShape)
            .border(1.dp, extras.glassBorderStrong, ChipShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "حکم",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        if (trump != null) {
            Text(
                text = trump.symbol,
                fontSize = 20.sp,
                color = trump.color.let { if (trump.isRed) it else MaterialTheme.colorScheme.onSurface },
                fontWeight = FontWeight.Black,
            )
        } else {
            Text(text = "؟", fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- میز

@Composable
private fun HokmTable(state: HokmUiState, game: HokmState, modifier: Modifier = Modifier) {
    val variant = game.variant
    // چیدمان میز چپ‌به‌راست است تا راست/چپ واقعی باشند؛ متن‌ها خودشان راست‌چین می‌شوند
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            // حریف‌ها
            for (seat in 1 until variant.playerCount) {
                val pos = tablePos(variant, seat)
                val alignment = when (pos) {
                    TablePos.TOP -> BiasAlignment(0f, -1f)
                    TablePos.RIGHT -> BiasAlignment(1f, if (variant == HokmVariant.THREE) -0.75f else -0.25f)
                    TablePos.LEFT -> BiasAlignment(-1f, if (variant == HokmVariant.THREE) -0.75f else -0.25f)
                    TablePos.BOTTOM -> BiasAlignment(0f, 1f)
                }
                OpponentPanel(
                    name = state.nameOf(seat),
                    cardCount = game.hands[seat].size,
                    tricks = game.tricksWon[seat],
                    isHakem = game.hakem == seat,
                    isTurn = game.phase == HokmPhase.PLAYING && game.turn == seat && !game.trickComplete,
                    isPartner = variant.partnerOf(0) == seat,
                    color = hokmTeamColor(variant.teamOf(seat), state.humanTeam),
                    modifier = Modifier.align(alignment),
                )
            }

            // وسط میز
            TrickArea(
                state = state,
                game = game,
                modifier = Modifier.align(BiasAlignment(0f, 0.15f)),
            )
        }
    }
}

@Composable
private fun OpponentPanel(
    name: String,
    cardCount: Int,
    tricks: Int,
    isHakem: Boolean,
    isTurn: Boolean,
    isPartner: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val extras = kiExtras
    val glow by animateFloatAsState(if (isTurn) 1f else 0f, tween(300), label = "turn_glow")
    Column(
        modifier = modifier
            .then(if (isTurn) Modifier.breathing(intensity = 0.03f, periodMs = 1400) else Modifier)
            .background(extras.glass, RoundedCornerShape(16.dp))
            .border(
                width = if (isTurn) 2.dp else 1.dp,
                color = androidx.compose.ui.graphics.lerp(extras.glassBorder, color, glow),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HiddenHand(count = cardCount, width = 28.dp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = (if (isHakem) "👑 " else "") + name + (if (isPartner) " 🤝" else ""),
            style = MaterialTheme.typography.labelLarge,
            color = if (isTurn) color else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "${tricks.toPersianDigits()} دست • ${cardCount.toPersianDigits()} کارت",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** ردیف اطلاعات بازیکن: اسم، تاج، دست‌ها و نوبت */
@Composable
private fun MyRow(state: HokmUiState, game: HokmState, myTurn: Boolean) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (game.hakem == 0) "👑 " else "") + state.nameOf(0),
                style = MaterialTheme.typography.titleMedium,
                color = if (myTurn) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(extras.glass, ChipShape)
                    .border(1.dp, extras.glassBorder, ChipShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${game.tricksWon[0].toPersianDigits()} دست",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = myTurn, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .breathing(intensity = 0.04f, periodMs = 1200)
                    .background(accent, ChipShape)
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
private fun TrickArea(state: HokmUiState, game: HokmState, modifier: Modifier = Modifier) {
    val variant = game.variant
    val winnerSeat = if (state.sweeping) game.trickLeader else null
    Box(modifier = modifier.size(240.dp, 210.dp), contentAlignment = Alignment.Center) {
        // راهنمای وسط میز وقتی خالی است
        if (game.trick.isEmpty() && game.phase == HokmPhase.PLAYING) {
            CenterHint(state = state, game = game)
        }
        if (game.phase == HokmPhase.CHOOSE_TRUMP && game.hakem != 0) {
            WaitingBubble(text = "${state.nameOf(game.hakem)} داره حکم می‌کنه… 🤔")
        }
        game.trick.forEach { tc ->
            key(tc.card.id, game.handNumber) {
                FlyingTrickCard(
                    tc = tc,
                    pos = tablePos(variant, tc.seat),
                    sweepTo = winnerSeat?.let { tablePos(variant, it) },
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
        width = 62.dp,
        rotation = slotRotation(pos) * (1f - sweep * 0.5f),
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .alpha((1f - sweep * 0.9f).coerceIn(0.1f, 1f)),
    )
}

@Composable
private fun CenterHint(state: HokmUiState, game: HokmState) {
    val accent = LocalGameAccent.current
    val text = when {
        game.turn == 0 -> if (game.hakem == 0 && game.played.isEmpty()) "تو حاکمی — شروع کن! 👑" else "نوبت توئه — یه کارت بنداز"
        else -> "${state.nameOf(game.turn)} داره فکر می‌کنه…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (game.turn == 0) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun WaitingBubble(text: String) {
    val extras = kiExtras
    val transition = rememberInfiniteTransition(label = "wait")
    val a by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "wait_a",
    )
    Box(
        modifier = Modifier
            .alpha(a)
            .background(extras.glassStrong, RoundedCornerShape(18.dp))
            .border(1.dp, extras.glassBorderStrong, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- انتخاب حکم

@Composable
private fun TrumpChoiceSheet(game: HokmState, onChoose: (Suit) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
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
                    text = "از روی این پنج کارت تصمیم بگیر",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(14.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game.hands[0].forEachIndexed { i, card ->
                            PlayingCard(card = card, width = 54.dp, rotation = (i - 2) * 2.5f)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Suit.entries.forEach { suit ->
                        val count = game.hands[0].count { it.suit == suit }
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
                    text = "بعدش بقیه‌ی کارت‌ها پخش می‌شه و تو شروع می‌کنی",
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
