package com.navidabbasian.kibord.games.ludo.engine

/** رنگ‌های چهارگانه‌ی منچ به ترتیب ساعتگرد روی صفحه؛ هر کدام خانه‌ی شروع خودش روی مسیر ۵۲تایی */
enum class LudoColor(val startIndex: Int, val persianName: String) {
    RED(0, "قرمز"),
    GREEN(13, "سبز"),
    YELLOW(26, "زرد"),
    BLUE(39, "آبی");

    /** خانه‌ی مطلقِ مسیر برای گام نسبیِ ۰ تا ۵۰ — بقیه‌ی گام‌ها روی مسیر نیستند */
    fun trackIndexOf(step: Int): Int? =
        if (step in 0..LAST_TRACK_STEP) (startIndex + step) % TRACK_LENGTH else null

    companion object {
        /** خانه‌های شروع همه‌ی رنگ‌ها — با گزینه‌ی «شروع امن» زدن روی این‌ها ممنوع است */
        val startSquares: Set<Int> = entries.map { it.startIndex }.toSet()
    }
}

/** هر صندلی می‌تواند آدم، ربات یا خالی باشد */
enum class LudoSeatKind { HUMAN, BOT, EMPTY }

data class LudoSeat(
    val color: LudoColor,
    val kind: LudoSeatKind,
    val name: String,
) {
    val active: Boolean get() = kind != LudoSeatKind.EMPTY
    val isBot: Boolean get() = kind == LudoSeatKind.BOT
}

/** قوانین اختیاری دست */
data class LudoRules(
    /** سه تا شش پشت سر هم = نوبت می‌سوزد */
    val tripleSixLosesTurn: Boolean = true,
    /** روی خانه‌ی شروع هر رنگ نمی‌شود مهره زد (خانه‌های امن) */
    val safeStartSquares: Boolean = false,
)

enum class LudoPhase {
    /** منتظر پرتاب تاس */
    ROLLING,
    /** تاس ریخته شده، باید یک مهره حرکت کند */
    MOVING,
    /** نوبت بدون حرکت می‌گذرد (حرکت مجاز نیست یا سه‌شش) — رابط مکث می‌کند و بعد endTurn */
    PASSING,
    /** یک بازیکن همه‌ی مهره‌هایش را رساند — صفحه‌ی برنده؛ شاید ادامه برای رتبه‌های بعدی */
    FINISHED,
}

/** آخرین اتفاقِ مهم برای پیام و صدا در رابط کاربری */
enum class LudoEvent { NONE, ENTERED, MOVED, CAPTURED, REACHED_GOAL, TRIPLE_SIX, NO_MOVE, PLAYER_FINISHED }

/** یک حرکت: مهره‌ی شماره‌ی token از گام from به گام to */
data class LudoMove(
    val color: LudoColor,
    val token: Int,
    val from: Int,
    val to: Int,
) {
    val entersBoard: Boolean get() = from == BASE
    val reachesGoal: Boolean get() = to == GOAL
    /** تعداد پرش‌های انیمیشن: ورود از پایگاه یک پرش، بقیه به اندازه‌ی تاس */
    val hops: Int get() = if (entersBoard) 1 else to - from
}

/** مهره‌ای که زده شده و به پایگاه برگشته */
data class LudoCapture(val color: LudoColor, val token: Int, val trackIndex: Int)

/** آخرین حرکت انجام‌شده به همراه مهره‌ی زده‌شده — برای انیمیشن و صدا */
data class LudoLastMove(val move: LudoMove, val captured: LudoCapture?)

/** مهره در پایگاه */
const val BASE = -1
/** آخرین گامِ روی مسیر مشترک (۵۱ خانه: ۰ تا ۵۰) */
const val LAST_TRACK_STEP = 50
/** اولین خانه‌ی ستون رنگی خانه */
const val HOME_COLUMN_START = 51
/** مقصد نهایی: وسط صفحه */
const val GOAL = 56
/** طول مسیر مشترک */
const val TRACK_LENGTH = 52
/** تعداد مهره‌های هر رنگ */
const val TOKENS_PER_PLAYER = 4

/**
 * وضعیت کامل یک دست منچ — تغییرناپذیر؛ موتور نسخه‌ی تازه برمی‌گرداند.
 * tokens[color.ordinal][i] گامِ مهره‌ی i است: BASE، ۰ تا ۵۵ روی صفحه، GOAL رسیده.
 */
data class LudoState(
    val seats: List<LudoSeat>,
    val rules: LudoRules,
    val tokens: List<List<Int>>,
    val turn: LudoColor,
    val phase: LudoPhase = LudoPhase.ROLLING,
    val die: Int? = null,
    val sixStreak: Int = 0,
    /** رنگ‌هایی که همه‌ی مهره‌هایشان رسیده — به ترتیب رسیدن */
    val finished: List<LudoColor> = emptyList(),
    val event: LudoEvent = LudoEvent.NONE,
    val lastMove: LudoLastMove? = null,
    /** دیگر کسی برای ادامه نمانده (حداکثر یک نفر بیرون از رده‌بندی) */
    val gameOver: Boolean = false,
) {
    fun seat(color: LudoColor): LudoSeat = seats[color.ordinal]
    fun tokensOf(color: LudoColor): List<Int> = tokens[color.ordinal]
    fun isActive(color: LudoColor): Boolean = seat(color).active
    fun isFinished(color: LudoColor): Boolean = color in finished
    val activeColors: List<LudoColor> get() = LudoColor.entries.filter { isActive(it) }
    /** رنگ‌های فعالی که هنوز در حال بازی‌اند */
    val playingColors: List<LudoColor> get() = activeColors.filter { !isFinished(it) }
    val currentSeat: LudoSeat get() = seat(turn)

    /** همه‌ی مهره‌های روی خانه‌ی مطلق index از مسیر مشترک */
    fun tokensAtTrack(index: Int): List<Pair<LudoColor, Int>> = buildList {
        LudoColor.entries.forEach { c ->
            tokensOf(c).forEachIndexed { i, step ->
                if (c.trackIndexOf(step) == index) add(c to i)
            }
        }
    }

    /** چند مهره از رنگ color در پایگاه‌اند؟ */
    fun inBase(color: LudoColor): Int = tokensOf(color).count { it == BASE }
    /** چند مهره از رنگ color رسیده‌اند؟ */
    fun inGoal(color: LudoColor): Int = tokensOf(color).count { it == GOAL }
    /** پیشرفت کل یک رنگ برای رده‌بندی */
    fun progress(color: LudoColor): Int = tokensOf(color).sumOf { it.coerceAtLeast(0) }
}
