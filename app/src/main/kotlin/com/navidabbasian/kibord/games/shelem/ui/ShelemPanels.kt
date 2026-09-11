package com.navidabbasian.kibord.games.shelem.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.PlayingCard
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.TableGlowCyan
import com.navidabbasian.kibord.core.cards.TablePillBrown
import com.navidabbasian.kibord.core.cards.TablePillCream
import com.navidabbasian.kibord.core.cards.TablePillGold
import com.navidabbasian.kibord.core.cards.color
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.shelem.ShelemUiState
import com.navidabbasian.kibord.games.shelem.ShelemViewModel
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemState

/** پنل پایین صفحه: تراشه‌های شرط، تأیید خواباندن، یا یک خط وضعیت */
@Composable
fun ShelemActionPanel(
    state: ShelemUiState,
    game: ShelemState,
    humanTurn: Boolean,
    viewModel: ShelemViewModel,
) {
    val key = when {
        state.dealing -> "deal"
        game.phase == ShelemPhase.BIDDING && humanTurn -> "bid"
        game.phase == ShelemPhase.DISCARDING && game.declarer == state.mySeat -> "discard"
        game.phase == ShelemPhase.BIDDING -> "bidwait"
        else -> "status"
    }
    AnimatedContent(
        targetState = key,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "panel",
    ) { k ->
        when (k) {
            "bid" -> BidPanel(game = game, me = state.mySeat, onBid = viewModel::humanBid, onPass = viewModel::humanPass)
            "discard" -> DiscardPanel(state = state, game = game, onConfirm = viewModel::confirmDiscard)
            "bidwait" -> BidWaitPanel(state = state, game = game)
            else -> Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/** تراشه‌های ۱۰۰ تا ۱۶۵ + پاس؛ مبالغ پایین‌تر از بالاترین شرط خاموش‌اند */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BidPanel(game: ShelemState, me: Int, onBid: (Int) -> Unit, onPass: () -> Unit) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val sound = LocalSoundManager.current
    val available = remember(game, me) { ShelemEngine.availableBids(game, me).toSet() }
    val canPass = ShelemEngine.canPass(game, me)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TablePillBrown, RoundedCornerShape(20.dp))
            .border(1.dp, TablePillGold, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (canPass) "چند می‌بندی؟" else "بقیه پاس دادن — تو دیلری و باید حداقل ۱۰۰ برداری!",
                style = MaterialTheme.typography.labelLarge,
                color = TablePillCream,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = 7,
        ) {
            ShelemRules.ALL_BIDS.forEach { amount ->
                val enabled = amount in available
                BidChip(
                    text = amount.toPersianDigits(),
                    enabled = enabled,
                    fill = if (enabled) accent.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.25f),
                    border = if (enabled) accent else TablePillGold.copy(alpha = 0.35f),
                    textColor = if (enabled) Color.White else TablePillCream.copy(alpha = 0.4f),
                    onClick = { sound?.playButtonClick(); onBid(amount) },
                )
            }
            BidChip(
                text = "پاس",
                enabled = canPass,
                fill = if (canPass) extras.danger.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.25f),
                border = if (canPass) extras.danger else TablePillGold.copy(alpha = 0.35f),
                textColor = if (canPass) Color.White else TablePillCream.copy(alpha = 0.4f),
                onClick = { sound?.playButtonClick(); onPass() },
            )
        }
    }
}

@Composable
private fun BidChip(
    text: String,
    enabled: Boolean,
    fill: Color,
    border: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 34.dp)
            .background(fill, RoundedCornerShape(11.dp))
            .border(1.dp, border, RoundedCornerShape(11.dp))
            .then(
                if (enabled) Modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BidWaitPanel(state: ShelemUiState, game: ShelemState) {
    Row(
        modifier = Modifier
            .background(TablePillBrown, RoundedCornerShape(16.dp))
            .border(1.dp, TablePillGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.mySeat in game.passed) "پاس دادی — ببینیم کی حاکم می‌شه"
            else "شرط‌بندی دور میز می‌چرخه…",
            style = MaterialTheme.typography.bodyMedium,
            color = TablePillCream,
        )
    }
}

/** پیش‌نمایش ویدو + شمارنده‌ی انتخاب + دکمه‌ی «بخوابون» */
@Composable
private fun DiscardPanel(state: ShelemUiState, game: ShelemState, onConfirm: () -> Unit) {
    val sound = LocalSoundManager.current
    val n = state.selectedDiscards.size
    val ready = n == ShelemRules.KITTY_SIZE
    val kittyPts = ShelemRules.points(state.selectedDiscards)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TablePillBrown, RoundedCornerShape(20.dp))
            .border(1.dp, TablePillGold, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ویدو مال توئه! ✦ چهار کارتِ تازه",
                    style = MaterialTheme.typography.labelLarge,
                    color = TablePillCream,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "۴ کارت بخوابون — امتیازشون آخرِ دست مال تیمته" +
                        if (n > 0) " · انتخاب: ${n.toPersianDigits()}/۴ (${kittyPts.toPersianDigits()} امتیاز)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TablePillCream.copy(alpha = 0.8f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                game.kitty.forEach { c ->
                    PlayingCard(card = c, width = 26.dp, dimmed = c in state.selectedDiscards)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        KButton(
            text = if (ready) "بخوابون 🙈" else "۴ کارت انتخاب کن",
            enabled = ready,
            onClick = { sound?.playButtonClick(); onConfirm() },
            modifier = Modifier.then(if (ready) Modifier.breathing(intensity = 0.03f) else Modifier),
        )
    }
}

/** ورقه‌ی انتخاب حکم: کارتِ قهوه‌ای با حاشیه‌ی طلایی و چهار خال درشت */
@Composable
fun ShelemTrumpSheet(hand: List<Card>, onPick: (Suit) -> Unit) {
    val sound = LocalSoundManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .background(Color(0xF53A2413), RoundedCornerShape(24.dp))
                .border(1.5.dp, TablePillGold, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BobbingEmoji(emoji = "👑", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = "حکم چیه؟", fontSize = 24.sp, rotation = -2f)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تو حاکمی — رنگی رو انتخاب کن که بیشتر و قوی‌تر داری",
                    style = MaterialTheme.typography.bodySmall,
                    color = TablePillCream.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Suit.entries.forEachIndexed { i, suit ->
                        val count = hand.count { it.suit == suit }
                        Column(
                            modifier = Modifier
                                .width(66.dp)
                                .background(Color(0xFFFFFDF7), RoundedCornerShape(16.dp))
                                .border(1.5.dp, TablePillGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    sound?.playButtonClick()
                                    onPick(suit)
                                }
                                .padding(vertical = 10.dp)
                                .breathing(intensity = 0.025f, phase = i * 1.1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = suit.symbol, color = suit.color, fontSize = 34.sp, lineHeight = 36.sp)
                            Text(text = suit.persian, color = Color(0xFF26262E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${count.toPersianDigits()} تا",
                                color = Color(0xFF26262E).copy(alpha = 0.6f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** پایان دست: ریزِ حساب — شرط، گرفته/نگرفته، ویدو، دست آخر، تغییر امتیاز */
@Composable
fun ShelemHandEndOverlay(state: ShelemUiState, game: ShelemState, onNext: () -> Unit) {
    val r = game.handResult ?: return
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val declName = if (r.declarer == state.mySeat) "تو" else state.seatName(r.declarer)
    val declTeamName = state.teamName(r.declarerTeam)
    val oppTeam = 1 - r.declarerTeam
    val weMade = (r.declarerTeam == state.myTeam) == r.made
    val title = when {
        r.shelem -> "شلم! 🔥"
        r.made -> "شرط گرفته شد ✅"
        else -> "شرط نگرفت ❌"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        TicketCard(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            tilt = -1.2f,
            golden = r.shelem,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BobbingEmoji(emoji = if (weMade) "🎉" else "😬", fontSize = 38.sp)
                Spacer(modifier = Modifier.height(6.dp))
                StickerTitle(
                    text = title,
                    fontSize = 22.sp,
                    rotation = 1.5f,
                    accent = if (weMade) extras.success else extras.danger,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "دست ${r.handNumber.toPersianDigits()} · حاکم: $declName ($declTeamName) · حکم ${r.trump.symbol} ${r.trump.persian}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))

                BreakdownRow("شرط", r.bid.toPersianDigits())
                BreakdownRow("امتیاز دست‌های $declTeamName", r.trickPoints[r.declarerTeam].toPersianDigits())
                BreakdownRow("ویدو (کارت‌های خوابیده)", "+${r.kittyPoints.toPersianDigits()}")
                BreakdownRow(
                    "دست آخر (+۵)",
                    if (r.lastTrickTeam == r.declarerTeam) "مال $declTeamName" else "مال ${state.teamName(oppTeam)}",
                )
                BreakdownRow(
                    "جمع $declTeamName",
                    "${r.declarerPoints.toPersianDigits()} از ${r.bid.toPersianDigits()}",
                    strong = true,
                )
                BreakdownRow("جمع ${state.teamName(oppTeam)}", r.opponentPoints.toPersianDigits())
                if (r.doubled) {
                    BreakdownRow(if (r.bid == ShelemRules.MAX_BID) "شرط ۱۶۵ گرفته شد — دوبرابر!" else "شلم — دوبرابر!", "×۲", strong = true)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    GainChip(label = state.teamName(0), gain = r.teamGain[0], total = game.scores[0], color = shelemTeamColor(0))
                    GainChip(label = state.teamName(1), gain = r.teamGain[1], total = game.scores[1], color = shelemTeamColor(1))
                }
                Spacer(modifier = Modifier.height(16.dp))
                KButton(
                    text = if (game.phase == ShelemPhase.MATCH_OVER) "نتیجه‌ی نهایی 🏆" else "دست بعدی 🎴",
                    onClick = { sound?.playButtonClick(); onNext() },
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, strong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GainChip(label: String, gain: Int, total: Int, color: Color) {
    val extras = kiExtras
    val sign = if (gain >= 0) "+" else "−"
    Column(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "$sign${kotlin.math.abs(gain).toPersianDigits()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (gain >= 0) extras.success else extras.danger,
        )
        Text(
            text = "جمع: ${total.toPersianDigits()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
