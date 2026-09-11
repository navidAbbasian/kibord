package com.navidabbasian.kibord.games.uno.engine

/** چهار رنگ اونو */
enum class UnoColor { RED, YELLOW, GREEN, BLUE }

/** نوع کارت */
enum class UnoKind { NUMBER, SKIP, REVERSE, DRAW_TWO, WILD, WILD_DRAW_FOUR }

/** سه مدل محبوب بازی */
enum class UnoMode { CLASSIC, SEVEN_ZERO, MERCILESS }

/** فاز جاری دست */
enum class UnoPhase {
    /** برگ شروع «عوض رنگ» بود؛ نفر اول باید رنگ را انتخاب کند */
    CHOOSE_COLOR,

    /** جریان عادی نوبت‌ها */
    PLAYING,

    /** بازیکنی ۷ زده و باید هم‌بازی برای تعویض دست انتخاب کند (فقط هفت-صفر) */
    CHOOSE_SWAP,

    /** دست تمام شده؛ امتیازها شمرده شده‌اند */
    ROUND_OVER,

    /** یکی به سقف امتیاز رسیده؛ مسابقه تمام است */
    MATCH_OVER,
}

/**
 * یک برگ اونو. `id` یکتاست چون از بیشتر برگ‌ها دو نسخه در دسته هست؛
 * برابری روی `id` هم حساب می‌شود تا حذف از دست دقیق باشد.
 */
data class UnoCard(
    val id: Int,
    val kind: UnoKind,
    val color: UnoColor?,
    val number: Int = -1,
) {
    val isWild: Boolean get() = kind == UnoKind.WILD || kind == UnoKind.WILD_DRAW_FOUR

    /** ارزش امتیازی برگ برای شمارش پایان دست */
    val points: Int
        get() = when (kind) {
            UnoKind.NUMBER -> number
            UnoKind.SKIP, UnoKind.REVERSE, UnoKind.DRAW_TWO -> 20
            UnoKind.WILD, UnoKind.WILD_DRAW_FOUR -> 50
        }
}

/** تنظیمات مسابقه؛ سقف صفر یعنی تک‌دست */
data class UnoSettings(
    val mode: UnoMode = UnoMode.CLASSIC,
    val players: Int = 4,
    val target: Int = UnoRules.DEFAULT_TARGET,
) {
    init {
        require(players in 2..4) { "بازیکن‌ها باید ۲ تا ۴ نفر باشند" }
    }
}

/** ثابت‌های قانون */
object UnoRules {
    const val HAND_SIZE = 7
    const val DECK_SIZE = 108
    const val UNO_PENALTY = 2

    /** سقف‌های مسابقه: صفر = تک‌دست */
    val TARGETS = listOf(0, 200, 500)
    const val DEFAULT_TARGET = 200
}

/**
 * وضعیت کامل یک مسابقه‌ی اونو — تغییرناپذیر؛ هر حرکت نسخه‌ی تازه می‌سازد.
 * آخرین عضو `discard` روی دسته‌ی رد است.
 */
data class UnoState(
    val settings: UnoSettings,
    val hands: List<List<UnoCard>>,
    val drawPile: List<UnoCard>,
    val discard: List<UnoCard>,
    /** رنگ فعال (بعد از وایلد، رنگ انتخابی)؛ فقط در CHOOSE_COLOR تهی است */
    val currentColor: UnoColor?,
    val turn: Int,
    /** ‎+۱ ساعتگرد، ‎−۱ پادساعتگرد */
    val direction: Int = 1,
    val phase: UnoPhase = UnoPhase.PLAYING,
    /** جریمه‌ی انباشته‌ی ‎+۲/+۴ که نوبتی باید بکشد (در بی‌رحم قابل پاس‌کاری) */
    val pendingDraw: Int = 0,
    /** نوع زنجیره‌ی جریمه — فقط همان نوع رویش سوار می‌شود */
    val chainKind: UnoKind? = null,
    /** برگی که همین الان کشیده شده و قابل بازی است؛ صاحبش باید تصمیم بگیرد */
    val drawnCard: UnoCard? = null,
    /** بازیکنی که ۷ زده و منتظر انتخاب هم‌بازی برای تعویض است */
    val swapSeat: Int? = null,
    /** بازیکنی که به یک برگ رسیده و هنوز «اونو!» نگفته */
    val unoPending: Int? = null,
    val totals: List<Int>,
    /** امتیاز هر صندلی در دستِ تمام‌شده (برنده جمع دست بقیه را می‌گیرد) */
    val roundScores: List<Int>? = null,
    val roundWinner: Int? = null,
    val matchWinner: Int? = null,
    val roundNumber: Int = 1,
) {
    val players: Int get() = settings.players
    val topCard: UnoCard get() = discard.last()

    /** صندلی بعدی در جهت جاری */
    fun nextSeat(from: Int, steps: Int = 1): Int {
        val n = players
        return ((from + direction * steps) % n + n) % n
    }

    /** مجموع همه‌ی برگ‌های در گردش — همیشه باید ۱۰۸ بماند */
    val cardCount: Int get() = hands.sumOf { it.size } + drawPile.size + discard.size
}
