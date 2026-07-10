package com.navidabbasian.kibord.games.esmfamil.net

import com.navidabbasian.kibord.games.esmfamil.model.EfSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * پیام‌های شبکه — هر پیام یک خط JSON روی سوکت است.
 * مهمان‌ها فرمان می‌فرستند، میزبان عکس کامل وضعیت را برمی‌گرداند.
 */
@Serializable
sealed class EfMessage {

    /** اولین پیام مهمان: معرفی با اسم */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String) : EfMessage()

    /** پاسخ میزبان به معرفی */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val ok: Boolean, val error: String = "") : EfMessage()

    /** پخش وضعیت کامل از میزبان */
    @Serializable
    @SerialName("state")
    data class State(val snapshot: EfSnapshot) : EfMessage()

    /** بازیکنِ نوبت‌دار حرف را انتخاب کرد */
    @Serializable
    @SerialName("pick")
    data class PickLetter(val letter: String) : EfMessage()

    /** استپ — فقط وقتی همه‌ی خانه‌ها پر باشد مجاز است */
    @Serializable
    @SerialName("stop")
    data object StopRound : EfMessage()

    /** ارسال جواب‌های راند (موضوع → کلمه) */
    @Serializable
    @SerialName("submit")
    data class Submit(val answers: Map<String, String>) : EfMessage()

    /** رای رد/پس‌گرفتن رای برای یک جواب در بازبینی */
    @Serializable
    @SerialName("vote")
    data class Vote(val topic: String, val owner: String, val reject: Boolean) : EfMessage()
}

val efJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun EfMessage.encode(): String = efJson.encodeToString(EfMessage.serializer(), this)

fun decodeEfMessage(line: String): EfMessage? = try {
    efJson.decodeFromString(EfMessage.serializer(), line)
} catch (_: Exception) {
    null
}
