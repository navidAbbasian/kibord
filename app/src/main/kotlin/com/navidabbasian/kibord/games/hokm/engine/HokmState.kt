package com.navidabbasian.kibord.games.hokm.engine

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.trickWinnerIndex
import kotlinx.serialization.Serializable

/** روش بازی: چهار نفره (دو تیم)، سه نفره «مردابادی» (سهمیه و طلب/بدهی) و دو نفره */
@Serializable
enum class HokmVariant(
    val playerCount: Int,
    val handSize: Int,
    val teamCount: Int,
    val analyticsName: String,
    val persian: String,
) {
    FOUR(playerCount = 4, handSize = 13, teamCount = 2, analyticsName = "4p", persian = "۴ نفره"),
    THREE(playerCount = 3, handSize = 17, teamCount = 3, analyticsName = "mordabadi", persian = "۳ نفره — مردابادی"),
    TWO(playerCount = 2, handSize = 13, teamCount = 2, analyticsName = "2p", persian = "۲ نفره");

    /** تیمِ هر صندلی: در چهار نفره ۰و۲ مقابل ۱و۳؛ در بقیه هرکس تیم خودش است */
    fun teamOf(seat: Int): Int = if (this == FOUR) seat % 2 else seat

    /** یارِ هر صندلی (فقط چهار نفره) */
    fun partnerOf(seat: Int): Int? = if (this == FOUR) (seat + 2) % 4 else null

    /**
     * الگوی پخش کارت: در ۴/۲ نفره حاکم اول ۵ تا می‌گیرد و بعد دورهای ۴تایی؛
     * در مردابادی اول همه ۹ تا می‌گیرند (حاکم از همان ۹ حکم می‌کند) و بعد دو دورِ ۴تایی.
     */
    val dealPattern: List<Int>
        get() = when (this) {
            FOUR -> listOf(5, 4, 4)
            THREE -> listOf(9, 4, 4)
            TWO -> listOf(5, 4, 4)
        }

    /** آیا «کُت» (۷–۰) در این روش معنا دارد؟ */
    val kotApplicable: Boolean get() = this != THREE

    fun nextSeat(seat: Int): Int = (seat + 1) % playerCount
}

/** فاز یک دستِ حکم */
@Serializable
enum class HokmPhase {
    /** حاکم ۵ کارت اول را گرفته و باید حکم را انتخاب کند */
    CHOOSE_TRUMP,
    /** مردابادی: طلبکارها قبل از شروع بازی طلبشان را وصول می‌کنند */
    COLLECTION,
    /** بازیِ دست‌ها (تریک‌ها) */
    PLAYING,
    /** دست تمام شد؛ نتیجه در [HokmState.lastResult] */
    HAND_OVER,
    /** بازی (مسابقه) تمام شد */
    MATCH_OVER,
}

/** یک کارتِ روی میز: چه کسی آن را انداخته */
@Serializable
data class TrickCard(val seat: Int, val card: Card)

/** نتیجه‌ی یک دستِ تمام‌شده */
@Serializable
data class HandResult(
    /** تیمِ برنده — null یعنی مساوی یا مردابادی (که برنده‌ی تکی ندارد) */
    val winnerTeam: Int?,
    /** امتیازی که برنده گرفت: ۱، کُت ۲، حاکم‌کُت ۳ */
    val points: Int,
    val kot: Boolean,
    val hakemKot: Boolean,
    /** حاکم همین دست */
    val hakemBefore: Int,
    /** حاکم دست بعدی */
    val hakemAfter: Int,
    /** تعداد دست‌های بُرده‌ی هر تیم */
    val teamTricks: List<Int>,
    /** آیا تیم حاکم برنده شد؟ */
    val hakemTeamWon: Boolean,
    /** مردابادی: دستِ گرفته منهای سهمیه برای هر صندلی */
    val deltas: List<Int> = emptyList(),
    /** مردابادی: صندلی‌ای که با پر شدن سقف بدهی حذف شد */
    val eliminatedSeat: Int? = null,
)

/**
 * وضعیت کامل یک مسابقه‌ی حکم — تغییرناپذیر؛ هر حرکت نسخه‌ی تازه می‌سازد.
 * صندلی ۰ همیشه بازیکن انسانی است؛ ترتیب نوبت ۰→۱→۲→۳ است.
 */
@Serializable
data class HokmState(
    val variant: HokmVariant,
    /** امتیاز لازم برای بردن مسابقه — در مردابادی همان «سقف بدهی» است */
    val target: Int,
    val hakem: Int,
    val phase: HokmPhase,
    val hands: List<List<Card>>,
    /** کارت‌های هنوز پخش‌نشده (یا کنارگذاشته‌شده در دو نفره) */
    val stock: List<Card> = emptyList(),
    val trump: Suit? = null,
    val turn: Int = hakem,
    val trick: List<TrickCard> = emptyList(),
    /** دست‌های بُرده‌ی هر صندلی در این دست */
    val tricksWon: List<Int> = List(variant.playerCount) { 0 },
    /** امتیاز هر تیم در مسابقه */
    val scores: List<Int> = List(variant.teamCount) { 0 },
    /** کارت‌های بازی‌شده‌ی این دست (بدون کارت‌های روی میز) — برای حافظه‌ی ربات‌ها */
    val played: List<Card> = emptyList(),
    val lastResult: HandResult? = null,
    /** شماره‌ی دست (از ۱) */
    val handNumber: Int = 0,
    // ---------- مردابادی ----------
    /** دویی که برای کل مسابقه از دسته بیرون مانده (فقط مردابادی) */
    val removedCard: Card? = null,
    /** سهمیه‌ی هر صندلی در این دست: ۳، ۵ یا ۹ — جمعش ۱۷ */
    val quotas: List<Int> = emptyList(),
    /** تراز جاری هر صندلی: مثبت = طلب، منفی = بدهی (با تبادل کم و زیاد می‌شود) */
    val balances: List<Int> = emptyList(),
    /** جمعِ کل بدهی‌هایی که هر صندلی از اول مسابقه بالا آورده (هیچ‌وقت کم نمی‌شود) */
    val totalDebts: List<Int> = emptyList(),
    /** نوبت وصول: اندیس در صف طلبکارها (به ترتیب ۹→۵→۳) */
    val collectorIndex: Int = 0,
) {
    val playerCount: Int get() = variant.playerCount

    /** همه‌ی کارت‌های میز کامل شده‌اند و باید جمع شوند */
    val trickComplete: Boolean get() = trick.size == playerCount && phase == HokmPhase.PLAYING

    /** برنده‌ی کارت‌های روی میز (حتی ناقص) — null اگر میز خالی است */
    val trickLeader: Int?
        get() = if (trick.isEmpty()) null else trick[trickWinnerIndex(trick.map { it.card }, trump)].seat

    /** دست‌های بُرده‌ی یک تیم */
    fun teamTricks(team: Int): Int =
        tricksWon.indices.filter { variant.teamOf(it) == team }.sumOf { tricksWon[it] }

    val teamTricksAll: List<Int> get() = List(variant.teamCount) { teamTricks(it) }

    /** تیمی که به هدف رسیده — null یعنی هنوز ادامه دارد */
    val matchWinnerTeam: Int? get() = scores.indices.firstOrNull { scores[it] >= target }

    fun teamOf(seat: Int): Int = variant.teamOf(seat)

    /** همه‌ی کارت‌هایی که تا این لحظه از این دست دیده شده‌اند (بازی‌شده + روی میز) */
    val seenCards: List<Card> get() = played + trick.map { it.card }

    // ---------- مردابادی ----------

    /** آیا این مسابقه با قواعد مردابادی است؟ */
    val isMordabadi: Boolean get() = variant == HokmVariant.THREE

    /** سقف بدهی که به حذف می‌رسد (همان target در مردابادی) */
    val debtLimit: Int get() = target

    /** کارت(های) بیرون‌مانده از دسته — برای حافظه‌ی ربات‌ها */
    val removedCards: List<Card> get() = listOfNotNull(removedCard)

    /** سهمیه‌ی یک صندلی در این دست */
    fun quotaOf(seat: Int): Int = quotas.getOrElse(seat) { 0 }
}
