package com.navidabbasian.kibord.games.uno

import com.navidabbasian.kibord.games.uno.engine.UnoBot
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoEngine
import com.navidabbasian.kibord.games.uno.engine.UnoKind
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoPhase
import com.navidabbasian.kibord.games.uno.engine.UnoRules
import com.navidabbasian.kibord.games.uno.engine.UnoSettings
import com.navidabbasian.kibord.games.uno.engine.UnoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** تست‌های موتور اونو: دسته، قانون‌سنجی، اثر برگ‌ها، هفت-صفر، انباشت، اونو و امتیازها */
class UnoEngineTest {

    private var nextId = 10_000

    private fun card(kind: UnoKind, color: UnoColor? = null, number: Int = -1): UnoCard =
        UnoCard(nextId++, kind, color, number)

    private fun num(color: UnoColor, n: Int): UnoCard = card(UnoKind.NUMBER, color, n)

    private fun freshPile(count: Int): List<UnoCard> = List(count) { num(UnoColor.RED, 5) }

    private fun state(
        hands: List<List<UnoCard>>,
        top: UnoCard,
        mode: UnoMode = UnoMode.CLASSIC,
        turn: Int = 0,
        currentColor: UnoColor? = top.color,
        direction: Int = 1,
        drawPile: List<UnoCard> = freshPile(30),
        pendingDraw: Int = 0,
        chainKind: UnoKind? = null,
        target: Int = UnoRules.DEFAULT_TARGET,
        totals: List<Int>? = null,
    ): UnoState = UnoState(
        settings = UnoSettings(mode = mode, players = hands.size, target = target),
        hands = hands,
        drawPile = drawPile,
        discard = listOf(top),
        currentColor = currentColor,
        turn = turn,
        direction = direction,
        pendingDraw = pendingDraw,
        chainKind = chainKind,
        totals = totals ?: List(hands.size) { 0 },
    )

    // ------------------------------------------------------------------
    // دسته
    // ------------------------------------------------------------------

    @Test
    fun `deck has 108 cards with exact composition`() {
        val deck = UnoEngine.buildDeck()
        assertEquals(108, deck.size)
        assertEquals(108, deck.map { it.id }.toSet().size)
        for (color in UnoColor.entries) {
            assertEquals(1, deck.count { it.color == color && it.kind == UnoKind.NUMBER && it.number == 0 })
            for (n in 1..9) {
                assertEquals(2, deck.count { it.color == color && it.kind == UnoKind.NUMBER && it.number == n })
            }
            assertEquals(2, deck.count { it.color == color && it.kind == UnoKind.SKIP })
            assertEquals(2, deck.count { it.color == color && it.kind == UnoKind.REVERSE })
            assertEquals(2, deck.count { it.color == color && it.kind == UnoKind.DRAW_TWO })
        }
        assertEquals(4, deck.count { it.kind == UnoKind.WILD })
        assertEquals(4, deck.count { it.kind == UnoKind.WILD_DRAW_FOUR })
    }

    /** پخش تازه: ۷ برگ برای همه، مگر برگ شروع «+۲» باشد که نفر اول ۹تایی می‌شود */
    private fun assertFreshDeal(s: UnoState) {
        if (s.topCard.kind == UnoKind.DRAW_TWO) {
            assertEquals(1, s.hands.count { it.size == UnoRules.HAND_SIZE + 2 })
            assertEquals(s.players - 1, s.hands.count { it.size == UnoRules.HAND_SIZE })
        } else {
            s.hands.forEach { assertEquals(UnoRules.HAND_SIZE, it.size) }
        }
    }

    @Test
    fun `new match deals 7 cards each and start card is never wild draw four`() {
        repeat(50) { seed ->
            val s = UnoEngine.newMatch(UnoSettings(players = 4), Random(seed))
            assertFreshDeal(s)
            assertTrue(s.topCard.kind != UnoKind.WILD_DRAW_FOUR)
            assertEquals(108, s.cardCount)
        }
    }

    @Test
    fun `wild start card asks first player for color`() {
        val seed = (0..5000).first { seed ->
            UnoEngine.newMatch(UnoSettings(players = 3), Random(seed)).phase == UnoPhase.CHOOSE_COLOR
        }
        val s = UnoEngine.newMatch(UnoSettings(players = 3), Random(seed))
        assertNull(s.currentColor)
        val after = UnoEngine.chooseStartColor(s, UnoColor.GREEN)
        assertEquals(UnoPhase.PLAYING, after.phase)
        assertEquals(UnoColor.GREEN, after.currentColor)
    }

    // ------------------------------------------------------------------
    // قانون‌سنجی
    // ------------------------------------------------------------------

    @Test
    fun `legality matrix on a red 5 top`() {
        val red5 = num(UnoColor.RED, 5)
        val hand = listOf(
            num(UnoColor.RED, 9),            // هم‌رنگ — قانونی
            num(UnoColor.BLUE, 5),           // هم‌عدد — قانونی
            num(UnoColor.GREEN, 2),          // هیچ‌کدام — غیرقانونی
            card(UnoKind.SKIP, UnoColor.RED),    // هم‌رنگ — قانونی
            card(UnoKind.SKIP, UnoColor.BLUE),   // نه هم‌رنگ نه هم‌نماد با عدد — غیرقانونی
            card(UnoKind.WILD),               // وایلد — همیشه قانونی
        )
        val s = state(hands = listOf(hand, listOf(num(UnoColor.RED, 1))), top = red5)
        assertTrue(UnoEngine.isLegal(s, 0, hand[0]))
        assertTrue(UnoEngine.isLegal(s, 0, hand[1]))
        assertFalse(UnoEngine.isLegal(s, 0, hand[2]))
        assertTrue(UnoEngine.isLegal(s, 0, hand[3]))
        assertFalse(UnoEngine.isLegal(s, 0, hand[4]))
        assertTrue(UnoEngine.isLegal(s, 0, hand[5]))
    }

    @Test
    fun `symbol matches symbol across colors`() {
        val topSkip = card(UnoKind.SKIP, UnoColor.RED)
        val blueSkip = card(UnoKind.SKIP, UnoColor.BLUE)
        val blueReverse = card(UnoKind.REVERSE, UnoColor.BLUE)
        val s = state(hands = listOf(listOf(blueSkip, blueReverse), listOf(num(UnoColor.RED, 1))), top = topSkip)
        assertTrue(UnoEngine.isLegal(s, 0, blueSkip))
        assertFalse(UnoEngine.isLegal(s, 0, blueReverse))
    }

    @Test
    fun `wild draw four is legal only without cards of the current color`() {
        val plus4 = card(UnoKind.WILD_DRAW_FOUR)
        val withRed = listOf(plus4, num(UnoColor.RED, 3))
        val withoutRed = listOf(card(UnoKind.WILD_DRAW_FOUR), num(UnoColor.BLUE, 3))
        val top = num(UnoColor.RED, 5)
        val s1 = state(hands = listOf(withRed, listOf(num(UnoColor.RED, 1))), top = top)
        assertFalse(UnoEngine.isLegal(s1, 0, plus4))
        val s2 = state(hands = listOf(withoutRed, listOf(num(UnoColor.RED, 1))), top = top)
        assertTrue(UnoEngine.isLegal(s2, 0, withoutRed[0]))
    }

    // ------------------------------------------------------------------
    // اثر برگ‌ها
    // ------------------------------------------------------------------

    @Test
    fun `skip jumps over the next player`() {
        val skip = card(UnoKind.SKIP, UnoColor.RED)
        val s = state(
            hands = listOf(
                listOf(skip, num(UnoColor.RED, 1)),
                listOf(num(UnoColor.RED, 2)),
                listOf(num(UnoColor.RED, 3)),
            ),
            top = num(UnoColor.RED, 5),
        )
        val after = UnoEngine.playCard(s, 0, skip)
        assertEquals(2, after.turn)
    }

    @Test
    fun `reverse flips direction with three players`() {
        val rev = card(UnoKind.REVERSE, UnoColor.RED)
        val s = state(
            hands = listOf(
                listOf(rev, num(UnoColor.RED, 1)),
                listOf(num(UnoColor.RED, 2)),
                listOf(num(UnoColor.RED, 3)),
            ),
            top = num(UnoColor.RED, 5),
        )
        val after = UnoEngine.playCard(s, 0, rev)
        assertEquals(-1, after.direction)
        assertEquals(2, after.turn)
    }

    @Test
    fun `reverse acts as skip with two players`() {
        val rev = card(UnoKind.REVERSE, UnoColor.RED)
        val s = state(
            hands = listOf(listOf(rev, num(UnoColor.RED, 1)), listOf(num(UnoColor.RED, 2))),
            top = num(UnoColor.RED, 5),
        )
        val after = UnoEngine.playCard(s, 0, rev)
        assertEquals(0, after.turn)
    }

    @Test
    fun `draw two builds a pending penalty and resolving draws and skips`() {
        val plus2 = card(UnoKind.DRAW_TWO, UnoColor.RED)
        val s = state(
            hands = listOf(
                listOf(plus2, num(UnoColor.RED, 1)),
                listOf(num(UnoColor.RED, 2)),
                listOf(num(UnoColor.RED, 3)),
            ),
            top = num(UnoColor.RED, 5),
        )
        val hit = UnoEngine.playCard(s, 0, plus2)
        assertEquals(2, hit.pendingDraw)
        assertEquals(1, hit.turn)
        val resolved = UnoEngine.resolvePendingDraw(hit)
        assertEquals(3, resolved.hands[1].size)
        assertEquals(0, resolved.pendingDraw)
        assertEquals(2, resolved.turn)
    }

    @Test
    fun `drawn playable card can be played immediately or kept`() {
        val pile = listOf(num(UnoColor.RED, 8)) + freshPile(10)
        val base = state(
            hands = listOf(listOf(num(UnoColor.BLUE, 2)), listOf(num(UnoColor.RED, 2))),
            top = num(UnoColor.RED, 5),
            drawPile = pile,
        )
        val drawn = UnoEngine.drawCard(base, 0)
        assertNotNull(drawn.drawnCard)
        assertEquals(0, drawn.turn)
        // بازی فوری
        val played = UnoEngine.playDrawn(drawn)
        assertEquals(UnoColor.RED, played.topCard.color)
        assertEquals(8, played.topCard.number)
        assertEquals(1, played.turn)
        // یا نگه داشتن
        val kept = UnoEngine.keepDrawn(drawn)
        assertNull(kept.drawnCard)
        assertEquals(2, kept.hands[0].size)
        assertEquals(1, kept.turn)
    }

    @Test
    fun `drawn unplayable card passes the turn`() {
        val pile = listOf(num(UnoColor.GREEN, 2)) + freshPile(10)
        val base = state(
            hands = listOf(listOf(num(UnoColor.BLUE, 2)), listOf(num(UnoColor.RED, 2))),
            top = num(UnoColor.RED, 5),
            drawPile = pile,
        )
        val after = UnoEngine.drawCard(base, 0)
        assertNull(after.drawnCard)
        assertEquals(2, after.hands[0].size)
        assertEquals(1, after.turn)
    }

    // ------------------------------------------------------------------
    // اونو!
    // ------------------------------------------------------------------

    @Test
    fun `playing down to one card opens the uno window and calling closes it`() {
        val red1 = num(UnoColor.RED, 1)
        val s = state(
            hands = listOf(listOf(red1, num(UnoColor.BLUE, 4)), listOf(num(UnoColor.RED, 2))),
            top = num(UnoColor.RED, 5),
        )
        val after = UnoEngine.playCard(s, 0, red1)
        assertEquals(0, after.unoPending)
        val called = UnoEngine.callUno(after, 0)
        assertNull(called.unoPending)
    }

    @Test
    fun `uncalled uno costs two penalty cards`() {
        val red1 = num(UnoColor.RED, 1)
        val s = state(
            hands = listOf(listOf(red1, num(UnoColor.BLUE, 4)), listOf(num(UnoColor.RED, 2))),
            top = num(UnoColor.RED, 5),
        )
        val after = UnoEngine.playCard(s, 0, red1)
        val caught = UnoEngine.penalizeUno(after)
        assertNull(caught.unoPending)
        assertEquals(3, caught.hands[0].size)
    }

    // ------------------------------------------------------------------
    // هفت-صفر
    // ------------------------------------------------------------------

    @Test
    fun `seven lets the player swap hands with a chosen player`() {
        val seven = num(UnoColor.RED, 7)
        val mine = listOf(seven, num(UnoColor.BLUE, 4))
        val theirs = listOf(num(UnoColor.GREEN, 1), num(UnoColor.GREEN, 2), num(UnoColor.GREEN, 3))
        val third = listOf(num(UnoColor.YELLOW, 9))
        val s = state(hands = listOf(mine, theirs, third), top = num(UnoColor.RED, 5), mode = UnoMode.SEVEN_ZERO)
        val pending = UnoEngine.playCard(s, 0, seven)
        assertEquals(UnoPhase.CHOOSE_SWAP, pending.phase)
        assertEquals(0, pending.swapSeat)
        val after = UnoEngine.chooseSwap(pending, 1)
        assertEquals(theirs, after.hands[0])
        assertEquals(listOf(mine[1]), after.hands[1])
        assertEquals(UnoPhase.PLAYING, after.phase)
        assertEquals(1, after.turn)
    }

    @Test
    fun `zero rotates all hands one step in play direction`() {
        val zero = num(UnoColor.RED, 0)
        val h0 = listOf(zero, num(UnoColor.BLUE, 4))
        val h1 = listOf(num(UnoColor.GREEN, 1), num(UnoColor.GREEN, 2))
        val h2 = listOf(num(UnoColor.YELLOW, 9))
        val s = state(hands = listOf(h0, h1, h2), top = num(UnoColor.RED, 5), mode = UnoMode.SEVEN_ZERO)
        val after = UnoEngine.playCard(s, 0, zero)
        // دستِ بعد از بازیِ صفر (بدون خود صفر) به نفر بعد می‌رود
        assertEquals(listOf(h0[1]), after.hands[1])
        assertEquals(h1, after.hands[2])
        assertEquals(h2, after.hands[0])
        assertEquals(1, after.turn)
    }

    @Test
    fun `seven and zero are plain numbers in classic mode`() {
        val seven = num(UnoColor.RED, 7)
        val h0 = listOf(seven, num(UnoColor.BLUE, 4))
        val h1 = listOf(num(UnoColor.GREEN, 1))
        val s = state(hands = listOf(h0, h1), top = num(UnoColor.RED, 5), mode = UnoMode.CLASSIC)
        val after = UnoEngine.playCard(s, 0, seven)
        assertEquals(UnoPhase.PLAYING, after.phase)
        assertNull(after.swapSeat)
        assertEquals(1, after.turn)
    }

    // ------------------------------------------------------------------
    // بی‌رحم (انباشت)
    // ------------------------------------------------------------------

    @Test
    fun `merciless stacks accumulate and the loser draws the whole pile`() {
        val a = card(UnoKind.DRAW_TWO, UnoColor.RED)
        val b = card(UnoKind.DRAW_TWO, UnoColor.BLUE)
        val s = state(
            hands = listOf(
                listOf(a, num(UnoColor.RED, 1)),
                listOf(b, num(UnoColor.GREEN, 1)),
                listOf(num(UnoColor.YELLOW, 1)),
            ),
            top = num(UnoColor.RED, 5),
            mode = UnoMode.MERCILESS,
        )
        val first = UnoEngine.playCard(s, 0, a)
        assertEquals(2, first.pendingDraw)
        // قربانی به‌جای کشیدن سوار می‌کند
        assertTrue(UnoEngine.isLegal(first, 1, b))
        val second = UnoEngine.playCard(first, 1, b)
        assertEquals(4, second.pendingDraw)
        assertEquals(2, second.turn)
        // نفر سوم چیزی ندارد: کل انباشته را می‌کشد و نوبتش می‌سوزد
        assertTrue(UnoEngine.legalPlays(second, 2).isEmpty())
        val resolved = UnoEngine.resolvePendingDraw(second)
        assertEquals(5, resolved.hands[2].size)
        assertEquals(0, resolved.pendingDraw)
        assertEquals(0, resolved.turn)
    }

    @Test
    fun `classic mode forbids stacking a draw two`() {
        val a = card(UnoKind.DRAW_TWO, UnoColor.RED)
        val b = card(UnoKind.DRAW_TWO, UnoColor.BLUE)
        val s = state(
            hands = listOf(listOf(a, num(UnoColor.RED, 1)), listOf(b, num(UnoColor.GREEN, 1))),
            top = num(UnoColor.RED, 5),
            mode = UnoMode.CLASSIC,
        )
        val hit = UnoEngine.playCard(s, 0, a)
        assertFalse(UnoEngine.isLegal(hit, 1, b))
        assertTrue(UnoEngine.legalPlays(hit, 1).isEmpty())
    }

    @Test
    fun `plus four stacks only on plus four and respects the color restriction`() {
        val first4 = card(UnoKind.WILD_DRAW_FOUR)
        val second4 = card(UnoKind.WILD_DRAW_FOUR)
        val plus2 = card(UnoKind.DRAW_TWO, UnoColor.YELLOW)
        val s = state(
            hands = listOf(
                listOf(first4, num(UnoColor.BLUE, 1)),
                listOf(second4, plus2, num(UnoColor.BLUE, 9)),
                listOf(num(UnoColor.YELLOW, 1)),
            ),
            top = num(UnoColor.RED, 5),
            mode = UnoMode.MERCILESS,
        )
        val hit = UnoEngine.playCard(s, 0, first4, chosenColor = UnoColor.GREEN)
        assertEquals(4, hit.pendingDraw)
        // ‎+۲ روی زنجیره‌ی ‎+۴ سوار نمی‌شود
        assertFalse(UnoEngine.isLegal(hit, 1, plus2))
        // ‎+۴ سوار می‌شود چون از رنگ فعال (سبز) چیزی در دست نیست
        assertTrue(UnoEngine.isLegal(hit, 1, second4))
        val stacked = UnoEngine.playCard(hit, 1, second4, chosenColor = UnoColor.BLUE)
        assertEquals(8, stacked.pendingDraw)
        val resolved = UnoEngine.resolvePendingDraw(stacked)
        assertEquals(9, resolved.hands[2].size)
    }

    // ------------------------------------------------------------------
    // امتیاز و پایان مسابقه
    // ------------------------------------------------------------------

    @Test
    fun `round scoring sums opponents hands with action and wild values`() {
        val last = num(UnoColor.RED, 3)
        val s = state(
            hands = listOf(
                listOf(last),
                listOf(num(UnoColor.GREEN, 9), card(UnoKind.SKIP, UnoColor.BLUE)),   // ۹+۲۰
                listOf(card(UnoKind.WILD), card(UnoKind.DRAW_TWO, UnoColor.RED)),    // ۵۰+۲۰
            ),
            top = num(UnoColor.RED, 5),
        )
        val over = UnoEngine.playCard(s, 0, last)
        assertEquals(UnoPhase.ROUND_OVER, over.phase)
        assertEquals(0, over.roundWinner)
        assertEquals(listOf(99, 0, 0), over.roundScores)
        assertEquals(listOf(99, 0, 0), over.totals)
        assertNull(over.matchWinner)
    }

    @Test
    fun `going out with a draw card still charges the next player before scoring`() {
        val last = card(UnoKind.DRAW_TWO, UnoColor.RED)
        val s = state(
            hands = listOf(listOf(last), listOf(num(UnoColor.GREEN, 4))),
            top = num(UnoColor.RED, 5),
        )
        val over = UnoEngine.playCard(s, 0, last)
        assertEquals(UnoPhase.ROUND_OVER, over.phase)
        assertEquals(3, over.hands[1].size)
        // ۴ + دو برگ ۵ قرمزِ کشیده‌شده از دسته‌ی تست
        assertEquals(listOf(14, 0), over.roundScores)
    }

    @Test
    fun `match ends when the winner reaches the target`() {
        val last = num(UnoColor.RED, 3)
        val s = state(
            hands = listOf(listOf(last), listOf(num(UnoColor.GREEN, 9))),
            top = num(UnoColor.RED, 5),
            target = 200,
            totals = listOf(195, 0),
        )
        val over = UnoEngine.playCard(s, 0, last)
        assertEquals(UnoPhase.MATCH_OVER, over.phase)
        assertEquals(0, over.matchWinner)
        assertEquals(listOf(204, 0), over.totals)
    }

    @Test
    fun `single hand target ends the match after one round`() {
        val last = num(UnoColor.RED, 3)
        val s = state(
            hands = listOf(listOf(last), listOf(num(UnoColor.GREEN, 9))),
            top = num(UnoColor.RED, 5),
            target = 0,
        )
        val over = UnoEngine.playCard(s, 0, last)
        assertEquals(UnoPhase.MATCH_OVER, over.phase)
        assertEquals(0, over.matchWinner)
    }

    @Test
    fun `next round keeps totals and rotates the first player`() {
        val last = num(UnoColor.RED, 3)
        val s = state(
            hands = listOf(listOf(last), listOf(num(UnoColor.GREEN, 9))),
            top = num(UnoColor.RED, 5),
            target = 500,
        )
        val over = UnoEngine.playCard(s, 0, last)
        assertEquals(UnoPhase.ROUND_OVER, over.phase)
        val next = UnoEngine.newRound(over, Random(7))
        assertEquals(over.totals, next.totals)
        assertEquals(2, next.roundNumber)
        assertEquals(108, next.cardCount)
        assertFreshDeal(next)
    }

    // ------------------------------------------------------------------
    // شبیه‌سازی ربات‌ها
    // ------------------------------------------------------------------

    /** یک قدم ربات — همان منطقی که بازی واقعی اجرا می‌کند */
    private fun botStep(s: UnoState, random: Random): UnoState = when {
        s.phase == UnoPhase.CHOOSE_COLOR ->
            UnoEngine.chooseStartColor(s, UnoBot.pickColor(s.hands[s.turn], random))

        s.phase == UnoPhase.CHOOSE_SWAP ->
            UnoEngine.chooseSwap(s, UnoBot.chooseSwapTarget(s, s.swapSeat!!))

        s.unoPending != null -> UnoEngine.callUno(s, s.unoPending!!)

        s.pendingDraw > 0 -> {
            val stack = UnoBot.chooseStack(s, s.turn, random)
            if (stack != null) {
                UnoEngine.playCard(s, s.turn, stack, if (stack.isWild) UnoBot.pickColor(s.hands[s.turn], random) else null)
            } else {
                UnoEngine.resolvePendingDraw(s)
            }
        }

        s.drawnCard != null -> {
            val card = s.drawnCard!!
            UnoEngine.playDrawn(s, if (card.isWild) UnoBot.pickColor(s.hands[s.turn], random) else null)
        }

        else -> {
            val card = UnoBot.choosePlay(s, s.turn, random)
            if (card != null) {
                UnoEngine.playCard(s, s.turn, card, if (card.isWild) UnoBot.pickColor(s.hands[s.turn], random) else null)
            } else {
                UnoEngine.drawCard(s, s.turn)
            }
        }
    }

    @Test
    fun `bots play 500 legal turns in every mode without losing a card`() {
        for (mode in UnoMode.entries) {
            val random = Random(42 + mode.ordinal)
            var s = UnoEngine.newMatch(UnoSettings(mode = mode, players = 4, target = 500), random)
            var turns = 0
            var guard = 0
            while (turns < 500 && guard++ < 20_000) {
                s = when (s.phase) {
                    UnoPhase.ROUND_OVER -> UnoEngine.newRound(s, random)
                    UnoPhase.MATCH_OVER -> UnoEngine.newMatch(UnoSettings(mode = mode, players = 2 + turns % 3, target = 500), random)
                    else -> {
                        turns++
                        botStep(s, random)
                    }
                }
                assertEquals("برگ گم شد در $mode", 108, s.cardCount)
                assertTrue(s.turn in 0 until s.players)
            }
            assertTrue("شبیه‌سازی $mode به ۵۰۰ نوبت نرسید", turns >= 500)
        }
    }
}
