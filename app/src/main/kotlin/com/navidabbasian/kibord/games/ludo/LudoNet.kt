package com.navidabbasian.kibord.games.ludo

import com.navidabbasian.kibord.games.ludo.engine.LudoCapture
import com.navidabbasian.kibord.games.ludo.engine.LudoMove
import com.navidabbasian.kibord.games.ludo.engine.LudoState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** صندلی در میز چندگوشی — ترتیب لیست همان ترتیب رنگ‌هاست (قرمز، سبز، زرد، آبی) */
@Serializable
enum class LudoNetSeatKind { HOST, GUEST, BOT, EMPTY }

@Serializable
data class LudoNetSeat(
    val name: String = "",
    val kind: LudoNetSeatKind = LudoNetSeatKind.EMPTY,
    val connected: Boolean = true,
) {
    val isHuman: Boolean get() = kind == LudoNetSeatKind.HOST || kind == LudoNetSeatKind.GUEST
}

/** انیمیشن حرکت روی شبکه — زمان شروع را هر گوشی خودش می‌گذارد */
@Serializable
data class LudoNetAnim(
    val nonce: Int,
    val move: LudoMove,
    val captured: LudoCapture?,
)

/**
 * عکس کامل میز منچ که میزبان بعد از هر تغییر می‌فرستد — منچ اطلاعات پنهان
 * ندارد، پس همه یک عکس می‌گیرند. میزبان همیشه قرمز است.
 */
@Serializable
data class LudoRoomSnapshot(
    val seats: List<LudoNetSeat> = List(4) { LudoNetSeat() },
    val tripleSix: Boolean = true,
    val safeStart: Boolean = false,
    val started: Boolean = false,
    val game: LudoState? = null,
    val rollNonce: Int = 0,
    val rolling: Boolean = false,
    val anim: LudoNetAnim? = null,
    val legalTokens: Set<Int> = emptySet(),
    val autoToken: Int? = null,
    val message: String? = null,
    val busy: Boolean = false,
)

/** پیام‌های شبکه‌ی منچ — مهمان فرمان می‌فرستد، میزبان وضعیت کامل برمی‌گرداند */
@Serializable
sealed class LudoMessage {

    @Serializable
    @SerialName("state")
    data class State(val room: LudoRoomSnapshot) : LudoMessage()

    /** مهمان: «تاس بریز» — فقط در نوبت خودش */
    @Serializable
    @SerialName("roll")
    data object Roll : LudoMessage()

    /** مهمان: این مهره حرکت کند — میزبان قانونی بودن را می‌سنجد */
    @Serializable
    @SerialName("tap")
    data class Tap(val token: Int) : LudoMessage()

    /** مهمان از صفحه‌ی برنده: بقیه ادامه بدهند */
    @Serializable
    @SerialName("cont")
    data object Continue : LudoMessage()

    @Serializable
    @SerialName("again")
    data object PlayAgain : LudoMessage()
}

val ludoJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun LudoMessage.encode(): String = ludoJson.encodeToString(LudoMessage.serializer(), this)

fun decodeLudoMessage(line: String): LudoMessage? = try {
    ludoJson.decodeFromString(LudoMessage.serializer(), line)
} catch (_: Exception) {
    null
}
