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

/** سه روش بازی که موتور مشترک پشتیبانی می‌کند */
@Serializable
enum class BgVariant { STANDARD, DUTCH, HYPER }

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

        fun of(variant: BgVariant): BgRules = when (variant) {
            BgVariant.STANDARD -> STANDARD
            BgVariant.DUTCH -> DUTCH
            BgVariant.HYPER -> HYPER
        }
    }
}
