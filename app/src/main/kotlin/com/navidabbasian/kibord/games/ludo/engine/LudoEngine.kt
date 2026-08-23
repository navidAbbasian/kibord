package com.navidabbasian.kibord.games.ludo.engine

/**
 * موتور قوانین منچ — کاتلین خالص، بدون تصادف داخلی: عدد تاس از بیرون می‌آید
 * تا تست‌ها قطعی باشند و رابط کاربری خودش انیمیشن تاس را مدیریت کند.
 *
 * خلاصه‌ی قواعد:
 * - ورود مهره از پایگاه فقط با شش؛ شش (و زدن مهره‌ی حریف) یک پرتاب اضافه می‌دهد.
 * - سه تا شش پشت سر هم (اگر فعال باشد) نوبت را می‌سوزاند.
 * - مهره ۵۱ خانه‌ی مسیر مشترک را می‌رود و بعد وارد ستون رنگی خودش می‌شود؛
 *   رسیدن به وسط فقط با عدد دقیق.
 * - یک مهره‌ی تنها‌ی حریف با فرود روی آن زده می‌شود و به پایگاه برمی‌گردد؛
 *   دو یا چند مهره‌ی هم‌رنگ روی یک خانه «سد» می‌سازند: نه زده می‌شوند، نه می‌شود رویشان فرود آمد.
 * - با گزینه‌ی «شروع امن» روی خانه‌های شروع کسی زده نمی‌شود (کنار هم می‌مانند).
 */
object LudoEngine {

    /** دست تازه؛ همه‌ی مهره‌ها در پایگاه، نوبت با firstTurn (یا اولین صندلی فعال) */
    fun newGame(
        seats: List<LudoSeat>,
        rules: LudoRules = LudoRules(),
        firstTurn: LudoColor? = null,
    ): LudoState {
        require(seats.size == LudoColor.entries.size) { "چهار صندلی لازم است" }
        require(seats.count { it.active } >= 2) { "دست‌کم دو بازیکن لازم است" }
        val first = firstTurn?.takeIf { seats[it.ordinal].active }
            ?: LudoColor.entries.first { seats[it.ordinal].active }
        return LudoState(
            seats = seats,
            rules = rules,
            tokens = List(LudoColor.entries.size) { List(TOKENS_PER_PLAYER) { BASE } },
            turn = first,
            phase = LudoPhase.ROLLING,
        )
    }

    /** پرتاب تاس با عدد معلوم؛ نتیجه: MOVING (حرکت دارد) یا PASSING (سه‌شش یا بدون حرکت) */
    fun roll(state: LudoState, value: Int): LudoState {
        require(value in 1..6) { "تاس باید ۱ تا ۶ باشد" }
        check(state.phase == LudoPhase.ROLLING) { "الان وقت تاس ریختن نیست" }
        val streak = if (value == 6) state.sixStreak + 1 else 0
        if (value == 6 && streak >= 3 && state.rules.tripleSixLosesTurn) {
            return state.copy(die = value, sixStreak = streak, phase = LudoPhase.PASSING, event = LudoEvent.TRIPLE_SIX)
        }
        val rolled = state.copy(die = value, sixStreak = streak, phase = LudoPhase.MOVING, event = LudoEvent.NONE)
        return if (legalMoves(rolled).isEmpty()) {
            rolled.copy(phase = LudoPhase.PASSING, event = LudoEvent.NO_MOVE)
        } else {
            rolled
        }
    }

    /** حرکت‌های مجاز بازیکن نوبت با تاس فعلی */
    fun legalMoves(state: LudoState): List<LudoMove> {
        if (state.phase != LudoPhase.MOVING) return emptyList()
        val die = state.die ?: return emptyList()
        val color = state.turn
        return state.tokensOf(color).mapIndexedNotNull { i, step ->
            val to = when {
                step == BASE -> if (die == 6) 0 else return@mapIndexedNotNull null
                step == GOAL -> return@mapIndexedNotNull null
                else -> step + die
            }
            if (to > GOAL) return@mapIndexedNotNull null
            val abs = color.trackIndexOf(to)
            if (abs != null && !canLand(state, color, abs)) return@mapIndexedNotNull null
            LudoMove(color = color, token = i, from = step, to = to)
        }
    }

    /** آیا رنگ color می‌تواند روی خانه‌ی مطلق abs فرود بیاید؟ (سد حریف راه را می‌بندد) */
    fun canLand(state: LudoState, color: LudoColor, abs: Int): Boolean {
        val others = state.tokensAtTrack(abs).filter { it.first != color }
        return others.groupingBy { it.first }.eachCount().values.none { it >= 2 }
    }

    /** آیا این حرکت مهره‌ی حریف را می‌زند؟ */
    fun isCapture(state: LudoState, move: LudoMove): Boolean {
        val abs = move.color.trackIndexOf(move.to) ?: return false
        if (state.rules.safeStartSquares && abs in LudoColor.startSquares) return false
        return state.tokensAtTrack(abs).any { it.first != move.color }
    }

    /** اجرای حرکت؛ زدن، رسیدن، پایان بازیکن و پرتاب اضافه یا رد شدن نوبت همین‌جا حل می‌شود */
    fun applyMove(state: LudoState, move: LudoMove): LudoState {
        check(state.phase == LudoPhase.MOVING) { "الان وقت حرکت نیست" }
        require(move in legalMoves(state)) { "حرکت مجاز نیست" }
        val color = move.color
        val tokens = state.tokens.map { it.toMutableList() }
        tokens[color.ordinal][move.token] = move.to

        var capture: LudoCapture? = null
        if (isCapture(state, move)) {
            val abs = color.trackIndexOf(move.to)!!
            state.tokensAtTrack(abs).filter { it.first != color }.forEach { (c, i) ->
                tokens[c.ordinal][i] = BASE
                capture = LudoCapture(color = c, token = i, trackIndex = abs)
            }
        }

        val event = when {
            capture != null -> LudoEvent.CAPTURED
            move.reachesGoal -> LudoEvent.REACHED_GOAL
            move.entersBoard -> LudoEvent.ENTERED
            else -> LudoEvent.MOVED
        }
        val moved = state.copy(
            tokens = tokens.map { it.toList() },
            event = event,
            lastMove = LudoLastMove(move, capture),
        )

        // همه‌ی مهره‌ها رسید؟ بازیکن تمام کرد
        if (moved.tokensOf(color).all { it == GOAL }) {
            val finished = moved.finished + color
            val remaining = moved.activeColors.count { it !in finished }
            return moved.copy(
                finished = finished,
                phase = LudoPhase.FINISHED,
                event = LudoEvent.PLAYER_FINISHED,
                gameOver = remaining <= 1,
            )
        }

        val extraRoll = state.die == 6 || capture != null
        return if (extraRoll) {
            moved.copy(phase = LudoPhase.ROLLING)
        } else {
            endTurn(moved)
        }
    }

    /** نوبت به بازیکن فعال بعدی (ساعتگرد) می‌رسد؛ شمارنده‌ی شش صفر می‌شود */
    fun endTurn(state: LudoState): LudoState {
        val next = nextPlayer(state, state.turn) ?: state.turn
        return state.copy(
            turn = next,
            phase = LudoPhase.ROLLING,
            die = null,
            sixStreak = 0,
        )
    }

    /** بعد از صفحه‌ی برنده: بقیه برای رتبه‌های بعدی ادامه می‌دهند */
    fun continueAfterFinish(state: LudoState): LudoState {
        check(state.phase == LudoPhase.FINISHED && !state.gameOver) { "ادامه ممکن نیست" }
        return endTurn(state.copy(lastMove = null))
    }

    /** بازیکن فعالِ تمام‌نکرده‌ی بعدی به ترتیب ساعتگرد */
    fun nextPlayer(state: LudoState, after: LudoColor): LudoColor? {
        val all = LudoColor.entries
        for (k in 1..all.size) {
            val c = all[(after.ordinal + k) % all.size]
            if (state.isActive(c) && !state.isFinished(c)) return c
        }
        return null
    }

    /** رده‌بندی: رسیده‌ها به ترتیب، بعد بقیه بر اساس پیشرفت */
    fun ranking(state: LudoState): List<LudoColor> {
        val rest = state.activeColors
            .filter { it !in state.finished }
            .sortedWith(compareByDescending<LudoColor> { state.progress(it) }.thenBy { it.ordinal })
        return state.finished + rest
    }

    /**
     * آیا مهره‌ی رنگ color در گام step در تیررس حریف است؟ (برای ربات)
     * فقط روی مسیر مشترک؛ سد و خانه‌ی امن در امان‌اند. پایگاه حریف هم خانه‌ی شروعش را تهدید می‌کند.
     */
    fun isThreatened(state: LudoState, color: LudoColor, step: Int): Boolean {
        val abs = color.trackIndexOf(step) ?: return false
        if (state.rules.safeStartSquares && abs in LudoColor.startSquares) return false
        if (state.tokensOf(color).count { color.trackIndexOf(it) == abs } >= 2) return false
        return LudoColor.entries.any opponents@{ opp ->
            if (opp == color || !state.isActive(opp) || state.isFinished(opp)) return@opponents false
            val fromBase = abs == opp.startIndex && state.inBase(opp) > 0
            fromBase || state.tokensOf(opp).any tokens@{ s ->
                val oppAbs = opp.trackIndexOf(s) ?: return@tokens false
                val d = (abs - oppAbs + TRACK_LENGTH) % TRACK_LENGTH
                d in 1..6 && s + d <= LAST_TRACK_STEP
            }
        }
    }
}
