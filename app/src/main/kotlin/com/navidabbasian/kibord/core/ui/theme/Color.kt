package com.navidabbasian.kibord.core.ui.theme

import androidx.compose.ui.graphics.Color

// ---- برند «کی برد؟» — اسطوخودوسی پاستلی ----
val VioletPrimary = Color(0xFFA78BFA)
val VioletBright = Color(0xFFC4B0FF)
val VioletDeep = Color(0xFF8B72E8)
val VioletDark = Color(0xFF6D57C7)

// ---- سطوح تیره: شب آلویی گرم به‌جای مشکی خشک ----
val DarkBackground = Color(0xFF1A1626)
val DarkSurface = Color(0xFF241E33)
val DarkSurfaceHigh = Color(0xFF2F2842)

// ---- سطوح روشن: یاسی بسیار ملایم ----
val LightBackground = Color(0xFFFAF7FF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceHigh = Color(0xFFF0EAFB)

// ---- رنگ‌های معنایی پاستلی ----
val SuccessGreen = Color(0xFF7DE0B4)
val SuccessGreenDeep = Color(0xFF2FA97C)
val WarningAmber = Color(0xFFFFCF7D)
val WarningAmberDeep = Color(0xFFE09C3B)
val DangerRed = Color(0xFFF99B9B)
val DangerRedDeep = Color(0xFFE06565)
val GoldAccent = Color(0xFFFFD98E)

// ---- هویت رنگی هر بازی — آب‌نباتی پاستلی (گرادیان کارت و اکسنت داخل بازی) ----
val KalamzAccent = Color(0xFFF98BA0)
val KalamzAccentDark = Color(0xFFEF6D88)
val DorAccent = Color(0xFFFFB169)
val DorAccentDark = Color(0xFFF79852)
val GandeGooAccent = Color(0xFF63D8C7)
val GandeGooAccentDark = Color(0xFF3FBFAE)
val PantomimeClassicAccent = Color(0xFF7FB5F5)
val PantomimeClassicAccentDark = Color(0xFF5E97E8)
val PantomimeRivalAccent = Color(0xFFF794C6)
val PantomimeRivalAccentDark = Color(0xFFEE74B2)
val EsmFamilAccent = Color(0xFF8FB33E)
val EsmFamilAccentDark = Color(0xFF78992E)
val TabooAccent = Color(0xFFE96A6A)
val TabooAccentDark = Color(0xFFD34F4F)
val SpyAccent = Color(0xFF7C90C9)
val SpyAccentDark = Color(0xFF6178B3)
val ForeheadAccent = Color(0xFFE0A22E)
val ForeheadAccentDark = Color(0xFFC78A1C)
val WhoAmIAccent = Color(0xFF4FB0C9)
val WhoAmIAccentDark = Color(0xFF3B96AE)
val EsmRamzAccent = Color(0xFF6A79CE)
val EsmRamzAccentDark = Color(0xFF5361B5)
val ProverbAccent = Color(0xFF56B98C)
val ProverbAccentDark = Color(0xFF3EA274)
val NofooziAccent = Color(0xFF8A93A8)
val NofooziAccentDark = Color(0xFF707A92)
val MafiaAccent = Color(0xFFA85E6E)
val MafiaAccentDark = Color(0xFF8F4756)

// ---- رنگ تیم‌ها — پاستلی ----
// روشن‌تر و لطیف: مناسب پس‌زمینه‌ی تیره
val teamColorsOnDark = listOf(
    Color(0xFFFFA9B8), // گلبهی
    Color(0xFF9EC5FF), // آبی پودری
    Color(0xFF8FE6BE), // نعنایی
    Color(0xFFFFD494), // عسلی
    Color(0xFFDDB5FF), // یاسی
    Color(0xFFFFA9CE), // صورتی پنبه‌ای
    Color(0xFF9BDFFF), // آسمانی
    Color(0xFFCFF0A0), // لیمویی
)

/** رنگ تیم به‌صورت چرخشی — وقتی تیم‌ها از پالت بیشتر شدند، رنگ‌ها از سر گرفته می‌شوند */
fun List<Color>.teamColorFor(index: Int): Color =
    if (isEmpty()) Color.White else this[((index % size) + size) % size]

// کمی عمیق‌تر: مناسب پس‌زمینه‌ی روشن و متنِ سفید روی تراشه‌ها
val teamColorsOnLight = listOf(
    Color(0xFFF17E95), // گلبهی پررنگ
    Color(0xFF6A99E8), // آبی پودری پررنگ
    Color(0xFF4FBD92), // نعنایی پررنگ
    Color(0xFFF0A93F), // عسلی پررنگ
    Color(0xFFB287E0), // یاسی پررنگ
    Color(0xFFEC7FB0), // صورتی پررنگ
    Color(0xFF55B5DE), // آسمانی پررنگ
    Color(0xFF97C25F), // لیمویی پررنگ
)
