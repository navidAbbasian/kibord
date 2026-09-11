package com.navidabbasian.kibord.games.hokm

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.games.hokm.engine.HokmRules
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HokmProtocolTest {

    @Test
    fun `redaction keeps only my hand and the sizes of the others`() {
        val match = HokmRules.newMatch(HokmVariant.FOUR, 7, hakem = 1)
        val hand = HokmRules.startHand(match, Random(11))
        val view = hand.redactedFor(2)
        assertEquals(hand.hands[2], view.hands[2])
        (0 until 4).filter { it != 2 }.forEach { other ->
            assertEquals(hand.hands[other].size, view.hands[other].size)
            assertTrue(view.hands[other].all { it == HIDDEN_CARD })
        }
        assertEquals(hand.stock.size, view.stock.size)
        assertEquals(hand, hand.redactedFor(-1))
    }

    @Test
    fun `state and command messages round-trip`() {
        val match = HokmRules.newMatch(HokmVariant.TWO, 5, hakem = 0)
        val hand = HokmRules.startHand(match, Random(3)).redactedFor(1)
        val room = HokmRoomSnapshot(
            seats = listOf(HokmNetSeat("میزبان", HokmNetSeatKind.HOST), HokmNetSeat("مهمان", HokmNetSeatKind.GUEST)),
            variant = HokmVariant.TWO,
            started = true,
            stage = HokmStage.Playing,
            game = hand,
            exchangeFx = ExchangeFx(0, 1, null, Card(Suit.HEARTS, Rank.ACE), 42L),
            debtorPick = DebtorPick(0, 1, Suit.CLUBS, false),
        )
        val decoded = decodeHokmMessage(HokmMessage.State(room).encode()) as HokmMessage.State
        assertEquals(room, decoded.room)
        assertEquals(HokmMessage.Play(Card(Suit.SPADES, Rank.KING)), decodeHokmMessage(HokmMessage.Play(Card(Suit.SPADES, Rank.KING)).encode()))
        assertEquals(HokmMessage.Exchange(2, Suit.DIAMONDS), decodeHokmMessage(HokmMessage.Exchange(2, Suit.DIAMONDS).encode()))
        assertTrue(decodeHokmMessage(HokmMessage.NextHand.encode()) is HokmMessage.NextHand)
        assertNull(decodeHokmMessage("garbage"))
    }
}
