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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.cards.HandFan
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.Suit
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

// ---------------------------------------------------------------- رنگ‌های میز چوبی

private val WoodPlankColors = listOf(Color(0xFFB67B3E), Color(0xFFC68A4A), Color(0xFFA96F33))
private val WoodSeam = Color(0xFF7A5122)
private val PillBrown = Color(0xDB4A2F1B)
private val PillGold = Color(0xFFC9A24B)
private val PillCream = Color(0xFFF5E3B8)
private val BadgeBlue = Color(0xFF2D7DF6)
private val GlowCyan = Color(0xFF35D6E8)
private val BackBlueTop = Color(0xFF1F3A6E)
private val BackBlueBottom = Color(0xFF0F2547)
private val CreditGreen = Color(0xFF6FE3A5)
private val DebtRed = Color(0xFFFF9B8E)

/** جای هر صندلی دور میز */
private enum class TablePos { BOTTOM, RIGHT, TOP, LEFT }

private fun tablePos(variant: HokmVariant, seat: Int): TablePos = when (variant) {
    HokmVariant.FOUR -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.TOP, TablePos.LEFT)[seat]
    HokmVariant.THREE -> listOf(TablePos.BOTTOM, TablePos.RIGHT, TablePos.LEFT)[seat]
    HokmVariant.TWO -> listOf(TablePos.BOTTOM, TablePos.TOP)[seat]
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
    val humanPlays = state.humanSeatInGame == 0
    val myTurn = game.phase == HokmPhase.PLAYING && game.turn == 0 && humanPlays && !game.trickComplete
    val legal = remember(game) { if (myTurn) HokmRules.legalMoves(game, 0).toSet() else null }
    val debtorPicking = state.debtorPick != null
    val mordabadi = game.isMordabadi && game.quotas.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        WoodBackground(modifier = Modifier.fillMaxSize())

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
                cards = game.hands[0],
                playable = when {
                    debtorPicking -> game.hands[0].toSet()
                    else -> legal
                },
                maxCardWidth = if (mordabadi) 64.dp else 74.dp,
                onCardClick = when {
                    debtorPicking -> ({ card -> viewModel.giveDebtCard(card) })
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

        // ---- انتخاب حکم توسط بازیکن ----
        if (game.phase == HokmPhase.CHOOSE_TRUMP && game.hakem == 0 && humanPlays) {
            TrumpChoiceSheet(game = game, onChoose = viewModel::chooseTrump)
        }

        // ---- وصول طلب (مردابادی): نوبت طلبکارِ انسانی ----
        if (game.phase == HokmPhase.COLLECTION && MordabadiRules.collector(game) == 0 && !debtorPicking) {
            CollectionSheet(state = state, game = game, onExchange = viewModel::humanExchange)
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

// ---------------------------------------------------------------- زمینه‌ی چوبی

/** میز چوبی: تخته‌های عمودی گرم با درز، رگه و تیرگی ملایم گوشه‌ها */
@Composable
private fun WoodBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val plankW = 62.dp.toPx()
        var x = 0f
        var i = 0
        while (x < size.width) {
            drawRect(
                color = WoodPlankColors[i % WoodPlankColors.size],
                topLeft = Offset(x, 0f),
                size = Size(plankW, size.height),
            )
            // درز باریک تیره بین تخته‌ها
            drawLine(
                color = WoodSeam.copy(alpha = 0.8f),
                start = Offset(x + plankW, 0f),
                end = Offset(x + plankW, size.height),
                strokeWidth = 2.dp.toPx(),
            )
            // چند رگه‌ی کوتاه افقی
            val rnd = Random(i * 131 + 7)
            repeat(4) {
                val gy = rnd.nextFloat() * size.height
                val gx = x + 6.dp.toPx() + rnd.nextFloat() * (plankW - 24.dp.toPx())
                drawLine(
                    color = WoodSeam.copy(alpha = 0.20f),
                    start = Offset(gx, gy),
                    end = Offset(gx + 10.dp.toPx() + rnd.nextFloat() * 14.dp.toPx(), gy + rnd.nextFloat() * 3f),
                    strokeWidth = 1.5f,
                )
            }
            x += plankW
            i++
        }
        // تیرگی ملایم به سمت گوشه‌ها
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color(0x59000000)),
                center = center,
                radius = size.maxDimension * 0.72f,
            ),
            size = size,
        )
    }
}

// ---------------------------------------------------------------- پشتِ کارت تزئینی

/** پشتِ کارتِ سرمه‌ای با قاب دوخطِ طلایی و ترنجِ وسط — همه‌جای حکم استفاده می‌شود */
@Composable
internal fun OrnateCardBack(width: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width, width * 1.4f)) {
        val r = size.width * 0.13f
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(BackBlueTop, BackBlueBottom)),
            cornerRadius = CornerRadius(r, r),
        )
        val stroke = Stroke(width = (size.width * 0.022f).coerceAtLeast(1f))
        val i1 = size.width * 0.055f
        val i2 = size.width * 0.115f
        drawRoundRect(
            color = PillGold,
            topLeft = Offset(i1, i1),
            size = Size(size.width - 2 * i1, size.height - 2 * i1),
            cornerRadius = CornerRadius(r * 0.75f, r * 0.75f),
            style = stroke,
        )
        drawRoundRect(
            color = PillGold.copy(alpha = 0.7f),
            topLeft = Offset(i2, i2),
            size = Size(size.width - 2 * i2, size.height - 2 * i2),
            cornerRadius = CornerRadius(r * 0.5f, r * 0.5f),
            style = Stroke(width = stroke.width * 0.7f),
        )
        // ترنج مرکزی: مربع‌های تودرتو (یکی چرخیده) + نقطه‌ی وسط
        val s = size.width * 0.36f
        val c = center
        rotate(degrees = 45f, pivot = c) {
            drawRect(
                color = PillGold,
                topLeft = Offset(c.x - s / 2, c.y - s / 2),
                size = Size(s, s),
                style = stroke,
            )
        }
        val s2 = s * 0.68f
        drawRect(
            color = PillGold.copy(alpha = 0.85f),
            topLeft = Offset(c.x - s2 / 2, c.y - s2 / 2),
            size = Size(s2, s2),
            style = Stroke(width = stroke.width * 0.7f),
        )
        rotate(degrees = 45f, pivot = c) {
            val s3 = s * 0.3f
            drawRect(
                color = PillGold.copy(alpha = 0.45f),
                topLeft = Offset(c.x - s3 / 2, c.y - s3 / 2),
                size = Size(s3, s3),
            )
        }
        drawCircle(color = PillGold, radius = size.width * 0.035f, center = c)
        // گل‌های کوچک گوشه‌ها
        val fi = size.width * 0.19f
        listOf(
            Offset(fi, fi),
            Offset(size.width - fi, fi),
            Offset(fi, size.height - fi),
            Offset(size.width - fi, size.height - fi),
        ).forEach { p ->
            drawCircle(color = PillGold.copy(alpha = 0.65f), radius = size.width * 0.032f, center = p)
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

/** قرص کوچک قهوه‌ای با حاشیه‌ی طلایی */
@Composable
private fun WoodPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(PillBrown, RoundedCornerShape(14.dp))
            .border(1.dp, PillGold.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = PillCream,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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

            // حریف‌ها: بادبزنِ پشتِ کارت + قرص اسم + ستون دست‌های برده
            for (seat in 1 until variant.playerCount) {
                val pos = tablePos(variant, seat)
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
    val isPartner = game.variant.partnerOf(0) == seat
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
                OpponentFanTop(
                    count = count,
                    modifier = Modifier.align(BiasAlignment(0f, -0.62f)),
                )
                Column(
                    modifier = Modifier.align(BiasAlignment(0f, -0.30f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NamePill(name = name, crowned = crowned, badge = badge, glowing = isTurn)
                    if (mordabadi) {
                        Spacer(modifier = Modifier.height(3.dp))
                        MordabadiChips(game = game, seat = seat)
                    }
                }
            }

            TablePos.RIGHT, TablePos.LEFT -> {
                val edge = if (pos == TablePos.RIGHT) 1f else -1f
                OpponentFanSide(
                    count = count,
                    rightSide = pos == TablePos.RIGHT,
                    modifier = Modifier.align(BiasAlignment(edge, -0.35f)),
                )
                Column(
                    modifier = Modifier.align(BiasAlignment(edge * 0.94f, 0.28f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NamePill(
                        name = name,
                        crowned = crowned,
                        badge = badge,
                        glowing = isTurn,
                        vertical = true,
                        rotate = if (pos == TablePos.RIGHT) -90f else 90f,
                    )
                    if (mordabadi) {
                        Spacer(modifier = Modifier.height(3.dp))
                        MordabadiChips(game = game, seat = seat)
                    }
                }
            }

            TablePos.BOTTOM -> Unit
        }
    }
}

/** بادبزن بالای میز: کمان پشتِ کارت‌ها */
@Composable
private fun OpponentFanTop(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val n = min(count, 13)
    val cardW = 42.dp
    val step = min(20f, 150f / n)
    Box(modifier = modifier.height(cardW * 1.4f + 14.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val rel = i - (n - 1) / 2f
            OrnateCardBack(
                width = cardW,
                modifier = Modifier
                    .offset(x = (rel * step).dp, y = (abs(rel) * abs(rel) * 0.55f).dp)
                    .graphicsLayer { rotationZ = rel * 4.5f },
            )
        }
    }
}

/** بادبزن کناری: پشتِ کارت‌های چرخیده‌ی ۹۰ درجه چسبیده به لبه */
@Composable
private fun OpponentFanSide(count: Int, rightSide: Boolean, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val n = min(count, 17)
    val cardW = 36.dp
    val stepY = min(15f, 220f / n)
    val edgeX = if (rightSide) 10f else -10f
    Box(modifier = modifier.width(cardW * 1.4f), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val rel = i - (n - 1) / 2f
            OrnateCardBack(
                width = cardW,
                modifier = Modifier
                    .offset(
                        x = (edgeX + abs(rel) * abs(rel) * 0.12f * (if (rightSide) 1f else -1f)).dp,
                        y = (rel * stepY).dp,
                    )
                    .graphicsLayer { rotationZ = (if (rightSide) 90f else -90f) + rel * 2f },
            )
        }
    }
}

/** قرص اسم بازیکن: قهوه‌ای تیره با حاشیه‌ی طلایی و نشان آبیِ تعداد دست */
@Composable
private fun NamePill(
    name: String,
    crowned: Boolean,
    badge: String,
    glowing: Boolean,
    vertical: Boolean = false,
    rotate: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (glowing) GlowCyan else PillGold
    Row(
        modifier = modifier
            .then(if (vertical) Modifier.graphicsLayer { rotationZ = rotate } else Modifier)
            .then(if (glowing) Modifier.breathing(intensity = 0.04f, periodMs = 1400) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(PillBrown, RoundedCornerShape(14.dp))
                .border(if (glowing) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = (if (crowned) "👑 " else "") + name,
                style = MaterialTheme.typography.labelLarge,
                color = PillCream,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .background(BadgeBlue, RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

/** چیپ‌های مردابادی زیر قرص اسم: تراز (طلب سبز / بدهی قرمز) + جمعِ بدهی */
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
    Box(
        modifier = modifier
            .border(6.dp, GlowCyan.copy(alpha = if (declared) 0.22f else 0.10f), RoundedCornerShape(22.dp))
            .padding(3.dp)
            .border(3.dp, GlowCyan.copy(alpha = if (declared) 0.45f else 0.20f), RoundedCornerShape(19.dp))
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier
                .background(PillBrown, RoundedCornerShape(17.dp))
                .border(1.5.dp, GlowCyan.copy(alpha = if (declared) 0.95f else 0.45f), RoundedCornerShape(17.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
}

// ---------------------------------------------------------------- دسته‌های دستِ برده

/** دسته‌ی دست‌های برده: دو پشتِ کارت کوچک روی هم + نشانِ عددی با حاشیه‌ی فیروزه‌ای */
@Composable
private fun TrickPile(count: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(52.dp, 54.dp), contentAlignment = Alignment.Center) {
        OrnateCardBack(
            width = 26.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { rotationZ = -8f },
        )
        OrnateCardBack(
            width = 26.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 6.dp, y = 2.dp)
                .graphicsLayer { rotationZ = 9f },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .background(PillBrown, CircleShape)
                .border(1.5.dp, GlowCyan, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toPersianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

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
                TrickPile(count = game.teamTricks(0), modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)))
                TrickPile(count = game.teamTricks(1), modifier = Modifier.align(BiasAlignment(0.92f, -0.86f)))
            }

            HokmVariant.TWO -> {
                TrickPile(count = game.tricksWon[0], modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)))
                TrickPile(count = game.tricksWon[1], modifier = Modifier.align(BiasAlignment(0.92f, -0.86f)))
            }

            HokmVariant.THREE -> {
                TrickPile(count = game.tricksWon[0], modifier = Modifier.align(BiasAlignment(-0.92f, 0.94f)))
                TrickPile(count = game.tricksWon[1], modifier = Modifier.align(BiasAlignment(0.70f, 0.62f)))
                TrickPile(count = game.tricksWon[2], modifier = Modifier.align(BiasAlignment(-0.70f, 0.62f)))
            }
        }
    }
}

// ---------------------------------------------------------------- ردیف بازیکن

/** ردیف اطلاعات بازیکن: قرص «شما» با تاج و نشانِ دست‌ها + چیپ نوبت */
@Composable
private fun HumanRow(state: HokmUiState, game: HokmState, myTurn: Boolean, mordabadi: Boolean) {
    val name = state.playerName.trim().ifBlank { "شما" }
    val badge = if (mordabadi) {
        "${game.tricksWon[0].toPersianDigits()} از ${game.quotaOf(0).toPersianDigits()}"
    } else {
        game.tricksWon[0].toPersianDigits()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NamePill(
                name = if (state.duelSeats != null) state.nameOf(0) else name,
                crowned = game.hakem == 0,
                badge = badge,
                glowing = myTurn,
            )
            if (mordabadi) {
                Spacer(modifier = Modifier.width(6.dp))
                MordabadiChips(game = game, seat = 0)
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
        if (game.trick.isEmpty() && game.phase == HokmPhase.PLAYING) {
            CenterHint(state = state, game = game, humanPlays = humanPlays)
        }
        if (game.phase == HokmPhase.CHOOSE_TRUMP && !(game.hakem == 0 && humanPlays)) {
            WaitingBubble(text = "${state.nameOf(game.hakem)} داره حکم می‌کنه… 🤔")
        }
        if (game.phase == HokmPhase.COLLECTION) {
            val c = MordabadiRules.collector(game)
            if (state.debtorPick != null) {
                WaitingBubble(text = "یه کارت بده جای بدهیت 👇")
            } else if (c != null && c != 0) {
                WaitingBubble(text = "${state.nameOf(c)} داره طلبش رو وصول می‌کنه… 💰")
            }
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
        width = 92.dp,
        rotation = slotRotation(pos) * (1f - sweep * 0.5f),
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .alpha((1f - sweep * 0.9f).coerceIn(0.1f, 1f)),
    )
}

@Composable
private fun CenterHint(state: HokmUiState, game: HokmState, humanPlays: Boolean) {
    val text = when {
        game.turn == 0 && humanPlays ->
            if (game.hakem == 0 && game.played.isEmpty()) "تو حاکمی — شروع کن! 👑" else "نوبت توئه — یه کارت بنداز"
        else -> "${state.nameOf(game.turn)} داره فکر می‌کنه…"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (game.turn == 0 && humanPlays) Color.White else PillCream.copy(alpha = 0.9f),
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
private fun TrumpChoiceSheet(game: HokmState, onChoose: (Suit) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val cards = game.hands[0]
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
private fun CollectionSheet(state: HokmUiState, game: HokmState, onExchange: (Int, Suit) -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val debtors = MordabadiRules.debtors(game)
    val suits = MordabadiRules.availableSuits(game, 0)
    var debtor by remember(game) { mutableStateOf(debtors.minByOrNull { game.balances[it] } ?: -1) }
    if (debtor !in debtors && debtors.isNotEmpty()) debtor = debtors.first()
    val trump = game.trump
    val canTrump = debtor >= 0 && MordabadiRules.canDemandTrump(game, 0, debtor)

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
                    text = "طلب تو: ${game.balances[0].toPersianDigits()} — " +
                        "یه خال انتخاب کن: پایین‌ترینش رو می‌دی و بالاترینش رو می‌گیری",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // انتخاب بدهکار (وقتی دو بدهکار هست)
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
                                    text = "${state.nameOf(d)} (بدهی ${(-game.balances[d]).toPersianDigits()})",
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

/** پایان دستِ مردابادی: تسویه‌ی سهمیه‌ها، تراز، جمع بدهی و چرخش/حذف */
@Composable
private fun MordabadiHandOverOverlay(state: HokmUiState, game: HokmState, onNext: () -> Unit) {
    val result = game.lastResult ?: return
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val eliminated = result.eliminatedSeat
    val myDelta = result.deltas.getOrElse(0) { 0 }

    val title = when {
        eliminated == 0 -> "حذف شدی! 🚫"
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
            golden = eliminated != null && eliminated != 0,
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
                    ResultLine(
                        label = state.mordabadiNameOf(seat) + if (eliminated == seat) " 🚫" else "",
                        value = "${result.teamTricks[seat].toPersianDigits()} دست → $deltaText" +
                            " • تراز ${game.balances.getOrElse(seat) { 0 }.toPersianDigits()}" +
                            " • جمع بدهی ${game.totalDebts.getOrElse(seat) { 0 }.toPersianDigits()}",
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
