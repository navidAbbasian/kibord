package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.BLACK
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.WHITE
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * رگرسیون گزارش میدانی: «یک لمس روی تاس بریز نباید بازی را چند نوبت جلو ببرد».
 * پرتاب شروع فقط نوبت و تاس‌ها را تعیین می‌کند — چیدمان، بار و خارج‌شده‌ها
 * باید مو‌به‌مو دست‌نخورده بمانند و پرتاب دوباره در فاز اشتباه باید رد شود.
 */
class BgOpeningRegressionTest {

    private fun assertBoardUntouched(rules: BgRules, opening: List<Int>) {
        val engine = BgEngine(rules, FakeDice(*opening.toIntArray()))
        val fresh = engine.createGame()
        val rolled = engine.rollOpening(fresh)
        // چیدمان دقیقاً همان چیدمان شروع است — هیچ مهره‌ای جابه‌جا نشده
        assertEquals(fresh.points, rolled.points)
        assertEquals(0, rolled.bar(WHITE))
        assertEquals(0, rolled.bar(BLACK))
        assertEquals(0, rolled.borneOff(WHITE))
        assertEquals(0, rolled.borneOff(BLACK))
        assertEquals(fresh.offBoard(WHITE), rolled.offBoard(WHITE))
        assertEquals(fresh.offBoard(BLACK), rolled.offBoard(BLACK))
    }

    @Test
    fun `opening roll never moves checkers in any variant`() {
        assertBoardUntouched(BgRules.STANDARD, listOf(5, 3))
        assertBoardUntouched(BgRules.DUTCH, listOf(5, 3))
        assertBoardUntouched(BgRules.HYPER, listOf(5, 3))
    }

    @Test
    fun `standard opening - winner starts moving with exactly the two opening dice`() {
        val engine = BgEngine(BgRules.STANDARD, FakeDice(3, 6))
        val rolled = engine.rollOpening(engine.createGame())
        assertEquals(BLACK, rolled.turn)
        assertEquals(BgPhase.MOVING, rolled.phase)
        assertEquals(listOf(3, 6), rolled.remainingDice)
    }

    @Test
    fun `opening tie keeps the opening phase and the board`() {
        val engine = BgEngine(BgRules.STANDARD, FakeDice(4, 4, 6, 2))
        val fresh = engine.createGame()
        val tie = engine.rollOpening(fresh)
        assertTrue(tie.openingTie)
        assertEquals(BgPhase.OPENING_ROLL, tie.phase)
        assertEquals(fresh.points, tie.points)
        // پرتاب بعدی گره را باز می‌کند
        val decided = engine.rollOpening(tie)
        assertEquals(WHITE, decided.turn)
        assertEquals(BgPhase.MOVING, decided.phase)
    }

    @Test
    fun `rolling in the wrong phase is rejected`() {
        val engine = BgEngine(BgRules.STANDARD, FakeDice(3, 6))
        val moving = engine.rollOpening(engine.createGame())
        // بعد از تعیین نفر اول، نه پرتاب شروع دوباره مجاز است نه تاس نوبت وسط حرکت
        assertThrows(IllegalArgumentException::class.java) { engine.rollOpening(moving) }
        assertThrows(IllegalArgumentException::class.java) { engine.rollTurn(moving) }
    }
}
