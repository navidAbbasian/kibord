package com.navidabbasian.kibord.games.backgammon.engine

import kotlin.random.Random

/** منبع تاس — در تست‌ها با نسخه‌ی قطعی تزریق می‌شود */
interface DiceRoller {
    /** یک تاس شش‌وجهی: عددی از ۱ تا ۶ */
    fun roll(): Int
}

/** تاس واقعی با مولد تصادفی کاتلین */
class RandomDiceRoller(private val random: Random = Random.Default) : DiceRoller {
    override fun roll(): Int = random.nextInt(1, 7)
}
