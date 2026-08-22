package com.navidabbasian.kibord.games.backgammon.engine

/**
 * موتور مشترک تخته‌نرد: ساخت بازی، پرتاب شروع، چرخه‌ی نوبت و اعمال حرکت.
 * کاملاً خالص است — هیچ وابستگی اندرویدی ندارد و با تزریق DiceRoller
 * به‌صورت قطعی تست می‌شود. همه‌ی رفتارهای متفاوتِ سه روش از BgRules می‌آیند.
 */
class BgEngine(
    val rules: BgRules,
    private val roller: DiceRoller = RandomDiceRoller(),
) {

    /** بازی تازه با چیدمان اولیه‌ی روش انتخابی */
    fun createGame(): BgState {
        val points = MutableList(24) { BgPoint() }
        for ((rel, count) in rules.startingLayout) {
            points[relToAbs(BgPlayer.WHITE, rel) - 1] = BgPoint(BgPlayer.WHITE, count)
            points[relToAbs(BgPlayer.BLACK, rel) - 1] = BgPoint(BgPlayer.BLACK, count)
        }
        return BgState(
            rules = rules,
            points = points,
            offBoardWhite = rules.startingOffBoard,
            offBoardBlack = rules.startingOffBoard,
            phase = BgPhase.OPENING_ROLL,
        )
    }

    /**
     * پرتاب شروع: هر بازیکن یک تاس. مساوی یعنی تکرار؛ وگرنه برنده شروع می‌کند.
     * در استاندارد و هایپرگامون همان دو عدد، تاس‌های نوبت اول برنده‌اند؛
     * در هلندی برنده باید دوباره دو تاس بیندازد.
     */
    fun rollOpening(state: BgState): BgState {
        require(state.phase == BgPhase.OPENING_ROLL) { "پرتاب شروع فقط در فاز شروع مجاز است" }
        val w = roller.roll()
        val b = roller.roll()
        if (w == b) {
            return state.copy(openingDieWhite = w, openingDieBlack = b, openingTie = true)
        }
        val starter = if (w > b) BgPlayer.WHITE else BgPlayer.BLACK
        val base = state.copy(
            openingDieWhite = w,
            openingDieBlack = b,
            openingTie = false,
            turn = starter,
        )
        return if (rules.openingWinnerRerolls) {
            base.copy(phase = BgPhase.ROLLING, dice = emptyList(), remainingDice = emptyList())
        } else {
            base.copy(
                phase = BgPhase.MOVING,
                dice = listOf(w, b),
                remainingDice = listOf(w, b),
            )
        }
    }

    /** پرتاب دو تاس نوبت — جفت چهار مصرف می‌سازد */
    fun rollTurn(state: BgState): BgState {
        require(state.phase == BgPhase.ROLLING) { "الان نوبت تاس ریختن نیست" }
        val d1 = roller.roll()
        val d2 = roller.roll()
        val remaining = if (d1 == d2) List(4) { d1 } else listOf(d1, d2)
        return state.copy(dice = listOf(d1, d2), remainingDice = remaining, phase = BgPhase.MOVING)
    }

    /** حرکت‌های آغازین قانونی این لحظه */
    fun legalMoves(state: BgState): List<BgMove> = BgMoveGenerator.legalMoves(state)

    /** حرکت‌های قانونی از یک مبدأ مشخص (از دید بازیکن نوبت) */
    fun movesFrom(state: BgState, from: Int): List<BgMove> =
        legalMoves(state).filter { it.from == from }

    /** آیا بازیکن نوبت هیچ حرکتی دارد؟ */
    fun hasAnyMove(state: BgState): Boolean = BgMoveGenerator.hasAnyMove(state)

    /**
     * اعمال یک حرکت قانونی: جابه‌جایی مهره، زدن، به‌روزرسانی تاس‌های باقی‌مانده
     * و در پایان بررسی برد و محاسبه‌ی امتیاز نتیجه.
     */
    fun applyMove(state: BgState, move: BgMove): BgState {
        require(state.phase == BgPhase.MOVING) { "الان فاز حرکت نیست" }
        require(legalMoves(state).contains(move)) { "حرکت غیرقانونی است" }
        val next = applyMoveRaw(state, move)
        val p = state.turn!!
        if (next.borneOff(p) == rules.piecesPerPlayer) {
            return next.copy(
                phase = BgPhase.FINISHED,
                winner = p,
                resultScore = resultScore(next, p),
                remainingDice = emptyList(),
            )
        }
        return next
    }

    /** پایان نوبت: نوبت به حریف می‌رسد و او باید تاس بیندازد */
    fun endTurn(state: BgState): BgState {
        val p = state.turn ?: return state
        if (state.phase == BgPhase.FINISHED) return state
        return state.copy(
            turn = p.opponent,
            dice = emptyList(),
            remainingDice = emptyList(),
            phase = BgPhase.ROLLING,
        )
    }

    /**
     * امتیاز نتیجه از دید برنده:
     * تکی = ۱ (بازنده دست‌کم یک مهره خارج کرده)،
     * مارس = ۲ (بازنده هیچ مهره‌ای خارج نکرده)،
     * مارس کامل = ۳ (به‌علاوه مهره روی بار یا در خانه‌ی برنده — اگر روش داشته باشد).
     */
    fun resultScore(state: BgState, winner: BgPlayer): Int {
        val loser = winner.opponent
        if (state.borneOff(loser) > 0) return 1
        if (rules.hasBackgammonScore) {
            val loserInWinnerHome = (1..6).any { rel ->
                state.pointOf(winner, rel).owner == loser
            }
            if (state.bar(loser) > 0 || loserInWinnerHome) return 3
        }
        return 2
    }

    companion object {
        /**
         * اعمال خام حرکت بدون بررسی برد — هم موتور و هم جست‌وجوی توالی‌ها
         * از همین استفاده می‌کنند تا رفتار یکسان بماند.
         */
        internal fun applyMoveRaw(state: BgState, move: BgMove): BgState {
            val p = state.turn ?: return state
            val points = state.points.toMutableList()

            fun dec(abs: Int) {
                val pt = points[abs - 1]
                points[abs - 1] = if (pt.count <= 1) BgPoint() else pt.copy(count = pt.count - 1)
            }

            var barWhite = state.barWhite
            var barBlack = state.barBlack
            var offBoardWhite = state.offBoardWhite
            var offBoardBlack = state.offBoardBlack
            var borneOffWhite = state.borneOffWhite
            var borneOffBlack = state.borneOffBlack

            // برداشتن مهره از مبدأ: بار مقدم بر انبار واردنشده‌هاست
            if (move.from == BgMove.ENTRY) {
                if (state.bar(p) > 0) {
                    if (p == BgPlayer.WHITE) barWhite-- else barBlack--
                } else {
                    if (p == BgPlayer.WHITE) offBoardWhite-- else offBoardBlack--
                }
            } else {
                dec(relToAbs(p, move.from))
            }

            // نشاندن مهره روی مقصد یا خارج‌کردن آن
            if (move.to == BgMove.OFF) {
                if (p == BgPlayer.WHITE) borneOffWhite++ else borneOffBlack++
            } else {
                val abs = relToAbs(p, move.to)
                val pt = points[abs - 1]
                if (move.hit) {
                    // تک‌مهره‌ی حریف به بار می‌رود
                    if (p.opponent == BgPlayer.WHITE) barWhite++ else barBlack++
                    points[abs - 1] = BgPoint(p, 1)
                } else {
                    points[abs - 1] = BgPoint(p, if (pt.owner == p) pt.count + 1 else 1)
                }
            }

            // مصرف یک نمونه از تاس استفاده‌شده
            val remaining = state.remainingDice.toMutableList()
            remaining.remove(move.die)

            // رسیدن به خانه‌ی خودی — پرچم چسبنده برای فعال‌شدن زدن در هلندی
            var homeReachedWhite = state.homeReachedWhite
            var homeReachedBlack = state.homeReachedBlack
            if (move.to in 1..6) {
                if (p == BgPlayer.WHITE) homeReachedWhite = true else homeReachedBlack = true
            }

            return state.copy(
                points = points,
                barWhite = barWhite,
                barBlack = barBlack,
                offBoardWhite = offBoardWhite,
                offBoardBlack = offBoardBlack,
                borneOffWhite = borneOffWhite,
                borneOffBlack = borneOffBlack,
                remainingDice = remaining,
                homeReachedWhite = homeReachedWhite,
                homeReachedBlack = homeReachedBlack,
            )
        }
    }
}
