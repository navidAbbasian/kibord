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

/** تست‌های حیاتی سند ۰۱ — تخته‌نرد استاندارد */
class StandardBackgammonTest {

    private val engine = BgEngine(BgRules.STANDARD)

    /** Test 1 — شروع: هر بازیکن ۱۵ مهره، بار و خارج‌شده صفر */
    @Test
    fun `start - fifteen checkers each, empty bar and off`() {
        val s = engine.createGame()
        assertEquals(15, s.checkersOnBoard(WHITE))
        assertEquals(15, s.checkersOnBoard(BLACK))
        assertEquals(0, s.bar(WHITE))
        assertEquals(0, s.bar(BLACK))
        assertEquals(0, s.borneOff(WHITE))
        assertEquals(0, s.borneOff(BLACK))
        // چیدمان استاندارد از دید هر بازیکن: 24×2، 13×5، 8×3، 6×5
        for (p in listOf(WHITE, BLACK)) {
            assertEquals(2, s.pointOf(p, 24).count)
            assertEquals(5, s.pointOf(p, 13).count)
            assertEquals(3, s.pointOf(p, 8).count)
            assertEquals(5, s.pointOf(p, 6).count)
        }
    }

    /** Test 2 — Hit: فرود روی تک‌مهره‌ی حریف قانونی است و آن را به بار می‌فرستد */
    @Test
    fun `hit - landing on enemy blot sends it to bar`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 11, 1), Place(BLACK, 25 - 8, 1)), // سیاه از دید خودش 17 = مطلق 8
        )
        val hit = BgMove(from = 11, to = 8, die = 3, hit = true)
        assertTrue(engine.legalMoves(s).contains(hit))
        val after = engine.applyMove(s, hit)
        assertEquals(1, after.bar(BLACK))
        assertEquals(WHITE, after.pointAt(8).owner)
        assertEquals(1, after.pointAt(8).count)
    }

    /** Test 3 — Block: خانه‌ی با دو مهره‌ی حریف بسته است */
    @Test
    fun `block - point with two enemy checkers is illegal`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 11, 1), Place(BLACK, 25 - 8, 2)),
        )
        assertTrue(engine.legalMoves(s).none { it.from == 11 && it.to == 8 })
    }

    /** Test 4 — اولویت بار: با مهره روی بار هیچ حرکت معمولی مجاز نیست */
    @Test
    fun `bar priority - only entry moves while bar is occupied`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(4, 6),
            placements = listOf(Place(WHITE, 13, 3)),
            barWhite = 1,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it.from == BgMove.ENTRY })
    }

    /** Test 4b — بار بدون ورود قانونی: کل نوبت از دست می‌رود */
    @Test
    fun `bar priority - no legal entry means no moves at all`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(4, 6),
            // هر دو خانه‌ی ورود (25-4=21 و 25-6=19) با دو مهره‌ی حریف بسته‌اند
            placements = listOf(
                Place(WHITE, 13, 3),
                Place(BLACK, 25 - 21, 2),
                Place(BLACK, 25 - 19, 2),
            ),
            barWhite = 1,
        )
        assertFalse(engine.hasAnyMove(s))
    }

    /** Test 5 — جفت: ۴-۴ چهار مصرف تاس می‌سازد */
    @Test
    fun `doubles - four-four gives four dice uses`() {
        val engine = BgEngine(BgRules.STANDARD, FakeDice(4, 4))
        val s = engine.createGame().copy(turn = WHITE, phase = BgPhase.ROLLING)
        val rolled = engine.rollTurn(s)
        assertEquals(listOf(4, 4, 4, 4), rolled.remainingDice)
    }

    /** Test 6 — Bear Off: با همه‌ی مهره‌ها در خانه، تاس دقیق مهره را خارج می‌کند */
    @Test
    fun `bear off - exact roll takes checker off the board`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(5),
            placements = listOf(Place(WHITE, 5, 2), Place(WHITE, 3, 2)),
            borneOffWhite = 11,
        )
        val off = BgMove(from = 5, to = BgMove.OFF, die = 5)
        assertTrue(engine.legalMoves(s).contains(off))
        val after = engine.applyMove(s, off)
        assertEquals(12, after.borneOff(WHITE))
        assertEquals(1, after.pointOf(WHITE, 5).count)
    }

    /** Test 6b — تاس بزرگ‌تر از بالاترین خانه: خروج از بالاترین خانه‌ی موجود */
    @Test
    fun `bear off - big die takes from highest point when nothing higher exists`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(6),
            placements = listOf(Place(WHITE, 4, 2), Place(WHITE, 2, 1)),
            borneOffWhite = 12,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.contains(BgMove(from = 4, to = BgMove.OFF, die = 6)))
        // از خانه‌ی ۲ با تاس ۶ نمی‌شود خارج کرد چون خانه‌ی بالاتری (۴) اشغال است
        assertTrue(moves.none { it.from == 2 && it.to == BgMove.OFF })
    }

    /** Test 7 — Hit در حین Bear Off: با مهره روی بار خارج‌کردن غیرفعال می‌شود */
    @Test
    fun `hit during bear off - bearing disabled while on bar`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(5, 2),
            placements = listOf(Place(WHITE, 5, 2), Place(WHITE, 2, 1)),
            borneOffWhite = 11,
            barWhite = 1,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.none { it.to == BgMove.OFF })
        assertTrue(moves.all { it.from == BgMove.ENTRY })
    }

    /** Test 8 — Gammon: بازنده هیچ مهره‌ای خارج نکرده و در وضعیت مارس کامل نیست */
    @Test
    fun `gammon - loser with zero borne off scores two`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(1),
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 13, 15)),
            borneOffWhite = 14,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(BgPhase.FINISHED, after.phase)
        assertEquals(WHITE, after.winner)
        assertEquals(2, after.resultScore)
    }

    /** Test 8b — Backgammon: مهره‌ی بازنده در خانه‌ی برنده امتیاز ۳ می‌دهد */
    @Test
    fun `backgammon - loser checker in winner home scores three`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(1),
            // سیاه یک مهره در خانه‌ی سفید (مطلق ۳ = از دید سیاه ۲۲) دارد
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 22, 1), Place(BLACK, 13, 14)),
            borneOffWhite = 14,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(3, after.resultScore)
    }

    /** قاعده‌ی ۱۰ — فقط توالی‌هایی که بیشترین تاس را مصرف می‌کنند مجازند */
    @Test
    fun `rule ten - only sequences consuming maximum dice survive`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(5, 3),
            // ۲۰-۵=۱۵ بسته است؛ تنها راهِ مصرف هر دو تاس: اول ۳ بعد ۵
            placements = listOf(Place(WHITE, 20, 1), Place(BLACK, 25 - 15, 2)),
        )
        val moves = engine.legalMoves(s)
        assertEquals(listOf(BgMove(from = 20, to = 17, die = 3)), moves)
    }

    /** قاعده‌ی ۱۰ — وقتی فقط یکی از دو تاس بازی می‌شود، تاس بزرگ‌تر اجباری است */
    @Test
    fun `rule ten - when only one die is playable the higher one is forced`() {
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(6, 5),
            // هر دو حرکت تکی ممکن‌اند ولی ادامه از ۱۳ بسته است — بیشینه یک حرکت
            placements = listOf(Place(WHITE, 24, 1), Place(BLACK, 25 - 13, 2)),
        )
        val moves = engine.legalMoves(s)
        assertEquals(listOf(BgMove(from = 24, to = 18, die = 6)), moves)
    }
}
