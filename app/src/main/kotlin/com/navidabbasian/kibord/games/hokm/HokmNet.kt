package com.navidabbasian.kibord.games.hokm

import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Rank
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.games.hokm.engine.AceDeal
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** صندلی در میز چندگوشی حکم — ترتیب لیست همان شماره‌ی صندلی است؛ میزبان ۰ */
@Serializable
enum class HokmNetSeatKind { HOST, GUEST, BOT, EMPTY }

@Serializable
data class HokmNetSeat(
    val name: String = "",
    val kind: HokmNetSeatKind = HokmNetSeatKind.EMPTY,
    val connected: Boolean = true,
) {
    val isHuman: Boolean get() = kind == HokmNetSeatKind.HOST || kind == HokmNetSeatKind.GUEST
}

/**
 * عکس میز از دیدِ یک مهمان مشخص: دست بقیه و کارت‌های کنارگذاشته فقط «تعداد»
 * است (کارت جای‌گزین)، کارت‌های در پروازِ وصول فقط برای طرفِ خودِ بیننده رو
 * دیده می‌شود.
 */
@Serializable
data class HokmRoomSnapshot(
    val seats: List<HokmNetSeat> = emptyList(),
    val variant: HokmVariant = HokmVariant.FOUR,
    val target: Int = 7,
    val debtLimit: Int = 17,
    val started: Boolean = false,
    val stage: HokmStage = HokmStage.Playing,
    val game: HokmState? = null,
    val aceDeal: AceDeal? = null,
    val aceRevealed: Int = 0,
    val sweeping: Boolean = false,
    val notice: String? = null,
    val duelSeats: List<Int>? = null,
    val mordabadiGame: HokmState? = null,
    val debtorPick: DebtorPick? = null,
    val collectionBanner: Boolean = false,
    val exchangeFx: ExchangeFx? = null,
    val receivedCard: Card? = null,
)

/** پیام‌های شبکه‌ی حکم — مهمان فرمان می‌فرستد، میزبان عکس سانسورشده برمی‌گرداند */
@Serializable
sealed class HokmMessage {

    @Serializable
    @SerialName("state")
    data class State(val room: HokmRoomSnapshot) : HokmMessage()

    @Serializable
    @SerialName("trump")
    data class ChooseTrump(val suit: Suit) : HokmMessage()

    @Serializable
    @SerialName("play")
    data class Play(val card: Card) : HokmMessage()

    /** مردابادی: طلبکار خال و بدهکار را انتخاب کرد */
    @Serializable
    @SerialName("exch")
    data class Exchange(val debtor: Int, val suit: Suit) : HokmMessage()

    /** مردابادی: بدهکاری که خال را نداشت، این کارت را می‌دهد */
    @Serializable
    @SerialName("give")
    data class GiveDebtCard(val card: Card) : HokmMessage()

    @Serializable
    @SerialName("skipace")
    data object SkipAceDeal : HokmMessage()

    @Serializable
    @SerialName("next")
    data object NextHand : HokmMessage()

    @Serializable
    @SerialName("again")
    data object PlayAgain : HokmMessage()
}

val hokmJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun HokmMessage.encode(): String = hokmJson.encodeToString(HokmMessage.serializer(), this)

fun decodeHokmMessage(line: String): HokmMessage? = try {
    hokmJson.decodeFromString(HokmMessage.serializer(), line)
} catch (_: Exception) {
    null
}

/** کارت جای‌گزین برای دست‌های پنهان — فقط برای شمارش */
val HIDDEN_CARD = Card(Suit.SPADES, Rank.TWO)

/** سانسور وضعیت برای صندلی [seat] (در دوئل: صندلی دوئل). منفی = نسخه‌ی کامل */
fun HokmState.redactedFor(seat: Int): HokmState {
    if (seat < 0) return this
    return copy(
        hands = hands.mapIndexed { i, hand -> if (i == seat) hand else List(hand.size) { HIDDEN_CARD } },
        stock = List(stock.size) { HIDDEN_CARD },
    )
}
