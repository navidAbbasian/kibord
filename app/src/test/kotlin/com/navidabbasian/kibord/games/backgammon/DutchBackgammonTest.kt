package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.BLACK
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.WHITE
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های حیاتی سند ۰۲ — تخته‌نرد هلندی */
class DutchBackgammonTest {

    private val engine = BgEngine(BgRules.DUTCH)

    /** Test 1 — شروع: صفحه خالی، هر ۱۵ مهره بیرون، بار صفر */
    @Test
    fun `start - board empty and fifteen checkers off board`() {
        val s = engine.createGame()
        assertEquals(0, s.checkersOnBoard(WHITE))
        assertEquals(0, s.checkersOnBoard(BLACK))
        assertEquals(15, s.offBoard(WHITE))
        assertEquals(15, s.offBoard(BLACK))
        assertEquals(0, s.bar(WHITE))
        assertEquals(0, s.bar(BLACK))
    }

    /** Test 2 — برنده‌ی پرتاب شروع دوباره تاس می‌اندازد و همان پرتاب نوبت اول اوست */
    @Test
    fun `opening winner rerolls - first real roll belongs to the winner`() {
        val engine = BgEngine(BgRules.DUTCH, FakeDice(5, 2, 6, 3))
        var s = engine.createGame()
        s = engine.rollOpening(s)
        // سفید ۵ آورد و سیاه ۲ — سفید شروع می‌کند ولی هنوز تاسِ حرکت ندارد
        assertEquals(WHITE, s.turn)
        assertEquals(BgPhase.ROLLING, s.phase)
        assertTrue(s.remainingDice.isEmpty())
        // پرتاب واقعی نوبت اول
        s = engine.rollTurn(s)
        assertEquals(listOf(6, 3), s.remainingDice)
    }

    /** Test 3 — ورود اجباری: تا وقتی ورودِ قانونی هست، حرکت معمولی ممنوع است */
    @Test
    fun `forced entry - normal moves illegal while entry is possible`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(6, 3),
            placements = listOf(Place(WHITE, 20, 2)),
            offBoardWhite = 13,
            offBoardBlack = 15,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it.from == BgMove.ENTRY })
    }

    /** Test 3b — وقتی هیچ ورودی ممکن نیست، حرکت با مهره‌های واردشده آزاد می‌شود */
    @Test
    fun `forced entry - normal move allowed only when no entry is possible`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(6, 3),
            // هر دو خانه‌ی ورود (19 و 22 از دید سفید) با دو مهره‌ی حریف بسته‌اند
            placements = listOf(
                Place(WHITE, 20, 2),
                Place(BLACK, 25 - 19, 2),
                Place(BLACK, 25 - 22, 2),
            ),
            offBoardWhite = 13,
            offBoardBlack = 11,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it.from == 20 })
    }

    /** Test 4 — زدن زودهنگام ممنوع: تا مهره‌ای به خانه‌ی خودی نرسیده، تک‌مهره‌ی حریف دست‌نزدنی است */
    @Test
    fun `early hit forbidden - blot untouchable before reaching own home`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 10, 1), Place(BLACK, 25 - 7, 1)),
            homeReachedWhite = false,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.none { it.hit })
        assertTrue(moves.none { it.from == 10 && it.to == 7 })
    }

    /** Test 5 — فعال‌شدن زدن: بعد از رسیدن مهره به خانه‌ی خودی، زدن قانونی است */
    @Test
    fun `hit enabled - after own home entry hitting becomes legal`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 10, 1), Place(BLACK, 25 - 7, 1)),
            homeReachedWhite = true,
        )
        val hit = BgMove(from = 10, to = 7, die = 3, hit = true)
        assertTrue(engine.legalMoves(s).contains(hit))
        val after = engine.applyMove(s, hit)
        assertEquals(1, after.bar(BLACK))
    }

    /** Test 6 — ورود کامل: با صفرشدن مهره‌های بیرون، مرحله‌ی ورود تمام می‌شود */
    @Test
    fun `all entered - entering phase ends when off board hits zero`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(4),
            placements = listOf(Place(WHITE, 20, 14)),
            offBoardWhite = 1,
        )
        assertTrue(s.isEntering(WHITE))
        val entry = engine.legalMoves(s).first { it.from == BgMove.ENTRY }
        val after = engine.applyMove(s, entry)
        assertEquals(0, after.offBoard(WHITE))
        assertFalse(after.isEntering(WHITE))
    }

    /** پرچم رسیدن به خانه با خودِ حرکت روشن می‌شود */
    @Test
    fun `reaching own home sets the sticky flag`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(4),
            placements = listOf(Place(WHITE, 9, 1)),
            homeReachedWhite = false,
        )
        val after = engine.applyMove(s, BgMove(from = 9, to = 5, die = 4))
        assertTrue(after.homeReached(WHITE))
    }

    /** امتیازدهی ساده‌ی هلندی: مارس کامل وجود ندارد — سقف امتیاز ۲ است */
    @Test
    fun `simple scoring - no backgammon score in dutch`() {
        val s = stateOf(
            rules = BgRules.DUTCH,
            remainingDice = listOf(1),
            // سیاه مهره روی بار دارد؛ در استاندارد این مارس کامل می‌شد
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 13, 14)),
            borneOffWhite = 14,
            barBlack = 1,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(WHITE, after.winner)
        assertEquals(2, after.resultScore)
    }
}
