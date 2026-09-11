package com.navidabbasian.kibord.core.net.lan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * دست‌دادنِ اولِ سوکت محلی — مستقل از پیام‌های خودِ بازی: اولین خطِ مهمان
 * معرفی است و اولین خطِ میزبان پاسخ. بعد از آن هر خط یک پیام بازی است.
 */
@Serializable
internal data class LanHello(val hello: String)

@Serializable
internal data class LanWelcome(val ok: Boolean, val error: String = "")

internal val lanJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
