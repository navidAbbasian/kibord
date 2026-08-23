package com.navidabbasian.kibord.games.shelem.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Deck
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.trickWinnerIndex
import kotlin.random.Random

/**
 * سیاست ربات‌های شلم: شرط بستن بر اساس قدرت دست، خواباندن ویدو، انتخاب حکم
 * و بازی کردن (حاکم: حکم‌کِشی و نقد کردن آس و ده‌ها؛ مدافع: محافظت از ده و پنج‌ها،
 * خوراک دادن به یار). همه‌چیز قطعی است مگر جایی که [Random] تزریق شود.
 */
object ShelemBot {

    // ------------------------------------------------------------------
    // ارزیابی دست و شرط‌بندی
    // ------------------------------------------------------------------

    /** بهترین حکم برای این دست و تخمین امتیازی که با آن می‌شود گرفت (گرد شده به ۵) */
    fun evaluate(hand: List<Card>): Pair<Suit, Int> {
        var best: Suit = Suit.SPADES
        var bestScore = Int.MIN_VALUE
        for (s in Suit.entries) {
            val score = estimate(hand, s)
            if (score > bestScore) {
                bestScore = score
                best = s
            }
        }
        return best to (bestScore / ShelemRules.BID_STEP * ShelemRules.BID_STEP).coerceIn(0, ShelemRules.MAX_BID)
    }

    private fun estimate(hand: List<Card>, trump: Suit): Int {
        val trumps = hand.filter { it.suit == trump }
        var score = 48 + trumps.size * 7
        for (c in trumps) {
            score += when (c.rank) {
                Rank.ACE -> 9
                Rank.KING -> 6
                Rank.QUEEN -> 4
                Rank.JACK -> 2
                else -> 0
            }
        }
        for (s in Suit.entries) {
            if (s == trump) continue
            val cards = hand.filter { it.suit == s }
            val hasAce = cards.any { it.rank == Rank.ACE }
            val hasKing = cards.any { it.rank == Rank.KING }
            if (hasAce) score += 9
            if (hasKing && cards.size >= 2) score += 4
            if (hasAce && cards.any { it.rank == Rank.TEN }) score += 4
            when (cards.size) {
                0 -> score += 6
                1 -> if (!hasAce) score += 3
            }
        }
        score += hand.count { ShelemRules.cardPoints(it) > 0 } * 2
        return score
    }

    /**
     * تصمیم شرط: مبلغ (مضرب ۵) یا null برای پاس.
     * - همیشه پله‌پله ۵ تا بالا می‌رود
     * - بالای ۱۳۰ محافظه‌کار است و بالای ۱۵۰ خیلی محافظه‌کار
     * - اگر یارش بالاترین شرط را دارد، کمتر رویش می‌پرد
     * - دیلری که مجبور شده، حداقل ۱۰۰ را برمی‌دارد
     */
    fun decideBid(state: ShelemState, seat: Int, random: Random = Random.Default): Int? {
        val bids = ShelemEngine.availableBids(state, seat)
        if (bids.isEmpty()) return null
        val (_, est0) = evaluate(state.hands[seat])
        val jitter = (random.nextInt(3) - 1) * ShelemRules.BID_STEP // -۵، ۰، +۵
        val est = (est0 + jitter).coerceIn(0, ShelemRules.MAX_BID)
        val min = bids.first()
        if (!ShelemEngine.canPass(state, seat)) {
            return maxOf(ShelemRules.MIN_BID, minOf(est, ShelemRules.MAX_BID))
                .let { it / ShelemRules.BID_STEP * ShelemRules.BID_STEP }
                .coerceAtLeast(min)
        }
        var margin = when {
            min > 150 -> 15
            min > 130 -> 10
            else -> 0
        }
        if (state.highBidder != null && ShelemRules.teamOf(state.highBidder) == ShelemRules.teamOf(seat)) margin += 10
        return if (est >= min + margin) min else null
    }

    // ------------------------------------------------------------------
    // ویدو و حکم
    // ------------------------------------------------------------------

    /** حکم: رنگی که بیشترین قدرت را می‌دهد */
    fun chooseTrump(hand: List<Card>): Suit = evaluate(hand).first

    /**
     * ۴ کارت برای خواباندن از دستِ ۱۶تایی:
     * ده و پنج‌های بی‌پشتوانه در رنگ‌های کوتاهِ غیرحکم را می‌خواباند (امتیازشان
     * قطعاً مال خودش می‌شود)، بعد آشغال‌های رنگ‌های کوتاه تا رنگ خالی بسازد؛
     * آس و حکم را نگه می‌دارد مگر چاره‌ای نباشد.
     */
    fun chooseDiscards(hand: List<Card>, trump: Suit): List<Card> {
        val bySuit = hand.groupBy { it.suit }
        val scored = hand.map { c ->
            val suitCards = bySuit[c.suit].orEmpty()
            val len = suitCards.size
            val hasAce = suitCards.any { it.rank == Rank.ACE }
            var keep = 0 // هرچه بیشتر، ماندنی‌تر
            if (c.suit == trump) keep += 100 + c.rank.order
            else {
                keep += len * 4
                when (c.rank) {
                    Rank.ACE -> keep += 60
                    Rank.KING -> keep += if (len >= 2) 30 else 12
                    Rank.TEN -> keep += if (hasAce) 25 else 4
                    Rank.FIVE -> keep += if (hasAce && len >= 3) 18 else 2
                    Rank.QUEEN -> keep += 14
                    Rank.JACK -> keep += 10
                    else -> keep += c.rank.order
                }
            }
            c to keep
        }
        return scored.sortedBy { it.second }.take(ShelemRules.KITTY_SIZE).map { it.first }
    }

    // ------------------------------------------------------------------
    // بازی
    // ------------------------------------------------------------------

    /** کارت انتخابی ربات برای نوبت جاری — همیشه از بین کارت‌های مجاز */
    fun choosePlay(state: ShelemState, seat: Int, random: Random = Random.Default): Card {
        val legal = ShelemEngine.legalPlaysFor(state, seat)
        require(legal.isNotEmpty()) { "ربات کارتی برای بازی ندارد" }
        if (legal.size == 1) return legal.first()
        val trump = state.trump ?: return legal.first()
        val hand = state.hands[seat]
        val declarer = state.declarer ?: seat
        val myTeam = ShelemRules.teamOf(seat)
        val declSide = myTeam == ShelemRules.teamOf(declarer)
        val partner = ShelemRules.partnerOf(seat)

        // کارت‌های ندیده از نگاه این بازیکن (حاکم خوابانده‌ها را می‌شناسد)
        val known = HashSet<Card>(hand)
        known.addAll(state.playedCards)
        if (seat == declarer) known.addAll(state.discarded)
        val unseen = Deck.full().filterNot { it in known }
        fun isBoss(c: Card) = unseen.none { it.suit == c.suit && it.rank.order > c.rank.order }
        val unseenTrumps = unseen.count { it.suit == trump }

        fun lowest(cards: List<Card>) = cards.minWith(compareBy({ ShelemRules.cardPoints(it) }, { it.rank.order }))
        fun lowestNonTrumpJunk(cards: List<Card>): Card {
            val nonTrump = cards.filter { it.suit != trump }
            val pool = nonTrump.ifEmpty { cards }
            val junk = pool.filter { ShelemRules.cardPoints(it) == 0 }
            return if (junk.isNotEmpty()) junk.minBy { it.rank.order } else lowest(pool)
        }

        // ---------------- شروع دست ----------------
        if (state.trick.isEmpty()) {
            val trumps = hand.filter { it.suit == trump }
            val bossTrumps = trumps.filter { isBoss(it) }
            if (declSide && unseenTrumps > 0) {
                // حکم‌کِشی: با بزرگ‌ترین حکمِ مطمئن حکم‌های حریف را بکش
                if (bossTrumps.isNotEmpty()) return bossTrumps.maxBy { it.rank.order }
                if (trumps.size >= 5) return trumps.maxBy { it.rank.order }
            }
            // نقد کردن کارت‌های مطمئن رنگ‌های دیگر (اول آس‌ها و ده‌ها)
            val bossSide = hand.filter { it.suit != trump && isBoss(it) && (!declSide || unseenTrumps == 0 || trumps.isNotEmpty()) }
            if (bossSide.isNotEmpty()) {
                val safe = if (declSide) bossSide else bossSide.filter { unseen.count { u -> u.suit == it.suit } >= 3 || unseenTrumps == 0 }
                if (safe.isNotEmpty()) return safe.maxWith(compareBy({ ShelemRules.cardPoints(it) }, { it.rank.order }))
            }
            if (declSide && unseenTrumps == 0 && bossTrumps.isNotEmpty()) return bossTrumps.maxBy { it.rank.order }
            // وگرنه از بلندترین رنگ غیرحکم کوچک‌ترین کارت بی‌امتیاز
            val bySuit = hand.filter { it.suit != trump }.groupBy { it.suit }
            val longest = bySuit.maxByOrNull { it.value.size }?.value
            if (longest != null) {
                val junk = longest.filter { ShelemRules.cardPoints(it) == 0 }
                if (junk.isNotEmpty()) return junk.minBy { it.rank.order }
            }
            return lowestNonTrumpJunk(hand)
        }

        // ---------------- دنبال کردن ----------------
        val trick = state.trick
        val winIdx = trickWinnerIndex(trick, trump)
        val winnerSeat = state.trickPlayer(winIdx)
        val winningCard = trick[winIdx]
        val partnerWinning = winnerSeat == partner
        val lastToPlay = trick.size == ShelemRules.PLAYERS - 1
        val trickPts = ShelemRules.points(trick)
        val winners = legal.filter { trickWinnerIndex(trick + it, trump) == trick.size }
        val nonWinners = legal.filterNot { it in winners }

        if (partnerWinning) {
            val partnerSafe = lastToPlay ||
                (isBoss(winningCard) && (winningCard.suit == trump || unseen.count { it.suit == winningCard.suit } >= 2 && unseenTrumps == 0)) ||
                (isBoss(winningCard) && winningCard.suit == trick.first().suit && unseen.count { it.suit == winningCard.suit } >= 3)
            return if (partnerSafe && nonWinners.isNotEmpty()) {
                // خوراک دادن: پرامتیازترین کارتی که دست یار را نمی‌گیرد
                nonWinners.maxWith(compareBy({ ShelemRules.cardPoints(it) }, { -it.rank.order }))
            } else if (partnerSafe) {
                lowest(legal)
            } else if (winners.isNotEmpty() && lastToPlay) {
                lowest(legal)
            } else {
                // یار ممکن است ببازد: امتیاز نده، کوچک‌ترین آشغال
                val junk = legal.filter { ShelemRules.cardPoints(it) == 0 }
                if (junk.isNotEmpty()) junk.minBy { it.rank.order } else lowest(legal)
            }
        }

        // حریف (یا هنوز هیچ‌کس از تیم ما) برنده است
        if (winners.isNotEmpty()) {
            val cheapest = winners.minWith(compareBy({ it.suit == trump }, { it.rank.order }))
            if (lastToPlay) {
                // دست نقد است: ارزان‌ترین برنده؛ ولی حکم را برای دستِ بی‌امتیاز حیف نکن
                if (cheapest.suit == trump && trickPts == 0 && trick.first().suit != trump && nonWinners.isNotEmpty()) {
                    return lowestNonTrumpJunk(nonWinners)
                }
                return cheapest
            }
            val bossWinners = winners.filter { isBoss(it) }
            if (bossWinners.isNotEmpty()) {
                val b = bossWinners.minBy { it.rank.order }
                if (b.suit != trump || trickPts > 0 || trick.first().suit == trump) return b
            }
            if (trickPts >= 10 || (trickPts > 0 && cheapest.suit != trump)) return cheapest
            // دستِ کم‌ارزش و برنده‌ی مطمئنی نداریم: نگه دار
            if (nonWinners.isNotEmpty()) return lowestNonTrumpJunk(nonWinners)
            return cheapest
        }

        // نمی‌توانیم ببریم: کم‌ارزش‌ترین کارت — ده و پنج را نگه دار
        return lowestNonTrumpJunk(legal)
    }
}
