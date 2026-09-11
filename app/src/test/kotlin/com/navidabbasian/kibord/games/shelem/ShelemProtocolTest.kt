package com.navidabbasian.kibord.games.shelem

import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShelemProtocolTest {

    @Test
    fun `redaction hides other hands and the kitty unless I am the declarer`() {
        var g = ShelemEngine.newMatch(ShelemSettings(), Random(5), dealer = 3)
        // نفر بعد از دهنده (۰) شرط می‌بندد، بقیه پاس → ۰ حاکم می‌شود و وارد خواباندن می‌شود
        g = ShelemEngine.bid(g, 0, 100)
        g = ShelemEngine.pass(g, 1)
        g = ShelemEngine.pass(g, 2)
        g = ShelemEngine.pass(g, 3)
        assertEquals(0, g.declarer)
        val mine = g.redactedFor(0)
        assertEquals(g.hands[0], mine.hands[0])
        assertEquals(g.kitty, mine.kitty)
        val other = g.redactedFor(1)
        assertEquals(g.hands[1], other.hands[1])
        assertEquals(g.hands[0].size, other.hands[0].size)
        assertNotEquals(g.hands[0], other.hands[0])
        assertEquals(g.kitty.size, other.kitty.size)
        assertTrue(other.kitty.all { it != g.kitty[0] || g.kitty.count { c -> c == it } > 1 } || other.kitty != g.kitty)
        assertEquals(g, g.redactedFor(-1))
    }

    @Test
    fun `state and command messages round-trip`() {
        val g = ShelemEngine.newMatch(ShelemSettings(targetScore = 500), Random(9)).redactedFor(2)
        val room = ShelemRoomSnapshot(
            seats = listOf(
                ShelemNetSeat("میزبان", ShelemNetSeatKind.HOST),
                ShelemNetSeat("الف", ShelemNetSeatKind.GUEST),
                ShelemNetSeat("ب", ShelemNetSeatKind.GUEST, connected = false),
                ShelemNetSeat("ربات", ShelemNetSeatKind.BOT),
            ),
            target = 500,
            started = true,
            game = g,
            bidBubbles = mapOf(1 to "120", 2 to "پاس"),
            thinkingSeat = 3,
        )
        val decoded = decodeShelemMessage(ShelemMessage.State(room).encode()) as ShelemMessage.State
        assertEquals(room, decoded.room)
        assertEquals(ShelemMessage.Bid(135), decodeShelemMessage(ShelemMessage.Bid(135).encode()))
        assertEquals(ShelemMessage.Discard(g.hands[2].take(4)), decodeShelemMessage(ShelemMessage.Discard(g.hands[2].take(4)).encode()))
        assertTrue(decodeShelemMessage(ShelemMessage.Pass.encode()) is ShelemMessage.Pass)
        assertNull(decodeShelemMessage("[]"))
    }
}
