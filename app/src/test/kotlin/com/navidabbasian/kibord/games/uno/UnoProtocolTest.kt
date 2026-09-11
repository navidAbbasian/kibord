package com.navidabbasian.kibord.games.uno

import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoEngine
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoProtocolTest {

    private val settings = UnoSettings(mode = UnoMode.SEVEN_ZERO, players = 4, target = 200)

    @Test
    fun `redaction hides other hands and the draw pile but keeps sizes`() {
        val game = UnoEngine.newMatch(settings, Random(7))
        val seat = 2
        val view = game.redactedFor(seat)
        assertEquals(game.hands[seat], view.hands[seat])
        (0 until 4).filter { it != seat }.forEach { other ->
            assertEquals(game.hands[other].size, view.hands[other].size)
            assertTrue(view.hands[other].all { it.id < 0 })
        }
        assertEquals(game.drawPile.size, view.drawPile.size)
        assertTrue(view.drawPile.all { it.id < 0 })
        assertEquals(game.discard, view.discard)
        // برگ‌های جای‌گزین همه شناسه‌ی متفاوت دارند تا کلیدهای رابط تکراری نشوند
        val hidden = view.hands.flatten().filter { it.id < 0 } + view.drawPile
        assertEquals(hidden.size, hidden.map { it.id }.toSet().size)
        // نسخه‌ی کامل برای خودِ میزبان
        assertEquals(game, game.redactedFor(-1))
    }

    @Test
    fun `state message round-trips through json`() {
        val game = UnoEngine.newMatch(settings, Random(3)).redactedFor(1)
        val room = UnoRoomSnapshot(
            seats = listOf(
                UnoNetSeat("میزبان", UnoNetSeatKind.HOST),
                UnoNetSeat("مهمان", UnoNetSeatKind.GUEST, connected = false),
                UnoNetSeat("ربات", UnoNetSeatKind.BOT),
                UnoNetSeat(),
            ),
            mode = UnoMode.SEVEN_ZERO,
            started = true,
            game = game,
            handAnim = UnoNetHandAnim(3, listOf(listOf(0, 1), listOf(1, 0))),
            toast = "سلام",
            toastId = 5,
        )
        val decoded = decodeUnoMessage(UnoMessage.State(room).encode()) as UnoMessage.State
        assertEquals(room, decoded.room)
    }

    @Test
    fun `commands round-trip and garbage is rejected`() {
        assertEquals(UnoMessage.Play(17, UnoColor.BLUE), decodeUnoMessage(UnoMessage.Play(17, UnoColor.BLUE).encode()))
        assertEquals(UnoMessage.Swap(3), decodeUnoMessage(UnoMessage.Swap(3).encode()))
        assertTrue(decodeUnoMessage(UnoMessage.CallUno.encode()) is UnoMessage.CallUno)
        assertNull(decodeUnoMessage("nope"))
    }
}
