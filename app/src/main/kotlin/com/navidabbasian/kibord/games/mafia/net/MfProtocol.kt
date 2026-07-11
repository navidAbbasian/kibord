package com.navidabbasian.kibord.games.mafia.net

import com.navidabbasian.kibord.games.mafia.model.MfSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * پیام‌های شبکه‌ی مافیا — هر پیام یک خط JSON روی سوکت است.
 * مهمان‌ها فرمان می‌فرستند، میزبان عکس کامل وضعیت را برمی‌گرداند.
 */
@Serializable
sealed class MfMessage {

    /** اولین پیام مهمان: معرفی با اسم */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String) : MfMessage()

    /** پاسخ میزبان به معرفی */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val ok: Boolean, val error: String = "") : MfMessage()

    /** پخش وضعیت کامل از میزبان */
    @Serializable
    @SerialName("state")
    data class State(val snapshot: MfSnapshot) : MfMessage()

    /** «نقشم رو دیدم» در فاز پخش نقش‌ها */
    @Serializable
    @SerialName("seen")
    data object Seen : MfMessage()

    /** اقدام شبانه — معنایش را نقشِ فرستنده تعیین می‌کند */
    @Serializable
    @SerialName("night")
    data class NightAction(val target: String) : MfMessage()

    /** رای روز به متهم */
    @Serializable
    @SerialName("day_vote")
    data class DayVote(val target: String) : MfMessage()
}

val mfJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun MfMessage.encode(): String = mfJson.encodeToString(MfMessage.serializer(), this)

fun decodeMfMessage(line: String): MfMessage? = try {
    mfJson.decodeFromString(MfMessage.serializer(), line)
} catch (_: Exception) {
    null
}
