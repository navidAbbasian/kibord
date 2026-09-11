package com.navidabbasian.kibord.games.ludo

import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoEngine
import com.navidabbasian.kibord.games.ludo.engine.LudoSeat
import com.navidabbasian.kibord.games.ludo.engine.LudoSeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LudoProtocolTest {

    @Test
    fun `state message round-trips with a full engine state`() {
        val seats = LudoColor.entries.map { c ->
            LudoSeat(c, if (c == LudoColor.BLUE) LudoSeatKind.EMPTY else LudoSeatKind.HUMAN, c.persianName)
        }
        var game = LudoEngine.newGame(seats, firstTurn = LudoColor.RED)
        game = LudoEngine.roll(game, 6)
        val move = LudoEngine.legalMoves(game).first()
        game = LudoEngine.applyMove(game, move)
        val room = LudoRoomSnapshot(
            seats = listOf(
                LudoNetSeat("میزبان", LudoNetSeatKind.HOST),
                LudoNetSeat("مهمان", LudoNetSeatKind.GUEST),
                LudoNetSeat("ربات", LudoNetSeatKind.BOT),
                LudoNetSeat(),
            ),
            started = true,
            game = game,
            rollNonce = 1,
            anim = LudoNetAnim(1, move, null),
            legalTokens = setOf(0, 2),
            message = "یه مهره‌ی روشن رو لمس کن",
        )
        val decoded = decodeLudoMessage(LudoMessage.State(room).encode()) as LudoMessage.State
        assertEquals(room, decoded.room)
    }

    @Test
    fun `commands round-trip and garbage is rejected`() {
        assertEquals(LudoMessage.Tap(2), decodeLudoMessage(LudoMessage.Tap(2).encode()))
        assertTrue(decodeLudoMessage(LudoMessage.Roll.encode()) is LudoMessage.Roll)
        assertTrue(decodeLudoMessage(LudoMessage.Continue.encode()) is LudoMessage.Continue)
        assertNull(decodeLudoMessage("{}"))
    }
}
