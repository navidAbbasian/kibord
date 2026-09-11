package com.navidabbasian.kibord.games.hokm.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.sortedForHand
import kotlin.random.Random

/** جزئیات یک تبادلِ وصول طلب: چه کسی چه داد و چه گرفت */
data class ExchangeOutcome(
    val state: HokmState,
    val collector: Int,
    val debtor: Int,
    val suit: Suit,
    /** حکم‌خواهی بود؟ (۳ طلب خرج شد) */
    val trumpDemand: Boolean,
    /** کارتی که طلبکار داد (پایین‌ترینِ خالِ انتخابی‌اش) */
    val gaveCard: Card,
    /** کارتی که از بدهکار گرفت */
    val tookCard: Card,
    /** بدهکار خال را نداشت و کارت دلخواه داد */
    val debtorWasVoid: Boolean,
)

/** راه‌اندازی دوئل پایانی دو نفره؛ [seats] صندلی دوئل → صندلی مردابادی */
data class DuelSetup(val state: HokmState, val seats: List<Int>)

/** تصمیم وصول ربات: از کدام بدهکار و با کدام خال (خالِ حکم = حکم‌خواهی) */
data class BotCollect(val debtor: Int, val suit: Suit)

/**
 * قواعد «حکم مردابادی» — روش سه نفره‌ی فولکلور:
 * یک دو از دسته بیرون، سهمیه‌های ۳/۵/۹، وصول طلب پیش از هر دست،
 * تسویه‌ی تراز بعد از ۱۷ دست و حذف با پر شدن سقف بدهی.
 */
object MordabadiRules {

    /** سهمیه‌های سه صندلی — جمعشان ۱۷ است */
    val QUOTAS = listOf(3, 5, 9)

    /** هزینه‌ی حکم‌خواهی: ۳ طلب خرج می‌شود و ۳ بدهی پاک می‌کند */
    const val TRUMP_DEMAND_COST = 3

    /** سقف‌های بدهی که سازنده‌ی مسابقه انتخاب می‌کند */
    val DEBT_LIMITS = listOf(11, 17, 21)

    /**
     * مسابقه‌ی تازه: یک دوِ تصادفی برای کل مسابقه بیرون می‌ماند،
     * سهمیه‌ها تصادفی پخش می‌شوند و صاحبِ ۹ حاکم است.
     */
    fun newMatch(debtLimit: Int, random: Random): HokmState {
        val removed = Card(Suit.entries.random(random), Rank.TWO)
        val quotas = QUOTAS.shuffled(random)
        return HokmState(
            variant = HokmVariant.THREE,
            target = debtLimit,
            hakem = quotas.indexOf(9),
            phase = HokmPhase.CHOOSE_TRUMP,
            hands = List(3) { emptyList() },
            removedCard = removed,
            quotas = quotas,
            balances = List(3) { 0 },
            totalDebts = List(3) { 0 },
        )
    }

    // ---------- وصول طلب ----------

    /** ترتیب وصول: اول حاکم (۹دستی)، بعد ۵دستی، بعد ۳دستی */
    fun collectionQueue(state: HokmState): List<Int> =
        state.quotas.indices.sortedByDescending { state.quotas[it] }

    /** طلبکاری که الان نوبت وصولش است — null اگر فاز وصول نیست */
    fun collector(state: HokmState): Int? =
        if (state.phase == HokmPhase.COLLECTION) collectionQueue(state).getOrNull(state.collectorIndex) else null

    /** صندلی‌های بدهکار (تراز منفی) */
    fun debtors(state: HokmState): List<Int> =
        state.balances.indices.filter { state.balances[it] < 0 }

    /** خال‌های غیرحکمی که این طلبکار می‌تواند انتخاب کند (باید خودش کارتش را داشته باشد) */
    fun availableSuits(state: HokmState, seat: Int): List<Suit> =
        Suit.entries.filter { s -> s != state.trump && state.hands[seat].any { it.suit == s } }

    /** آیا حکم‌خواهی از این بدهکار مجاز است؟ طلبِ ≥۳، بدهیِ ≥۳ و داشتن حداقل یک حکم */
    fun canDemandTrump(state: HokmState, collectorSeat: Int, debtor: Int): Boolean {
        val trump = state.trump ?: return false
        return state.balances.getOrElse(collectorSeat) { 0 } >= TRUMP_DEMAND_COST &&
            -state.balances.getOrElse(debtor) { 0 } >= TRUMP_DEMAND_COST &&
            state.hands[collectorSeat].any { it.suit == trump }
    }

    /** بدهکار از این خال کارتی ندارد و باید کارت دلخواه بدهد */
    fun debtorVoidIn(state: HokmState, debtor: Int, suit: Suit): Boolean =
        state.hands[debtor].none { it.suit == suit }

    /** آیا این طلبکار هنوز حرکتی دارد؟ (طلب دارد، بدهکاری هست و خالی برای انتخاب دارد) */
    fun hasMove(state: HokmState, seat: Int): Boolean {
        if (state.balances.getOrElse(seat) { 0 } <= 0) return false
        val ds = debtors(state)
        if (ds.isEmpty()) return false
        if (availableSuits(state, seat).isNotEmpty()) return true
        return ds.any { canDemandTrump(state, seat, it) }
    }

    /** فاز وصول را شروع می‌کند؛ اگر طلبکاری با حرکت نباشد یک‌راست به بازی می‌رود */
    fun beginCollection(state: HokmState): HokmState =
        advance(state.copy(phase = HokmPhase.COLLECTION, collectorIndex = 0))

    /** نوبت وصول را جلو می‌برد: طلبکارهای بی‌حرکت رد می‌شوند؛ آخرش بازی با حاکم شروع می‌شود */
    fun advance(state: HokmState): HokmState {
        check(state.phase == HokmPhase.COLLECTION) { "فاز وصول نیست" }
        val queue = collectionQueue(state)
        var i = state.collectorIndex
        while (i < queue.size && !hasMove(state, queue[i])) i++
        return if (i < queue.size) {
            state.copy(collectorIndex = i)
        } else {
            state.copy(phase = HokmPhase.PLAYING, turn = state.hakem, collectorIndex = queue.size)
        }
    }

    /**
     * یک تبادل: طلبکارِ نوبت، خال و بدهکار را انتخاب کرده.
     * خال حکم = حکم‌خواهی (۳ طلب)؛ وگرنه ۱ طلب.
     * طلبکار پایین‌ترینِ خال را می‌دهد و بالاترینِ همان خال را از بدهکار می‌گیرد؛
     * اگر بدهکار خال را ندارد، کارت دلخواه ([debtorGive]، یا بدترین کارتِ ربات) می‌دهد.
     */
    fun exchange(state: HokmState, debtor: Int, suit: Suit, debtorGive: Card? = null): ExchangeOutcome {
        check(state.phase == HokmPhase.COLLECTION) { "الان وقت وصول طلب نیست" }
        val collectorSeat = collector(state) ?: error("طلبکاری در نوبت نیست")
        require(debtor != collectorSeat) { "طلبکار نمی‌تواند از خودش بگیرد" }
        require(state.balances[debtor] < 0) { "این بازیکن بدهکار نیست" }
        val trumpDemand = suit == state.trump
        val cost = if (trumpDemand) TRUMP_DEMAND_COST else 1
        require(state.balances[collectorSeat] >= cost) { "طلب کافی نیست" }
        if (trumpDemand) {
            require(-state.balances[debtor] >= TRUMP_DEMAND_COST) { "بدهیِ بدهکار برای حکم‌خواهی کافی نیست" }
        }
        val ownSuit = state.hands[collectorSeat].filter { it.suit == suit }
        require(ownSuit.isNotEmpty()) { "طلبکار از این خال کارتی ندارد" }
        val give = ownSuit.minBy { it.rank.order }

        val debtorSuit = state.hands[debtor].filter { it.suit == suit }
        val wasVoid = debtorSuit.isEmpty()
        val take = if (!wasVoid) {
            debtorSuit.maxBy { it.rank.order }
        } else {
            val chosen = debtorGive ?: botWorstCard(state, debtor)
            require(chosen in state.hands[debtor]) { "کارت انتخابی دستِ بدهکار نیست" }
            chosen
        }

        val hands = state.hands.toMutableList()
        hands[collectorSeat] = (hands[collectorSeat] - give + take).sortedForHand(state.trump)
        hands[debtor] = (hands[debtor] - take + give).sortedForHand(state.trump)
        val balances = state.balances.toMutableList()
        balances[collectorSeat] -= cost
        balances[debtor] += cost

        val next = advance(state.copy(hands = hands, balances = balances))
        return ExchangeOutcome(
            state = next,
            collector = collectorSeat,
            debtor = debtor,
            suit = suit,
            trumpDemand = trumpDemand,
            gaveCard = give,
            tookCard = take,
            debtorWasVoid = wasVoid,
        )
    }

    /** بدترین کارتِ یک صندلی (برای وقتی بدهکارِ ربات خال را ندارد): پایین‌ترین غیرحکم */
    fun botWorstCard(state: HokmState, seat: Int): Card {
        val hand = state.hands[seat]
        val nonTrump = hand.filter { it.suit != state.trump }
        val pool = nonTrump.ifEmpty { hand }
        return pool.minWith(
            compareBy({ it.rank.order }, { c -> hand.count { it.suit == c.suit } }),
        )
    }

    /**
     * سیاست وصول ربات: بدهکارِ پربدهی‌تر را هدف می‌گیرد؛
     * اگر طلبش ≥۳ است و سهمیه‌اش ۵ یا ۹ است (حاکمِ بعدی یا حاکمِ فعلی) حکم‌خواهی می‌کند،
     * وگرنه خالی را می‌گیرد که پایین‌ترین کارتِ خودش در آن از همه ضعیف‌تر است.
     */
    fun botCollect(state: HokmState): BotCollect? {
        val seat = collector(state) ?: return null
        val ds = debtors(state)
        if (ds.isEmpty()) return null
        val target = ds.minBy { state.balances[it] }
        val trump = state.trump
        if (trump != null && state.quotaOf(seat) >= 5 && canDemandTrump(state, seat, target)) {
            return BotCollect(target, trump)
        }
        val suits = availableSuits(state, seat)
        if (suits.isEmpty()) {
            val alt = ds.firstOrNull { trump != null && canDemandTrump(state, seat, it) } ?: return null
            return BotCollect(alt, trump!!)
        }
        val suit = suits.minBy { s -> state.hands[seat].filter { it.suit == s }.minOf { it.rank.order } }
        return BotCollect(target, suit)
    }

    // ---------- تسویه و حذف ----------

    /**
     * تسویه بعد از ۱۷ دست: (دستِ گرفته − سهمیه) به تراز اضافه می‌شود،
     * بدهی‌های تازه به جمعِ بدهی می‌رود، سهمیه‌ها می‌چرخند (۳→۵→۹→۳)
     * و اگر جمع بدهی کسی به سقف رسید حذف می‌شود.
     */
    fun settle(state: HokmState): HokmState {
        val deltas = List(3) { state.tricksWon[it] - state.quotas[it] }
        val balances = List(3) { state.balances[it] + deltas[it] }
        val totalDebts = List(3) { state.totalDebts[it] + maxOf(0, -deltas[it]) }
        val crossed = (0..2).filter { totalDebts[it] >= state.debtLimit }
        val eliminated = when {
            crossed.isEmpty() -> null
            crossed.size == 1 -> crossed.first()
            else -> {
                // هر دو رد کردند: پربدهی‌تر؛ مساوی → آن که این دست ۳دستی بود
                val worst = crossed.maxOf { totalDebts[it] }
                crossed.filter { totalDebts[it] == worst }.minBy { state.quotas[it] }
            }
        }
        val newQuotas = state.quotas.map { q ->
            when (q) {
                3 -> 5
                5 -> 9
                else -> 3
            }
        }
        val hakemAfter = newQuotas.indexOf(9)
        val result = HandResult(
            winnerTeam = null,
            points = 0,
            kot = false,
            hakemKot = false,
            hakemBefore = state.hakem,
            hakemAfter = hakemAfter,
            teamTricks = state.teamTricksAll,
            hakemTeamWon = false,
            deltas = deltas,
            eliminatedSeat = eliminated,
        )
        return state.copy(
            phase = HokmPhase.HAND_OVER,
            balances = balances,
            totalDebts = totalDebts,
            quotas = newQuotas,
            hakem = hakemAfter,
            turn = hakemAfter,
            lastResult = result,
        )
    }

    /**
     * دوئل پایانی: دو بازمانده یک دستِ حکمِ دو نفره (۱۳ کارتی، تا ۷ دست) بازی می‌کنند.
     * انسان اگر مانده باشد صندلی ۰ دوئل است؛ حاکمِ دوئل بازمانده‌ی کم‌بدهی‌تر است.
     */
    fun startDuel(state: HokmState, random: Random): DuelSetup {
        val out = state.lastResult?.eliminatedSeat ?: error("کسی حذف نشده است")
        val survivors = (0..2).filter { it != out }
        val seats = if (0 in survivors) listOf(0, survivors.first { it != 0 }) else survivors
        val hakemDuel = if (state.totalDebts[seats[1]] < state.totalDebts[seats[0]]) 1 else 0
        val match = HokmRules.newMatch(HokmVariant.TWO, target = 1, hakem = hakemDuel)
        return DuelSetup(HokmRules.startHand(match, random), seats)
    }
}
