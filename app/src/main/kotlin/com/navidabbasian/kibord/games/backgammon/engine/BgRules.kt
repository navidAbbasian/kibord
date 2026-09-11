package com.navidabbasian.kibord.games.backgammon.engine

import kotlinx.serialization.Serializable

/**
 * بازیکن‌های تخته‌نرد — هر بازیکن از دید خودش از خانه‌ی ۲۴ به سمت ۱ حرکت می‌کند.
 * شماره‌گذاری موتور همیشه ثابت است؛ آینه‌کردن فقط کار رابط کاربری است.
 */
@Serializable
enum class BgPlayer {
    WHITE, BLACK;

    val opponent: BgPlayer get() = if (this == WHITE) BLACK else WHITE
}

/** چهار روش بازی که موتور مشترک پشتیبانی می‌کند */
@Serializable
enum class BgVariant { STANDARD, DUTCH, HYPER, IRANI }

/** فازهای چرخه‌ی نوبت */
@Serializable
enum class BgPhase {
    /** پرتاب تک‌تاس شروع برای تعیین نفر اول */
    OPENING_ROLL,

    /** بازیکنِ نوبت باید دو تاس بیندازد */
    ROLLING,

    /** تاس ریخته شده و حرکت‌ها در جریان‌اند */
    MOVING,

    /** بازی تمام شده و برنده مشخص است */
    FINISHED,
}

/**
 * پیکربندی قوانین هر روش — موتور مشترک همه‌چیز را از همین شیء می‌خواند و
 * هیچ عددی مثل ۱۵ در بدنه‌ی موتور hard-code نمی‌شود (بند ۱۶ سند هایپرگامون).
 */
@Serializable
data class BgRules(
    val variant: BgVariant,
    /** تعداد مهره‌ی هر بازیکن */
    val piecesPerPlayer: Int,
    /** چیدمان اولیه از دید خود بازیکن: شماره‌ی خانه به تعداد مهره */
    val startingLayout: Map<Int, Int>,
    /** چند مهره بیرون صفحه شروع می‌کنند (هلندی: هر ۱۵ تا) */
    val startingOffBoard: Int,
    /** تا ورود کامل مهره‌ها، حرکت معمولی ممنوع است (هلندی) */
    val mustEnterAllBeforeNormalMovement: Boolean,
    /** برنده‌ی پرتاب شروع دوباره دو تاس می‌اندازد (هلندی) */
    val openingWinnerRerolls: Boolean,
    /** آیا پیش از رسیدن مهره‌ای به خانه‌ی خودی، زدن مهره‌ی حریف مجاز است؟ */
    val canHitBeforeHomeEntry: Boolean,
    /** آیا امتیاز مارس کامل (۳) در این روش تعریف شده؟ هلندی فقط تکی/مارس دارد */
    val hasBackgammonScore: Boolean,
    /** آیا مکعب دوبل در این روش وجود دارد؟ (ایرانی: هرگز) */
    val usesCube: Boolean = true,
    /** دست به مهره: مهره‌ای که لمس شد باید بازی شود — قفلِ رابط کاربری (ایرانی) */
    val touchMove: Boolean = false,
    /** مهره‌ای که در خانه‌ی خودی زد، همان نوبت روی خانه‌ی پرِ خودی نمی‌نشیند (ایرانی) */
    val hitInHomeStaysSingle: Boolean = false,
    /** با آخرین مهره در حال خروج، اول باید تاس بزرگ‌تر بازی شود (ایرانی) */
    val lastCheckerHigherDie: Boolean = false,
) {
    companion object {
        /** تخته‌نرد کلاسیک: چیدمان استاندارد بین‌المللی */
        val STANDARD = BgRules(
            variant = BgVariant.STANDARD,
            piecesPerPlayer = 15,
            startingLayout = mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5),
            startingOffBoard = 0,
            mustEnterAllBeforeNormalMovement = false,
            openingWinnerRerolls = false,
            canHitBeforeHomeEntry = true,
            hasBackgammonScore = true,
        )

        /** تخته‌نرد هلندی: همه‌ی مهره‌ها بیرون شروع می‌کنند و زدن دیر فعال می‌شود */
        val DUTCH = BgRules(
            variant = BgVariant.DUTCH,
            piecesPerPlayer = 15,
            startingLayout = emptyMap(),
            startingOffBoard = 15,
            mustEnterAllBeforeNormalMovement = true,
            openingWinnerRerolls = true,
            canHitBeforeHomeEntry = false,
            hasBackgammonScore = false,
        )

        /** هایپرگامون: فقط سه مهره روی ۲۲ و ۲۳ و ۲۴ */
        val HYPER = BgRules(
            variant = BgVariant.HYPER,
            piecesPerPlayer = 3,
            startingLayout = mapOf(24 to 1, 23 to 1, 22 to 1),
            startingOffBoard = 0,
            mustEnterAllBeforeNormalMovement = false,
            openingWinnerRerolls = false,
            canHitBeforeHomeEntry = true,
            hasBackgammonScore = true,
        )

        /**
         * تخته‌نرد ایرانی: چیدمان استاندارد ولی بدون مکعب دوبل، با دست به مهره،
         * قانون «زننده در خانه‌ی خودی تک می‌ماند» و «آخرین مهره با تاس بزرگ‌تر».
         * امتیاز ۳ همان سگ‌مارس است (مهره‌ی بازنده در خانه‌ی برنده یا روی بار).
         */
        val IRANI = BgRules(
            variant = BgVariant.IRANI,
            piecesPerPlayer = 15,
            startingLayout = mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5),
            startingOffBoard = 0,
            mustEnterAllBeforeNormalMovement = false,
            openingWinnerRerolls = false,
            canHitBeforeHomeEntry = true,
            hasBackgammonScore = true,
            usesCube = false,
            touchMove = true,
            hitInHomeStaysSingle = true,
            lastCheckerHigherDie = true,
        )

        fun of(variant: BgVariant): BgRules = when (variant) {
            BgVariant.STANDARD -> STANDARD
            BgVariant.DUTCH -> DUTCH
            BgVariant.HYPER -> HYPER
            BgVariant.IRANI -> IRANI
        }
    }
}
