package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgGameEnd
import com.navidabbasian.kibord.games.backgammon.engine.BgMatch
import com.navidabbasian.kibord.games.backgammon.engine.BgMatchRules
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.BLACK
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.WHITE
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.pipCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های مسابقه‌ی چندامتیازی: امتیاز تا N، کرافورد، مکعب دوبل و ساعت */
class BgMatchTest {

    // ---- امتیاز مسابقه تا N ----

    /** برد تکی یک امتیاز و مارس دو امتیاز اضافه می‌کند تا کسی به N برسد */
    @Test
    fun `match scoring - points accumulate until N is reached`() {
        var m = BgMatchRules.newMatch(length = 3)
        assertNull(m.matchWinner)
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, WHITE, 1))
        assertEquals(1, m.scoreWhite)
        assertNull(m.matchWinner)
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, BLACK, 2))
        assertEquals(2, m.scoreBlack)
        assertNull(m.matchWinner)
        m = BgMatchRules.recordGameResult(m, WHITE, 2)
        assertEquals(3, m.scoreWhite)
        assertEquals(WHITE, m.matchWinner)
    }

    /** امتیاز دست = مقدار مکعب ضرب در نتیجه: مکعب ۲ و مارس یعنی ۴ امتیاز */
    @Test
    fun `match scoring - game points are cube value times result`() {
        var m = BgMatchRules.newMatch(length = 7)
        m = BgMatchRules.offerDouble(m, WHITE)
        m = BgMatchRules.takeDouble(m)
        assertEquals(2, m.cubeValue)
        m = BgMatchRules.recordGameResult(m, WHITE, 2)
        assertEquals(4, m.scoreWhite)
        assertEquals(4, m.lastGamePoints)
    }

    /** «N مونده» هیچ‌وقت منفی نمی‌شود و برنده صفر مونده دارد */
    @Test
    fun `match scoring - away counts`() {
        var m = BgMatchRules.newMatch(length = 3)
        assertEquals(3, m.away(WHITE))
        m = BgMatchRules.recordGameResult(m, WHITE, 3)
        assertEquals(0, m.away(WHITE))
        assertEquals(3, m.away(BLACK))
    }

    // ---- کرافورد ----

    /** وقتی بازیکنی به یک قدمی برد می‌رسد، دست بعدی کرافورد است و بعدش آزاد */
    @Test
    fun `crawford - first game at one-away has no doubling, then doubling resumes`() {
        var m = BgMatchRules.newMatch(length = 3)
        assertFalse(m.crawford)
        // سفید به ۲ می‌رسد (۱ مونده) — دست بعد کرافورد
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, WHITE, 2))
        assertTrue(m.crawford)
        assertFalse(BgMatchRules.canDouble(m, WHITE))
        assertFalse(BgMatchRules.canDouble(m, BLACK))
        // دست کرافورد را سیاه می‌برد — کرافورد بازی شده و دوبل دوباره آزاد است
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, BLACK, 1))
        assertFalse(m.crawford)
        assertTrue(m.crawfordPlayed)
        assertTrue(BgMatchRules.canDouble(m, BLACK))
    }

    /** مسابقه‌ی تک‌امتیازی از همان اول کرافورد است: دوبل معنا ندارد */
    @Test
    fun `crawford - single-point match never allows doubling`() {
        val m = BgMatchRules.newMatch(length = 1)
        assertTrue(m.crawford)
        assertFalse(BgMatchRules.canDouble(m, WHITE))
    }

    /** پیشنهاد دوبل وسط کرافورد بی‌اثر است */
    @Test
    fun `crawford - offerDouble is a no-op`() {
        var m = BgMatchRules.newMatch(length = 3)
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, BLACK, 2))
        assertTrue(m.crawford)
        m = BgMatchRules.offerDouble(m, WHITE)
        assertNull(m.doubleOfferedBy)
    }

    // ---- مکعب دوبل ----

    /** قبول: مکعب دو برابر و مال قبول‌کننده؛ فقط او می‌تواند دوباره دوبل کند */
    @Test
    fun `cube - take doubles the cube and gives it to the taker`() {
        var m = BgMatchRules.newMatch(length = 5)
        assertTrue(BgMatchRules.canDouble(m, WHITE))
        assertTrue(BgMatchRules.canDouble(m, BLACK))
        m = BgMatchRules.offerDouble(m, WHITE)
        assertEquals(WHITE, m.doubleOfferedBy)
        // وسط پیشنهاد معلق کسی حق دوبل تازه ندارد
        assertFalse(BgMatchRules.canDouble(m, BLACK))
        m = BgMatchRules.takeDouble(m)
        assertEquals(2, m.cubeValue)
        assertEquals(BLACK, m.cubeOwner)
        assertNull(m.doubleOfferedBy)
        // حالا فقط سیاه (صاحب مکعب) می‌تواند دوبل کند
        assertFalse(BgMatchRules.canDouble(m, WHITE))
        assertTrue(BgMatchRules.canDouble(m, BLACK))
    }

    /** رد: پیشنهاددهنده همان لحظه دست را با مقدار فعلی مکعب می‌برد */
    @Test
    fun `cube - drop ends the game for the offerer at current cube value`() {
        val engine = BgEngine(BgRules.STANDARD)
        var m = BgMatchRules.newMatch(length = 5)
        // مکعب را به ۲ برسان و بعد سیاه دوباره دوبل کند
        m = BgMatchRules.takeDouble(BgMatchRules.offerDouble(m, WHITE)) // ۲ مال سیاه
        m = BgMatchRules.offerDouble(m, BLACK)
        val (state, after) = BgMatchRules.dropDouble(m, engine.createGame())
        assertEquals(BgPhase.FINISHED, state.phase)
        assertEquals(BLACK, state.winner)
        assertEquals(2, after.scoreBlack) // مقدار مکعبِ پیش از قبول‌نشدن
        assertEquals(BgGameEnd.DROP, after.lastGameEnd)
        assertEquals(BLACK, after.lastGameWinner)
    }

    /** سقف مکعب ۶۴ است — بالاتر نمی‌رود و دوبلِ بعدی مجاز نیست */
    @Test
    fun `cube - value is capped at 64`() {
        var m = BgMatchRules.newMatch(length = 99)
        var who = WHITE
        repeat(6) {
            m = BgMatchRules.takeDouble(BgMatchRules.offerDouble(m, who))
            who = who.opponent
        }
        assertEquals(64, m.cubeValue)
        assertFalse(BgMatchRules.canDouble(m, m.cubeOwner!!))
    }

    /** دست تازه: مکعب به وسط و یک برمی‌گردد */
    @Test
    fun `cube - beginGame recenters the cube`() {
        var m = BgMatchRules.newMatch(length = 5)
        m = BgMatchRules.takeDouble(BgMatchRules.offerDouble(m, WHITE))
        m = BgMatchRules.beginGame(BgMatchRules.recordGameResult(m, BLACK, 1))
        assertEquals(1, m.cubeValue)
        assertNull(m.cubeOwner)
    }

    // ---- ساعت ----

    /** تسویه فقط از ساعت بازیکنِ در حال اجرا کم می‌کند و منفی نمی‌شود */
    @Test
    fun `clock - settle deducts only from the running player`() {
        var m = BgMatchRules.newMatch(length = 3, clockTotalMs = 120_000)
        assertEquals(120_000, m.clockWhiteMs)
        m = BgMatchRules.setClockRunning(m, WHITE)
        m = BgMatchRules.settleClock(m, 30_000)
        assertEquals(90_000, m.clockWhiteMs)
        assertEquals(120_000, m.clockBlackMs)
        m = BgMatchRules.settleClock(m, 200_000)
        assertEquals(0, m.clockWhiteMs)
        assertTrue(BgMatchRules.isOutOfTime(m, WHITE))
        assertFalse(BgMatchRules.isOutOfTime(m, BLACK))
    }

    /** پایان وقت: دستِ جاری با مقدار مکعب به حریف می‌رسد، نه کل مسابقه */
    @Test
    fun `clock - timeout loses the game at cube value`() {
        val engine = BgEngine(BgRules.STANDARD)
        var m = BgMatchRules.newMatch(length = 5, clockTotalMs = 60_000)
        m = BgMatchRules.takeDouble(BgMatchRules.offerDouble(m, BLACK)) // مکعب ۲ مال سفید
        m = BgMatchRules.setClockRunning(m, WHITE)
        m = BgMatchRules.settleClock(m, 60_000)
        assertTrue(BgMatchRules.isOutOfTime(m, WHITE))
        val (state, after) = BgMatchRules.concede(m, engine.createGame(), WHITE, BgGameEnd.TIMEOUT)
        assertEquals(BgPhase.FINISHED, state.phase)
        assertEquals(BLACK, state.winner)
        assertEquals(2, after.scoreBlack)
        assertNull(after.matchWinner)
        assertEquals(BgGameEnd.TIMEOUT, after.lastGameEnd)
        // دست تازه ساعت‌ها را دوباره پر می‌کند
        val next = BgMatchRules.beginGame(after)
        assertEquals(60_000, next.clockWhiteMs)
        assertEquals(60_000, next.clockBlackMs)
    }

    /** بدون ساعت (صفر): تسویه و اجرا بی‌اثرند */
    @Test
    fun `clock - disabled clocks never run`() {
        var m = BgMatchRules.newMatch(length = 3, clockTotalMs = 0)
        assertFalse(m.hasClocks)
        m = BgMatchRules.setClockRunning(m, WHITE)
        assertNull(m.clockRunning)
        assertFalse(BgMatchRules.isOutOfTime(m, WHITE))
    }

    /** درون‌یابی نمایش: فقط ساعتِ در حال اجرا با گذر زمان کم دیده می‌شود */
    @Test
    fun `clock - remainingNow interpolates only for the running player`() {
        var m = BgMatchRules.newMatch(length = 3, clockTotalMs = 120_000)
        m = BgMatchRules.setClockRunning(m, BLACK)
        assertEquals(110_000, BgMatchRules.remainingNow(m, BLACK, 10_000))
        assertEquals(120_000, BgMatchRules.remainingNow(m, WHITE, 10_000))
    }

    // ---- تسلیم ----

    /** تسلیم: حریف دست را تکی با مقدار مکعب می‌برد */
    @Test
    fun `resign - opponent wins the game at cube value`() {
        val engine = BgEngine(BgRules.STANDARD)
        val m = BgMatchRules.newMatch(length = 3)
        val (state, after) = BgMatchRules.concede(m, engine.createGame(), BLACK, BgGameEnd.RESIGN)
        assertEquals(WHITE, state.winner)
        assertEquals(1, after.scoreWhite)
        assertEquals(BgGameEnd.RESIGN, after.lastGameEnd)
    }

    // ---- شمار پیپ ----

    /** چیدمان شروع استاندارد برای هر دو بازیکن ۱۶۷ پیپ است */
    @Test
    fun `pip count - standard opening is 167 for both`() {
        val s = BgEngine(BgRules.STANDARD).createGame()
        assertEquals(167, s.pipCount(WHITE))
        assertEquals(167, s.pipCount(BLACK))
    }

    /** در هلندی مهره‌های واردنشده ۲۵ پیپ حساب می‌شوند */
    @Test
    fun `pip count - dutch off-board checkers count 25 each`() {
        val s = BgEngine(BgRules.DUTCH).createGame()
        assertEquals(15 * 25, s.pipCount(WHITE))
    }
}
