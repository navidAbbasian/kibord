package com.navidabbasian.kibord.games.backgammon.engine

import kotlinx.serialization.Serializable

/** یک خانه‌ی صفحه: صاحب و تعداد مهره‌ها (صاحبِ خانه‌ی خالی null است) */
@Serializable
data class BgPoint(val owner: BgPlayer? = null, val count: Int = 0)

/**
 * یک حرکت تکی با یک تاس — مختصات از دید بازیکنِ نوبت است:
 * from برابر ۲۵ یعنی ورود (از بار، یا در هلندی از مهره‌های واردنشده)
 * و to برابر صفر یعنی خارج‌کردن مهره از صفحه.
 */
@Serializable
data class BgMove(val from: Int, val to: Int, val die: Int, val hit: Boolean = false) {
    companion object {
        /** مبدأ ورود: بار یا انبار مهره‌های واردنشده‌ی هلندی */
        const val ENTRY = 25

        /** مقصد خارج‌کردن مهره */
        const val OFF = 0
    }
}

/** تبدیل شماره‌ی خانه از دید بازیکن به اندیس مطلق صفحه (۱ تا ۲۴) */
fun relToAbs(player: BgPlayer, rel: Int): Int = if (player == BgPlayer.WHITE) rel else 25 - rel

/** تبدیل اندیس مطلق صفحه به شماره از دید بازیکن — همان تابع، چون خودمعکوس است */
fun absToRel(player: BgPlayer, abs: Int): Int = relToAbs(player, abs)

/**
 * وضعیت کامل یک بازی تخته‌نرد — دیتاکلاس تغییرناپذیر و سریالایز‌پذیر.
 * points با اندیس ۰ تا ۲۳ خانه‌های مطلق ۱ تا ۲۴ را نگه می‌دارد
 * (شماره‌گذاری مطلق همان شماره‌گذاری از دید بازیکن سفید است).
 */
@Serializable
data class BgState(
    val rules: BgRules,
    val points: List<BgPoint>,
    val barWhite: Int = 0,
    val barBlack: Int = 0,
    /** مهره‌های هنوز واردنشده — فقط در هلندی غیرصفر است */
    val offBoardWhite: Int = 0,
    val offBoardBlack: Int = 0,
    val borneOffWhite: Int = 0,
    val borneOffBlack: Int = 0,
    val turn: BgPlayer? = null,
    /** تاس‌های اصلی این نوبت (برای نمایش) */
    val dice: List<Int> = emptyList(),
    /** مصرف‌های باقی‌مانده‌ی تاس — جفت چهارتا مصرف می‌سازد */
    val remainingDice: List<Int> = emptyList(),
    val phase: BgPhase = BgPhase.OPENING_ROLL,
    /** تک‌تاس‌های پرتاب شروع برای نمایش (سفید، سیاه) */
    val openingDieWhite: Int? = null,
    val openingDieBlack: Int? = null,
    /** پرتاب شروع مساوی شد و باید تکرار شود */
    val openingTie: Boolean = false,
    /** آیا بازیکن مهره‌ای به خانه‌ی خودش رسانده؟ (شرط فعال‌شدن زدن در هلندی) */
    val homeReachedWhite: Boolean = false,
    val homeReachedBlack: Boolean = false,
    val winner: BgPlayer? = null,
    /** امتیاز نتیجه: ۱ تکی، ۲ مارس، ۳ مارس کامل — تا پایان بازی صفر است */
    val resultScore: Int = 0,
) {
    fun bar(p: BgPlayer): Int = if (p == BgPlayer.WHITE) barWhite else barBlack

    fun offBoard(p: BgPlayer): Int = if (p == BgPlayer.WHITE) offBoardWhite else offBoardBlack

    fun borneOff(p: BgPlayer): Int = if (p == BgPlayer.WHITE) borneOffWhite else borneOffBlack

    fun homeReached(p: BgPlayer): Boolean = if (p == BgPlayer.WHITE) homeReachedWhite else homeReachedBlack

    /** خانه‌ی مطلق شماره‌ی abs (۱ تا ۲۴) */
    fun pointAt(abs: Int): BgPoint = points[abs - 1]

    /** خانه از دید بازیکن p */
    fun pointOf(p: BgPlayer, rel: Int): BgPoint = pointAt(relToAbs(p, rel))

    /** تعداد مهره‌های روی صفحه‌ی این بازیکن */
    fun checkersOnBoard(p: BgPlayer): Int = points.sumOf { if (it.owner == p) it.count else 0 }

    /** جمع کل مهره‌ها: صفحه + بار + واردنشده + خارج‌شده — همیشه باید piecesPerPlayer باشد */
    fun totalCheckers(p: BgPlayer): Int = checkersOnBoard(p) + bar(p) + offBoard(p) + borneOff(p)

    /** بالاترین خانه‌ی اشغال‌شده از دید بازیکن — صفر یعنی هیچ مهره‌ای روی صفحه نیست */
    fun highestOccupied(p: BgPlayer): Int =
        (24 downTo 1).firstOrNull { pointOf(p, it).owner == p } ?: 0

    /** آیا همه‌ی مهره‌های فعال در خانه‌ی خودی‌اند و خارج‌کردن مجاز است؟ */
    fun allActiveInHome(p: BgPlayer): Boolean =
        bar(p) == 0 && offBoard(p) == 0 && highestOccupied(p) <= 6

    /** آیا بازیکن هنوز در مرحله‌ی ورود اولیه‌ی هلندی است؟ */
    fun isEntering(p: BgPlayer): Boolean =
        rules.mustEnterAllBeforeNormalMovement && offBoard(p) > 0
}
