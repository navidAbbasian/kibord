package com.navidabbasian.kibord.games.uno.engine

import kotlin.random.Random

/**
 * مغز ربات‌های اونو: همیشه از میان حرکت‌های قانونی انتخاب می‌کند،
 * رنگ پرتکرار دستش را برای وایلد برمی‌دارد و در بی‌رحم تا بتواند سوار می‌کند.
 */
object UnoBot {

    /** رنگ انتخابی وایلد: پرتکرارترین رنگ دست */
    fun pickColor(hand: List<UnoCard>, random: Random): UnoColor {
        val counts = hand.filter { !it.isWild }.groupingBy { it.color!! }.eachCount()
        val best = counts.maxByOrNull { it.value }?.key
        return best ?: UnoColor.entries[random.nextInt(UnoColor.entries.size)]
    }

    /**
     * برگ انتخابی برای بازی، یا تهی یعنی «بکش».
     * غیروایلدها مقدمند (وایلد ذخیره می‌ماند)؛ بین آن‌ها برگ پرامتیازتر زودتر خرج می‌شود.
     */
    fun choosePlay(state: UnoState, seat: Int, random: Random): UnoCard? {
        val legal = UnoEngine.legalPlays(state, seat)
        if (legal.isEmpty()) return null
        val nonWild = legal.filter { !it.isWild }
        val pool = nonWild.ifEmpty { legal }
        val maxPoints = pool.maxOf { it.points }
        val best = pool.filter { it.points == maxPoints }
        return best[random.nextInt(best.size)]
    }

    /** آیا برگ کشیده را همین حالا بازی کند؟ ربات همیشه بازی می‌کند */
    fun playsDrawn(state: UnoState): Boolean = state.drawnCard != null

    /** هدف تعویض دست بعد از ۷: کوچک‌ترین دستِ حریف */
    fun chooseSwapTarget(state: UnoState, seat: Int): Int =
        (0 until state.players).filter { it != seat }.minByOrNull { state.hands[it].size }!!

    /** در بی‌رحم: برگی برای سوار کردن روی جریمه، یا تهی یعنی «کل جریمه را بکش» */
    fun chooseStack(state: UnoState, seat: Int, random: Random): UnoCard? {
        if (state.pendingDraw == 0 || state.settings.mode != UnoMode.MERCILESS) return null
        val legal = UnoEngine.legalPlays(state, seat)
        return if (legal.isEmpty()) null else legal[random.nextInt(legal.size)]
    }
}
