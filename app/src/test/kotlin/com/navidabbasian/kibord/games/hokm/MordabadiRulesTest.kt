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
import com.navidabbasian.kibord.games.hokm.engine.MordabadiRules
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * تست‌های حکم مردابادی: دسته‌ی ۵۱تایی، سهمیه‌ها و چرخش، ۱۷ دستِ کامل،
 * تسویه‌ی تراز و جمع بدهی، فاز وصول، حکم‌خواهی، حذف و دوئل پایانی.
 */
class MordabadiRulesTest {

    private fun c(id: String): Card = Card.parse(id) ?: error("کارت نامعتبر $id")
    private fun cards(vararg ids: String): List<Card> = ids.map { c(it) }

    /** وضعیت دست‌سازِ مردابادی */
    private fun mord(
        quotas: List<Int>,
        balances: List<Int> = listOf(0, 0, 0),
        totalDebts: List<Int> = listOf(0, 0, 0),
        hands: List<List<Card>> = List(3) { emptyList() },
        trump: Suit? = Suit.SPADES,
        phase: HokmPhase = HokmPhase.COLLECTION,
        tricksWon: List<Int> = listOf(0, 0, 0),
        limit: Int = 17,
        turn: Int = quotas.indexOf(9),
    ): HokmState = HokmState(
        variant = HokmVariant.THREE,
        target = limit,
        hakem = quotas.indexOf(9),
        phase = phase,
        hands = hands.map { it.sortedForHand(trump) },
        trump = trump,
        turn = turn,
        tricksWon = tricksWon,
        quotas = quotas,
        balances = balances,
        totalDebts = totalDebts,
        removedCard = Card(Suit.DIAMONDS, Rank.TWO),
        handNumber = 1,
    )

    /** فاز وصول را با سیاست ربات‌ها تا آخر می‌بَرد */
    private fun playCollection(start: HokmState): HokmState {
        var s = start
        var guard = 0
        while (s.phase == HokmPhase.COLLECTION && guard++ < 200) {
            val pick = MordabadiRules.botCollect(s)
            s = if (pick == null) MordabadiRules.advance(s) else MordabadiRules.exchange(s, pick.debtor, pick.suit).state
        }
        check(s.phase != HokmPhase.COLLECTION) { "فاز وصول تمام نشد" }
        return s
    }

    /** یک دست کامل را با ربات‌ها بازی می‌کند و مجاز بودن هر کارت را چک می‌کند */
    private fun playOut(start: HokmState): HokmState {
        var s = start
        var guard = 0
        while (s.phase == HokmPhase.PLAYING && guard++ < 300) {
            if (s.trickComplete) {
                s = HokmRules.collectTrick(s)
                continue
            }
            val card = HokmBot.choosePlay(s, s.turn)
            assertTrue("ربات کارت غیرمجاز زد: $card", HokmRules.isLegal(s, s.turn, card))
            s = HokmRules.play(s, s.turn, card)
        }
        return s
    }

    // ---------- برچسب و دسته ----------

    @Test
    fun `three player variant is labeled mordabadi`() {
        assertEquals("۳ نفره — مردابادی", HokmVariant.THREE.persian)
        assertEquals("mordabadi", HokmVariant.THREE.analyticsName)
        assertEquals(17, MordabadiRules.QUOTAS.sum())
        assertEquals(listOf(11, 17, 21), MordabadiRules.DEBT_LIMITS)
    }

    @Test
    fun `new match removes exactly one random two for the whole match`() {
        val suits = HashSet<Suit>()
        repeat(30) { seed ->
            val m = MordabadiRules.newMatch(17, Random(seed))
            val removed = m.removedCard
            assertNotNull(removed)
            assertEquals(Rank.TWO, removed!!.rank)
            suits += removed.suit
        }
        // دوِ حذف‌شده تصادفی است: در ۳۰ بازی بیش از یک خال دیده می‌شود
        assertTrue(suits.size > 1)
    }

    @Test
    fun `quotas are a random permutation of 3 5 9 and hakem holds nine`() {
        repeat(20) { seed ->
            val m = MordabadiRules.newMatch(17, Random(seed))
            assertEquals(listOf(3, 5, 9), m.quotas.sorted())
            assertEquals(9, m.quotas[m.hakem])
        }
    }

    @Test
    fun `deal gives everyone nine then seventeen from a 51 card deck`() {
        val m = MordabadiRules.newMatch(17, Random(4))
        val h = HokmRules.startHand(m, Random(4))
        // مرحله‌ی اول: همه ۹ کارت — حاکم از همین ۹ حکم می‌کند
        assertEquals(HokmPhase.CHOOSE_TRUMP, h.phase)
        assertTrue(h.hands.all { it.size == 9 })
        assertEquals(24, h.stock.size)
        val p = HokmRules.chooseTrump(h, HokmBot.chooseTrump(h.hands[h.hakem]))
        // مرحله‌ی دوم: دو دورِ ۴تایی تا ۱۷
        assertTrue(p.hands.all { it.size == 17 })
        assertEquals(0, p.stock.size)
        val all = p.hands.flatten()
        assertEquals(51, all.toSet().size)
        assertFalse(all.contains(m.removedCard))
        // دست اول کسی طلبی ندارد → وصول رد می‌شود و حاکم شروع می‌کند
        assertEquals(HokmPhase.PLAYING, p.phase)
        assertEquals(p.hakem, p.turn)
    }

    // ---------- ۱۷ دستِ کامل ----------

    @Test
    fun `hand does not stop at seven tricks`() {
        // صندلی ۰ هفت دست دارد و هنوز کارت مانده — دست ادامه دارد
        var s = mord(
            quotas = listOf(3, 5, 9),
            phase = HokmPhase.PLAYING,
            hands = listOf(cards("H5", "S2"), cards("H9", "S3"), cards("HK", "S4")),
            tricksWon = listOf(7, 5, 3),
            turn = 0,
        )
        s = HokmRules.play(s, 0, c("H5"))
        s = HokmRules.play(s, 1, c("H9"))
        s = HokmRules.play(s, 2, c("HK"))
        s = HokmRules.collectTrick(s)
        assertEquals(HokmPhase.PLAYING, s.phase)
        assertEquals(listOf(7, 5, 4), s.tricksWon)
    }

    @Test
    fun `full dealt hand plays exactly seventeen tricks`() {
        val m = MordabadiRules.newMatch(17, Random(9))
        var s = HokmRules.startHand(m, Random(9))
        s = HokmRules.chooseTrump(s, HokmBot.chooseTrump(s.hands[s.hakem]))
        s = playOut(s)
        assertEquals(HokmPhase.HAND_OVER, s.phase)
        assertEquals(17, s.lastResult!!.teamTricks.sum())
        assertEquals(0, s.lastResult!!.deltas.sum())
    }

    // ---------- تسویه ----------

    @Test
    fun `settlement adds tricks minus quota to balance and tracks accumulated debt`() {
        var s = mord(
            quotas = listOf(3, 5, 9),
            phase = HokmPhase.PLAYING,
            hands = listOf(cards("S5"), cards("S3"), cards("S4")),
            tricksWon = listOf(6, 5, 5),
            turn = 0,
        )
        s = HokmRules.play(s, 0, c("S5"))
        s = HokmRules.play(s, 1, c("S3"))
        s = HokmRules.play(s, 2, c("S4"))
        s = HokmRules.collectTrick(s)
        assertEquals(HokmPhase.HAND_OVER, s.phase)
        val r = s.lastResult!!
        assertEquals(listOf(4, 0, -4), r.deltas)
        assertEquals(listOf(4, 0, -4), s.balances)
        assertEquals(listOf(0, 0, 4), s.totalDebts)
        assertNull(r.eliminatedSeat)
    }

    @Test
    fun `settlement credits and debts always net to zero`() {
        val s = MordabadiRules.settle(
            mord(
                quotas = listOf(5, 3, 9),
                balances = listOf(1, 0, -1),
                totalDebts = listOf(0, 0, 1),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(3, 5, 9),
            ),
        )
        assertEquals(listOf(-1, 2, -1), s.balances)
        assertEquals(0, s.balances.sum())
        assertEquals(listOf(2, 0, 1), s.totalDebts)
    }

    @Test
    fun `positions rotate three to five to nine regardless of results`() {
        val s = MordabadiRules.settle(
            mord(quotas = listOf(3, 5, 9), phase = HokmPhase.PLAYING, tricksWon = listOf(3, 5, 9)),
        )
        assertEquals(listOf(5, 9, 3), s.quotas)
        // ۵دستیِ قبلی حاکم تازه است
        assertEquals(1, s.hakem)
        assertEquals(1, s.turn)
        assertEquals(1, s.lastResult!!.hakemAfter)
    }

    @Test
    fun `accumulated debt is never reduced by exchanges but balance is`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(2, -2, 0),
            totalDebts = listOf(0, 5, 3),
            hands = listOf(cards("H5", "H7", "S2"), cards("HA", "HK", "C9"), cards("C3", "H2", "D4")),
        )
        val out = MordabadiRules.exchange(s, debtor = 1, suit = Suit.HEARTS)
        assertEquals(listOf(1, -1, 0), out.state.balances)
        // جمع بدهی دست‌نخورده می‌ماند
        assertEquals(listOf(0, 5, 3), out.state.totalDebts)
    }

    // ---------- وصول ----------

    @Test
    fun `collection order is hakem then five then three quota`() {
        val s = mord(
            quotas = listOf(5, 9, 3),
            balances = listOf(1, 1, -2),
            hands = listOf(cards("H2"), cards("H3"), cards("H4", "C2")),
        )
        // صف: اول صندلی ۱ (۹دستی/حاکم)، بعد ۰ (۵دستی)، بعد ۲ (۳دستی)
        assertEquals(listOf(1, 0, 2), MordabadiRules.collectionQueue(s))
        val begun = MordabadiRules.beginCollection(s.copy(phase = HokmPhase.PLAYING))
        assertEquals(1, MordabadiRules.collector(begun))
    }

    @Test
    fun `exchange swaps collectors lowest for debtors highest of the chosen suit`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(2, -3, 1),
            hands = listOf(cards("H5", "H7", "S2"), cards("HA", "HK", "C9"), cards("C3", "H2", "D4")),
        )
        assertEquals(0, MordabadiRules.collector(s))
        val out = MordabadiRules.exchange(s, debtor = 1, suit = Suit.HEARTS)
        assertEquals(c("H5"), out.gaveCard)
        assertEquals(c("HA"), out.tookCard)
        assertFalse(out.debtorWasVoid)
        assertTrue(c("HA") in out.state.hands[0])
        assertTrue(c("H5") in out.state.hands[1])
        assertFalse(c("H5") in out.state.hands[0])
        assertEquals(listOf(1, -2, 1), out.state.balances)
        // هنوز طلب دارد → خودش در نوبت می‌ماند
        assertEquals(0, MordabadiRules.collector(out.state))

        val out2 = MordabadiRules.exchange(out.state, debtor = 1, suit = Suit.HEARTS)
        assertEquals(c("H7"), out2.gaveCard)
        assertEquals(c("HK"), out2.tookCard)
        assertEquals(listOf(0, -1, 1), out2.state.balances)
        // طلبش صفر شد → نوبت به طلبکار بعدی (۳دستی، صندلی ۲) می‌رسد
        assertEquals(2, MordabadiRules.collector(out2.state))

        val out3 = MordabadiRules.exchange(out2.state, debtor = 1, suit = Suit.CLUBS)
        assertEquals(c("C3"), out3.gaveCard)
        assertEquals(c("C9"), out3.tookCard)
        assertEquals(listOf(0, 0, 0), out3.state.balances)
        // دیگر طلبی نیست → بازی با حاکم شروع می‌شود
        assertEquals(HokmPhase.PLAYING, out3.state.phase)
        assertEquals(s.hakem, out3.state.turn)
    }

    @Test
    fun `void debtor gives any card of their own choosing`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(1, -1, 0),
            hands = listOf(cards("H5", "C4"), cards("S9", "C2"), cards("D4")),
        )
        // بدهکار دل ندارد و خودش کارت انتخاب می‌کند
        val out = MordabadiRules.exchange(s, debtor = 1, suit = Suit.HEARTS, debtorGive = c("S9"))
        assertTrue(out.debtorWasVoid)
        assertEquals(c("H5"), out.gaveCard)
        assertEquals(c("S9"), out.tookCard)
        assertTrue(c("S9") in out.state.hands[0])
        assertTrue(c("H5") in out.state.hands[1])
    }

    @Test
    fun `void bot debtor gives its worst non trump card automatically`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(1, -1, 0),
            hands = listOf(cards("H5", "C4"), cards("SA", "C2", "D9"), cards("D4")),
        )
        val out = MordabadiRules.exchange(s, debtor = 1, suit = Suit.HEARTS)
        assertTrue(out.debtorWasVoid)
        // بدترین کارت: دو گشنیز (نه آسِ حکم)
        assertEquals(c("C2"), out.tookCard)
    }

    @Test
    fun `trump demand costs three credits and clears three debts`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(4, -4, 0),
            hands = listOf(cards("S2", "S5", "H2"), cards("SA", "SK", "SQ"), cards("D4")),
        )
        assertTrue(MordabadiRules.canDemandTrump(s, 0, 1))
        val out = MordabadiRules.exchange(s, debtor = 1, suit = Suit.SPADES)
        assertTrue(out.trumpDemand)
        assertEquals(c("S2"), out.gaveCard)
        assertEquals(c("SA"), out.tookCard)
        assertEquals(listOf(1, -1, 0), out.state.balances)
    }

    @Test
    fun `trump demand needs at least three credit and three debt`() {
        val lowCredit = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(2, -4, 2),
            hands = listOf(cards("S2", "H2"), cards("SA"), cards("D4")),
        )
        assertFalse(MordabadiRules.canDemandTrump(lowCredit, 0, 1))
        assertThrows(IllegalArgumentException::class.java) {
            MordabadiRules.exchange(lowCredit, debtor = 1, suit = Suit.SPADES)
        }
        val lowDebt = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(3, -2, -1),
            hands = listOf(cards("S2", "H2"), cards("SA"), cards("D4")),
        )
        assertFalse(MordabadiRules.canDemandTrump(lowDebt, 0, 1))
        assertThrows(IllegalArgumentException::class.java) {
            MordabadiRules.exchange(lowDebt, debtor = 1, suit = Suit.SPADES)
        }
    }

    @Test
    fun `creditor cannot target a non debtor`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(2, 0, -2),
            hands = listOf(cards("H5"), cards("H9"), cards("HK")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            MordabadiRules.exchange(s, debtor = 1, suit = Suit.HEARTS)
        }
    }

    @Test
    fun `creditor must hold a card of the chosen suit`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(2, -2, 0),
            hands = listOf(cards("H5", "C4"), cards("D9", "DK"), cards("D4")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            MordabadiRules.exchange(s, debtor = 1, suit = Suit.DIAMONDS)
        }
    }

    @Test
    fun `collection is skipped entirely when nobody has credit`() {
        val s = mord(
            quotas = listOf(9, 5, 3),
            balances = listOf(0, 0, 0),
            hands = listOf(cards("H5"), cards("H9"), cards("HK")),
            phase = HokmPhase.PLAYING,
        )
        val begun = MordabadiRules.beginCollection(s)
        assertEquals(HokmPhase.PLAYING, begun.phase)
        assertEquals(s.hakem, begun.turn)
    }

    // ---------- حذف و دوئل ----------

    @Test
    fun `player is eliminated when accumulated debt reaches the limit`() {
        val below = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(13, 0, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(0, 5, 12),
                limit = 17,
            ),
        )
        assertEquals(16, below.totalDebts[0])
        assertNull(below.lastResult!!.eliminatedSeat)

        val at = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(14, 0, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(0, 5, 12),
                limit = 17,
            ),
        )
        assertEquals(17, at.totalDebts[0])
        assertEquals(0, at.lastResult!!.eliminatedSeat)
    }

    @Test
    fun `double cross eliminates the bigger debtor and ties go to the three quota seat`() {
        // هر دو رد می‌کنند؛ صندلی ۱ بدهی بیشتری دارد
        val bigger = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(8, 10, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(0, 2, 15),
                limit = 11,
            ),
        )
        assertEquals(listOf(11, 13, 0), bigger.totalDebts)
        assertEquals(1, bigger.lastResult!!.eliminatedSeat)

        // مساوی → آن که این دست ۳دستی بود (صندلی ۰)
        val tie = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(9, 9, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(0, 2, 15),
                limit = 11,
            ),
        )
        assertEquals(listOf(12, 12, 0), tie.totalDebts)
        assertEquals(0, tie.lastResult!!.eliminatedSeat)
    }

    @Test
    fun `final duel uses two player rules and decides the winner`() {
        val settled = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(3, 16, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(3, 2, 12),
                limit = 17,
            ),
        )
        assertEquals(1, settled.lastResult!!.eliminatedSeat)
        val duel = MordabadiRules.startDuel(settled, Random(11))
        // بازمانده‌ها: ۰ و ۲ — انسان صندلی ۰ دوئل می‌ماند
        assertEquals(listOf(0, 2), duel.seats)
        assertEquals(HokmVariant.TWO, duel.state.variant)
        assertEquals(1, duel.state.target)
        assertEquals(HokmPhase.CHOOSE_TRUMP, duel.state.phase)
        // حاکم دوئل: بازمانده‌ی کم‌بدهی‌تر (صندلی ۲ با صفر در برابر ۶ِ صندلی ۰)
        assertEquals(1, duel.state.hakem)

        var d = HokmRules.chooseTrump(duel.state, HokmBot.chooseTrump(duel.state.hands[duel.state.hakem]))
        assertTrue(d.hands.all { it.size == 13 })
        d = playOut(d)
        assertEquals(HokmPhase.MATCH_OVER, d.phase)
        val winner = d.matchWinnerTeam
        assertNotNull(winner)
        assertTrue(d.teamTricks(winner!!) >= 7)
    }

    @Test
    fun `duel excludes the human when the human is eliminated`() {
        val settled = MordabadiRules.settle(
            mord(
                quotas = listOf(3, 5, 9),
                totalDebts = listOf(16, 3, 0),
                phase = HokmPhase.PLAYING,
                tricksWon = listOf(2, 3, 12),
                limit = 17,
            ),
        )
        assertEquals(0, settled.lastResult!!.eliminatedSeat)
        val duel = MordabadiRules.startDuel(settled, Random(2))
        assertEquals(listOf(1, 2), duel.seats)
        assertFalse(0 in duel.seats)
    }

    // ---------- سیاست ربات ----------

    @Test
    fun `bot collector targets the biggest debtor and picks its weakest suit`() {
        val s = mord(
            quotas = listOf(3, 9, 5),
            balances = listOf(-1, 3, -2),
            hands = listOf(cards("D9"), cards("H2", "C8", "S5"), cards("HA")),
        )
        // طلبکار صندلی ۱ (حاکم) است
        assertEquals(1, MordabadiRules.collector(s))
        val pick = MordabadiRules.botCollect(s)!!
        // بدهکار بزرگ‌تر: صندلی ۲؛ ولی طلب ۳ و بدهی ۲ است → حکم‌خواهی نمی‌شود
        assertEquals(2, pick.debtor)
        // ضعیف‌ترین «پایین‌ترین کارت»: دو دل در برابر هشت گشنیز
        assertEquals(Suit.HEARTS, pick.suit)
    }

    @Test
    fun `bot with big credit and big target debt demands trump`() {
        val s = mord(
            quotas = listOf(3, 9, 5),
            balances = listOf(0, 3, -3),
            hands = listOf(cards("D9"), cards("H2", "C8", "S5"), cards("HA", "SK", "SQ")),
        )
        val pick = MordabadiRules.botCollect(s)!!
        assertEquals(2, pick.debtor)
        assertEquals(Suit.SPADES, pick.suit)
    }

    @Test
    fun `bot at quota ducks cheaply and bot below quota plays to win`() {
        val base = mord(
            quotas = listOf(9, 3, 5),
            phase = HokmPhase.PLAYING,
            hands = listOf(cards("H9", "D3"), cards("HK", "H2", "S3"), cards("H4", "C5")),
            turn = 0,
        )
        val led = HokmRules.play(base, 0, c("H9"))
        // صندلی ۱ سهمیه‌اش (۳) پر است → با دوی دل رد می‌شود
        val satisfied = led.copy(tricksWon = listOf(0, 3, 0))
        assertEquals(c("H2"), HokmBot.choosePlay(satisfied, 1))
        // زیر سهمیه → برای بردن شاه دل را می‌زند
        val hungry = led.copy(tricksWon = listOf(0, 0, 0))
        assertEquals(c("HK"), HokmBot.choosePlay(hungry, 1))
    }

    // ---------- سریال‌سازی ----------

    @Test
    fun `mordabadi state serialization round trips`() {
        val s = mord(
            quotas = listOf(5, 9, 3),
            balances = listOf(2, -3, 1),
            totalDebts = listOf(1, 6, 2),
            hands = listOf(cards("H5", "S2"), cards("HA", "C9"), cards("C3", "D4")),
            tricksWon = listOf(1, 2, 3),
            limit = 21,
        )
        val json = Json.encodeToString(s)
        val back = Json.decodeFromString<HokmState>(json)
        assertEquals(s, back)
        assertEquals(listOf(5, 9, 3), back.quotas)
        assertEquals(listOf(2, -3, 1), back.balances)
        assertEquals(listOf(1, 6, 2), back.totalDebts)
        assertEquals(Card(Suit.DIAMONDS, Rank.TWO), back.removedCard)
        assertEquals(HokmPhase.COLLECTION, back.phase)
        assertEquals(21, back.debtLimit)
    }

    // ---------- مسابقه‌ی کامل ----------

    @Test
    fun `full simulated match reaches elimination and the duel crowns a winner`() {
        val r = Random(1234)
        var s = MordabadiRules.newMatch(11, r)
        var hands = 0
        while (hands < 300) {
            s = HokmRules.startHand(s, r)
            assertTrue(s.hands.all { it.size == 9 })
            s = HokmRules.chooseTrump(s, HokmBot.chooseTrump(s.hands[s.hakem]))
            s = playCollection(s)
            assertEquals(HokmPhase.PLAYING, s.phase)
            s = playOut(s)
            assertEquals(HokmPhase.HAND_OVER, s.phase)
            assertEquals(17, s.lastResult!!.teamTricks.sum())
            assertEquals(0, s.balances.sum())
            hands++
            if (s.lastResult!!.eliminatedSeat != null) break
        }
        val eliminated = s.lastResult!!.eliminatedSeat
        assertNotNull("در ۳۰۰ دست کسی حذف نشد", eliminated)
        assertTrue(s.totalDebts[eliminated!!] >= 11)

        val duel = MordabadiRules.startDuel(s, r)
        assertFalse(eliminated in duel.seats)
        var d = HokmRules.chooseTrump(duel.state, HokmBot.chooseTrump(duel.state.hands[duel.state.hakem]))
        d = playOut(d)
        assertEquals(HokmPhase.MATCH_OVER, d.phase)
        assertNotNull(d.matchWinnerTeam)
    }
}
