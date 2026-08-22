package com.navidabbasian.kibord.games.backgammon.engine

/**
 * تولیدکننده‌ی حرکت‌های قانونی — قلب موتور مشترک هر سه روش.
 *
 * قاعده‌ی بند ۱۰ سند استاندارد این‌جا اجرا می‌شود: همه‌ی توالی‌های قانونی
 * ساخته می‌شوند و فقط توالی‌هایی می‌مانند که بیشترین تعداد تاس را مصرف می‌کنند.
 * هر دو ترتیب تاس امتحان می‌شود و تاس جفت چهار مصرف می‌سازد.
 */
object BgMoveGenerator {

    /** نتیجه‌ی فرود روی یک خانه */
    private enum class Landing { OPEN, HIT, BLOCKED }

    /** وضعیت فرود روی خانه‌ی rel از دید بازیکن p — زدن در هلندی مشروط است */
    private fun landing(state: BgState, p: BgPlayer, rel: Int): Landing {
        val pt = state.pointOf(p, rel)
        return when {
            pt.owner == null || pt.count == 0 || pt.owner == p -> Landing.OPEN
            pt.count >= 2 -> Landing.BLOCKED
            // تک‌مهره‌ی حریف: زدن فقط وقتی مجاز که قانون روش اجازه بدهد
            state.rules.canHitBeforeHomeEntry || state.homeReached(p) -> Landing.HIT
            else -> Landing.BLOCKED
        }
    }

    /** حرکت‌های ورود (از بار یا انبار هلندی) با یک تاس: فرود روی خانه‌ی حریفِ شماره‌ی تاس */
    private fun entryMoves(state: BgState, p: BgPlayer, die: Int): List<BgMove> {
        val target = 25 - die
        return when (landing(state, p, target)) {
            Landing.OPEN -> listOf(BgMove(BgMove.ENTRY, target, die, hit = false))
            Landing.HIT -> listOf(BgMove(BgMove.ENTRY, target, die, hit = true))
            Landing.BLOCKED -> emptyList()
        }
    }

    /** حرکت‌های معمولی و خارج‌کردن با یک تاس */
    private fun normalMoves(state: BgState, p: BgPlayer, die: Int): List<BgMove> {
        val moves = mutableListOf<BgMove>()
        val bearingAllowed = state.allActiveInHome(p)
        val highest = state.highestOccupied(p)
        for (rel in 1..24) {
            if (state.pointOf(p, rel).owner != p) continue
            val to = rel - die
            if (to >= 1) {
                when (landing(state, p, to)) {
                    Landing.OPEN -> moves += BgMove(rel, to, die, hit = false)
                    Landing.HIT -> moves += BgMove(rel, to, die, hit = true)
                    Landing.BLOCKED -> Unit
                }
            } else if (bearingAllowed) {
                // خروج دقیق، یا تاس بزرگ‌تر از بالاترین خانه‌ی اشغال‌شده
                if (rel == die || (die > rel && rel == highest)) {
                    moves += BgMove(rel, BgMove.OFF, die, hit = false)
                }
            }
        }
        return moves
    }

    /**
     * همه‌ی حرکت‌های قانونی این لحظه با یک تاس مشخص.
     * اولویت مطلق بار، و در هلندی اولویت ورود اولیه، همین‌جا اعمال می‌شود:
     * حرکت معمولی فقط وقتی مجاز است که با هیچ‌کدام از تاس‌های باقی‌مانده ورود ممکن نباشد.
     */
    internal fun movesForDie(state: BgState, die: Int): List<BgMove> {
        val p = state.turn ?: return emptyList()
        if (state.bar(p) > 0) return entryMoves(state, p, die)
        if (state.isEntering(p)) {
            val anyEntryPossible = state.remainingDice.distinct()
                .any { entryMoves(state, p, it).isNotEmpty() }
            return if (anyEntryPossible) entryMoves(state, p, die) else normalMoves(state, p, die)
        }
        return normalMoves(state, p, die)
    }

    /**
     * همه‌ی توالی‌های بیشینه: هر توالی فهرستی از حرکت‌هاست که با ترتیب مجاز تاس‌ها
     * ساخته شده و هیچ توالی دیگری تاس بیشتری مصرف نمی‌کند.
     * وقتی فقط یکی از دو تاسِ نابرابر قابل بازی است، طبق قانون استاندارد
     * توالی‌های تاس بزرگ‌تر نگه داشته می‌شوند.
     */
    fun maximalSequences(state: BgState): List<List<BgMove>> {
        if (state.phase != BgPhase.MOVING || state.turn == null) return emptyList()
        val all = mutableListOf<List<BgMove>>()

        fun dfs(s: BgState, prefix: List<BgMove>) {
            var extended = false
            for (die in s.remainingDice.distinct()) {
                for (move in movesForDie(s, die)) {
                    extended = true
                    dfs(BgEngine.applyMoveRaw(s, move), prefix + move)
                }
            }
            if (!extended && prefix.isNotEmpty()) all += prefix
        }
        dfs(state, emptyList())
        if (all.isEmpty()) return emptyList()

        val maxLen = all.maxOf { it.size }
        var best = all.filter { it.size == maxLen }

        // اگر از دو تاس نابرابر فقط یکی بازی می‌شود، تاس بزرگ‌تر اجباری است
        val d = state.remainingDice
        if (maxLen == 1 && d.size == 2 && d[0] != d[1]) {
            val hi = maxOf(d[0], d[1])
            val hiSeqs = best.filter { it.first().die == hi }
            if (hiSeqs.isNotEmpty()) best = hiSeqs
        }
        return best
    }

    /** حرکت‌های آغازینِ مجاز این لحظه — فقط سرِ توالی‌های بیشینه */
    fun legalMoves(state: BgState): List<BgMove> =
        maximalSequences(state).map { it.first() }.distinct()

    /** آیا بازیکنِ نوبت اصلاً حرکتی دارد؟ */
    fun hasAnyMove(state: BgState): Boolean = legalMoves(state).isNotEmpty()
}
