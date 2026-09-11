package com.navidabbasian.kibord.games.hokm.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Deck
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.legalPlays
import com.navidabbasian.kibord.core.cards.sortedForHand
import kotlin.random.Random

/** نتیجه‌ی «آس‌کِشی» برای تعیین حاکم اول: کارت‌های رو‌شده به ترتیب و صندلی برنده */
data class AceDeal(val cards: List<TrickCard>, val hakem: Int)

/** تعداد دست‌هایی که یک تیم باید ببرد تا دست تمام شود */
const val TRICKS_TO_WIN = 7

/**
 * قوانین حکم — خالص و بدون وابستگی به اندروید.
 * همه‌ی توابع وضعیت تازه برمی‌گردانند؛ حرکت غیرمجاز استثنا می‌دهد.
 */
object HokmRules {

    /** دسته‌ی این روش، بُرخورده — در مردابادی دوِ بیرون‌مانده‌ی مسابقه حذف می‌شود */
    fun deck(variant: HokmVariant, random: Random): List<Card> = Deck.shuffled(random)

    /**
     * آس‌کِشی: کارت‌ها یکی‌یکی از صندلی ۰ به بعد رو می‌شوند تا اولین آس بیاید؛
     * صاحبِ آس حاکمِ اول است.
     */
    fun firstHakemDeal(variant: HokmVariant, random: Random): AceDeal {
        val cards = deck(variant, random)
        val revealed = ArrayList<TrickCard>()
        var seat = 0
        for (card in cards) {
            revealed += TrickCard(seat, card)
            if (card.rank == Rank.ACE) return AceDeal(revealed, seat)
            seat = variant.nextSeat(seat)
        }
        // غیرممکن (دسته همیشه آس دارد) — محض اطمینان
        return AceDeal(revealed, 0)
    }

    /** مسابقه‌ی تازه با حاکمِ داده‌شده؛ هنوز کارتی پخش نشده */
    fun newMatch(variant: HokmVariant, target: Int, hakem: Int): HokmState =
        HokmState(
            variant = variant,
            target = target,
            hakem = hakem,
            phase = HokmPhase.CHOOSE_TRUMP,
            hands = List(variant.playerCount) { emptyList() },
        )

    /** شروع یک دستِ تازه: حاکم ۵ کارت اول را می‌گیرد و باید حکم کند */
    fun startHand(state: HokmState, random: Random): HokmState {
        val cards = if (state.isMordabadi) {
            Deck.shuffledWithout(state.removedCards, random)
        } else {
            deck(state.variant, random)
        }
        val n = state.playerCount
        val hands = MutableList<List<Card>>(n) { emptyList() }
        var stock = cards
        if (state.isMordabadi) {
            // مردابادی: اول همه ۹ کارت می‌گیرند و حاکم از همان ۹ حکم می‌کند
            val order = (0 until n).map { (state.hakem + it) % n }
            for (seat in order) {
                hands[seat] = stock.take(9).sortedForHand(null)
                stock = stock.drop(9)
            }
        } else {
            hands[state.hakem] = stock.take(5).sortedForHand(null)
            stock = stock.drop(5)
        }
        return state.copy(
            phase = HokmPhase.CHOOSE_TRUMP,
            hands = hands,
            stock = stock,
            trump = null,
            turn = state.hakem,
            trick = emptyList(),
            tricksWon = List(n) { 0 },
            played = emptyList(),
            lastResult = null,
            handNumber = state.handNumber + 1,
            collectorIndex = 0,
        )
    }

    /** حاکم حکم را انتخاب کرد → بقیه‌ی کارت‌ها پخش می‌شود و بازی شروع می‌شود */
    fun chooseTrump(state: HokmState, trump: Suit): HokmState {
        check(state.phase == HokmPhase.CHOOSE_TRUMP) { "الان وقت انتخاب حکم نیست" }
        val n = state.playerCount
        val hands = state.hands.map { it.toMutableList() }
        var stock = state.stock
        val order = (1..n).map { (state.hakem + it) % n } // از نفر بعد از حاکم تا خود حاکم
        state.variant.dealPattern.forEachIndexed { round, count ->
            for (seat in order) {
                // دور اول قبلاً پخش شده: در مردابادی همه ۹ تا دارند، وگرنه حاکم ۵ تا
                if (round == 0 && (state.isMordabadi || seat == state.hakem)) continue
                hands[seat] += stock.take(count)
                stock = stock.drop(count)
            }
        }
        val dealt = state.copy(
            phase = HokmPhase.PLAYING,
            trump = trump,
            hands = hands.map { it.sortedForHand(trump) },
            stock = stock,
            turn = state.hakem,
            trick = emptyList(),
        )
        // مردابادی: قبل از اولین دست، طلبکارها وصول می‌کنند
        return if (dealt.isMordabadi) MordabadiRules.beginCollection(dealt) else dealt
    }

    /** کارت‌های مجاز این صندلی در این لحظه (اگر نوبتش نباشد، خالی) */
    fun legalMoves(state: HokmState, seat: Int): List<Card> {
        if (state.phase != HokmPhase.PLAYING || state.turn != seat || state.trickComplete) return emptyList()
        return legalPlays(state.hands[seat], state.trick.map { it.card })
    }

    fun isLegal(state: HokmState, seat: Int, card: Card): Boolean = card in legalMoves(state, seat)

    /** انداختن یک کارت روی میز */
    fun play(state: HokmState, seat: Int, card: Card): HokmState {
        require(isLegal(state, seat, card)) { "حرکت غیرمجاز: $card از صندلی $seat" }
        val hands = state.hands.toMutableList()
        hands[seat] = hands[seat] - card
        val trick = state.trick + TrickCard(seat, card)
        val nextTurn = if (trick.size == state.playerCount) seat else state.variant.nextSeat(seat)
        return state.copy(hands = hands, trick = trick, turn = nextTurn)
    }

    /**
     * جمع‌کردنِ میزِ کامل: برنده یک دست می‌گیرد و شروع‌کننده‌ی بعدی می‌شود.
     * اگر دست تمام شده باشد، امتیاز و حاکم بعدی حساب می‌شود.
     */
    fun collectTrick(state: HokmState): HokmState {
        check(state.trickComplete) { "میز هنوز کامل نیست" }
        val winner = state.trickLeader ?: error("میز خالی است")
        val tricksWon = state.tricksWon.toMutableList()
        tricksWon[winner] = tricksWon[winner] + 1
        val next = state.copy(
            tricksWon = tricksWon,
            played = state.played + state.trick.map { it.card },
            trick = emptyList(),
            turn = winner,
        )
        return finishHandIfOver(next)
    }

    /** اگر شرط پایان دست برقرار است، نتیجه را حساب می‌کند؛ وگرنه همان وضعیت */
    private fun finishHandIfOver(state: HokmState): HokmState {
        val cardsLeft = state.hands.any { it.isNotEmpty() }
        // مردابادی: توقفِ ۷ دست نداریم — هر ۱۷ دست بازی و بعد تسویه می‌شود
        if (state.isMordabadi) {
            return if (cardsLeft) state else MordabadiRules.settle(state)
        }
        val variant = state.variant
        val teamTricks = state.teamTricksAll
        val reached = teamTricks.indices.firstOrNull { teamTricks[it] >= TRICKS_TO_WIN }
        if (reached == null && cardsLeft) return state

        val hakemTeam = variant.teamOf(state.hakem)
        val winnerTeam: Int? = when {
            reached != null -> reached
            else -> {
                // کارت‌ها تمام شد و کسی به ۷ نرسید (عملاً پیش نمی‌آید) → بیشترین دست
                val best = teamTricks.max()
                val winners = teamTricks.indices.filter { teamTricks[it] == best }
                if (winners.size == 1) winners.first() else null
            }
        }

        var points = 0
        var kot = false
        var hakemKot = false
        if (winnerTeam != null) {
            points = 1
            val othersZero = teamTricks.indices.all { it == winnerTeam || teamTricks[it] == 0 }
            if (variant.kotApplicable && othersZero) {
                kot = true
                points = 2
                if (winnerTeam != hakemTeam) {
                    hakemKot = true
                    points = 3
                }
            }
        }

        val hakemAfter: Int = when {
            winnerTeam == null -> state.hakem
            winnerTeam == hakemTeam -> state.hakem
            variant == HokmVariant.FOUR -> variant.nextSeat(state.hakem) // نفر بعدی همیشه از تیم مقابل است
            else -> winnerTeam // در سه/دو نفره تیم = صندلی
        }

        val scores = state.scores.toMutableList()
        if (winnerTeam != null) scores[winnerTeam] += points

        val result = HandResult(
            winnerTeam = winnerTeam,
            points = points,
            kot = kot,
            hakemKot = hakemKot,
            hakemBefore = state.hakem,
            hakemAfter = hakemAfter,
            teamTricks = teamTricks,
            hakemTeamWon = winnerTeam == hakemTeam,
        )
        val matchOver = scores.any { it >= state.target }
        return state.copy(
            phase = if (matchOver) HokmPhase.MATCH_OVER else HokmPhase.HAND_OVER,
            scores = scores,
            lastResult = result,
            hakem = hakemAfter,
            turn = hakemAfter,
        )
    }

    /** امتیاز کامل یک کارت برای ارزیابی حکم: شاه و آس بیشتر می‌ارزند */
    internal fun honorValue(rank: Rank): Int = when (rank) {
        Rank.ACE -> 4
        Rank.KING -> 3
        Rank.QUEEN -> 2
        Rank.JACK -> 1
        else -> 0
    }
}
