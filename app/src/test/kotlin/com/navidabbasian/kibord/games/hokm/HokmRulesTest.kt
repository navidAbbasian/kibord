package com.navidabbasian.kibord.games.hokm

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.cards.sortedForHand
import com.navidabbasian.kibord.games.hokm.engine.HokmBot
import com.navidabbasian.kibord.games.hokm.engine.HokmPhase
import com.navidabbasian.kibord.games.hokm.engine.HokmRules
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import com.navidabbasian.kibord.games.hokm.engine.TrickCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** تست‌های موتور حکم: قوانین خال، برنده‌ی میز، پایان دست، کُت، چرخش حاکم، ربات */
class HokmRulesTest {

    private fun c(id: String): Card = Card.parse(id) ?: error("کارت نامعتبر $id")
    private fun cards(vararg ids: String): List<Card> = ids.map { c(it) }

    /** وضعیتی دست‌ساز در فاز بازی با دست‌های داده‌شده */
    private fun playing(
        variant: HokmVariant,
        hands: List<List<Card>>,
        trump: Suit,
        hakem: Int = 0,
        turn: Int = hakem,
        scores: List<Int> = List(variant.teamCount) { 0 },
        tricksWon: List<Int> = List(variant.playerCount) { 0 },
        target: Int = 7,
    ): HokmState = HokmState(
        variant = variant,
        target = target,
        hakem = hakem,
        phase = HokmPhase.PLAYING,
        hands = hands.map { it.sortedForHand(trump) },
        trump = trump,
        turn = turn,
        scores = scores,
        tricksWon = tricksWon,
        handNumber = 1,
    )

    /** یک دست کامل را با ربات‌ها بازی می‌کند (بدون جمع‌کردن آخرین نتیجه) */
    private fun playOutHand(start: HokmState): HokmState {
        var s = start
        var guard = 0
        while (s.phase == HokmPhase.PLAYING && guard++ < 500) {
            s = if (s.trickComplete) HokmRules.collectTrick(s)
            else HokmRules.play(s, s.turn, HokmBot.choosePlay(s, s.turn))
        }
        return s
    }

    private fun dealtState(variant: HokmVariant, seed: Int, hakem: Int = 0): HokmState {
        val r = Random(seed)
        val m = HokmRules.newMatch(variant, 7, hakem)
        val h = HokmRules.startHand(m, r)
        return HokmRules.chooseTrump(h, HokmBot.chooseTrump(h.hands[hakem]))
    }

    // ---------- پخش کارت ----------

    @Test
    fun `four player deal gives 13 cards each and hakem sees 5 first`() {
        val r = Random(1)
        val m = HokmRules.newMatch(HokmVariant.FOUR, 7, hakem = 2)
        val h = HokmRules.startHand(m, r)
        assertEquals(HokmPhase.CHOOSE_TRUMP, h.phase)
        assertEquals(5, h.hands[2].size)
        assertEquals(0, h.hands[0].size)
        val p = HokmRules.chooseTrump(h, Suit.HEARTS)
        assertEquals(HokmPhase.PLAYING, p.phase)
        assertTrue(p.hands.all { it.size == 13 })
        assertEquals(0, p.stock.size)
        assertEquals(52, p.hands.flatten().toSet().size)
        assertEquals(2, p.turn)
        // ۵ کارت اول حاکم در دست نهایی‌اش هست
        assertTrue(p.hands[2].containsAll(h.hands[2]))
    }

    @Test
    fun `two player deal gives 13 each and leaves 26 unused`() {
        val p = dealtState(HokmVariant.TWO, seed = 5)
        assertEquals(13, p.hands[0].size)
        assertEquals(13, p.hands[1].size)
        assertEquals(26, p.stock.size)
        assertEquals(52, (p.hands.flatten() + p.stock).toSet().size)
    }

    @Test
    fun `first hakem deal stops at the first ace`() {
        repeat(20) { seed ->
            val deal = HokmRules.firstHakemDeal(HokmVariant.FOUR, Random(seed))
            assertEquals(Rank.ACE, deal.cards.last().card.rank)
            assertTrue(deal.cards.dropLast(1).none { it.card.rank == Rank.ACE })
            assertEquals(deal.cards.last().seat, deal.hakem)
            // صندلی‌ها به ترتیب ۰،۱،۲،۳ می‌چرخند
            deal.cards.forEachIndexed { i, tc -> assertEquals(i % 4, tc.seat) }
        }
    }

    @Test
    fun `choose trump is rejected outside the trump phase`() {
        val p = dealtState(HokmVariant.FOUR, seed = 9)
        assertThrows(IllegalStateException::class.java) { HokmRules.chooseTrump(p, Suit.CLUBS) }
    }

    // ---------- قانون خال ----------

    @Test
    fun `must follow suit when able`() {
        val s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H5", "S9"), cards("H9", "SA"), cards("HK", "C2"), cards("HQ", "D3")),
            trump = Suit.SPADES,
        )
        val afterLead = HokmRules.play(s, 0, c("H5"))
        val legal = HokmRules.legalMoves(afterLead, 1)
        assertEquals(cards("H9"), legal)
        assertThrows(IllegalArgumentException::class.java) { HokmRules.play(afterLead, 1, c("SA")) }
    }

    @Test
    fun `any card is legal when void in led suit`() {
        val s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H5", "S9"), cards("C9", "SA"), cards("HK", "C2"), cards("HQ", "D3")),
            trump = Suit.SPADES,
        )
        val afterLead = HokmRules.play(s, 0, c("H5"))
        assertEquals(setOf(c("C9"), c("SA")), HokmRules.legalMoves(afterLead, 1).toSet())
    }

    @Test
    fun `playing out of turn is illegal`() {
        val s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H5"), cards("H9"), cards("HK"), cards("HQ")),
            trump = Suit.SPADES,
        )
        assertTrue(HokmRules.legalMoves(s, 1).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { HokmRules.play(s, 1, c("H9")) }
    }

    // ---------- برنده‌ی میز ----------

    @Test
    fun `highest of led suit wins when no trump played`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H5", "S2"), cards("H9", "S3"), cards("HK", "S4"), cards("HQ", "S5")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("H5"))
        s = HokmRules.play(s, 1, c("H9"))
        s = HokmRules.play(s, 2, c("HK"))
        s = HokmRules.play(s, 3, c("HQ"))
        assertTrue(s.trickComplete)
        assertEquals(2, s.trickLeader)
        val collected = HokmRules.collectTrick(s)
        assertEquals(1, collected.tricksWon[2])
        assertEquals(2, collected.turn)
        assertTrue(collected.trick.isEmpty())
        assertEquals(4, collected.played.size)
    }

    @Test
    fun `lowest trump beats highest of led suit`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("HA", "C2"), cards("S2", "C3"), cards("HK", "C4"), cards("HQ", "C5")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("HA"))
        s = HokmRules.play(s, 1, c("S2"))
        s = HokmRules.play(s, 2, c("HK"))
        s = HokmRules.play(s, 3, c("HQ"))
        assertEquals(1, s.trickLeader)
    }

    @Test
    fun `off-suit non-trump card never wins`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H2", "C2"), cards("DA", "C3"), cards("H3", "C4"), cards("CA", "C5")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("H2"))
        s = HokmRules.play(s, 1, c("DA"))
        s = HokmRules.play(s, 2, c("H3"))
        s = HokmRules.play(s, 3, c("CA"))
        assertEquals(2, s.trickLeader)
    }

    // ---------- پایان دست و امتیاز ----------

    @Test
    fun `hand ends as soon as a team reaches seven tricks`() {
        // تیم ۰ شش دست دارد؛ با بردن این میز دست تمام می‌شود با وجود کارت‌های باقی‌مانده
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("SA", "H2"), cards("S3", "H3"), cards("S4", "H4"), cards("S5", "H5")),
            trump = Suit.SPADES,
            tricksWon = listOf(4, 3, 2, 0),
        )
        s = HokmRules.play(s, 0, c("SA"))
        s = HokmRules.play(s, 1, c("S3"))
        s = HokmRules.play(s, 2, c("S4"))
        s = HokmRules.play(s, 3, c("S5"))
        s = HokmRules.collectTrick(s)
        assertEquals(HokmPhase.HAND_OVER, s.phase)
        val r = s.lastResult!!
        assertEquals(0, r.winnerTeam)
        assertEquals(1, r.points)
        assertFalse(r.kot)
        assertEquals(listOf(1, 0), s.scores)
        assertTrue(s.hands.any { it.isNotEmpty() })
    }

    @Test
    fun `kot seven to zero scores two points`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("SA"), cards("S3"), cards("S4"), cards("S5")),
            trump = Suit.SPADES,
            tricksWon = listOf(3, 0, 3, 0),
            hakem = 0,
        )
        s = HokmRules.play(s, 0, c("SA"))
        s = HokmRules.play(s, 1, c("S3"))
        s = HokmRules.play(s, 2, c("S4"))
        s = HokmRules.play(s, 3, c("S5"))
        s = HokmRules.collectTrick(s)
        val r = s.lastResult!!
        assertTrue(r.kot)
        assertFalse(r.hakemKot)
        assertEquals(2, r.points)
        assertEquals(listOf(2, 0), s.scores)
        // حاکم برنده ماند
        assertEquals(0, s.hakem)
        assertTrue(r.hakemTeamWon)
    }

    @Test
    fun `hakem kot scores three points and hakem passes to next seat`() {
        // حاکم صندلی ۰ (تیم ۰) است؛ تیم ۱ هفت‌هیچ می‌برد
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H2"), cards("SA"), cards("H3"), cards("H4")),
            trump = Suit.SPADES,
            tricksWon = listOf(0, 3, 0, 3),
            hakem = 0,
            turn = 1,
        )
        s = HokmRules.play(s, 1, c("SA"))
        s = HokmRules.play(s, 2, c("H3"))
        s = HokmRules.play(s, 3, c("H4"))
        s = HokmRules.play(s, 0, c("H2"))
        s = HokmRules.collectTrick(s)
        val r = s.lastResult!!
        assertEquals(1, r.winnerTeam)
        assertTrue(r.kot)
        assertTrue(r.hakemKot)
        assertEquals(3, r.points)
        assertEquals(listOf(0, 3), s.scores)
        assertEquals(1, s.hakem)
        assertEquals(1, r.hakemAfter)
        assertFalse(r.hakemTeamWon)
    }

    @Test
    fun `hakem rotates to next seat of winning team on loss and stays on win`() {
        // حاکم ۲ می‌بازد → حاکم بعدی ۳ (تیم ۱)
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H2"), cards("SA"), cards("H3"), cards("H4")),
            trump = Suit.SPADES,
            tricksWon = listOf(2, 3, 1, 3),
            hakem = 2,
            turn = 1,
        )
        s = HokmRules.play(s, 1, c("SA"))
        s = HokmRules.play(s, 2, c("H3"))
        s = HokmRules.play(s, 3, c("H4"))
        s = HokmRules.play(s, 0, c("H2"))
        s = HokmRules.collectTrick(s)
        assertEquals(3, s.hakem)
        assertEquals(1, s.lastResult!!.winnerTeam)
        assertFalse(s.lastResult!!.kot)

        // حاکم ۲ می‌برد → می‌ماند
        var w = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H2"), cards("H5"), cards("SA"), cards("H4")),
            trump = Suit.SPADES,
            tricksWon = listOf(3, 2, 3, 1),
            hakem = 2,
            turn = 2,
        )
        w = HokmRules.play(w, 2, c("SA"))
        w = HokmRules.play(w, 3, c("H4"))
        w = HokmRules.play(w, 0, c("H2"))
        w = HokmRules.play(w, 1, c("H5"))
        w = HokmRules.collectTrick(w)
        assertEquals(2, w.hakem)
        assertEquals(listOf(1, 0), w.scores)
    }

    @Test
    fun `two player hakem kot and hakem switch`() {
        var s = playing(
            HokmVariant.TWO,
            hands = listOf(cards("H2"), cards("SA")),
            trump = Suit.SPADES,
            tricksWon = listOf(0, 6),
            hakem = 0,
            turn = 1,
        )
        s = HokmRules.play(s, 1, c("SA"))
        s = HokmRules.play(s, 0, c("H2"))
        s = HokmRules.collectTrick(s)
        val r = s.lastResult!!
        assertTrue(r.kot)
        assertTrue(r.hakemKot)
        assertEquals(3, r.points)
        assertEquals(1, s.hakem)
        assertEquals(listOf(0, 3), s.scores)
    }

    @Test
    fun `match ends when a team reaches the target`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("SA"), cards("S3"), cards("S4"), cards("S5")),
            trump = Suit.SPADES,
            tricksWon = listOf(6, 0, 0, 0),
            scores = listOf(2, 1),
            target = 3,
        )
        s = HokmRules.play(s, 0, c("SA"))
        s = HokmRules.play(s, 1, c("S3"))
        s = HokmRules.play(s, 2, c("S4"))
        s = HokmRules.play(s, 3, c("S5"))
        s = HokmRules.collectTrick(s)
        assertEquals(HokmPhase.MATCH_OVER, s.phase)
        assertEquals(0, s.matchWinnerTeam)
        assertEquals(listOf(4, 1), s.scores) // کُت ۷–۰: دو امتیاز
    }

    @Test
    fun `next hand after hand over deals fresh cards with the new hakem`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H2"), cards("SA"), cards("H3"), cards("H4")),
            trump = Suit.SPADES,
            tricksWon = listOf(0, 6, 0, 0),
            hakem = 0,
            turn = 1,
        )
        s = HokmRules.play(s, 1, c("SA"))
        s = HokmRules.play(s, 2, c("H3"))
        s = HokmRules.play(s, 3, c("H4"))
        s = HokmRules.play(s, 0, c("H2"))
        s = HokmRules.collectTrick(s)
        assertEquals(HokmPhase.HAND_OVER, s.phase)
        val next = HokmRules.startHand(s, Random(4))
        assertEquals(HokmPhase.CHOOSE_TRUMP, next.phase)
        assertEquals(1, next.hakem)
        assertEquals(5, next.hands[1].size)
        assertEquals(2, next.handNumber)
        assertTrue(next.tricksWon.all { it == 0 })
        assertEquals(s.scores, next.scores)
    }

    // ---------- ربات ----------

    @Test
    fun `bot chooses the strongest suit as trump`() {
        assertEquals(Suit.HEARTS, HokmBot.chooseTrump(cards("HA", "HK", "H3", "S2", "D4")))
        assertEquals(Suit.CLUBS, HokmBot.chooseTrump(cards("C2", "C3", "C4", "HA", "SK")))
    }

    @Test
    fun `bot never plays an illegal card over many random tricks`() {
        var tricks = 0
        var seed = 0
        while (tricks < 500) {
            for (variant in listOf(HokmVariant.FOUR, HokmVariant.TWO)) {
                var s = dealtState(variant, seed = seed++, hakem = seed % variant.playerCount)
                while (s.phase == HokmPhase.PLAYING) {
                    if (s.trickComplete) {
                        s = HokmRules.collectTrick(s)
                        tricks++
                        continue
                    }
                    val card = HokmBot.choosePlay(s, s.turn)
                    assertTrue("ربات کارت غیرمجاز زد: $card", HokmRules.isLegal(s, s.turn, card))
                    s = HokmRules.play(s, s.turn, card)
                }
                assertNotNull(s.lastResult)
            }
        }
        assertTrue(tricks >= 500)
    }

    @Test
    fun `bot follows suit and wins cheaply when last to play`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("H5", "C2"), cards("H4", "C3"), cards("H9", "C4"), cards("HK", "HJ", "H7")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("H5"))
        s = HokmRules.play(s, 1, c("H4"))
        s = HokmRules.play(s, 2, c("H9"))
        // نفر آخر و حریف برنده است: سرباز دل برای بردن کافی است — نباید شاه را حرام کند
        assertEquals(c("HJ"), HokmBot.choosePlay(s, 3))
    }

    @Test
    fun `bot dumps lowest when partner is already winning as last player`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("HA", "C2"), cards("H9", "C3"), cards("HK", "H3", "C4"), cards("H4", "C5")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("HA"))
        s = HokmRules.play(s, 1, c("H9"))
        assertEquals(c("H3"), HokmBot.choosePlay(s, 2))
    }

    @Test
    fun `bot trumps when void and trick is not won by partner`() {
        var s = playing(
            HokmVariant.FOUR,
            hands = listOf(cards("HA", "C2"), cards("S2", "S9", "C3"), cards("H3", "C4"), cards("H4", "C5")),
            trump = Suit.SPADES,
        )
        s = HokmRules.play(s, 0, c("HA"))
        // کوچک‌ترین حکمی که می‌بَرد
        assertEquals(c("S2"), HokmBot.choosePlay(s, 1))
    }

    @Test
    fun `full simulated matches always finish with a winner`() {
        for (variant in listOf(HokmVariant.FOUR, HokmVariant.TWO)) {
            val r = Random(77 + variant.ordinal)
            val deal = HokmRules.firstHakemDeal(variant, r)
            var s = HokmRules.newMatch(variant, 3, deal.hakem)
            var hands = 0
            while (s.phase != HokmPhase.MATCH_OVER && hands < 60) {
                s = HokmRules.startHand(s, r)
                s = HokmRules.chooseTrump(s, HokmBot.chooseTrump(s.hands[s.hakem]))
                s = playOutHand(s)
                hands++
            }
            assertEquals(HokmPhase.MATCH_OVER, s.phase)
            assertNotNull(s.matchWinnerTeam)
            assertTrue(s.scores[s.matchWinnerTeam!!] >= 3)
        }
    }

    @Test
    fun `trick card remembers its owner`() {
        val tc = TrickCard(3, c("DA"))
        assertEquals(3, tc.seat)
        assertEquals(Suit.DIAMONDS, tc.card.suit)
    }
}
