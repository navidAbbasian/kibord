package com.navidabbasian.kibord.games.ludo.engine

import kotlin.random.Random

/**
 * ربات ساده ولی عاقل منچ — اولویت‌ها از بالا به پایین، تساوی‌ها شانسی:
 * زدن مهره‌ی حریف › رساندن مهره به خانه › بیرون آوردن مهره با شش ›
 * فرار مهره‌ی در خطر به جای امن › پیش بردن جلوترین مهره به خانه‌ی امن ›
 * پیش بردن هر مهره‌ای به خانه‌ی امن › عقب‌ترین مهره.
 */
object LudoBot {

    fun choose(state: LudoState, moves: List<LudoMove>, random: Random = Random.Default): LudoMove? {
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        val captures = moves.filter { LudoEngine.isCapture(state, it) }
        if (captures.isNotEmpty()) return captures.random(random)

        val goals = moves.filter { it.reachesGoal }
        if (goals.isNotEmpty()) return goals.random(random)

        val exits = moves.filter { it.entersBoard }
        if (exits.isNotEmpty()) return exits.random(random)

        val safeAfter = { m: LudoMove -> !LudoEngine.isThreatened(stateAfter(state, m), m.color, m.to) }
        val escapes = moves.filter { LudoEngine.isThreatened(state, it.color, it.from) && safeAfter(it) }
        if (escapes.isNotEmpty()) return escapes.random(random)

        val safeAdvances = moves.filter(safeAfter)
        if (safeAdvances.isNotEmpty()) {
            val best = safeAdvances.maxOf { it.to }
            return safeAdvances.filter { it.to == best }.random(random)
        }

        val rear = moves.minOf { it.from }
        return moves.filter { it.from == rear }.random(random)
    }

    /** تخمین وضعیت بعد از حرکت فقط برای سنجش تهدید (بدون تغییر نوبت) */
    private fun stateAfter(state: LudoState, move: LudoMove): LudoState {
        val tokens = state.tokens.map { it.toMutableList() }
        tokens[move.color.ordinal][move.token] = move.to
        return state.copy(tokens = tokens.map { it.toList() })
    }
}
