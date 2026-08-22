package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.BLACK
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.WHITE
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های حیاتی سند ۰۳ — هایپرگامون */
class HypergammonTest {

    private val engine = BgEngine(BgRules.HYPER)

    /** Test 1 — چیدمان: سفید روی ۲۲ و ۲۳ و ۲۴ خودش، سیاه قرینه (مطلق ۱ و ۲ و ۳) */
    @Test
    fun `setup - three lone checkers on 22 23 24 for each player`() {
        val s = engine.createGame()
        for (rel in listOf(22, 23, 24)) {
            assertEquals(1, s.pointOf(WHITE, rel).count)
            assertEquals(WHITE, s.pointOf(WHITE, rel).owner)
            assertEquals(1, s.pointOf(BLACK, rel).count)
            assertEquals(BLACK, s.pointOf(BLACK, rel).owner)
        }
        // قرینه‌ی سیاه در شماره‌گذاری مطلق: خانه‌های ۱ و ۲ و ۳
        for (abs in listOf(1, 2, 3)) {
            assertEquals(BLACK, s.pointAt(abs).owner)
        }
    }

    /** Test 2 — هر بازیکن دقیقاً سه مهره دارد */
    @Test
    fun `three checkers - totals are three per player`() {
        val s = engine.createGame()
        assertEquals(3, s.totalCheckers(WHITE))
        assertEquals(3, s.totalCheckers(BLACK))
    }

    /** Test 3 — Hit: زدن تک‌مهره‌ی حریف آن را به بار می‌فرستد */
    @Test
    fun `hit - enemy blot goes to bar`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(2),
            placements = listOf(
                Place(WHITE, 24, 1), Place(WHITE, 23, 1), Place(WHITE, 12, 1),
                Place(BLACK, 25 - 10, 1), Place(BLACK, 20, 1), Place(BLACK, 21, 1),
            ),
        )
        val hit = BgMove(from = 12, to = 10, die = 2, hit = true)
        assertTrue(engine.legalMoves(s).contains(hit))
        val after = engine.applyMove(s, hit)
        assertEquals(1, after.bar(BLACK))
    }

    /** Test 4 — اولویت بار: با مهره روی بار حرکت معمولی غیرفعال است */
    @Test
    fun `bar priority - normal movement disabled while on bar`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(3, 5),
            placements = listOf(Place(WHITE, 24, 1), Place(WHITE, 23, 1)),
            barWhite = 1,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.isNotEmpty())
        assertTrue(moves.all { it.from == BgMove.ENTRY })
    }

    /** Test 5 — بازشدن Bear Off: با رسیدن هر سه مهره به خانه، خروج مجاز می‌شود */
    @Test
    fun `bear off unlock - all three in home enables bearing off`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(6, 2),
            placements = listOf(Place(WHITE, 6, 1), Place(WHITE, 4, 1), Place(WHITE, 2, 1)),
        )
        assertTrue(s.allActiveInHome(WHITE))
        val moves = engine.legalMoves(s)
        assertTrue(moves.contains(BgMove(from = 6, to = BgMove.OFF, die = 6)))
        assertTrue(moves.contains(BgMove(from = 2, to = BgMove.OFF, die = 2)))
    }

    /** Test 6 — برد: با سه مهره‌ی خارج‌شده بازی تمام است */
    @Test
    fun `win - three borne off finishes the game`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 3, 1), Place(BLACK, 15, 2)),
            borneOffWhite = 2,
            borneOffBlack = 1,
        )
        val after = engine.applyMove(s, BgMove(from = 3, to = BgMove.OFF, die = 3))
        assertEquals(BgPhase.FINISHED, after.phase)
        assertEquals(WHITE, after.winner)
        assertEquals(1, after.resultScore)
    }

    /** Test 7 — Gammon: بازنده هیچ مهره‌ای خارج نکرده — امتیاز از piecesPerPlayer خوانده می‌شود نه ۱۵ */
    @Test
    fun `gammon - winner off three and loser off zero`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(3),
            placements = listOf(Place(WHITE, 3, 1), Place(BLACK, 15, 3)),
            borneOffWhite = 2,
        )
        val after = engine.applyMove(s, BgMove(from = 3, to = BgMove.OFF, die = 3))
        assertEquals(WHITE, after.winner)
        assertEquals(2, after.resultScore)
    }

    /** ضربه در Bear Off: تا برگشتن مهره از بار، خروج غیرفعال می‌ماند */
    @Test
    fun `hit during bear off - disabled until checker returns home`() {
        val s = stateOf(
            rules = BgRules.HYPER,
            remainingDice = listOf(4, 1),
            placements = listOf(Place(WHITE, 5, 1), Place(WHITE, 3, 1)),
            barWhite = 1,
        )
        val moves = engine.legalMoves(s)
        assertTrue(moves.none { it.to == BgMove.OFF })
        assertTrue(moves.all { it.from == BgMove.ENTRY })
    }
}
