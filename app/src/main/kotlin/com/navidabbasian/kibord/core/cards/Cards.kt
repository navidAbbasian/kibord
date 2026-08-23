package com.navidabbasian.kibord.core.cards

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * مدلِ مشترک کارت‌های بازی برای حکم، شلم و هر بازی کارتیِ بعدی.
 * فقط داده و منطقِ پایه؛ هیچ وابستگی به رابط کاربری ندارد تا در تست‌ها راحت باشد.
 */
@Serializable
enum class Suit(val symbol: String, val persian: String, val isRed: Boolean) {
    SPADES("♠", "پیک", false),
    HEARTS("♥", "دل", true),
    DIAMONDS("♦", "خشت", true),
    CLUBS("♣", "گشنیز", false),
}

/** از ۲ تا آس؛ [order] برای مقایسه (آس بزرگ‌ترین است) */
@Serializable
enum class Rank(val order: Int, val label: String, val persian: String) {
    TWO(2, "2", "دو"),
    THREE(3, "3", "سه"),
    FOUR(4, "4", "چهار"),
    FIVE(5, "5", "پنج"),
    SIX(6, "6", "شش"),
    SEVEN(7, "7", "هفت"),
    EIGHT(8, "8", "هشت"),
    NINE(9, "9", "نه"),
    TEN(10, "10", "ده"),
    JACK(11, "J", "سرباز"),
    QUEEN(12, "Q", "بی‌بی"),
    KING(13, "K", "شاه"),
    ACE(14, "A", "آس"),
}

@Serializable
data class Card(val suit: Suit, val rank: Rank) : Comparable<Card> {
    /** شناسه‌ی کوتاه و پایدار برای پروتکل/ذخیره: مثل "SA" یا "H10" */
    val id: String get() = "${suit.name.first()}${rank.label}"

    /** نام خوانای فارسی: «آس دل»، «ده پیک» */
    val persianName: String get() = "${rank.persian} ${suit.persian}"

    override fun compareTo(other: Card): Int =
        compareValuesBy(this, other, { it.suit.ordinal }, { it.rank.order })

    override fun toString(): String = id

    companion object {
        fun parse(id: String): Card? {
            if (id.length < 2) return null
            val suit = Suit.entries.firstOrNull { it.name.first() == id[0] } ?: return null
            val rank = Rank.entries.firstOrNull { it.label == id.substring(1) } ?: return null
            return Card(suit, rank)
        }
    }
}

object Deck {
    /** دستِ کامل ۵۲ تایی، مرتب */
    fun full(): List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(s, r) } }

    /** بُر زده */
    fun shuffled(random: Random = Random.Default): List<Card> = full().shuffled(random)

    /** بُر زده بدون کارت‌های داده‌شده (مثلاً ۲ خشت در حکمِ سه‌نفره) */
    fun shuffledWithout(excluded: Collection<Card>, random: Random = Random.Default): List<Card> =
        full().filterNot { it in excluded }.shuffled(random)
}

/** چیدمان استانداردِ دست: رنگ‌ها به ترتیب، و در هر رنگ از بزرگ به کوچک؛ حکم اول */
fun List<Card>.sortedForHand(trump: Suit? = null): List<Card> {
    val suitOrder = listOfNotNull(trump) + Suit.entries.filter { it != trump }
    return sortedWith(compareBy<Card> { suitOrder.indexOf(it.suit) }.thenByDescending { it.rank.order })
}

/** برنده‌ی یک دست (trick): بزرگ‌ترین حکم، وگرنه بزرگ‌ترینِ رنگِ شروع. کارت اول = کارت شروع */
fun trickWinnerIndex(trick: List<Card>, trump: Suit?): Int {
    require(trick.isNotEmpty())
    val lead = trick.first().suit
    var best = 0
    for (i in 1 until trick.size) {
        val c = trick[i]
        val b = trick[best]
        val cTrump = c.suit == trump
        val bTrump = b.suit == trump
        val better = when {
            cTrump && !bTrump -> true
            !cTrump && bTrump -> false
            cTrump && bTrump -> c.rank.order > b.rank.order
            c.suit == lead && b.suit == lead -> c.rank.order > b.rank.order
            c.suit == lead -> true
            else -> false
        }
        if (better) best = i
    }
    return best
}

/** کارت‌های مجاز برای بازی: اگر رنگِ شروع را داری باید همان را بدهی */
fun legalPlays(hand: List<Card>, trick: List<Card>): List<Card> {
    if (trick.isEmpty()) return hand
    val lead = trick.first().suit
    val same = hand.filter { it.suit == lead }
    return if (same.isNotEmpty()) same else hand
}
