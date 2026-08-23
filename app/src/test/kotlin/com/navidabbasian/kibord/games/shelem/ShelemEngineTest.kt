package com.navidabbasian.kibord.games.shelem

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Deck
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.games.shelem.engine.ShelemBot
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemSettings
import com.navidabbasian.kibord.games.shelem.engine.ShelemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** تست‌های موتور شلم: پخش، شرط‌بندی، ویدو، حکم، دست‌ها، امتیازدهی و ربات‌ها */
class ShelemEngineTest {

    private fun c(id: String): Card = Card.parse(id)!!

    /** شرط‌بندی را با ربات‌ها تا انتها می‌برد و حاکم را برمی‌گرداند */
    private fun botBidding(start: ShelemState, random: Random): ShelemState {
        var s = start
        var guard = 0
        while (s.phase == ShelemPhase.BIDDING && guard++ < 100) {
            val seat = s.bidTurn
            val amount = ShelemBot.decideBid(s, seat, random)
            s = if (amount == null) ShelemEngine.pass(s, seat) else ShelemEngine.bid(s, seat, amount)
        }
        return s
    }

    /** یک دستِ کامل را با ربات‌ها بازی می‌کند (از پخش تا پایان دست) */
    private fun playFullHand(start: ShelemState, random: Random, onPlay: (ShelemState, Int, Card) -> Unit = { _, _, _ -> }): ShelemState {
        var s = botBidding(start, random)
        assertEquals(ShelemPhase.DISCARDING, s.phase)
        val declarer = s.declarer!!
        val trump = ShelemBot.chooseTrump(s.hands[declarer])
        s = ShelemEngine.discard(s, ShelemBot.chooseDiscards(s.hands[declarer], trump))
        s = ShelemEngine.chooseTrump(s, trump)
        var guard = 0
        while (s.phase == ShelemPhase.PLAYING && guard++ < 200) {
            if (s.trickWinner != null) {
                s = ShelemEngine.collectTrick(s)
                continue
            }
            val seat = s.turn
            val card = ShelemBot.choosePlay(s, seat, random)
            onPlay(s, seat, card)
            s = ShelemEngine.play(s, seat, card)
        }
        return s
    }

    // ---------------- پخش ----------------

    @Test
    fun `deal - each player gets 12 cards and kitty has 4, no duplicates`() {
        val s = ShelemEngine.newMatch(random = Random(1))
        s.hands.forEach { assertEquals(12, it.size) }
        assertEquals(4, s.kitty.size)
        val all = s.hands.flatten() + s.kitty
        assertEquals(52, all.size)
        assertEquals(52, all.toSet().size)
        assertEquals(ShelemPhase.BIDDING, s.phase)
        assertEquals(ShelemRules.nextSeat(s.dealer), s.bidTurn)
    }

    @Test
    fun `points - full deck is 160 card points, hand total 165 with last trick`() {
        assertEquals(160, ShelemRules.points(Deck.full()))
        assertEquals(165, ShelemRules.HAND_TOTAL)
        assertEquals(10, ShelemRules.cardPoints(c("SA")))
        assertEquals(10, ShelemRules.cardPoints(c("H10")))
        assertEquals(5, ShelemRules.cardPoints(c("D5")))
        assertEquals(5, ShelemRules.cardPoints(c("CK")))
        assertEquals(5, ShelemRules.cardPoints(c("CQ")))
        assertEquals(5, ShelemRules.cardPoints(c("CJ")))
        assertEquals(0, ShelemRules.cardPoints(c("C9")))
        assertEquals(0, ShelemRules.cardPoints(c("C2")))
    }

    @Test
    fun `points - every hand ends with 165 total between the two teams`() {
        repeat(30) { seed ->
            val rnd = Random(seed)
            val s = playFullHand(ShelemEngine.newMatch(random = rnd), rnd)
            val r = s.handResult!!
            assertEquals("seed $seed", 165, r.declarerPoints + r.opponentPoints)
            assertEquals(160, r.trickPoints.sum() + r.kittyPoints)
            assertEquals(12, s.tricksTaken.sum())
            s.hands.forEach { assertTrue(it.isEmpty()) }
        }
    }

    // ---------------- شرط‌بندی ----------------

    @Test
    fun `bid - only 5-steps between 100 and 165 are valid`() {
        assertTrue(ShelemRules.isValidBidAmount(100))
        assertTrue(ShelemRules.isValidBidAmount(165))
        assertFalse(ShelemRules.isValidBidAmount(95))
        assertFalse(ShelemRules.isValidBidAmount(170))
        assertFalse(ShelemRules.isValidBidAmount(102))
        val s = ShelemEngine.newMatch(random = Random(3), dealer = 3)
        assertEquals(0, s.bidTurn)
        try { ShelemEngine.bid(s, 0, 103); fail("باید رد می‌شد") } catch (_: IllegalArgumentException) {}
        try { ShelemEngine.bid(s, 1, 100); fail("نوبت ۱ نیست") } catch (_: IllegalArgumentException) {}
        val s2 = ShelemEngine.bid(s, 0, 110)
        assertEquals(110, s2.highBid)
        assertEquals(0, s2.highBidder)
        assertEquals(1, s2.bidTurn)
        try { ShelemEngine.bid(s2, 1, 110); fail("باید بیشتر باشد") } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(115, 120, 125, 130, 135, 140, 145, 150, 155, 160, 165), ShelemEngine.availableBids(s2, 1))
    }

    @Test
    fun `bid - once passed you stay out and highest bidder becomes declarer`() {
        var s = ShelemEngine.newMatch(random = Random(4), dealer = 3)
        s = ShelemEngine.bid(s, 0, 100)
        s = ShelemEngine.pass(s, 1)
        s = ShelemEngine.bid(s, 2, 105)
        s = ShelemEngine.bid(s, 3, 110)
        // نوبت دوباره به ۰ می‌رسد؛ ۱ که پاس داده رد می‌شود
        assertEquals(0, s.bidTurn)
        s = ShelemEngine.bid(s, 0, 115)
        assertEquals(2, s.bidTurn)
        try { ShelemEngine.bid(s, 1, 120); fail("۱ پاس داده") } catch (_: IllegalArgumentException) {}
        s = ShelemEngine.pass(s, 2)
        s = ShelemEngine.pass(s, 3)
        assertEquals(ShelemPhase.DISCARDING, s.phase)
        assertEquals(0, s.declarer)
        assertEquals(115, s.contract)
        assertEquals(16, s.hands[0].size)
    }

    @Test
    fun `bid - 165 ends the bidding immediately`() {
        var s = ShelemEngine.newMatch(random = Random(5), dealer = 3)
        s = ShelemEngine.bid(s, 0, 165)
        assertEquals(ShelemPhase.DISCARDING, s.phase)
        assertEquals(0, s.declarer)
        assertEquals(165, s.contract)
    }

    @Test
    fun `bid - dealer is forced to take at least 100 when everyone else passes`() {
        var s = ShelemEngine.newMatch(random = Random(6), dealer = 3)
        s = ShelemEngine.pass(s, 0)
        s = ShelemEngine.pass(s, 1)
        s = ShelemEngine.pass(s, 2)
        assertEquals(3, s.bidTurn)
        assertFalse(ShelemEngine.canPass(s, 3))
        try { ShelemEngine.pass(s, 3); fail("دیلر نمی‌تواند پاس بدهد") } catch (_: IllegalArgumentException) {}
        assertEquals(ShelemRules.ALL_BIDS, ShelemEngine.availableBids(s, 3))
        // ربات دیلر هم مجبور است شرط ببندد
        val botBid = ShelemBot.decideBid(s, 3, Random(1))
        assertNotNull(botBid)
        assertTrue(botBid!! >= 100)
        s = ShelemEngine.bid(s, 3, 100)
        assertEquals(ShelemPhase.DISCARDING, s.phase)
        assertEquals(3, s.declarer)
        assertEquals(100, s.contract)
        assertTrue(s.forcedBid)
    }

    @Test
    fun `bid - bots always reach a declarer and bot bids are legal`() {
        repeat(60) { seed ->
            val rnd = Random(seed)
            var s = ShelemEngine.newMatch(random = rnd)
            var guard = 0
            while (s.phase == ShelemPhase.BIDDING && guard++ < 50) {
                val seat = s.bidTurn
                val amount = ShelemBot.decideBid(s, seat, rnd)
                if (amount == null) {
                    assertTrue("seed $seed", ShelemEngine.canPass(s, seat))
                    s = ShelemEngine.pass(s, seat)
                } else {
                    assertTrue("seed $seed bid $amount", amount in ShelemEngine.availableBids(s, seat))
                    s = ShelemEngine.bid(s, seat, amount)
                }
            }
            assertEquals(ShelemPhase.DISCARDING, s.phase)
            assertTrue(s.contract in 100..165)
        }
    }

    // ---------------- ویدو و حکم ----------------

    @Test
    fun `kitty - declarer takes 4, must discard exactly 4 own cards, then picks trump`() {
        var s = ShelemEngine.newMatch(random = Random(7), dealer = 3)
        s = ShelemEngine.bid(s, 0, 120)
        s = ShelemEngine.pass(s, 1); s = ShelemEngine.pass(s, 2); s = ShelemEngine.pass(s, 3)
        assertEquals(16, s.hands[0].size)
        assertTrue(s.hands[0].containsAll(s.kitty))
        try { ShelemEngine.discard(s, s.hands[0].take(3)); fail("۴ کارت لازم است") } catch (_: IllegalArgumentException) {}
        val notMine = s.hands[1].first()
        try { ShelemEngine.discard(s, s.hands[0].take(3) + notMine); fail("کارت حریف") } catch (_: IllegalArgumentException) {}
        val toDiscard = ShelemBot.chooseDiscards(s.hands[0], Suit.HEARTS)
        assertEquals(4, toDiscard.size)
        s = ShelemEngine.discard(s, toDiscard)
        assertEquals(ShelemPhase.TRUMP, s.phase)
        assertEquals(12, s.hands[0].size)
        assertEquals(toDiscard.toSet(), s.discarded.toSet())
        s = ShelemEngine.chooseTrump(s, Suit.HEARTS)
        assertEquals(ShelemPhase.PLAYING, s.phase)
        assertEquals(Suit.HEARTS, s.trump)
        assertEquals(0, s.turn)
        assertEquals(0, s.leader)
    }

    @Test
    fun `kitty - discarded point cards count for the declarer's team`() {
        val r = ShelemEngine.scoreHand(
            settings = ShelemSettings(),
            handNumber = 1,
            declarer = 1,
            bid = 100,
            trump = Suit.SPADES,
            trickPoints = listOf(70, 70),
            kittyPoints = 20,
            lastTrickTeam = 0,
        )
        assertEquals(1, r.declarerTeam)
        assertEquals(90, r.declarerPoints)      // ۷۰ + ۲۰ ویدو
        assertEquals(75, r.opponentPoints)      // ۷۰ + ۵ دست آخر
        assertFalse(r.made)
        assertEquals(listOf(75, -100), r.teamGain)
    }

    @Test
    fun `bot discard - never discards trump or aces when it has junk`() {
        val hand = listOf(
            "SA", "SK", "SQ", "SJ", "S9", "HA", "H10", "H3", "D2", "D4", "D7", "C3", "C6", "C8", "D10", "C5",
        ).map { c(it) }
        val d = ShelemBot.chooseDiscards(hand, Suit.SPADES)
        assertEquals(4, d.size)
        assertTrue(d.none { it.suit == Suit.SPADES })
        assertTrue(d.none { it.rank == Rank.ACE })
        assertTrue(d.all { it in hand })
    }

    // ---------------- بازی ----------------

    @Test
    fun `play - must follow suit, and trick winner leads next`() {
        // دست ساختگی: ۴ کارت دل + بقیه
        val base = ShelemEngine.newMatch(random = Random(8), dealer = 3)
        val hands = listOf(
            listOf("HA", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9", "S10", "SJ", "SQ"),
            listOf("H2", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "CJ", "CQ"),
            listOf("HK", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9", "D10", "DJ", "DQ"),
            listOf("H3", "H4", "H5", "H6", "H7", "H8", "H9", "H10", "HJ", "HQ", "SK", "SA"),
        ).map { l -> l.map { c(it) } }
        var s = base.copy(
            phase = ShelemPhase.PLAYING, hands = hands, declarer = 0, contract = 100,
            trump = Suit.CLUBS, leader = 0, turn = 0, discarded = listOf(c("CA"), c("CK"), c("DA"), c("DK")),
        )
        s = ShelemEngine.play(s, 0, c("HA"))
        // بازیکن ۱ دل دارد (H2) → نمی‌تواند گشنیز بزند
        assertEquals(listOf(c("H2")), ShelemEngine.legalPlaysFor(s, 1))
        try { ShelemEngine.play(s, 1, c("C2")); fail("باید دل بدهد") } catch (_: IllegalArgumentException) {}
        s = ShelemEngine.play(s, 1, c("H2"))
        s = ShelemEngine.play(s, 2, c("HK"))
        s = ShelemEngine.play(s, 3, c("H3"))
        assertEquals(0, s.trickWinner)
        assertEquals(4, s.trick.size)
        s = ShelemEngine.collectTrick(s)
        assertEquals(0, s.leader)
        assertEquals(0, s.turn)
        assertEquals(listOf(15, 0), s.trickPoints)
        assertEquals(listOf(1, 0), s.tricksTaken)
        assertEquals(1, s.tricksPlayed)
    }

    @Test
    fun `play - trump beats led suit`() {
        val base = ShelemEngine.newMatch(random = Random(9), dealer = 3)
        val hands = listOf(
            listOf("HA", "S2"), listOf("C2", "S3"), listOf("HK", "S4"), listOf("H3", "S5"),
        ).map { l -> l.map { c(it) } }
        var s = base.copy(
            phase = ShelemPhase.PLAYING, hands = hands, declarer = 0, contract = 100,
            trump = Suit.CLUBS, leader = 0, turn = 0, tricksPlayed = 10,
        )
        s = ShelemEngine.play(s, 0, c("HA"))
        s = ShelemEngine.play(s, 1, c("C2")) // بازیکن ۱ دل ندارد؛ با حکم می‌برد
        s = ShelemEngine.play(s, 2, c("HK"))
        s = ShelemEngine.play(s, 3, c("H3"))
        assertEquals(1, s.trickWinner)
        s = ShelemEngine.collectTrick(s)
        assertEquals(listOf(0, 15), s.trickPoints)
        assertEquals(1, s.leader)
    }

    @Test
    fun `bot play - legal over 300+ random tricks`() {
        var tricks = 0
        var seed = 100
        while (tricks < 320) {
            val rnd = Random(seed++)
            playFullHand(ShelemEngine.newMatch(random = rnd), rnd) { s, seat, card ->
                assertTrue("seed $seed seat $seat card $card", card in ShelemEngine.legalPlaysFor(s, seat))
                if (s.trick.size == 3) tricks++
            }
        }
        assertTrue(tricks >= 300)
    }

    // ---------------- امتیازدهی ----------------

    @Test
    fun `score - made bid scores actual points, not just the bid`() {
        val r = ShelemEngine.scoreHand(ShelemSettings(), 1, 0, 110, Suit.SPADES, listOf(100, 45), 15, 0)
        assertEquals(120, r.declarerPoints) // ۱۰۰ + ۱۵ ویدو + ۵ دست آخر
        assertEquals(45, r.opponentPoints)
        assertTrue(r.made)
        assertFalse(r.doubled)
        assertEquals(listOf(120, 45), r.teamGain)
    }

    @Test
    fun `score - failed bid is minus the bid, opponents keep what they took`() {
        val r = ShelemEngine.scoreHand(ShelemSettings(), 1, 2, 130, Suit.HEARTS, listOf(60, 90), 10, 1)
        assertEquals(70, r.declarerPoints)  // ۶۰ + ۱۰ ویدو
        assertEquals(95, r.opponentPoints)  // ۹۰ + ۵ دست آخر
        assertFalse(r.made)
        assertEquals(listOf(-130, 95), r.teamGain)
    }

    @Test
    fun `score - last trick bonus of 5 goes to the team that took the last trick`() {
        val a = ShelemEngine.scoreHand(ShelemSettings(), 1, 0, 100, Suit.SPADES, listOf(80, 70), 10, 0)
        assertEquals(95, a.declarerPoints)
        assertEquals(70, a.opponentPoints)
        val b = ShelemEngine.scoreHand(ShelemSettings(), 1, 0, 100, Suit.SPADES, listOf(80, 70), 10, 1)
        assertEquals(90, b.declarerPoints)
        assertEquals(75, b.opponentPoints)
    }

    @Test
    fun `score - shelem doubles when toggle on, not when off`() {
        val on = ShelemEngine.scoreHand(ShelemSettings(shelemBonus = true), 1, 1, 120, Suit.DIAMONDS, listOf(0, 150), 10, 1)
        assertTrue(on.shelem)
        assertTrue(on.doubled)
        assertEquals(listOf(0, 330), on.teamGain)
        val off = ShelemEngine.scoreHand(ShelemSettings(shelemBonus = false), 1, 1, 120, Suit.DIAMONDS, listOf(0, 150), 10, 1)
        assertTrue(off.shelem)
        assertFalse(off.doubled)
        assertEquals(listOf(0, 165), off.teamGain)
    }

    @Test
    fun `score - bidding 165 and making it doubles even with toggle off`() {
        val r = ShelemEngine.scoreHand(ShelemSettings(shelemBonus = false), 1, 0, 165, Suit.CLUBS, listOf(150, 0), 10, 0)
        assertTrue(r.made)
        assertTrue(r.doubled)
        assertEquals(listOf(330, 0), r.teamGain)
        val failed = ShelemEngine.scoreHand(ShelemSettings(), 1, 0, 165, Suit.CLUBS, listOf(140, 10), 10, 0)
        assertFalse(failed.made)
        assertEquals(listOf(-165, 10), failed.teamGain)
    }

    // ---------------- پایان مسابقه ----------------

    @Test
    fun `match - first team to target wins, tie goes to the bidder's team`() {
        assertNull(ShelemEngine.matchWinner(listOf(400, 450), 500, 0))
        assertEquals(1, ShelemEngine.matchWinner(listOf(400, 520), 500, 0))
        assertEquals(0, ShelemEngine.matchWinner(listOf(560, 520), 500, 1))
        assertEquals(1, ShelemEngine.matchWinner(listOf(530, 530), 500, 1))
        assertEquals(0, ShelemEngine.matchWinner(listOf(530, 530), 500, 0))
    }

    @Test
    fun `match - full bot match ends with a winner and scores are consistent`() {
        val rnd = Random(42)
        var s = ShelemEngine.newMatch(ShelemSettings(targetScore = 500), rnd)
        var hands = 0
        while (s.phase != ShelemPhase.MATCH_OVER && hands < 60) {
            s = playFullHand(s, rnd)
            hands++
            if (s.phase == ShelemPhase.HAND_OVER) {
                val before = s
                s = ShelemEngine.nextHand(s, rnd)
                assertEquals(ShelemRules.nextSeat(before.dealer), s.dealer)
                assertEquals(before.handNumber + 1, s.handNumber)
                assertEquals(before.scores, s.scores)
            }
        }
        assertEquals(ShelemPhase.MATCH_OVER, s.phase)
        assertNotNull(s.matchWinner)
        assertTrue(s.scores[s.matchWinner!!] >= 500)
        assertEquals(s.history.size, hands)
        // مجموع تغییرها با امتیاز نهایی می‌خواند
        assertEquals(s.history.sumOf { it.teamGain[0] }, s.scores[0])
        assertEquals(s.history.sumOf { it.teamGain[1] }, s.scores[1])
    }

    @Test
    fun `bot bidding - strong hand bids higher than a weak hand`() {
        val strong = listOf("SA", "SK", "SQ", "SJ", "S10", "S9", "HA", "HK", "DA", "CA", "C5", "D10").map { c(it) }
        val weak = listOf("S2", "H3", "D4", "C2", "S7", "H6", "D8", "C9", "S4", "H2", "D3", "C7").map { c(it) }
        val (trumpStrong, estStrong) = ShelemBot.evaluate(strong)
        val (_, estWeak) = ShelemBot.evaluate(weak)
        assertEquals(Suit.SPADES, trumpStrong)
        assertTrue("strong=$estStrong weak=$estWeak", estStrong > estWeak)
        assertTrue(estStrong >= 120)
        assertTrue(estWeak < 100)
        assertEquals(0, estStrong % 5)
    }
}
