package com.navidabbasian.kibord.games.backgammon

import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgPoint
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.DiceRoller
import com.navidabbasian.kibord.games.backgammon.engine.relToAbs

/** تاس قطعی برای تست: اعداد از صف داده‌شده برداشته می‌شوند */
class FakeDice(vararg rolls: Int) : DiceRoller {
    private val queue = ArrayDeque(rolls.toList())
    override fun roll(): Int = queue.removeFirst()
}

/** یک جای‌گذاری روی صفحه: بازیکن، خانه از دید خودش، تعداد مهره */
data class Place(val player: BgPlayer, val rel: Int, val count: Int)

/**
 * ساخت وضعیت دلخواه برای سناریوهای تست — خانه‌ها از دید هر بازیکن داده می‌شوند
 * و به اندیس مطلق صفحه ترجمه می‌شوند.
 */
fun stateOf(
    rules: BgRules,
    turn: BgPlayer = BgPlayer.WHITE,
    remainingDice: List<Int> = emptyList(),
    placements: List<Place> = emptyList(),
    barWhite: Int = 0,
    barBlack: Int = 0,
    offBoardWhite: Int = 0,
    offBoardBlack: Int = 0,
    borneOffWhite: Int = 0,
    borneOffBlack: Int = 0,
    homeReachedWhite: Boolean = false,
    homeReachedBlack: Boolean = false,
): BgState {
    val points = MutableList(24) { BgPoint() }
    for (p in placements) {
        points[relToAbs(p.player, p.rel) - 1] = BgPoint(p.player, p.count)
    }
    return BgState(
        rules = rules,
        points = points,
        barWhite = barWhite,
        barBlack = barBlack,
        offBoardWhite = offBoardWhite,
        offBoardBlack = offBoardBlack,
        borneOffWhite = borneOffWhite,
        borneOffBlack = borneOffBlack,
        turn = turn,
        dice = remainingDice,
        remainingDice = remainingDice,
        phase = BgPhase.MOVING,
        homeReachedWhite = homeReachedWhite,
        homeReachedBlack = homeReachedBlack,
    )
}
