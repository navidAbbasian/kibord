package com.navidabbasian.kibord.games.backgammon.engine

import kotlinx.serialization.Serializable

/**
 * وضعیت مسابقه‌ی چندامتیازی، مکعب دوبل و ساعت‌ها — لایه‌ای بالای [BgState]
 * که یک دست را نگه می‌دارد. همه‌چیز خالص و سریالایز‌پذیر است تا هم روی
 * شبکه برود و هم در اتاق پایدار ذخیره شود؛ منطقش در [BgMatchRules] است.
 */
@Serializable
data class BgMatch(
    /** مسابقه تا چند امتیاز؟ ۱ یعنی همان تک‌دست همیشگی */
    val length: Int = 1,
    val scoreWhite: Int = 0,
    val scoreBlack: Int = 0,
    /** مقدار مکعب: ۱ (وسط، روی صفحه ۶۴ نشان داده می‌شود) تا ۶۴ */
    val cubeValue: Int = 1,
    /** صاحب مکعب — تهی یعنی وسط است و هر دو می‌توانند دوبل کنند */
    val cubeOwner: BgPlayer? = null,
    /** این دست، دستِ کرافورد است: دوبل ممنوع */
    val crawford: Boolean = false,
    /** دست کرافورد قبلاً بازی شده — بعدش دوبل دوباره آزاد است */
    val crawfordPlayed: Boolean = false,
    /** پیشنهاد دوبلِ معلق: چه کسی پیشنهاد داده؟ تهی یعنی پیشنهادی نیست */
    val doubleOfferedBy: BgPlayer? = null,
    /** بانک زمان هر بازیکن در شروع هر دست (میلی‌ثانیه) — صفر یعنی بدون ساعت */
    val clockTotalMs: Long = 0,
    /** زمان باقی‌مانده‌ی هر بازیکن در لحظه‌ی ثبت این عکس */
    val clockWhiteMs: Long = 0,
    val clockBlackMs: Long = 0,
    /** ساعتِ چه کسی در حال کم‌شدن است؟ تهی یعنی هیچ‌کدام */
    val clockRunning: BgPlayer? = null,
    /** نتیجه‌ی آخرین دستِ تمام‌شده برای پرده‌ی «دست بعدی» */
    val lastGameWinner: BgPlayer? = null,
    val lastGamePoints: Int = 0,
    /** دلیل پایان آخرین دست: BEAR_OFF، DROP، TIMEOUT، RESIGN */
    val lastGameEnd: BgGameEnd = BgGameEnd.BEAR_OFF,
    /** شمار دست‌های تمام‌شده‌ی این مسابقه */
    val gamesPlayed: Int = 0,
) {
    fun score(p: BgPlayer): Int = if (p == BgPlayer.WHITE) scoreWhite else scoreBlack

    /** چند امتیاز تا برد مسابقه مانده؟ (هیچ‌وقت منفی نمی‌شود) */
    fun away(p: BgPlayer): Int = (length - score(p)).coerceAtLeast(0)

    fun clock(p: BgPlayer): Long = if (p == BgPlayer.WHITE) clockWhiteMs else clockBlackMs

    val hasClocks: Boolean get() = clockTotalMs > 0

    /** برنده‌ی مسابقه — تهی یعنی هنوز ادامه دارد */
    val matchWinner: BgPlayer?
        get() = when {
            scoreWhite >= length -> BgPlayer.WHITE
            scoreBlack >= length -> BgPlayer.BLACK
            else -> null
        }

    /** نسخه‌ای با ساعتِ این بازیکن به‌روزشده */
    fun withClock(p: BgPlayer, ms: Long): BgMatch =
        if (p == BgPlayer.WHITE) copy(clockWhiteMs = ms) else copy(clockBlackMs = ms)
}

/** چطور یک دست تمام شد؟ */
@Serializable
enum class BgGameEnd { BEAR_OFF, DROP, TIMEOUT, RESIGN }

/**
 * قواعد مسابقه: امتیازدهی تا N، قانون کرافورد، دوبل/قبول/رد، تسلیم و
 * پایان وقت. همه‌ی توابع خالص‌اند و نسخه‌ی تازه برمی‌گردانند.
 */
object BgMatchRules {

    const val MAX_CUBE = 64

    /** مسابقه‌ی تازه: امتیازها صفر، مکعب وسط، ساعت‌ها پر */
    fun newMatch(length: Int, clockTotalMs: Long = 0): BgMatch = beginGame(
        BgMatch(length = length.coerceAtLeast(1), clockTotalMs = clockTotalMs.coerceAtLeast(0)),
    )

    /**
     * شروع یک دست تازه در همین مسابقه: مکعب به وسط برمی‌گردد، پیشنهاد معلق
     * پاک می‌شود، ساعت‌ها پر می‌شوند و پرچم کرافورد حساب می‌شود —
     * وقتی یکی از بازیکن‌ها دقیقاً یک امتیاز تا برد فاصله دارد و هنوز
     * دست کرافورد بازی نشده، این دست کرافورد است (مسابقه‌ی تک‌امتیازی هم
     * همین‌طور: دوبل معنا ندارد).
     */
    fun beginGame(m: BgMatch): BgMatch {
        val crawford = !m.crawfordPlayed &&
            (m.away(BgPlayer.WHITE) == 1 || m.away(BgPlayer.BLACK) == 1)
        return m.copy(
            cubeValue = 1,
            cubeOwner = null,
            crawford = crawford,
            doubleOfferedBy = null,
            clockWhiteMs = m.clockTotalMs,
            clockBlackMs = m.clockTotalMs,
            clockRunning = null,
            lastGameWinner = null,
            lastGamePoints = 0,
        )
    }

    /** آیا این بازیکن الان حق دوبل دارد؟ (وسط یا مال خودش، نه کرافورد، نه سقف) */
    fun canDouble(m: BgMatch, p: BgPlayer): Boolean =
        m.matchWinner == null &&
            !m.crawford &&
            m.doubleOfferedBy == null &&
            (m.cubeOwner == null || m.cubeOwner == p) &&
            m.cubeValue < MAX_CUBE

    /** پیشنهاد دوبل — اگر مجاز نباشد همان وضعیت برمی‌گردد */
    fun offerDouble(m: BgMatch, p: BgPlayer): BgMatch =
        if (canDouble(m, p)) m.copy(doubleOfferedBy = p) else m

    /** قبول: مکعب دو برابر می‌شود و صاحبش طرف مقابل (قبول‌کننده) است */
    fun takeDouble(m: BgMatch): BgMatch {
        val offerer = m.doubleOfferedBy ?: return m
        return m.copy(
            cubeValue = (m.cubeValue * 2).coerceAtMost(MAX_CUBE),
            cubeOwner = offerer.opponent,
            doubleOfferedBy = null,
        )
    }

    /**
     * رد: پیشنهاددهنده همین حالا دست را با مقدار فعلی مکعب (تکی) می‌برد.
     * وضعیت دست هم تمام‌شده برمی‌گردد.
     */
    fun dropDouble(m: BgMatch, state: BgState): Pair<BgState, BgMatch> {
        val offerer = m.doubleOfferedBy ?: return state to m
        val finished = finishWithWinner(state, offerer)
        return finished to recordGameResult(m.copy(doubleOfferedBy = null), offerer, 1, BgGameEnd.DROP)
    }

    /** تسلیم یا پایان وقت: حریف دست را تکی با مقدار فعلی مکعب می‌برد */
    fun concede(m: BgMatch, state: BgState, loser: BgPlayer, reason: BgGameEnd): Pair<BgState, BgMatch> {
        val winner = loser.opponent
        val finished = finishWithWinner(state, winner)
        return finished to recordGameResult(m.copy(doubleOfferedBy = null), winner, 1, reason)
    }

    /**
     * ثبت نتیجه‌ی دست: امتیاز دست = مقدار مکعب × نتیجه (۱ تکی، ۲ مارس، ۳ مارس کامل).
     * اگر دست کرافورد بود، از این به بعد دوبل دوباره آزاد است.
     */
    fun recordGameResult(
        m: BgMatch,
        winner: BgPlayer,
        resultScore: Int,
        reason: BgGameEnd = BgGameEnd.BEAR_OFF,
    ): BgMatch {
        val points = m.cubeValue * resultScore.coerceAtLeast(1)
        val scoreWhite = m.scoreWhite + if (winner == BgPlayer.WHITE) points else 0
        val scoreBlack = m.scoreBlack + if (winner == BgPlayer.BLACK) points else 0
        return m.copy(
            scoreWhite = scoreWhite,
            scoreBlack = scoreBlack,
            crawfordPlayed = m.crawfordPlayed || m.crawford,
            doubleOfferedBy = null,
            clockRunning = null,
            lastGameWinner = winner,
            lastGamePoints = points,
            lastGameEnd = reason,
            gamesPlayed = m.gamesPlayed + 1,
        )
    }

    /** پایان دادن فوری دست به نفع یک بازیکن (رد دوبل، تسلیم، پایان وقت) — همیشه تکی */
    fun finishWithWinner(state: BgState, winner: BgPlayer): BgState = state.copy(
        phase = BgPhase.FINISHED,
        winner = winner,
        resultScore = 1,
        remainingDice = emptyList(),
        dice = emptyList(),
    )

    // ---- ساعت ----

    /**
     * کم‌کردن زمانِ سپری‌شده از ساعتِ بازیکنِ در حال اجرا.
     * [elapsedMs] فاصله‌ی زمانی از آخرین تسویه است؛ نتیجه هیچ‌وقت منفی نمی‌شود.
     */
    fun settleClock(m: BgMatch, elapsedMs: Long): BgMatch {
        val p = m.clockRunning ?: return m
        if (!m.hasClocks || elapsedMs <= 0) return m
        return m.withClock(p, (m.clock(p) - elapsedMs).coerceAtLeast(0))
    }

    /** ساعت چه کسی باید بدود؟ اگر ساعتی در کار نیست، همان وضعیت */
    fun setClockRunning(m: BgMatch, p: BgPlayer?): BgMatch =
        if (!m.hasClocks) m else m.copy(clockRunning = p)

    /** زمان بازیکن تمام شده؟ */
    fun isOutOfTime(m: BgMatch, p: BgPlayer): Boolean = m.hasClocks && m.clock(p) <= 0

    /** زمان باقی‌مانده‌ی این لحظه با درون‌یابی از آخرین عکس — برای نمایش */
    fun remainingNow(m: BgMatch, p: BgPlayer, elapsedSinceStampMs: Long): Long {
        val base = m.clock(p)
        return if (m.clockRunning == p) (base - elapsedSinceStampMs.coerceAtLeast(0)).coerceAtLeast(0) else base
    }
}

/** شمار پیپ (مجموع فاصله‌ی مهره‌ها تا خروج) از دید یک بازیکن */
fun BgState.pipCount(p: BgPlayer): Int {
    var sum = 0
    for (rel in 1..24) {
        val pt = pointOf(p, rel)
        if (pt.owner == p) sum += rel * pt.count
    }
    return sum + 25 * (bar(p) + offBoard(p))
}
