package com.navidabbasian.kibord.games.uno

import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoKind
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** صندلی در میز چندگوشی اونو — ترتیب لیست همان شماره‌ی صندلی است؛ میزبان ۰ */
@Serializable
enum class UnoNetSeatKind { HOST, GUEST, BOT, EMPTY }

@Serializable
data class UnoNetSeat(
    val name: String = "",
    val kind: UnoNetSeatKind = UnoNetSeatKind.EMPTY,
    val connected: Boolean = true,
) {
    val isHuman: Boolean get() = kind == UnoNetSeatKind.HOST || kind == UnoNetSeatKind.GUEST
}

/** انیمیشن پرواز دست‌ها روی شبکه: جفت‌های (از، به) */
@Serializable
data class UnoNetHandAnim(val id: Int, val moves: List<List<Int>>)

/**
 * عکس میز از دیدِ یک مهمان مشخص: دست بقیه و دسته‌ی کشیدن فقط «تعداد» است
 * (برگ‌های جای‌گزینِ بی‌معنا با همان اندازه) تا هیچ‌کس دست دیگری را نبیند.
 */
@Serializable
data class UnoRoomSnapshot(
    val seats: List<UnoNetSeat> = emptyList(),
    val mode: UnoMode = UnoMode.CLASSIC,
    val players: Int = 4,
    val target: Int = 200,
    val started: Boolean = false,
    val game: UnoState? = null,
    val thinkingSeat: Int? = null,
    val toast: String? = null,
    val toastId: Int = 0,
    val unoBubbleSeat: Int? = null,
    val handAnim: UnoNetHandAnim? = null,
    val dealing: Boolean = false,
)

/** پیام‌های شبکه‌ی اونو — مهمان فرمان می‌فرستد (با شناسه‌ی برگ)، میزبان عکس سانسورشده برمی‌گرداند */
@Serializable
sealed class UnoMessage {

    @Serializable
    @SerialName("state")
    data class State(val room: UnoRoomSnapshot) : UnoMessage()

    @Serializable
    @SerialName("play")
    data class Play(val cardId: Int, val color: UnoColor? = null) : UnoMessage()

    @Serializable
    @SerialName("draw")
    data object DrawTap : UnoMessage()

    @Serializable
    @SerialName("playdrawn")
    data class PlayDrawn(val color: UnoColor? = null) : UnoMessage()

    @Serializable
    @SerialName("keep")
    data object KeepDrawn : UnoMessage()

    @Serializable
    @SerialName("startcolor")
    data class StartColor(val color: UnoColor) : UnoMessage()

    @Serializable
    @SerialName("swap")
    data class Swap(val target: Int) : UnoMessage()

    @Serializable
    @SerialName("uno")
    data object CallUno : UnoMessage()

    @Serializable
    @SerialName("next")
    data object NextRound : UnoMessage()

    @Serializable
    @SerialName("again")
    data object PlayAgain : UnoMessage()
}

val unoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun UnoMessage.encode(): String = unoJson.encodeToString(UnoMessage.serializer(), this)

fun decodeUnoMessage(line: String): UnoMessage? = try {
    unoJson.decodeFromString(UnoMessage.serializer(), line)
} catch (_: Exception) {
    null
}

/** برگ جای‌گزین برای دست‌های پنهان — شناسه‌ی منفی تا با هیچ برگ واقعی قاطی نشود */
private fun hiddenCard(slot: Int): UnoCard = UnoCard(id = -1 - slot, kind = UnoKind.NUMBER, color = null, number = -1)

/**
 * سانسور وضعیت برای صندلی [seat]: فقط دست خودش، برگ کشیده‌ی خودش و
 * تعدادِ برگ‌های بقیه و دسته. (میزبان با seat = -۱ نسخه‌ی کامل را می‌گیرد — برای ذخیره‌ی اتاق)
 */
fun UnoState.redactedFor(seat: Int): UnoState {
    if (seat < 0) return this
    var slot = 0
    return copy(
        hands = hands.mapIndexed { i, hand ->
            if (i == seat) hand else List(hand.size) { hiddenCard(slot++) }
        },
        drawPile = List(drawPile.size) { hiddenCard(slot++) },
        drawnCard = if (turn == seat) drawnCard else null,
    )
}
