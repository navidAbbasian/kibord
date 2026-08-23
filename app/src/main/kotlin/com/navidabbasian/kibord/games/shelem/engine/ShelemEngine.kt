package com.navidabbasian.kibord.games.shelem.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Deck
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.legalPlays
import com.navidabbasian.kibord.core.cards.sortedForHand
import com.navidabbasian.kibord.core.cards.trickWinnerIndex
import kotlin.random.Random

/**
 * موتور خالص شلم: پخش کارت، ماشین شرط‌بندی، ویدو، حکم، دست‌ها و امتیازدهی.
 * همه‌ی متدها وضعیت جدید برمی‌گردانند و روی ورودی نامعتبر استثنا می‌دهند
 * (ViewModel قبل از صدا زدن، مجاز بودن را چک می‌کند).
 */
object ShelemEngine {

    /** شروع مسابقه: دیلرِ تصادفی، پخش دست اول */
    fun newMatch(
        settings: ShelemSettings = ShelemSettings(),
        random: Random = Random.Default,
        dealer: Int = random.nextInt(ShelemRules.PLAYERS),
    ): ShelemState = deal(ShelemState(settings = settings, dealer = dealer), random)

    /** پخش یک دست تازه با حفظ امتیازها و تاریخچه */
    fun deal(base: ShelemState, random: Random = Random.Default): ShelemState {
        val deck = Deck.shuffled(random)
        // پخش سنتی ۴-۴-۴: دور اول به هر نفر ۴ تا، بعد ۴ کارت ویدو، بعد دو دور دیگر
        val hands = List(ShelemRules.PLAYERS) { mutableListOf<Card>() }
        var idx = 0
        val first = ShelemRules.nextSeat(base.dealer)
        repeat(3) { round ->
            for (k in 0 until ShelemRules.PLAYERS) {
                val seat = (first + k) % ShelemRules.PLAYERS
                repeat(4) { hands[seat].add(deck[idx++]) }
            }
            if (round == 0) idx += ShelemRules.KITTY_SIZE
        }
        val kitty = deck.subList(ShelemRules.PLAYERS * 4, ShelemRules.PLAYERS * 4 + ShelemRules.KITTY_SIZE)
        return ShelemState(
            settings = base.settings,
            phase = ShelemPhase.BIDDING,
            handNumber = base.handNumber,
            dealer = base.dealer,
            hands = hands.map { it.sortedForHand(null) },
            kitty = kitty.toList(),
            bidTurn = first,
            scores = base.scores,
            history = base.history,
        )
    }

    // ------------------------------------------------------------------
    // شرط‌بندی
    // ------------------------------------------------------------------

    /** آیا این بازیکن حق پاس دادن دارد؟ دیلر وقتی بقیه پاس داده‌اند، مجبور است شرط ببندد */
    fun canPass(state: ShelemState, seat: Int): Boolean {
        if (state.phase != ShelemPhase.BIDDING || state.bidTurn != seat || seat in state.passed) return false
        val forced = seat == state.dealer && state.highBid == null && state.biddingActive == listOf(seat)
        return !forced
    }

    /** کمترین شرطِ مجاز برای نوبت جاری (null = شرطی ممکن نیست) */
    fun minBid(state: ShelemState): Int? {
        val next = (state.highBid ?: (ShelemRules.MIN_BID - ShelemRules.BID_STEP)) + ShelemRules.BID_STEP
        return next.takeIf { it <= ShelemRules.MAX_BID }
    }

    /** مبالغ مجاز برای بازیکنی که نوبتش است */
    fun availableBids(state: ShelemState, seat: Int): List<Int> {
        if (state.phase != ShelemPhase.BIDDING || state.bidTurn != seat || seat in state.passed) return emptyList()
        val min = minBid(state) ?: return emptyList()
        return ShelemRules.ALL_BIDS.filter { it >= min }
    }

    fun bid(state: ShelemState, seat: Int, amount: Int): ShelemState {
        require(state.phase == ShelemPhase.BIDDING) { "نوبت شرط‌بندی نیست" }
        require(state.bidTurn == seat) { "نوبت این بازیکن نیست" }
        require(seat !in state.passed) { "این بازیکن پاس داده" }
        require(ShelemRules.isValidBidAmount(amount)) { "مبلغ شرط باید مضرب ۵ بین ۱۰۰ و ۱۶۵ باشد" }
        require(state.highBid == null || amount > state.highBid) { "شرط باید از شرط قبلی بیشتر باشد" }
        val bids = state.bids.toMutableList().also { it[seat] = amount }
        val forced = state.highBid == null && seat == state.dealer && state.biddingActive == listOf(seat)
        val next = state.copy(
            bids = bids,
            highBid = amount,
            highBidder = seat,
            forcedBid = state.forcedBid || forced,
        )
        return advanceBidding(next)
    }

    fun pass(state: ShelemState, seat: Int): ShelemState {
        require(state.phase == ShelemPhase.BIDDING) { "نوبت شرط‌بندی نیست" }
        require(state.bidTurn == seat) { "نوبت این بازیکن نیست" }
        require(canPass(state, seat)) { "دیلر وقتی بقیه پاس داده‌اند نمی‌تواند پاس بدهد" }
        return advanceBidding(state.copy(passed = state.passed + seat))
    }

    private fun advanceBidding(state: ShelemState): ShelemState {
        val active = state.biddingActive
        val high = state.highBidder
        val finished = (high != null && active == listOf(high)) || state.highBid == ShelemRules.MAX_BID
        if (finished) return startDiscarding(state, high!!, state.highBid!!)
        // نوبت بعدی: اولین بازیکنِ پاس‌نداده بعد از نوبت جاری
        var next = ShelemRules.nextSeat(state.bidTurn)
        while (next in state.passed) next = ShelemRules.nextSeat(next)
        return state.copy(bidTurn = next)
    }

    private fun startDiscarding(state: ShelemState, declarer: Int, contract: Int): ShelemState {
        val hands = state.hands.toMutableList()
        hands[declarer] = (hands[declarer] + state.kitty).sortedForHand(null)
        return state.copy(
            phase = ShelemPhase.DISCARDING,
            declarer = declarer,
            contract = contract,
            hands = hands,
            turn = declarer,
        )
    }

    // ------------------------------------------------------------------
    // ویدو و حکم
    // ------------------------------------------------------------------

    /** حاکم ۴ کارت می‌خواباند */
    fun discard(state: ShelemState, cards: List<Card>): ShelemState {
        require(state.phase == ShelemPhase.DISCARDING) { "نوبت خواباندن نیست" }
        val declarer = state.declarer!!
        require(cards.size == ShelemRules.KITTY_SIZE) { "باید دقیقاً ۴ کارت بخوابانی" }
        require(cards.toSet().size == ShelemRules.KITTY_SIZE) { "کارت تکراری" }
        require(cards.all { it in state.hands[declarer] }) { "این کارت‌ها در دست حاکم نیستند" }
        val hands = state.hands.toMutableList()
        hands[declarer] = hands[declarer].filterNot { it in cards }
        return state.copy(phase = ShelemPhase.TRUMP, hands = hands, discarded = cards.toList())
    }

    fun chooseTrump(state: ShelemState, suit: Suit): ShelemState {
        require(state.phase == ShelemPhase.TRUMP) { "نوبت انتخاب حکم نیست" }
        val declarer = state.declarer!!
        return state.copy(
            phase = ShelemPhase.PLAYING,
            trump = suit,
            hands = state.hands.map { it.sortedForHand(suit) },
            leader = declarer,
            turn = declarer,
            trick = emptyList(),
            trickWinner = null,
        )
    }

    // ------------------------------------------------------------------
    // بازی
    // ------------------------------------------------------------------

    fun legalPlaysFor(state: ShelemState, seat: Int): List<Card> {
        if (state.phase != ShelemPhase.PLAYING || state.turn != seat || state.trickWinner != null) return emptyList()
        return legalPlays(state.hands[seat], state.trick)
    }

    fun play(state: ShelemState, seat: Int, card: Card): ShelemState {
        require(state.phase == ShelemPhase.PLAYING) { "بازی شروع نشده" }
        require(state.trickWinner == null) { "دست قبلی هنوز جمع نشده" }
        require(state.turn == seat) { "نوبت این بازیکن نیست" }
        require(card in legalPlaysFor(state, seat)) { "این کارت مجاز نیست" }
        val hands = state.hands.toMutableList()
        hands[seat] = hands[seat].filterNot { it == card }
        val trick = state.trick + card
        val played = state.playedCards + card
        if (trick.size < ShelemRules.PLAYERS) {
            return state.copy(hands = hands, trick = trick, playedCards = played, turn = ShelemRules.nextSeat(seat))
        }
        val winIdx = trickWinnerIndex(trick, state.trump)
        val winner = state.trickPlayer(winIdx)
        return state.copy(hands = hands, trick = trick, playedCards = played, trickWinner = winner)
    }

    /** جمع کردن دستِ کامل‌شده از روی میز؛ برنده دست بعدی را شروع می‌کند یا دست تمام می‌شود */
    fun collectTrick(state: ShelemState): ShelemState {
        require(state.phase == ShelemPhase.PLAYING && state.trickWinner != null) { "دستی برای جمع کردن نیست" }
        val winner = state.trickWinner
        val team = ShelemRules.teamOf(winner)
        val pts = state.trickPoints.toMutableList().also { it[team] += ShelemRules.points(state.trick) }
        val taken = state.tricksTaken.toMutableList().also { it[team] += 1 }
        val tricksPlayed = state.tricksPlayed + 1
        val next = state.copy(
            trick = emptyList(),
            trickWinner = null,
            trickPoints = pts,
            tricksTaken = taken,
            tricksPlayed = tricksPlayed,
            lastTrickWinner = winner,
            leader = winner,
            turn = winner,
        )
        return if (tricksPlayed == ShelemRules.HAND_SIZE) finishHand(next) else next
    }

    // ------------------------------------------------------------------
    // امتیازدهی
    // ------------------------------------------------------------------

    /** امتیاز یک دستِ تمام‌شده (خالص، برای تست) */
    fun scoreHand(
        settings: ShelemSettings,
        handNumber: Int,
        declarer: Int,
        bid: Int,
        trump: Suit,
        trickPoints: List<Int>,
        kittyPoints: Int,
        lastTrickTeam: Int,
    ): HandResult {
        val declTeam = ShelemRules.teamOf(declarer)
        val oppTeam = 1 - declTeam
        val declarerPoints = trickPoints[declTeam] + kittyPoints +
            (if (lastTrickTeam == declTeam) ShelemRules.LAST_TRICK_BONUS else 0)
        val opponentPoints = trickPoints[oppTeam] +
            (if (lastTrickTeam == oppTeam) ShelemRules.LAST_TRICK_BONUS else 0)
        val made = declarerPoints >= bid
        val shelem = declarerPoints == ShelemRules.HAND_TOTAL
        val doubled = made && (bid == ShelemRules.MAX_BID || (shelem && settings.shelemBonus))
        val gain = MutableList(2) { 0 }
        if (made) {
            gain[declTeam] = if (doubled) declarerPoints * 2 else declarerPoints
            gain[oppTeam] = opponentPoints
        } else {
            gain[declTeam] = -bid
            gain[oppTeam] = opponentPoints
        }
        return HandResult(
            handNumber = handNumber,
            declarer = declarer,
            declarerTeam = declTeam,
            bid = bid,
            trump = trump,
            trickPoints = trickPoints,
            kittyPoints = kittyPoints,
            lastTrickTeam = lastTrickTeam,
            declarerPoints = declarerPoints,
            opponentPoints = opponentPoints,
            made = made,
            shelem = shelem,
            doubled = doubled,
            teamGain = gain,
        )
    }

    /** برنده‌ی مسابقه بر اساس امتیازها؛ null = هنوز ادامه دارد */
    fun matchWinner(scores: List<Int>, target: Int, declarerTeam: Int): Int? {
        val reached = scores.indices.filter { scores[it] >= target }
        return when (reached.size) {
            0 -> null
            1 -> reached.first()
            else -> when {
                scores[0] > scores[1] -> 0
                scores[1] > scores[0] -> 1
                else -> declarerTeam
            }
        }
    }

    private fun finishHand(state: ShelemState): ShelemState {
        val declarer = state.declarer!!
        val result = scoreHand(
            settings = state.settings,
            handNumber = state.handNumber,
            declarer = declarer,
            bid = state.contract,
            trump = state.trump!!,
            trickPoints = state.trickPoints,
            kittyPoints = ShelemRules.points(state.discarded),
            lastTrickTeam = ShelemRules.teamOf(state.lastTrickWinner!!),
        )
        val scores = listOf(state.scores[0] + result.teamGain[0], state.scores[1] + result.teamGain[1])
        val winner = matchWinner(scores, state.settings.targetScore, result.declarerTeam)
        return state.copy(
            phase = if (winner != null) ShelemPhase.MATCH_OVER else ShelemPhase.HAND_OVER,
            scores = scores,
            handResult = result,
            history = state.history + result,
            matchWinner = winner,
        )
    }

    /** دست بعدی: دیلر یک نفر می‌چرخد */
    fun nextHand(state: ShelemState, random: Random = Random.Default): ShelemState {
        require(state.phase == ShelemPhase.HAND_OVER) { "دست هنوز تمام نشده" }
        return deal(
            state.copy(dealer = ShelemRules.nextSeat(state.dealer), handNumber = state.handNumber + 1),
            random,
        )
    }
}
