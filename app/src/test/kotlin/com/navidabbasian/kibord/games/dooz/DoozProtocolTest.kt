package com.navidabbasian.kibord.games.dooz

import com.navidabbasian.kibord.games.dooz.engine.DoozBoard
import com.navidabbasian.kibord.games.dooz.engine.DoozMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoozProtocolTest {

    @Test
    fun `state message round-trips with board pattern`() {
        val board = DoozBoard().place(4, DoozMark.X).place(0, DoozMark.O)
        val room = DoozRoomSnapshot(
            hostName = "شانتی",
            guestName = "مهمان",
            guestConnected = true,
            started = true,
            targetWins = 5,
            cells = board.pattern(),
            turn = DoozMark.X,
            roundNo = 2,
            xWins = 1,
            hasResult = true,
            resultWinner = null,
            resultLine = null,
            lastMove = 0,
        )
        val line = DoozMessage.State(room).encode()
        val decoded = decodeDoozMessage(line) as DoozMessage.State
        assertEquals(room, decoded.room)
        assertEquals(board, DoozBoard.of(decoded.room.cells))
        assertEquals("O...X....", decoded.room.cells)
    }

    @Test
    fun `commands round-trip and garbage is rejected`() {
        assertEquals(DoozMessage.Tap(7), decodeDoozMessage(DoozMessage.Tap(7).encode()))
        assertTrue(decodeDoozMessage(DoozMessage.NextRound.encode()) is DoozMessage.NextRound)
        assertTrue(decodeDoozMessage(DoozMessage.PlayAgain.encode()) is DoozMessage.PlayAgain)
        assertNull(decodeDoozMessage("{not json"))
        assertNull(decodeDoozMessage("""{"t":"unknown"}"""))
    }

    @Test
    fun `empty board pattern is nine dots`() {
        assertEquals(".........", DoozBoard().pattern())
        assertEquals(DoozBoard(), DoozBoard.of("........."))
    }
}
