package com.navidabbasian.kibord.games.uno.engine

import kotlin.random.Random

/**
 * موتور خالص اونو: ساخت دسته، قانون‌سنجی، ماشین نوبت/جهت، اثر برگ‌ها
 * (رد شدن، برعکس، ‎+۲/+۴، هفت-صفر، انباشت بی‌رحم)، «اونو!» و امتیازشماری.
 * همه‌ی توابع تغییرناپذیرند و روی حرکت غیرقانونی استثنا می‌اندازند.
 */
object UnoEngine {

    // ------------------------------------------------------------------
    // دسته و شروع
    // ------------------------------------------------------------------

    /** دسته‌ی استاندارد ۱۰۸تایی با شناسه‌های یکتا */
    fun buildDeck(): List<UnoCard> {
        val cards = mutableListOf<UnoCard>()
        var id = 0
        for (color in UnoColor.entries) {
            cards += UnoCard(id++, UnoKind.NUMBER, color, 0)
            for (n in 1..9) {
                repeat(2) { cards += UnoCard(id++, UnoKind.NUMBER, color, n) }
            }
            repeat(2) { cards += UnoCard(id++, UnoKind.SKIP, color) }
            repeat(2) { cards += UnoCard(id++, UnoKind.REVERSE, color) }
            repeat(2) { cards += UnoCard(id++, UnoKind.DRAW_TWO, color) }
        }
        repeat(4) { cards += UnoCard(id++, UnoKind.WILD, null) }
        repeat(4) { cards += UnoCard(id++, UnoKind.WILD_DRAW_FOUR, null) }
        return cards
    }

    /** مسابقه‌ی تازه از تنظیمات */
    fun newMatch(settings: UnoSettings, random: Random): UnoState =
        deal(settings, List(settings.players) { 0 }, roundNumber = 1, random = random)

    /** دست بعدی با حفظ جمع امتیازها */
    fun newRound(state: UnoState, random: Random): UnoState {
        require(state.phase == UnoPhase.ROUND_OVER) { "دست هنوز تمام نشده" }
        return deal(state.settings, state.totals, state.roundNumber + 1, random)
    }

    private fun deal(settings: UnoSettings, totals: List<Int>, roundNumber: Int, random: Random): UnoState {
        while (true) {
            val deck = buildDeck().shuffled(random).toMutableList()
            val hands = List(settings.players) { seat ->
                List(UnoRules.HAND_SIZE) { i -> deck[seat * UnoRules.HAND_SIZE + i] }
            }
            repeat(settings.players * UnoRules.HAND_SIZE) { deck.removeAt(0) }
            val start = deck.removeAt(0)
            // ‎+۴ نمی‌تواند برگ شروع باشد: کل دسته دوباره بر می‌خورد
            if (start.kind == UnoKind.WILD_DRAW_FOUR) continue

            // نفر اول: هر دست یک صندلی می‌چرخد
            val first = (roundNumber - 1).mod(settings.players)
            var state = UnoState(
                settings = settings,
                hands = hands,
                drawPile = deck,
                discard = listOf(start),
                currentColor = start.color,
                turn = first,
                totals = totals,
                roundNumber = roundNumber,
            )
            // قوانین برگ شروع
            state = when (start.kind) {
                UnoKind.WILD -> state.copy(phase = UnoPhase.CHOOSE_COLOR, currentColor = null)
                UnoKind.SKIP -> state.copy(turn = state.nextSeat(first))
                UnoKind.REVERSE ->
                    if (settings.players == 2) {
                        // در دونفره برعکس مثل رد شدن است
                        state.copy(direction = -1, turn = state.nextSeat(first))
                    } else {
                        val flipped = state.copy(direction = -1)
                        flipped.copy(turn = flipped.nextSeat(first))
                    }
                UnoKind.DRAW_TWO -> {
                    val (drawn, pile, disc) = draw(state.drawPile, state.discard, 2)
                    state.copy(
                        hands = state.hands.replace(first, state.hands[first] + drawn),
                        drawPile = pile,
                        discard = disc,
                        turn = state.nextSeat(first),
                    )
                }
                else -> state
            }
            return state
        }
    }

    /** انتخاب رنگ برای برگ شروعِ «عوض رنگ» */
    fun chooseStartColor(state: UnoState, color: UnoColor): UnoState {
        require(state.phase == UnoPhase.CHOOSE_COLOR) { "الان وقت انتخاب رنگ شروع نیست" }
        return state.copy(phase = UnoPhase.PLAYING, currentColor = color)
    }

    // ------------------------------------------------------------------
    // قانون‌سنجی
    // ------------------------------------------------------------------

    /** آیا این برگ الان برای این صندلی قابل بازی است؟ */
    fun isLegal(state: UnoState, seat: Int, card: UnoCard): Boolean {
        val hand = state.hands[seat]
        // انباشت جریمه: فقط در بی‌رحم و فقط با برگ هم‌نوع زنجیره
        if (state.pendingDraw > 0) {
            if (state.settings.mode != UnoMode.MERCILESS) return false
            if (card.kind != state.chainKind) return false
            if (card.kind == UnoKind.WILD_DRAW_FOUR && !noCurrentColorInHand(state, hand)) return false
            return true
        }
        val top = state.topCard
        return when (card.kind) {
            UnoKind.WILD -> true
            // ‎+۴ فقط وقتی قانونی است که از رنگ فعال چیزی در دست نباشد
            UnoKind.WILD_DRAW_FOUR -> noCurrentColorInHand(state, hand)
            UnoKind.NUMBER ->
                card.color == state.currentColor ||
                    (top.kind == UnoKind.NUMBER && card.number == top.number)
            else -> card.color == state.currentColor || card.kind == top.kind
        }
    }

    private fun noCurrentColorInHand(state: UnoState, hand: List<UnoCard>): Boolean =
        hand.none { !it.isWild && it.color == state.currentColor }

    /** همه‌ی برگ‌های قابل بازی صندلی در این لحظه */
    fun legalPlays(state: UnoState, seat: Int): List<UnoCard> {
        if (state.phase != UnoPhase.PLAYING || state.turn != seat) return emptyList()
        if (state.drawnCard != null) {
            // بعد از کشیدن فقط همان برگ کشیده قابل بازی است
            return if (isLegal(state, seat, state.drawnCard)) listOf(state.drawnCard) else emptyList()
        }
        return state.hands[seat].filter { isLegal(state, seat, it) }
    }

    // ------------------------------------------------------------------
    // حرکت‌ها
    // ------------------------------------------------------------------

    /** بازی یک برگ از دست؛ وایلدها `chosenColor` می‌خواهند */
    fun playCard(state: UnoState, seat: Int, card: UnoCard, chosenColor: UnoColor? = null): UnoState {
        require(state.phase == UnoPhase.PLAYING) { "الان وقت بازی نیست" }
        require(state.turn == seat) { "نوبت این صندلی نیست" }
        require(state.drawnCard == null) { "اول تکلیف برگ کشیده را روشن کن" }
        require(card in state.hands[seat]) { "این برگ در دست نیست" }
        require(isLegal(state, seat, card)) { "این برگ الان قانونی نیست" }
        if (card.isWild) require(chosenColor != null) { "برای وایلد باید رنگ انتخاب شود" }
        return applyPlay(state, seat, card, chosenColor)
    }

    /** یک برگ بکش؛ اگر قابل بازی بود منتظر تصمیم می‌ماند وگرنه نوبت می‌گذرد */
    fun drawCard(state: UnoState, seat: Int): UnoState {
        require(state.phase == UnoPhase.PLAYING) { "الان وقت کشیدن نیست" }
        require(state.turn == seat) { "نوبت این صندلی نیست" }
        require(state.pendingDraw == 0) { "اول جریمه‌ی انباشته را بکش" }
        require(state.drawnCard == null) { "همین الان یک برگ کشیده‌ای" }
        val (drawn, pile, disc) = draw(state.drawPile, state.discard, 1)
        if (drawn.isEmpty()) {
            // برگی نمانده؛ نوبت می‌گذرد
            return state.copy(turn = state.nextSeat(seat), unoPending = null)
        }
        val card = drawn.first()
        val next = state.copy(
            hands = state.hands.replace(seat, state.hands[seat] + card),
            drawPile = pile,
            discard = disc,
        )
        return if (isLegal(next, seat, card)) {
            next.copy(drawnCard = card)
        } else {
            next.copy(turn = next.nextSeat(seat), unoPending = null)
        }
    }

    /** برگ تازه‌کشیده را همین حالا بازی کن */
    fun playDrawn(state: UnoState, chosenColor: UnoColor? = null): UnoState {
        val card = requireNotNull(state.drawnCard) { "برگ کشیده‌ای در کار نیست" }
        val seat = state.turn
        require(isLegal(state, seat, card)) { "برگ کشیده قانونی نیست" }
        if (card.isWild) require(chosenColor != null) { "برای وایلد باید رنگ انتخاب شود" }
        return applyPlay(state.copy(drawnCard = null), seat, card, chosenColor)
    }

    /** برگ تازه‌کشیده را نگه دار؛ نوبت می‌گذرد */
    fun keepDrawn(state: UnoState): UnoState {
        requireNotNull(state.drawnCard) { "برگ کشیده‌ای در کار نیست" }
        return state.copy(drawnCard = null, turn = state.nextSeat(state.turn), unoPending = null)
    }

    /** جریمه‌ی انباشته را بکش و نوبت را بده (کشنده نوبتش می‌سوزد) */
    fun resolvePendingDraw(state: UnoState): UnoState {
        require(state.phase == UnoPhase.PLAYING) { "الان وقت کشیدن جریمه نیست" }
        require(state.pendingDraw > 0) { "جریمه‌ای انباشته نیست" }
        val seat = state.turn
        val (drawn, pile, disc) = draw(state.drawPile, state.discard, state.pendingDraw)
        return state.copy(
            hands = state.hands.replace(seat, state.hands[seat] + drawn),
            drawPile = pile,
            discard = disc,
            pendingDraw = 0,
            chainKind = null,
            turn = state.nextSeat(seat),
            unoPending = null,
        )
    }

    /** انتخاب هم‌بازی برای تعویض دست بعد از ۷ (فقط هفت-صفر) */
    fun chooseSwap(state: UnoState, target: Int): UnoState {
        require(state.phase == UnoPhase.CHOOSE_SWAP) { "الان وقت تعویض دست نیست" }
        val seat = requireNotNull(state.swapSeat)
        require(target != seat && target in 0 until state.players) { "هم‌بازی نامعتبر" }
        val hands = state.hands.toMutableList()
        val mine = hands[seat]
        hands[seat] = hands[target]
        hands[target] = mine
        val afterSwap = state.copy(
            hands = hands,
            phase = UnoPhase.PLAYING,
            swapSeat = null,
            unoPending = if (hands[seat].size == 1) seat else null,
        )
        return afterSwap.copy(turn = afterSwap.nextSeat(seat))
    }

    /** اعلام «اونو!» — پنجره‌ی جریمه بسته می‌شود */
    fun callUno(state: UnoState, seat: Int): UnoState {
        require(state.unoPending == seat) { "اعلام اونو برای این صندلی باز نیست" }
        return state.copy(unoPending = null)
    }

    /** مچ‌گیری: بازیکنی که اونو نگفته دو برگ جریمه می‌کشد */
    fun penalizeUno(state: UnoState): UnoState {
        val seat = requireNotNull(state.unoPending) { "کسی در پنجره‌ی اونو نیست" }
        val (drawn, pile, disc) = draw(state.drawPile, state.discard, UnoRules.UNO_PENALTY)
        return state.copy(
            hands = state.hands.replace(seat, state.hands[seat] + drawn),
            drawPile = pile,
            discard = disc,
            unoPending = null,
        )
    }

    // ------------------------------------------------------------------
    // هسته‌ی اثر برگ‌ها
    // ------------------------------------------------------------------

    private fun applyPlay(state: UnoState, seat: Int, card: UnoCard, chosenColor: UnoColor?): UnoState {
        var hands = state.hands.replace(seat, state.hands[seat] - card)
        val discard = state.discard + card
        val color = chosenColor ?: card.color!!
        var direction = state.direction
        var pending = state.pendingDraw
        var chain = state.chainKind
        var steps = 1

        when (card.kind) {
            UnoKind.SKIP -> steps = 2
            UnoKind.REVERSE -> {
                direction = -direction
                // در دونفره برعکس مثل رد شدن است: نوبت به خود بازیکن برمی‌گردد
                if (state.players == 2) steps = 2
            }
            UnoKind.DRAW_TWO -> {
                pending += 2
                chain = UnoKind.DRAW_TWO
            }
            UnoKind.WILD_DRAW_FOUR -> {
                pending += 4
                chain = UnoKind.WILD_DRAW_FOUR
            }
            else -> Unit
        }

        var next = state.copy(
            hands = hands,
            discard = discard,
            currentColor = color,
            direction = direction,
            pendingDraw = pending,
            chainKind = chain,
        )

        // پایان دست: اگر برگ آخر جریمه‌دار بود، قربانی قبل از شمارش می‌کشد
        if (hands[seat].isEmpty()) {
            if (pending > 0) {
                val victim = next.nextSeat(seat)
                val (drawn, pile, disc) = draw(next.drawPile, next.discard, pending)
                next = next.copy(
                    hands = next.hands.replace(victim, next.hands[victim] + drawn),
                    drawPile = pile,
                    discard = disc,
                    pendingDraw = 0,
                    chainKind = null,
                )
            }
            return finishRound(next, seat)
        }

        // قوانین ویژه‌ی هفت-صفر
        if (state.settings.mode == UnoMode.SEVEN_ZERO && card.kind == UnoKind.NUMBER) {
            if (card.number == 7) {
                // منتظر انتخاب هم‌بازی؛ نوبت بعد از تعویض می‌گذرد
                return next.copy(
                    phase = UnoPhase.CHOOSE_SWAP,
                    swapSeat = seat,
                    unoPending = if (next.hands[seat].size == 1) seat else null,
                )
            }
            if (card.number == 0) {
                // همه‌ی دست‌ها یک قدم در جهت بازی می‌چرخند
                val rotated = List(next.players) { i -> next.hands[next.nextSeat(i, -1)] }
                hands = rotated
                next = next.copy(hands = rotated)
            }
        }

        return next.copy(
            turn = next.nextSeat(seat, steps),
            unoPending = if (hands[seat].size == 1) seat else null,
        )
    }

    // ------------------------------------------------------------------
    // امتیازها
    // ------------------------------------------------------------------

    /** ارزش یک دستِ باقی‌مانده */
    fun handPoints(hand: List<UnoCard>): Int = hand.sumOf { it.points }

    private fun finishRound(state: UnoState, winner: Int): UnoState {
        val collected = state.hands.withIndex().filter { it.index != winner }.sumOf { handPoints(it.value) }
        val roundScores = List(state.players) { if (it == winner) collected else 0 }
        val totals = state.totals.mapIndexed { i, t -> t + roundScores[i] }
        val matchWinner = when {
            state.settings.target == 0 -> winner
            totals[winner] >= state.settings.target -> winner
            else -> null
        }
        return state.copy(
            phase = if (matchWinner != null) UnoPhase.MATCH_OVER else UnoPhase.ROUND_OVER,
            roundScores = roundScores,
            roundWinner = winner,
            matchWinner = matchWinner,
            totals = totals,
            unoPending = null,
            drawnCard = null,
            swapSeat = null,
        )
    }

    // ------------------------------------------------------------------
    // ابزار
    // ------------------------------------------------------------------

    /**
     * ‎`count`‎ برگ از دسته بکش؛ اگر کم آمد، دسته‌ی رد (به‌جز برگ رو) بر می‌خورد.
     * اگر باز هم کم بود، هر چه هست می‌دهد.
     */
    private fun draw(
        drawPile: List<UnoCard>,
        discard: List<UnoCard>,
        count: Int,
    ): Triple<List<UnoCard>, List<UnoCard>, List<UnoCard>> {
        var pile = drawPile
        var disc = discard
        val taken = mutableListOf<UnoCard>()
        repeat(count) {
            if (pile.isEmpty() && disc.size > 1) {
                // بازچینش: همه‌ی ردها جز برگ رو، با ترتیب ثابت (تصادف قبلاً در بر زدن بوده)
                pile = disc.dropLast(1)
                disc = listOf(disc.last())
            }
            if (pile.isNotEmpty()) {
                taken += pile.first()
                pile = pile.drop(1)
            }
        }
        return Triple(taken, pile, disc)
    }

    private fun <T> List<T>.replace(index: Int, value: T): List<T> =
        toMutableList().also { it[index] = value }

    private operator fun List<UnoCard>.minus(card: UnoCard): List<UnoCard> =
        filterNot { it.id == card.id }
}
