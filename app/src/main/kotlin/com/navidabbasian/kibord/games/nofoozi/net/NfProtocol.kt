package com.navidabbasian.kibord.games.nofoozi.net

import com.navidabbasian.kibord.games.nofoozi.model.NfSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * پیام‌های شبکه‌ی نفوذی — هر پیام یک خط JSON روی سوکت است.
 * مهمان‌ها فرمان می‌فرستند، میزبان عکس کامل وضعیت را برمی‌گرداند.
 */
@Serializable
sealed class NfMessage {

    /** اولین پیام مهمان: معرفی با اسم */
    @Serializable
    @SerialName("hello")
    data class Hello(val name: String) : NfMessage()

    /** پاسخ میزبان به معرفی */
    @Serializable
    @SerialName("welcome")
    data class Welcome(val ok: Boolean, val error: String = "") : NfMessage()

    /** پخش وضعیت کامل از میزبان */
    @Serializable
    @SerialName("state")
    data class State(val snapshot: NfSnapshot) : NfMessage()

    /** «کلمه‌مو دیدم» در فاز پخش کلمه‌ها */
    @Serializable
    @SerialName("seen")
    data object Seen : NfMessage()

    /** رای به متهم در فاز رای‌گیری */
    @Serializable
    @SerialName("vote")
    data class Vote(val target: String) : NfMessage()
}

val nfJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "t"
}

fun NfMessage.encode(): String = nfJson.encodeToString(NfMessage.serializer(), this)

fun decodeNfMessage(line: String): NfMessage? = try {
    nfJson.decodeFromString(NfMessage.serializer(), line)
} catch (_: Exception) {
    null
}
