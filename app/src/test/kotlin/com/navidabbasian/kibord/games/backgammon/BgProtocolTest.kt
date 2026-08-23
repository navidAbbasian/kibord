package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgGameEnd
import com.navidabbasian.kibord.games.backgammon.engine.BgMatchRules
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.net.BgMessage
import com.navidabbasian.kibord.games.backgammon.net.BgRoomSnapshot
import com.navidabbasian.kibord.games.backgammon.net.decodeBgMessage
import com.navidabbasian.kibord.games.backgammon.net.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** رفت‌وبرگشت پیام‌های تازه‌ی شبکه: مسابقه، مکعب، ساعت و چت */
class BgProtocolTest {

    /** عکس اتاق با همه‌ی میدان‌های مسابقه سالم رفت‌وبرگشت می‌شود */
    @Test
    fun `state round-trip - match, cube, crawford and clocks survive`() {
        var match = BgMatchRules.newMatch(length = 5, clockTotalMs = 120_000)
        match = BgMatchRules.takeDouble(BgMatchRules.offerDouble(match, BgPlayer.WHITE))
        match = BgMatchRules.setClockRunning(match, BgPlayer.BLACK)
        match = BgMatchRules.settleClock(match, 13_000)
        match = BgMatchRules.recordGameResult(match, BgPlayer.BLACK, 2, BgGameEnd.BEAR_OFF)
        val snapshot = BgRoomSnapshot(
            variant = BgVariant.HYPER,
            game = BgEngine(BgRules.HYPER).createGame(),
            hostName = "میزبان",
            guestName = "مهمان",
            guestConnected = true,
            rematchCount = 2,
            match = match,
        )
        val decoded = decodeBgMessage(BgMessage.State(snapshot).encode()) as BgMessage.State
        assertEquals(snapshot, decoded.room)
        assertEquals(5, decoded.room.match.length)
        assertEquals(2, decoded.room.match.cubeValue)
        assertEquals(BgPlayer.BLACK, decoded.room.match.cubeOwner)
        assertEquals(107_000, decoded.room.match.clockBlackMs)
        assertEquals(4, decoded.room.match.scoreBlack)
        assertEquals(BgGameEnd.BEAR_OFF, decoded.room.match.lastGameEnd)
    }

    /** عکس قدیمی بدون میدان مسابقه هم می‌خواند: پیش‌فرض تک‌دست بدون ساعت */
    @Test
    fun `state backward-compat - missing match decodes to defaults`() {
        val legacy = """{"t":"state","room":{"variant":"STANDARD","hostName":"ali","guestName":"","guestConnected":false,"rematchCount":0}}"""
        val decoded = decodeBgMessage(legacy) as? BgMessage.State
        assertNotNull(decoded)
        assertEquals(1, decoded!!.room.match.length)
        assertEquals(0L, decoded.room.match.clockTotalMs)
        assertNull(decoded.room.match.cubeOwner)
    }

    /** پیام‌های مکعب و تسلیم و دست بعدی رفت‌وبرگشت می‌شوند */
    @Test
    fun `cube and control messages round-trip`() {
        assertTrue(decodeBgMessage(BgMessage.DoubleOffer.encode()) is BgMessage.DoubleOffer)
        val take = decodeBgMessage(BgMessage.DoubleAnswer(take = true).encode()) as BgMessage.DoubleAnswer
        assertTrue(take.take)
        val drop = decodeBgMessage(BgMessage.DoubleAnswer(take = false).encode()) as BgMessage.DoubleAnswer
        assertEquals(false, drop.take)
        assertTrue(decodeBgMessage(BgMessage.Resign.encode()) is BgMessage.Resign)
        assertTrue(decodeBgMessage(BgMessage.NextGameRequest.encode()) is BgMessage.NextGameRequest)
    }

    /** پیام چت با متن فارسی و ایموجی سالم می‌رسد */
    @Test
    fun `chat message round-trip`() {
        val msg = BgMessage.Chat(from = "بازیکن۱", text = "چه تاسی! 🎲")
        val decoded = decodeBgMessage(msg.encode()) as BgMessage.Chat
        assertEquals("بازیکن۱", decoded.from)
        assertEquals("چه تاسی! 🎲", decoded.text)
    }

    /** پیام ناشناخته یا خراب، تهی برمی‌گردد و اتصال را نمی‌شکند */
    @Test
    fun `unknown or malformed lines decode to null`() {
        assertNull(decodeBgMessage("{\"t\":\"whatever\"}"))
        assertNull(decodeBgMessage("garbage"))
    }
}
