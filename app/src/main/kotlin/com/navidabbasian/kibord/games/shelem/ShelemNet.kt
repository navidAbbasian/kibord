package com.navidabbasian.kibord.games.shelem

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.games.shelem.engine.ShelemState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** صندلی در میز چندگوشی شلم — ترتیب لیست همان شماره‌ی صندلی است؛ میزبان ۰، یارش ۲ */
@Serializable
enum class ShelemNetSeatKind { HOST, GUEST, BOT, EMPTY }

@Serializable
data class ShelemNetSeat(
    val name: String = "",
    val kind: ShelemNetSeatKind = ShelemNetSeatKind.EMPTY,
    val connected: Boolean = true,
) {
    val isHuman: Boolean get() = kind == ShelemNetSeatKind.HOST || kind == ShelemNetSeatKind.GUEST
}

/**
 * عکس میز از دیدِ یک مهمان مشخص: دست بقیه فقط تعداد، ویدو و کارت‌های خوابانده
 * فقط برای خودِ حاکم.
 */
@Serializable
data class ShelemRoomSnapshot(
    val seats: List<ShelemNetSeat> = emptyList(),
    val target: Int = 1165,
    val shelemBonus: Boolean = true,
    val started: Boolean = false,
    val game: ShelemState? = null,
    val dealing: Boolean = false,
    val bidBubbles: Map<Int, String> = emptyMap(),
    val thinkingSeat: Int? = null,
)

/** پیام‌های شبکه‌ی شلم — مهمان فرمان می‌فرستد، میزبان عکس سانسورشده برمی‌گرداند */
@Serializable
sealed class ShelemMessage {

    @Serializable
    @SerialName("state")
    data class State(val room: ShelemRoomSnapshot) : ShelemMessage()

    @Serializable
    @SerialName("bid")
    data class Bid(val amount: Int) : ShelemMessage()

    @Serializable
    @SerialName("pass")
    data object Pass : ShelemMessage()

    /** حاکم چهار کارت را خواباند */
    @Serializable
    @SerialName("discard")
    data class Discard(val cards: List<Card>) : ShelemMessage()

    @Serializable
    @SerialName("trump")
    data class ChooseTrump(val suit: Suit) : ShelemMessage()

    @Serializable
    @SerialName("play")
    data class Play(val card: Card) : ShelemMessage()

    @Serializable
    @SerialName("next")
    data object NextHand : ShelemMessage()

    @Serializable
    @SerialName("again")
    data object PlayAgain : ShelemMessage()
}

val shelemJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun ShelemMessage.encode(): String = shelemJson.encodeToString(ShelemMessage.serializer(), this)

fun decodeShelemMessage(line: String): ShelemMessage? = try {
    shelemJson.decodeFromString(ShelemMessage.serializer(), line)
} catch (_: Exception) {
    null
}

/** کارت جای‌گزین برای دست‌های پنهان — فقط برای شمارش */
private val HIDDEN = Card(Suit.SPADES, Rank.TWO)

/** سانسور وضعیت برای صندلی [seat] (منفی = نسخه‌ی کامل برای ذخیره) */
fun ShelemState.redactedFor(seat: Int): ShelemState {
    if (seat < 0) return this
    val iAmDeclarer = declarer == seat
    return copy(
        hands = hands.mapIndexed { i, hand -> if (i == seat) hand else List(hand.size) { HIDDEN } },
        kitty = if (iAmDeclarer) kitty else List(kitty.size) { HIDDEN },
        discarded = if (iAmDeclarer) discarded else List(discarded.size) { HIDDEN },
    )
}
