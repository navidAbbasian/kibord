package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgMatchRules
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.BLACK
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer.WHITE
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.net.BgMessage
import com.navidabbasian.kibord.games.backgammon.net.BgRoomSnapshot
import com.navidabbasian.kibord.games.backgammon.net.decodeBgMessage
import com.navidabbasian.kibord.games.backgammon.net.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تست‌های روش تخته‌نرد ایرانی: بدون دوبل، دست به مهره، زننده تک می‌ماند، آخرین مهره و سگ‌مارس */
class IraniBackgammonTest {

    private val engine = BgEngine(BgRules.IRANI)

    // ================= پرچم‌های قوانین =================

    /** پرچم‌های روش ایرانی: بدون مکعب، دست به مهره، زننده تک، تاس بزرگ‌تر آخر */
    @Test
    fun `irani rules - flags and standard layout`() {
        val r = BgRules.IRANI
        assertEquals(BgVariant.IRANI, r.variant)
        assertFalse(r.usesCube)
        assertTrue(r.touchMove)
        assertTrue(r.hitInHomeStaysSingle)
        assertTrue(r.lastCheckerHigherDie)
        assertTrue(r.hasBackgammonScore)
        assertEquals(15, r.piecesPerPlayer)
        assertEquals(mapOf(24 to 2, 13 to 5, 8 to 3, 6 to 5), r.startingLayout)
        assertEquals(BgRules.IRANI, BgRules.of(BgVariant.IRANI))
        // روش‌های دیگر دست‌نخورده می‌مانند
        assertTrue(BgRules.STANDARD.usesCube)
        assertFalse(BgRules.STANDARD.touchMove)
        assertFalse(BgRules.STANDARD.hitInHomeStaysSingle)
        assertFalse(BgRules.STANDARD.lastCheckerHigherDie)
    }

    // ================= بند ۵: زننده در خانه‌ی خودی تک می‌ماند =================

    /** زدن در خانه‌ی خودی: زننده قفل می‌شود و روی خانه‌ی پرِ خودی نمی‌نشیند */
    @Test
    fun `hit in own home - hitter cannot stack on own point`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            // سفید ۹، تک‌مهره‌ی سیاه روی ۶ (خانه‌ی سفید)، جفت سفید روی ۴
            placements = listOf(Place(WHITE, 9, 1), Place(BLACK, 19, 1), Place(WHITE, 4, 2)),
        )
        val hit = BgMove(from = 9, to = 6, die = 3, hit = true)
        assertTrue(engine.legalMoves(s).contains(hit))
        val after = engine.applyMove(s, hit)
        assertEquals(6, after.homeHitLockRel)
        val moves = engine.legalMoves(after)
        // زننده نمی‌تواند روی جفت خودی در ۴ بنشیند؛ مهره‌ی دیگر آزاد است
        assertTrue(moves.none { it.from == 6 && it.to == 4 })
        assertTrue(moves.contains(BgMove(from = 4, to = 2, die = 2)))
    }

    /** خودِ خانه‌ی زده‌شده باز است: مهره‌ی دیگر رویش می‌نشیند و زننده به خانه‌ی خالی می‌رود */
    @Test
    fun `hit in own home - others may stack on it and hitter may go to empty point`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            placements = listOf(Place(WHITE, 9, 1), Place(BLACK, 19, 1), Place(WHITE, 8, 1)),
        )
        val after = engine.applyMove(s, BgMove(from = 9, to = 6, die = 3, hit = true))
        val moves = engine.legalMoves(after)
        // مهره‌ی ۸ می‌تواند روی خانه‌ی زده‌شده (۶) پشته شود
        assertTrue(moves.contains(BgMove(from = 8, to = 6, die = 2)))
        // زننده به خانه‌ی خالی ۴ آزاد است — فقط خانه‌ی پرِ خودی ممنوع بود
        assertTrue(moves.contains(BgMove(from = 6, to = 4, die = 2)))
    }

    /** بند ۴ مقدم بر بند ۵: اگر قفل جلوی بازی کامل تاس‌ها را بگیرد، برداشته می‌شود */
    @Test
    fun `full-roll priority - lock lifted when full roll otherwise unplayable`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            placements = listOf(
                Place(WHITE, 9, 1),
                Place(BLACK, 19, 1), // تک‌مهره روی ۶ سفید
                Place(WHITE, 4, 2),
                Place(BLACK, 18, 2), // خانه‌ی ۷ سفید بسته — تاس ۲ از ۹ بازی نمی‌شود
                Place(BLACK, 23, 2), // خانه‌ی ۲ سفید بسته — تاس ۲ از ۴ بازی نمی‌شود
            ),
        )
        // تنها راه مصرف هر دو تاس: زدن ۹→۶ و بعد نشستن زننده روی جفت ۴
        assertEquals(listOf(BgMove(from = 9, to = 6, die = 3, hit = true)), engine.legalMoves(s))
        val after = engine.applyMove(s, BgMove(from = 9, to = 6, die = 3, hit = true))
        assertEquals(listOf(BgMove(from = 6, to = 4, die = 2)), engine.legalMoves(after))
    }

    /** زدن بیرون از خانه‌ی خودی: هیچ قفلی نیست و زننده آزادانه پشته می‌شود */
    @Test
    fun `hit outside own home - no lock, hitter may stack`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            // زدن روی ۹ (بیرون خانه‌ی ۱ تا ۶)
            placements = listOf(Place(WHITE, 12, 1), Place(BLACK, 16, 1), Place(WHITE, 7, 2)),
        )
        val after = engine.applyMove(s, BgMove(from = 12, to = 9, die = 3, hit = true))
        assertNull(after.homeHitLockRel)
        assertTrue(engine.legalMoves(after).contains(BgMove(from = 9, to = 7, die = 2)))
    }

    /** قفلِ زننده با پایان نوبت پاک می‌شود */
    @Test
    fun `home hit lock - cleared at end of turn`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            placements = listOf(Place(WHITE, 9, 1), Place(BLACK, 19, 1), Place(WHITE, 4, 2)),
        )
        val after = engine.applyMove(s, BgMove(from = 9, to = 6, die = 3, hit = true))
        assertEquals(6, after.homeHitLockRel)
        val ended = engine.endTurn(after)
        assertNull(ended.homeHitLockRel)
        assertEquals(BLACK, ended.turn)
    }

    // ================= بند ۴: بازی کامل تاس‌ها و تاس بزرگ‌تر =================

    /** وقتی از دو تاس نابرابر فقط یکی بازی می‌شود، تاس بزرگ‌تر اجباری است */
    @Test
    fun `single playable die - higher die forced`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(6, 5),
            // ادامه از ۱۳ (سفید) بسته است — بیشینه یک حرکت
            placements = listOf(Place(WHITE, 24, 1), Place(BLACK, 12, 2)),
        )
        assertEquals(listOf(BgMove(from = 24, to = 18, die = 6)), engine.legalMoves(s))
    }

    // ================= بند ۶: آخرین مهره با تاس بزرگ‌تر =================

    /** آخرین مهره: تاس بزرگ‌تر اول — خروج فوری حتی اگر تاس دوم بسوزد */
    @Test
    fun `last checker - must bear off with higher die immediately`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(6, 2),
            placements = listOf(Place(WHITE, 5, 1)),
            borneOffWhite = 14,
        )
        assertEquals(listOf(BgMove(from = 5, to = BgMove.OFF, die = 6)), engine.legalMoves(s))
        val after = engine.applyMove(s, BgMove(from = 5, to = BgMove.OFF, die = 6))
        assertEquals(BgPhase.FINISHED, after.phase)
        assertEquals(WHITE, after.winner)
    }

    /** آخرین مهره: وقتی هر دو تاس لازم‌اند هم اول تاس بزرگ‌تر بازی می‌شود */
    @Test
    fun `last checker - higher die first then bear off with the lower`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(3, 2),
            placements = listOf(Place(WHITE, 5, 1)),
            borneOffWhite = 14,
        )
        assertEquals(listOf(BgMove(from = 5, to = 2, die = 3)), engine.legalMoves(s))
        val after = engine.applyMove(s, BgMove(from = 5, to = 2, die = 3))
        assertEquals(listOf(BgMove(from = 2, to = BgMove.OFF, die = 2)), engine.legalMoves(after))
    }

    /** روش استاندارد دست‌نخورده: همان قاعده‌ی بیشینه‌ی تاس، بدون اجبار تاس بزرگ‌تر */
    @Test
    fun `last checker - standard variant keeps plain max-dice behaviour`() {
        val std = BgEngine(BgRules.STANDARD)
        val s = stateOf(
            rules = BgRules.STANDARD,
            remainingDice = listOf(6, 2),
            placements = listOf(Place(WHITE, 5, 1)),
            borneOffWhite = 14,
        )
        // استاندارد: مصرف هر دو تاس مقدم است — اول ۲ بعد خروج با ۶
        assertEquals(listOf(BgMove(from = 5, to = 3, die = 2)), std.legalMoves(s))
    }

    // ================= بند ۷: امتیازدهی تکی/مارس/سگ‌مارس =================

    /** سگ‌مارس: مهره‌ی بازنده روی بار مانده → ۳ امتیاز */
    @Test
    fun `seg mars - loser checker on the bar scores three`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(1),
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 13, 14)),
            borneOffWhite = 14,
            barBlack = 1,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(BgPhase.FINISHED, after.phase)
        assertEquals(3, after.resultScore)
    }

    /** سگ‌مارس: مهره‌ی بازنده در خانه‌ی برنده → ۳ امتیاز */
    @Test
    fun `seg mars - loser checker in winner home scores three`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(1),
            // مطلق ۳ = از دید سیاه ۲۲ — داخل خانه‌ی سفید
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 22, 1), Place(BLACK, 13, 14)),
            borneOffWhite = 14,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(3, after.resultScore)
    }

    /** مارس: بازنده هیچ مهره‌ای خارج نکرده ولی از منطقه‌ی خطر بیرون است → ۲ */
    @Test
    fun `mars - loser borne off none scores two`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(1),
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 13, 15)),
            borneOffWhite = 14,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(2, after.resultScore)
    }

    /** تکی: بازنده دست‌کم یک مهره خارج کرده — حتی با مهره در خانه‌ی برنده → ۱ */
    @Test
    fun `single - loser with any borne off scores one`() {
        val s = stateOf(
            rules = BgRules.IRANI,
            remainingDice = listOf(1),
            placements = listOf(Place(WHITE, 1, 1), Place(BLACK, 22, 1), Place(BLACK, 13, 12)),
            borneOffWhite = 14,
            borneOffBlack = 2,
        )
        val after = engine.applyMove(s, BgMove(from = 1, to = BgMove.OFF, die = 1))
        assertEquals(1, after.resultScore)
    }

    // ================= بند ۲: مکعب دوبل وجود ندارد =================

    /** در ایرانی پیشنهاد دوبل هرگز فعال نمی‌شود؛ استاندارد سرِ جایش است */
    @Test
    fun `cube - never offerable in irani`() {
        val rolling = BgEngine(BgRules.IRANI).createGame()
            .copy(turn = WHITE, phase = BgPhase.ROLLING)
        val irani = BgUiState(
            stage = BgStage.Playing,
            variant = BgVariant.IRANI,
            game = rolling,
            match = BgMatchRules.newMatch(length = 5),
        )
        assertFalse(irani.cubeAllowed)
        assertFalse(irani.canOfferDouble)

        val stdRolling = BgEngine(BgRules.STANDARD).createGame()
            .copy(turn = WHITE, phase = BgPhase.ROLLING)
        val standard = irani.copy(variant = BgVariant.STANDARD, game = stdRolling)
        assertTrue(standard.cubeAllowed)
        assertTrue(standard.canOfferDouble)
    }

    // ================= شبکه و ادامه‌ی بازی =================

    /** عکس اتاق با روش ایرانی و قفل «زننده تک می‌ماند» سالم رفت‌وبرگشت می‌شود */
    @Test
    fun `snapshot round-trip - irani variant and home hit lock survive`() {
        val game = BgEngine(BgRules.IRANI).createGame()
            .copy(turn = WHITE, phase = BgPhase.MOVING, homeHitLockRel = 6)
        val snapshot = BgRoomSnapshot(
            variant = BgVariant.IRANI,
            game = game,
            hostName = "میزبان",
            guestName = "مهمان",
            guestConnected = true,
            match = BgMatchRules.newMatch(length = 3, clockTotalMs = 120_000),
        )
        val decoded = decodeBgMessage(BgMessage.State(snapshot).encode()) as BgMessage.State
        assertEquals(snapshot, decoded.room)
        assertEquals(BgVariant.IRANI, decoded.room.variant)
        assertEquals(BgRules.IRANI, decoded.room.game?.rules)
        assertEquals(6, decoded.room.game?.homeHitLockRel)
    }
}
