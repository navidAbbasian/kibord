package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.RandomDiceRoller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * شبیه‌سازی بازی کامل با حرکت‌های تصادفیِ بذردار: هر سه روش باید بدون نقض
 * پایستگی مهره‌ها به پایان برسند — تور ایمنی موتور در کنار تست‌های سناریویی.
 */
class BgSimulationTest {

    private fun playFullGame(rules: BgRules, seed: Int) {
        val engine = BgEngine(rules, RandomDiceRoller(Random(seed)))
        val picker = Random(seed + 1)
        var s = engine.createGame()
        while (s.phase == BgPhase.OPENING_ROLL) s = engine.rollOpening(s)
        var guard = 0
        while (s.phase != BgPhase.FINISHED && guard++ < 5000) {
            s = when (s.phase) {
                BgPhase.ROLLING -> engine.rollTurn(s)
                BgPhase.MOVING -> {
                    val moves = engine.legalMoves(s)
                    if (moves.isEmpty()) {
                        engine.endTurn(s)
                    } else {
                        var next = engine.applyMove(s, moves.random(picker))
                        if (next.phase != BgPhase.FINISHED && next.remainingDice.isEmpty()) {
                            next = engine.endTurn(next)
                        }
                        next
                    }
                }
                else -> s
            }
            // پایستگی: جمع مهره‌های هر بازیکن همیشه piecesPerPlayer است
            assertEquals(rules.piecesPerPlayer, s.totalCheckers(BgPlayer.WHITE))
            assertEquals(rules.piecesPerPlayer, s.totalCheckers(BgPlayer.BLACK))
        }
        assertTrue("بازی ${rules.variant} با بذر $seed تمام نشد", s.phase == BgPhase.FINISHED)
        assertTrue(s.resultScore in 1..3)
        val winner = s.winner!!
        assertEquals(rules.piecesPerPlayer, s.borneOff(winner))
    }

    @Test
    fun `standard games finish with conserved checkers`() {
        for (seed in listOf(1, 2, 3)) playFullGame(BgRules.STANDARD, seed)
    }

    @Test
    fun `dutch games finish with conserved checkers`() {
        for (seed in listOf(1, 2, 3)) playFullGame(BgRules.DUTCH, seed)
    }

    @Test
    fun `hyper games finish with conserved checkers`() {
        for (seed in listOf(1, 2, 3)) playFullGame(BgRules.HYPER, seed)
    }
}
