package com.navidabbasian.kibord.games.dooz

import com.navidabbasian.kibord.games.dooz.engine.DoozBoard
import com.navidabbasian.kibord.games.dooz.engine.DoozBot
import com.navidabbasian.kibord.games.dooz.engine.DoozDifficulty
import com.navidabbasian.kibord.games.dooz.engine.DoozMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** قوانین دوز: تشخیص برد روی سطر/ستون/قطر، مساوی، و رفتار ربات در سه سطح */
class DoozEngineTest {

    // ---------- قوانین ----------

    @Test
    fun `empty board has no winner and is not over`() {
        val b = DoozBoard()
        assertNull(b.winner)
        assertFalse(b.isDraw)
        assertFalse(b.isOver)
        assertEquals(9, b.emptyCells.size)
    }

    @Test
    fun `win on every row is detected with the right line`() {
        val rows = listOf(
            "XXXOO...." to listOf(0, 1, 2),
            "OO.XXX..." to listOf(3, 4, 5),
            "O.O...XXX" to listOf(6, 7, 8),
        )
        rows.forEach { (pattern, line) ->
            val win = DoozBoard.of(pattern).winner
            assertNotNull("باید برد تشخیص داده شود: $pattern", win)
            assertEquals(DoozMark.X, win!!.mark)
            assertEquals(line, win.line)
        }
    }

    @Test
    fun `win on every column is detected`() {
        val cols = listOf(
            "OX.OX.O.." to listOf(0, 3, 6),
            "XOXXO..O." to listOf(1, 4, 7),
            "XXO..O..O" to listOf(2, 5, 8),
        )
        cols.forEach { (pattern, line) ->
            val win = DoozBoard.of(pattern).winner
            assertNotNull("باید برد تشخیص داده شود: $pattern", win)
            assertEquals(DoozMark.O, win!!.mark)
            assertEquals(line, win.line)
        }
    }

    @Test
    fun `win on both diagonals is detected`() {
        val main = DoozBoard.of("XOO.X...X").winner
        assertEquals(DoozMark.X, main?.mark)
        assertEquals(listOf(0, 4, 8), main?.line)

        val anti = DoozBoard.of("XXO.O.OX.").winner
        assertEquals(DoozMark.O, anti?.mark)
        assertEquals(listOf(2, 4, 6), anti?.line)
    }

    @Test
    fun `full board without a line is a draw`() {
        // X O X / X O O / O X X
        val b = DoozBoard.of("XOXXOOOXX")
        assertNull(b.winner)
        assertTrue(b.isFull)
        assertTrue(b.isDraw)
        assertTrue(b.isOver)
    }

    @Test
    fun `placing on an occupied cell is rejected and board is immutable`() {
        val b = DoozBoard().place(4, DoozMark.X)
        assertEquals(DoozMark.X, b[4])
        assertFalse(b.canPlace(4))
        assertTrue(b.canPlace(0))
        var threw = false
        try {
            b.place(4, DoozMark.O)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        // صفحه‌ی اصلی دست‌نخورده است
        assertNull(DoozBoard()[4])
    }

    // ---------- ربات ----------

    @Test
    fun `easy bot always returns a legal move`() {
        val bot = DoozBot(Random(7))
        repeat(300) {
            var b = DoozBoard()
            var turn = DoozMark.X
            while (!b.isOver) {
                val m = bot.chooseMove(b, turn, DoozDifficulty.EASY)
                assertTrue("حرکت غیرمجاز $m روی\n$b", b.canPlace(m))
                b = b.place(m, turn)
                turn = turn.other
            }
        }
    }

    @Test
    fun `medium bot blocks an immediate threat`() {
        // X تهدید می‌کند روی خانه‌ی ۲ ببرد؛ ربات O باید سد کند
        val b = DoozBoard.of("XX.O.....")
        val bot = DoozBot(Random(1))
        repeat(20) { assertEquals(2, bot.chooseMove(b, DoozMark.O, DoozDifficulty.MEDIUM)) }
    }

    @Test
    fun `medium bot takes an immediate win over a block`() {
        // O می‌تواند روی ۵ ببرد، X هم روی ۲ تهدید دارد — برد مقدم است
        val b = DoozBoard.of("XX.OO....")
        val bot = DoozBot(Random(3))
        repeat(20) { assertEquals(5, bot.chooseMove(b, DoozMark.O, DoozDifficulty.MEDIUM)) }
    }

    @Test
    fun `hard bot never loses against random play over 200 games`() {
        val rnd = Random(2024)
        val bot = DoozBot(Random(99))
        var botLosses = 0
        repeat(200) { game ->
            val botMark = if (game % 2 == 0) DoozMark.X else DoozMark.O
            var b = DoozBoard()
            var turn = DoozMark.X
            while (!b.isOver) {
                val move = if (turn == botMark) {
                    bot.chooseMove(b, turn, DoozDifficulty.HARD)
                } else {
                    val empties = b.emptyCells
                    empties[rnd.nextInt(empties.size)]
                }
                b = b.place(move, turn)
                turn = turn.other
            }
            if (b.winner?.mark == botMark.other) botLosses++
        }
        assertEquals("ربات سخت نباید هیچ‌وقت ببازد", 0, botLosses)
    }

    @Test
    fun `hard bot versus hard bot always draws`() {
        val bot = DoozBot(Random(5))
        repeat(30) {
            var b = DoozBoard()
            var turn = DoozMark.X
            while (!b.isOver) {
                b = b.place(bot.chooseMove(b, turn, DoozDifficulty.HARD), turn)
                turn = turn.other
            }
            assertTrue("دو ربات کامل باید مساوی کنند:\n$b", b.isDraw)
        }
    }

    @Test
    fun `hard bot finds the fork-proof reply and blocks`() {
        // X در ۰ و ۸ (قطر)، O در مرکز — پاسخ درست O برای جلوگیری از فورک، لبه است نه گوشه
        val b = DoozBoard.of("X...O...X")
        val bot = DoozBot(Random(11))
        repeat(20) {
            val m = bot.chooseMove(b, DoozMark.O, DoozDifficulty.HARD)
            assertTrue("حرکت $m باید لبه باشد", m in listOf(1, 3, 5, 7))
        }
    }

    @Test
    fun `immediateWin helper finds winning cell`() {
        assertEquals(2, DoozBot.immediateWin(DoozBoard.of("XX.O.O..."), DoozMark.X))
        assertNull(DoozBot.immediateWin(DoozBoard.of("X...O...."), DoozMark.X))
    }
}
