package com.navidabbasian.kibord.games.hokm.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.trickWinnerIndex

/**
 * ربات حکم — قانون‌محور و قطعی (بدون تصادف) تا تست‌پذیر باشد:
 * حکم را از روی قدرت خال می‌چیند، با کارتِ مطمئن شروع می‌کند، ارزان می‌برد،
 * وقتی یار برنده است کارت کم می‌اندازد، و وقتی خال ندارد با حکم می‌بُرد.
 */
object HokmBot {

    /** انتخاب حکم از ۵ کارت اول: تعداد کارت‌های هر خال + ارزش عکس‌ها */
    fun chooseTrump(cards: List<Card>): Suit =
        Suit.entries.maxWith(
            compareBy<Suit> { s -> suitStrength(cards, s) }
                .thenBy { s -> cards.count { it.suit == s } },
        )

    private fun suitStrength(cards: List<Card>, suit: Suit): Int {
        val own = cards.filter { it.suit == suit }
        return own.size * 10 + own.sumOf { HokmRules.honorValue(it.rank) }
    }

    /** کارتی که ربات در این نوبت بازی می‌کند — همیشه مجاز */
    fun choosePlay(state: HokmState, seat: Int): Card {
        val legal = HokmRules.legalMoves(state, seat)
        require(legal.isNotEmpty()) { "نوبت این صندلی نیست" }
        if (legal.size == 1) return legal.first()
        val ctx = Ctx(state, seat, legal)
        return if (state.trick.isEmpty()) lead(ctx) else follow(ctx)
    }

    private class Ctx(val state: HokmState, val seat: Int, val legal: List<Card>) {
        val trump: Suit? = state.trump
        val hand: List<Card> = state.hands[seat]
        val seen: List<Card> = state.seenCards
        val partner: Int? = state.variant.partnerOf(seat)

        /** آیا این کارت بزرگ‌ترین کارتِ باقی‌مانده‌ی خالِ خودش است؟ */
        fun isTopRemaining(card: Card): Boolean =
            Rank.entries.filter { it.order > card.rank.order }.all { r ->
                val c = Card(card.suit, r)
                c in seen || c in hand || c in state.variant.excludedCards
            }

        fun suitLen(suit: Suit): Int = hand.count { it.suit == suit }

        /** تعداد حکم‌هایی که هنوز دستِ دیگران می‌تواند باشد */
        fun outstandingTrumps(): Int {
            val t = trump ?: return 0
            val total = 13 - state.variant.excludedCards.count { it.suit == t }
            return total - seen.count { it.suit == t } - hand.count { it.suit == t }
        }
    }

    // ---------- شروع‌کردن ----------

    private fun lead(c: Ctx): Card {
        val nonTrump = c.legal.filter { it.suit != c.trump }
        val trumps = c.legal.filter { it.suit == c.trump }

        // ۱) برنده‌ی مطمئن در خال غیرحکم — از خالی که بلندتر است (برنده‌های بعدی هم دارد)
        val sure = nonTrump.filter { c.isTopRemaining(it) }
        if (sure.isNotEmpty()) {
            return sure.maxWith(compareBy<Card> { c.suitLen(it.suit) }.thenBy { it.rank.order })
        }
        // ۲) حکم‌کشی: اگر حکمِ سر داریم و دیگران هنوز حکم دارند
        if (trumps.isNotEmpty() && c.outstandingTrumps() > 0) {
            val topTrump = trumps.maxBy { it.rank.order }
            if (c.isTopRemaining(topTrump) && trumps.size >= 3) return topTrump
            if (trumps.size > c.outstandingTrumps() && trumps.size >= 4) return trumps.minBy { it.rank.order }
        }
        // ۳) کارت پایینِ بلندترین خال غیرحکم
        if (nonTrump.isNotEmpty()) {
            val longest = nonTrump.groupBy { it.suit }.maxBy { (_, v) -> v.size }.value
            return longest.minBy { it.rank.order }
        }
        // ۴) فقط حکم داریم
        return trumps.minBy { it.rank.order }
    }

    // ---------- دنبال‌کردن ----------

    private fun follow(c: Ctx): Card {
        val trickCards = c.state.trick.map { it.card }
        val leadSuit = trickCards.first().suit
        val winIdx = trickWinnerIndex(trickCards, c.trump)
        val winningCard = trickCards[winIdx]
        val winningSeat = c.state.trick[winIdx].seat
        val partnerWinning = c.partner != null && c.partner == winningSeat
        val isLast = c.state.trick.size == c.state.playerCount - 1
        val following = c.legal.first().suit == leadSuit && c.hand.any { it.suit == leadSuit }

        val beaters = c.legal.filter { beats(it, winningCard, leadSuit, c.trump) }

        if (partnerWinning) {
            // یار برنده است: اگر نفر آخریم یا کارتش سر است، کم بینداز
            if (isLast || c.isTopRemaining(winningCard) || winningCard.suit == c.trump && leadSuit != c.trump) {
                return cheapest(c, following)
            }
            // یار ممکن است خورده شود: اگر خودم برنده‌ی مطمئن دارم، بزنم؛ وگرنه کم
            val sureBeater = beaters.filter { c.isTopRemaining(it) && it.suit == leadSuit }
            return sureBeater.minByOrNull { it.rank.order } ?: cheapest(c, following)
        }

        if (beaters.isNotEmpty()) {
            if (isLast) return beaters.minBy { it.rank.order } // ارزان‌ترین برد
            val sure = beaters.filter { c.isTopRemaining(it) }
            if (sure.isNotEmpty()) return sure.minBy { it.rank.order }
            return if (following) {
                // هم‌خال: با بزرگ‌ترین بزن تا بعدی‌ها به سختی بخورند
                beaters.maxBy { it.rank.order }
            } else {
                // بریدن با حکم: کوچک‌ترین حکمی که می‌بَرد
                beaters.minBy { it.rank.order }
            }
        }
        return cheapest(c, following)
    }

    /** کارتی که کمترین ضرر دارد: اگر باید خال بدهیم کوچک‌ترینِ خال، وگرنه از کوتاه‌ترین خال غیرحکم */
    private fun cheapest(c: Ctx, following: Boolean): Card {
        if (following) return c.legal.minBy { it.rank.order }
        val nonTrump = c.legal.filter { it.suit != c.trump }
        if (nonTrump.isNotEmpty()) {
            return nonTrump.minWith(compareBy<Card> { c.suitLen(it.suit) }.thenBy { it.rank.order })
        }
        return c.legal.minBy { it.rank.order }
    }

    /** آیا [card] روی [best] (با خال شروع [lead]) می‌بَرد؟ */
    private fun beats(card: Card, best: Card, lead: Suit, trump: Suit?): Boolean {
        val cTrump = card.suit == trump
        val bTrump = best.suit == trump
        return when {
            cTrump && !bTrump -> true
            !cTrump && bTrump -> false
            cTrump && bTrump -> card.rank.order > best.rank.order
            card.suit == lead && best.suit == lead -> card.rank.order > best.rank.order
            else -> false
        }
    }
}
