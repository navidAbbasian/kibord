package com.navidabbasian.kibord.games.backgammon.net

import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * عکس کامل اتاق تخته‌نرد که میزبان بعد از هر تغییر پخش می‌کند —
 * مهمان چیزی جز همین را نمی‌داند و فقط همین را نقاشی می‌کند.
 */
@Serializable
data class BgRoomSnapshot(
    val variant: BgVariant = BgVariant.STANDARD,
    /** وضعیت کامل موتور — تهی یعنی بازی هنوز ساخته نشده */
    val game: BgState? = null,
    val hostName: String = "",
    val guestName: String = "",
    /** میزبان همیشه سفید است و مهمان سیاه — قرارداد ثابت اتاق */
    val guestPlayer: BgPlayer = BgPlayer.BLACK,
    /** مهمان وصل است؟ برای نشان‌دادن قطعی حریف روی هر دو گوشی */
    val guestConnected: Boolean = false,
    /** پیام همگامِ «حرکتی نداری» — تهی یعنی پیامی نیست */
    val skipMessage: String? = null,
    /** شمارنده‌ی دست‌ها — با هر «دوباره بازی» یکی بالا می‌رود */
    val rematchCount: Int = 0,
)

/**
 * پیام‌های شبکه‌ی تخته‌نرد — هر پیام یک خط JSON روی سوکت است.
 * مهمان فرمان می‌فرستد و میزبان که مرجع حقیقت است، وضعیت کامل را برمی‌گرداند.
 * (در راه اینترنتی دست‌دادن Hello/Welcome را خودِ لایه‌ی اتاق انجام می‌دهد.)
 */
@Serializable
sealed class BgMessage {

    /** اولین پیام مهمان روی سوکت: معرفی با اسم */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String) : BgMessage()

    /** پاسخ میزبان به معرفی */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val ok: Boolean, val error: String = "") : BgMessage()

    /** پخش وضعیت کامل از میزبان */
    @Serializable
    @SerialName("state")
    data class State(val room: BgRoomSnapshot) : BgMessage()

    /** مهمان: «تاس بریز» — فقط در نوبت خودش پذیرفته می‌شود */
    @Serializable
    @SerialName("roll")
    data object RollRequest : BgMessage()

    /**
     * مهمان: یک گام حرکت از دید خودش (همان BgMove موتور: مبدأ/مقصد/تاس/زدن).
     * میزبان قانونی بودن را با موتور می‌سنجد و فقط بعدِ تایید اعمال می‌کند.
     */
    @Serializable
    @SerialName("move")
    data class MoveRequest(val move: BgMove) : BgMessage()

    /** مهمان پیام «حرکتی نداری» را دید و نوبت را واگذار کرد */
    @Serializable
    @SerialName("skip")
    data object SkipAck : BgMessage()

    /** مهمان درخواست بازی دوباره داد — میزبان تخته را از نو می‌چیند */
    @Serializable
    @SerialName("rematch")
    data object RematchRequest : BgMessage()
}

val bgJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun BgMessage.encode(): String = bgJson.encodeToString(BgMessage.serializer(), this)

fun decodeBgMessage(line: String): BgMessage? = try {
    bgJson.decodeFromString(BgMessage.serializer(), line)
} catch (_: Exception) {
    null
}
