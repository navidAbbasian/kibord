package com.navidabbasian.kibord.games.ludo.engine

/** مختصات یک خانه در شبکه‌ی ۱۵×۱۵ صفحه — اعداد اعشاری برای مرکزِ جایگاه‌های پایگاه و خانه‌ی پایانی */
data class LudoCell(val row: Float, val col: Float)

/**
 * هندسه‌ی صفحه‌ی کلاسیک ۱۵×۱۵ — کاتلین خالص تا هم تست شود و هم صفحه‌ی نقاشی مصرف کند.
 * مسیر ۵۲تایی ساعتگرد از خانه‌ی شروع قرمز (ردیف ۶، ستون ۱) آغاز می‌شود.
 */
object LudoGeometry {
    const val GRID = 15

    /** خانه‌های مسیر مشترک به ترتیب شاخص مطلق ۰ تا ۵۱ */
    val track: List<LudoCell> = buildList {
        fun add(r: Int, c: Int) = add(LudoCell(r.toFloat(), c.toFloat()))
        for (c in 1..5) add(6, c)          // ۰..۴   بازوی چپ، ردیف بالا → راست
        for (r in 5 downTo 0) add(r, 6)    // ۵..۱۰  بازوی بالا، ستون چپ → بالا
        add(0, 7); add(0, 8)               // ۱۱..۱۲ لبه‌ی بالا
        for (r in 1..5) add(r, 8)          // ۱۳..۱۷ بازوی بالا، ستون راست → پایین
        for (c in 9..14) add(6, c)         // ۱۸..۲۳ بازوی راست، ردیف بالا → راست
        add(7, 14); add(8, 14)             // ۲۴..۲۵ لبه‌ی راست
        for (c in 13 downTo 9) add(8, c)   // ۲۶..۳۰ بازوی راست، ردیف پایین → چپ
        for (r in 9..14) add(r, 8)         // ۳۱..۳۶ بازوی پایین، ستون راست → پایین
        add(14, 7); add(14, 6)             // ۳۷..۳۸ لبه‌ی پایین
        for (r in 13 downTo 9) add(r, 6)   // ۳۹..۴۳ بازوی پایین، ستون چپ → بالا
        for (c in 5 downTo 0) add(8, c)    // ۴۴..۴۹ بازوی چپ، ردیف پایین → چپ
        add(7, 0); add(6, 0)               // ۵۰..۵۱ لبه‌ی چپ
    }

    /** گوشه‌ی بالا-چپِ پایگاه ۶×۶ هر رنگ */
    fun baseOrigin(color: LudoColor): LudoCell = when (color) {
        LudoColor.RED -> LudoCell(0f, 0f)
        LudoColor.GREEN -> LudoCell(0f, 9f)
        LudoColor.YELLOW -> LudoCell(9f, 9f)
        LudoColor.BLUE -> LudoCell(9f, 0f)
    }

    /** مرکز جایگاه مهره‌ی i در پایگاه (مختصات سلولی اعشاری) */
    fun baseSlot(color: LudoColor, token: Int): LudoCell {
        val o = baseOrigin(color)
        val dr = if (token < 2) 1.5f else 3.5f
        val dc = if (token % 2 == 0) 1.5f else 3.5f
        return LudoCell(o.row + dr, o.col + dc)
    }

    /** خانه‌ی k (۰ تا ۴) ستون رنگی خانه‌ی هر رنگ — از لبه به سمت مرکز */
    fun homeColumnCell(color: LudoColor, k: Int): LudoCell = when (color) {
        LudoColor.RED -> LudoCell(7f, 1f + k)
        LudoColor.GREEN -> LudoCell(1f + k, 7f)
        LudoColor.YELLOW -> LudoCell(7f, 13f - k)
        LudoColor.BLUE -> LudoCell(13f - k, 7f)
    }

    /** جایگاه مهره‌های رسیده در مثلث مرکزیِ هر رنگ */
    fun goalSlot(color: LudoColor, token: Int): LudoCell {
        val spread = (token - 1.5f) * 0.4f
        return when (color) {
            LudoColor.RED -> LudoCell(7f + spread, 6.1f)
            LudoColor.GREEN -> LudoCell(6.1f, 7f + spread)
            LudoColor.YELLOW -> LudoCell(7f + spread, 7.9f)
            LudoColor.BLUE -> LudoCell(7.9f, 7f + spread)
        }
    }

    /** مرکز خانه‌ی مهره‌ی token از رنگ color در گام step (هر گامی: پایگاه، مسیر، ستون، هدف) */
    fun cellOf(color: LudoColor, token: Int, step: Int): LudoCell = when {
        step == BASE -> baseSlot(color, token)
        step == GOAL -> goalSlot(color, token)
        step in 0..LAST_TRACK_STEP -> track[color.trackIndexOf(step)!!]
        else -> homeColumnCell(color, step - HOME_COLUMN_START)
    }

    /** مسیر پرش‌های یک حرکت — فهرست خانه‌هایی که مهره روی آن‌ها می‌نشیند */
    fun pathOf(move: LudoMove): List<LudoCell> =
        if (move.entersBoard) {
            listOf(cellOf(move.color, move.token, BASE), cellOf(move.color, move.token, 0))
        } else {
            (move.from..move.to).map { cellOf(move.color, move.token, it) }
        }
}
