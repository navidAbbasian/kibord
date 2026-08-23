package com.navidabbasian.kibord.games.ludo

import com.navidabbasian.kibord.games.ludo.engine.BASE
import com.navidabbasian.kibord.games.ludo.engine.GOAL
import com.navidabbasian.kibord.games.ludo.engine.LudoBot
import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoColor.BLUE
import com.navidabbasian.kibord.games.ludo.engine.LudoColor.GREEN
import com.navidabbasian.kibord.games.ludo.engine.LudoColor.RED
import com.navidabbasian.kibord.games.ludo.engine.LudoColor.YELLOW
import com.navidabbasian.kibord.games.ludo.engine.LudoEngine
import com.navidabbasian.kibord.games.ludo.engine.LudoEvent
import com.navidabbasian.kibord.games.ludo.engine.LudoGeometry
import com.navidabbasian.kibord.games.ludo.engine.LudoMove
import com.navidabbasian.kibord.games.ludo.engine.LudoPhase
import com.navidabbasian.kibord.games.ludo.engine.LudoRules
import com.navidabbasian.kibord.games.ludo.engine.LudoSeat
import com.navidabbasian.kibord.games.ludo.engine.LudoSeatKind
import com.navidabbasian.kibord.games.ludo.engine.LudoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** تست‌های قواعد منچ: ورود با شش، پرتاب اضافه، سه‌شش، زدن، سد، ورود دقیق، رد شدن نوبت، برد و ربات */
class LudoEngineTest {

    private fun seats(vararg kinds: LudoSeatKind): List<LudoSeat> =
        LudoColor.entries.mapIndexed { i, c ->
            LudoSeat(c, kinds.getOrElse(i) { LudoSeatKind.EMPTY }, "بازیکن ${i + 1}")
        }

    private val fourHumans = seats(LudoSeatKind.HUMAN, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN)

    private fun game(rules: LudoRules = LudoRules(), vararg kinds: LudoSeatKind): LudoState =
        LudoEngine.newGame(
            seats = if (kinds.isEmpty()) fourHumans else seats(*kinds),
            rules = rules,
            firstTurn = RED,
        )

    /** مهره‌ی token از رنگ color را بی‌واسطه در گام step می‌نشاند */
    private fun LudoState.place(color: LudoColor, token: Int, step: Int): LudoState {
        val t = tokens.map { it.toMutableList() }
        t[color.ordinal][token] = step
        return copy(tokens = t.map { it.toList() })
    }

    private fun LudoState.turnOf(color: LudoColor) = copy(turn = color, phase = LudoPhase.ROLLING, die = null, sixStreak = 0)

    private fun LudoState.step(color: LudoColor, token: Int) = tokensOf(color)[token]

    // ---- ورود از پایگاه ----

    @Test
    fun `without a six no token can leave the base and the turn passes`() {
        val s = LudoEngine.roll(game(), 4)
        assertEquals(LudoPhase.PASSING, s.phase)
        assertEquals(LudoEvent.NO_MOVE, s.event)
        assertTrue(LudoEngine.legalMoves(s).isEmpty())
        val next = LudoEngine.endTurn(s)
        assertEquals(GREEN, next.turn)
        assertEquals(LudoPhase.ROLLING, next.phase)
    }

    @Test
    fun `a six brings a token to the start square and grants another roll`() {
        val rolled = LudoEngine.roll(game(), 6)
        assertEquals(LudoPhase.MOVING, rolled.phase)
        val moves = LudoEngine.legalMoves(rolled)
        assertEquals(4, moves.size)
        assertTrue(moves.all { it.from == BASE && it.to == 0 })
        val after = LudoEngine.applyMove(rolled, moves.first())
        assertEquals(0, after.step(RED, 0))
        assertEquals(LudoEvent.ENTERED, after.event)
        assertEquals(RED, after.turn)
        assertEquals(LudoPhase.ROLLING, after.phase)
        assertEquals(RED.startIndex, RED.trackIndexOf(0))
        assertEquals(LudoGeometry.track[RED.startIndex], LudoGeometry.cellOf(RED, 0, 0))
    }

    @Test
    fun `a six on the track also grants an extra roll while other numbers end the turn`() {
        val s = game().place(RED, 0, 10)
        val six = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 0, 10, 16))
        assertEquals(RED, six.turn)
        assertEquals(LudoPhase.ROLLING, six.phase)

        val three = LudoEngine.applyMove(LudoEngine.roll(six, 3), LudoMove(RED, 0, 16, 19))
        assertEquals(GREEN, three.turn)
        assertEquals(LudoPhase.ROLLING, three.phase)
        assertEquals(0, three.sixStreak)
    }

    // ---- سه‌شش ----

    @Test
    fun `three sixes in a row lose the turn when the rule is on`() {
        var s = game().place(RED, 0, 5)
        s = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 0, 5, 11))
        assertEquals(1, s.sixStreak)
        s = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 0, 11, 17))
        assertEquals(2, s.sixStreak)
        s = LudoEngine.roll(s, 6)
        assertEquals(LudoPhase.PASSING, s.phase)
        assertEquals(LudoEvent.TRIPLE_SIX, s.event)
        assertTrue(LudoEngine.legalMoves(s).isEmpty())
        assertEquals(17, s.step(RED, 0))
        val next = LudoEngine.endTurn(s)
        assertEquals(GREEN, next.turn)
    }

    @Test
    fun `three sixes are playable when the rule is off`() {
        var s = game(LudoRules(tripleSixLosesTurn = false)).place(RED, 0, 5)
        s = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 0, 5, 11))
        s = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 0, 11, 17))
        s = LudoEngine.roll(s, 6)
        assertEquals(LudoPhase.MOVING, s.phase)
        assertTrue(LudoEngine.legalMoves(s).any { it.token == 0 && it.to == 23 })
    }

    // ---- زدن و سد ----

    @Test
    fun `landing on a lone opponent sends it home and grants an extra roll`() {
        // سبز در گام ۲ (خانه‌ی مطلق ۱۵)؛ قرمز از گام ۱۲ با تاس ۳ به ۱۵ می‌رسد
        val s = game().place(RED, 0, 12).place(GREEN, 1, 2)
        val rolled = LudoEngine.roll(s, 3)
        val move = LudoMove(RED, 0, 12, 15)
        assertTrue(LudoEngine.isCapture(rolled, move))
        val after = LudoEngine.applyMove(rolled, move)
        assertEquals(BASE, after.step(GREEN, 1))
        assertEquals(LudoEvent.CAPTURED, after.event)
        assertEquals(GREEN, after.lastMove?.captured?.color)
        assertEquals(15, after.lastMove?.captured?.trackIndex)
        assertEquals(RED, after.turn)
        assertEquals(LudoPhase.ROLLING, after.phase)
    }

    @Test
    fun `two opponent tokens form a block that cannot be landed on`() {
        val s = game().place(RED, 0, 12).place(GREEN, 0, 2).place(GREEN, 1, 2)
        val rolled = LudoEngine.roll(s, 3)
        assertEquals(LudoPhase.PASSING, rolled.phase)
        assertTrue(LudoEngine.legalMoves(rolled).isEmpty())
        // با تاس دیگر که از روی سد رد می‌شود مشکلی نیست
        val jump = LudoEngine.roll(s, 4)
        assertEquals(listOf(LudoMove(RED, 0, 12, 16)), LudoEngine.legalMoves(jump))
    }

    @Test
    fun `own tokens may stack and are never captured by each other`() {
        val s = game().place(RED, 0, 12).place(RED, 1, 15)
        val rolled = LudoEngine.roll(s, 3)
        val after = LudoEngine.applyMove(rolled, LudoMove(RED, 0, 12, 15))
        assertEquals(15, after.step(RED, 0))
        assertEquals(15, after.step(RED, 1))
        assertEquals(LudoEvent.MOVED, after.event)
    }

    @Test
    fun `safe start squares prevent captures when enabled`() {
        // خانه‌ی شروع سبز (مطلق ۱۳) = گام ۱۳ قرمز
        val base = game(LudoRules(safeStartSquares = true)).place(RED, 0, 10).place(GREEN, 0, 0)
        val rolled = LudoEngine.roll(base, 3)
        val move = LudoMove(RED, 0, 10, 13)
        assertTrue(LudoEngine.legalMoves(rolled).contains(move))
        assertFalse(LudoEngine.isCapture(rolled, move))
        val after = LudoEngine.applyMove(rolled, move)
        assertEquals(0, after.step(GREEN, 0))
        assertEquals(GREEN, after.turn)

        // بدون گزینه همان حرکت می‌زند
        val plain = game().place(RED, 0, 10).place(GREEN, 0, 0)
        val hit = LudoEngine.applyMove(LudoEngine.roll(plain, 3), move)
        assertEquals(BASE, hit.step(GREEN, 0))
    }

    // ---- ستون خانه و ورود دقیق ----

    @Test
    fun `entering the goal needs the exact number`() {
        val s = game().place(RED, 0, 54)
        val tooBig = LudoEngine.roll(s, 3)
        assertEquals(LudoPhase.PASSING, tooBig.phase)
        val exact = LudoEngine.roll(s, 2)
        val move = LudoEngine.legalMoves(exact).single()
        assertEquals(GOAL, move.to)
        val after = LudoEngine.applyMove(exact, move)
        assertEquals(GOAL, after.step(RED, 0))
        assertEquals(LudoEvent.REACHED_GOAL, after.event)
        assertEquals(1, after.inGoal(RED))
    }

    @Test
    fun `tokens in the home column are private and cannot be captured`() {
        // قرمز در ستون خانه‌اش (گام ۵۲)؛ خانه‌ی مطلق ندارد
        val s = game().place(RED, 0, 52).place(GREEN, 0, 30)
        assertNull(RED.trackIndexOf(52))
        val rolled = LudoEngine.roll(s.turnOf(GREEN), 6)
        val moves = LudoEngine.legalMoves(rolled)
        assertTrue(moves.none { LudoEngine.isCapture(rolled, it) })
    }

    // ---- نوبت‌ها ----

    @Test
    fun `turn order skips empty seats`() {
        val s = game(LudoRules(), LudoSeatKind.HUMAN, LudoSeatKind.EMPTY, LudoSeatKind.BOT, LudoSeatKind.EMPTY)
        assertEquals(RED, s.turn)
        val passed = LudoEngine.endTurn(LudoEngine.roll(s, 2))
        assertEquals(YELLOW, passed.turn)
        assertEquals(RED, LudoEngine.endTurn(passed).turn)
    }

    // ---- برد ----

    @Test
    fun `bringing the fourth token home finishes the player and the game continues for others`() {
        val s = game().place(RED, 0, GOAL).place(RED, 1, GOAL).place(RED, 2, GOAL).place(RED, 3, 55)
        val after = LudoEngine.applyMove(LudoEngine.roll(s, 1), LudoMove(RED, 3, 55, GOAL))
        assertEquals(LudoPhase.FINISHED, after.phase)
        assertEquals(listOf(RED), after.finished)
        assertFalse(after.gameOver)
        assertEquals(LudoEvent.PLAYER_FINISHED, after.event)
        val cont = LudoEngine.continueAfterFinish(after)
        assertEquals(GREEN, cont.turn)
        assertEquals(LudoPhase.ROLLING, cont.phase)
        // قرمز دیگر نوبت نمی‌گیرد
        assertEquals(YELLOW, LudoEngine.nextPlayer(cont, GREEN))
        assertEquals(GREEN, LudoEngine.nextPlayer(cont, BLUE))
    }

    @Test
    fun `game is over when only one player is left unranked`() {
        val s = game(LudoRules(), LudoSeatKind.HUMAN, LudoSeatKind.BOT, LudoSeatKind.EMPTY, LudoSeatKind.EMPTY)
            .place(RED, 0, GOAL).place(RED, 1, GOAL).place(RED, 2, GOAL).place(RED, 3, 50)
        val after = LudoEngine.applyMove(LudoEngine.roll(s, 6), LudoMove(RED, 3, 50, GOAL))
        assertTrue(after.gameOver)
        assertEquals(listOf(RED, GREEN), LudoEngine.ranking(after))
    }

    @Test
    fun `ranking lists finishers first then others by progress`() {
        val s = game()
            .place(GREEN, 0, GOAL).place(GREEN, 1, GOAL).place(GREEN, 2, GOAL).place(GREEN, 3, GOAL)
            .copy(finished = listOf(GREEN))
            .place(RED, 0, 10)
            .place(BLUE, 0, 30).place(BLUE, 1, 5)
            .place(YELLOW, 0, GOAL)
        assertEquals(listOf(GREEN, YELLOW, BLUE, RED), LudoEngine.ranking(s))
    }

    // ---- ربات ----

    @Test
    fun `bot prefers capture over goal over leaving the base`() {
        // تاس ۶: مهره‌ی ۰ می‌تواند بزند (۹→۱۵ روی سبز)، مهره‌ی ۱ به خانه برسد (۵۰→۵۶)، مهره‌ی ۲ بیرون بیاید
        val s = game(LudoRules(), LudoSeatKind.BOT, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN)
            .place(RED, 0, 9).place(RED, 1, 50).place(RED, 3, 20).place(GREEN, 0, 2)
        val rolled = LudoEngine.roll(s, 6)
        val moves = LudoEngine.legalMoves(rolled)
        assertEquals(4, moves.size)
        val pick = LudoBot.choose(rolled, moves, Random(1))!!
        assertEquals(LudoMove(RED, 0, 9, 15), pick)

        // بدون زدن: رساندن به خانه
        val noCapture = LudoEngine.roll(s.place(GREEN, 0, BASE), 6)
        assertEquals(LudoMove(RED, 1, 50, GOAL), LudoBot.choose(noCapture, LudoEngine.legalMoves(noCapture), Random(2)))

        // بدون زدن و رسیدن: بیرون آوردن مهره
        val onlyExit = LudoEngine.roll(s.place(GREEN, 0, BASE).place(RED, 1, 20), 6)
        assertEquals(LudoMove(RED, 2, BASE, 0), LudoBot.choose(onlyExit, LudoEngine.legalMoves(onlyExit), Random(3)))
    }

    @Test
    fun `bot escapes a threatened token and otherwise advances the leading safe token`() {
        // مهره‌ی ۰ قرمز در گام ۲۰ (مطلق ۲۰) و سبز در گام ۴ (مطلق ۱۷) سه خانه پشتش: تهدید
        val threatened = game(LudoRules(), LudoSeatKind.BOT, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN, LudoSeatKind.HUMAN)
            .place(RED, 0, 20).place(RED, 1, 40).place(GREEN, 0, 4)
        assertTrue(LudoEngine.isThreatened(threatened, RED, 20))
        assertFalse(LudoEngine.isThreatened(threatened, RED, 40))
        val rolled = LudoEngine.roll(threatened, 4)
        assertEquals(LudoMove(RED, 0, 20, 24), LudoBot.choose(rolled, LudoEngine.legalMoves(rolled), Random(4)))

        // بدون تهدید: جلوترین مهره پیش می‌رود
        val calm = LudoEngine.roll(threatened.place(GREEN, 0, BASE), 4)
        assertEquals(LudoMove(RED, 1, 40, 44), LudoBot.choose(calm, LudoEngine.legalMoves(calm), Random(5)))
    }

    @Test
    fun `bot never picks an illegal move in a random simulation and games terminate`() {
        val seats = seats(LudoSeatKind.BOT, LudoSeatKind.BOT, LudoSeatKind.BOT, LudoSeatKind.BOT)
        val random = Random(42)
        var s = LudoEngine.newGame(seats, LudoRules())
        var guard = 0
        while (!s.gameOver && guard++ < 20_000) {
            s = when (s.phase) {
                LudoPhase.ROLLING -> LudoEngine.roll(s, random.nextInt(1, 7))
                LudoPhase.MOVING -> {
                    val moves = LudoEngine.legalMoves(s)
                    LudoEngine.applyMove(s, LudoBot.choose(s, moves, random)!!)
                }
                LudoPhase.PASSING -> LudoEngine.endTurn(s)
                LudoPhase.FINISHED -> LudoEngine.continueAfterFinish(s)
            }
            // پایستگی: همیشه ۴ مهره برای هر رنگ و هیچ سد حریفی شکسته نمی‌شود
            assertEquals(4, s.tokens.size)
            s.tokens.forEach { assertEquals(4, it.size) }
        }
        assertTrue("بازی باید تمام شود", s.gameOver)
        assertEquals(3, s.finished.size)
        assertEquals(4, LudoEngine.ranking(s).size)
    }

    @Test
    fun `geometry maps every color's last track square into its own home column`() {
        LudoColor.entries.forEach { c ->
            val last = LudoGeometry.cellOf(c, 0, 50)
            val first = LudoGeometry.cellOf(c, 0, 51)
            // خانه‌ی اول ستون درست کنار آخرین خانه‌ی مسیر است
            val dist = kotlin.math.abs(last.row - first.row) + kotlin.math.abs(last.col - first.col)
            assertEquals(1f, dist)
            assertEquals(52, LudoGeometry.track.size)
            assertEquals(LudoGeometry.track.size, LudoGeometry.track.toSet().size)
        }
    }
}
