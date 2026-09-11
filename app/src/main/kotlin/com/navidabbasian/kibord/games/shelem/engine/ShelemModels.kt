package com.navidabbasian.kibord.games.shelem.engine

import kotlinx.serialization.Serializable

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit

/** ثابت‌ها و قوانین پایه‌ی شلم — بدون وابستگی به اندروید */
object ShelemRules {
    const val PLAYERS = 4
    const val HAND_SIZE = 12
    const val KITTY_SIZE = 4
    const val MIN_BID = 100
    const val MAX_BID = 165
    const val BID_STEP = 5
    const val LAST_TRICK_BONUS = 5
    const val CARD_POINTS_TOTAL = 160
    const val HAND_TOTAL = CARD_POINTS_TOTAL + LAST_TRICK_BONUS
    const val DEFAULT_TARGET = 1165
    val TARGETS = listOf(500, 1000, 1165)

    /** امتیاز هر کارت: آس ۱۰، ده ۱۰، شاه/بی‌بی/سرباز ۵، پنج ۵ — جمعاً ۱۶۰ */
    fun cardPoints(card: Card): Int = when (card.rank) {
        Rank.ACE, Rank.TEN -> 10
        Rank.KING, Rank.QUEEN, Rank.JACK, Rank.FIVE -> 5
        else -> 0
    }

    fun points(cards: Collection<Card>): Int = cards.sumOf { cardPoints(it) }

    fun teamOf(seat: Int): Int = seat % 2
    fun partnerOf(seat: Int): Int = (seat + 2) % PLAYERS
    fun nextSeat(seat: Int): Int = (seat + 1) % PLAYERS
    fun isValidBidAmount(amount: Int): Boolean =
        amount in MIN_BID..MAX_BID && amount % BID_STEP == 0

    /** همه‌ی مبالغ مجاز شرط: ۱۰۰، ۱۰۵، …، ۱۶۵ */
    val ALL_BIDS: List<Int> = (MIN_BID..MAX_BID step BID_STEP).toList()
}

/** تنظیمات یک مسابقه */
@Serializable
data class ShelemSettings(
    val targetScore: Int = ShelemRules.DEFAULT_TARGET,
    val shelemBonus: Boolean = true,
)

@Serializable
enum class ShelemPhase {
    /** شرط‌بندی دور میز */
    BIDDING,
    /** حاکم ویدو را برداشته و باید ۴ کارت بخواباند */
    DISCARDING,
    /** حاکم حکم را اعلام می‌کند */
    TRUMP,
    /** ۱۲ دستِ بازی */
    PLAYING,
    /** دست تمام شد؛ نتیجه روی میز است */
    HAND_OVER,
    /** مسابقه تمام شد */
    MATCH_OVER,
}

/** نتیجه‌ی یک دستِ کامل — برای کارنامه و صفحه‌ی پایان دست */
@Serializable
data class HandResult(
    val handNumber: Int,
    val declarer: Int,
    val declarerTeam: Int,
    val bid: Int,
    val trump: Suit,
    /** امتیاز کارت‌هایی که هر تیم در دست‌ها برده (بدون ویدو و بدون دست آخر) */
    val trickPoints: List<Int>,
    /** امتیاز کارت‌های خوابانده‌شده — مال تیم حاکم */
    val kittyPoints: Int,
    val lastTrickTeam: Int,
    val declarerPoints: Int,
    val opponentPoints: Int,
    val made: Boolean,
    /** تیم حاکم همه‌ی ۱۶۵ امتیاز را برده */
    val shelem: Boolean,
    /** امتیاز دوبرابر شده (شلم یا شرط ۱۶۵) */
    val doubled: Boolean,
    /** تغییر امتیاز هر تیم در این دست */
    val teamGain: List<Int>,
)

/** وضعیت کامل یک مسابقه‌ی شلم — داده‌ی خالص و تغییرناپذیر */
@Serializable
data class ShelemState(
    val settings: ShelemSettings = ShelemSettings(),
    val phase: ShelemPhase = ShelemPhase.BIDDING,
    val handNumber: Int = 1,
    val dealer: Int = 0,
    val hands: List<List<Card>> = List(ShelemRules.PLAYERS) { emptyList() },
    val kitty: List<Card> = emptyList(),
    val discarded: List<Card> = emptyList(),
    // ---- شرط‌بندی ----
    val bidTurn: Int = 0,
    /** آخرین شرطِ هر بازیکن (null = هنوز شرطی نبسته) */
    val bids: List<Int?> = List(ShelemRules.PLAYERS) { null },
    val passed: Set<Int> = emptySet(),
    val highBid: Int? = null,
    val highBidder: Int? = null,
    /** حاکم مجبور شده ۱۰۰ بردارد چون بقیه پاس دادند */
    val forcedBid: Boolean = false,
    // ---- قرارداد ----
    val declarer: Int? = null,
    val contract: Int = 0,
    val trump: Suit? = null,
    // ---- بازی ----
    val leader: Int = 0,
    val turn: Int = 0,
    /** کارت‌های روی میز؛ کارت i را بازیکن (leader + i) % 4 زده */
    val trick: List<Card> = emptyList(),
    /** وقتی ۴ کارت روی میز است: برنده‌ی دست، تا وقتی جمع شود */
    val trickWinner: Int? = null,
    val tricksPlayed: Int = 0,
    val trickPoints: List<Int> = listOf(0, 0),
    val tricksTaken: List<Int> = listOf(0, 0),
    val lastTrickWinner: Int? = null,
    val playedCards: List<Card> = emptyList(),
    // ---- مسابقه ----
    val scores: List<Int> = listOf(0, 0),
    val handResult: HandResult? = null,
    val history: List<HandResult> = emptyList(),
    val matchWinner: Int? = null,
) {
    val declarerTeam: Int? get() = declarer?.let { ShelemRules.teamOf(it) }

    /** بازیکنی که کارت شماره‌ی i روی میز را زده */
    fun trickPlayer(i: Int): Int = (leader + i) % ShelemRules.PLAYERS

    /** کارتی که بازیکن [seat] در دست جاری زده (اگر زده باشد) */
    fun trickCardOf(seat: Int): Card? {
        val idx = (seat - leader + ShelemRules.PLAYERS) % ShelemRules.PLAYERS
        return trick.getOrNull(idx)
    }

    /**
     * امتیاز زنده‌ی هر تیم در این دست: دست‌ها + (اگر [includeKitty]) ویدو برای تیم حاکم.
     * ویدو را فقط وقتی نشان بده که بیننده خودش حاکم است؛ وگرنه اطلاعات لو می‌رود.
     */
    fun livePoints(team: Int, includeKitty: Boolean = true): Int {
        val kitty = if (includeKitty && declarerTeam == team && phase >= ShelemPhase.TRUMP) ShelemRules.points(discarded) else 0
        return trickPoints[team] + kitty
    }

    val biddingActive: List<Int> get() = (0 until ShelemRules.PLAYERS).filter { it !in passed }
}
