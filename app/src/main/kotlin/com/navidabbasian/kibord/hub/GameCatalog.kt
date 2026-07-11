package com.navidabbasian.kibord.hub

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.navidabbasian.kibord.core.ui.theme.DorAccent
import com.navidabbasian.kibord.core.ui.theme.DorAccentDark
import com.navidabbasian.kibord.core.ui.theme.EsmFamilAccent
import com.navidabbasian.kibord.core.ui.theme.EsmFamilAccentDark
import com.navidabbasian.kibord.core.ui.theme.GandeGooAccent
import com.navidabbasian.kibord.core.ui.theme.GandeGooAccentDark
import com.navidabbasian.kibord.core.ui.theme.KalamzAccent
import com.navidabbasian.kibord.core.ui.theme.KalamzAccentDark
import com.navidabbasian.kibord.core.ui.theme.PantomimeClassicAccent
import com.navidabbasian.kibord.core.ui.theme.PantomimeClassicAccentDark
import com.navidabbasian.kibord.core.ui.theme.PantomimeRivalAccent
import com.navidabbasian.kibord.core.ui.theme.ForeheadAccent
import com.navidabbasian.kibord.core.ui.theme.ForeheadAccentDark
import com.navidabbasian.kibord.core.ui.theme.MafiaAccent
import com.navidabbasian.kibord.core.ui.theme.MafiaAccentDark
import com.navidabbasian.kibord.core.ui.theme.NofooziAccent
import com.navidabbasian.kibord.core.ui.theme.NofooziAccentDark
import com.navidabbasian.kibord.core.ui.theme.ProverbAccent
import com.navidabbasian.kibord.core.ui.theme.ProverbAccentDark
import com.navidabbasian.kibord.core.ui.theme.ShahDozdAccent
import com.navidabbasian.kibord.core.ui.theme.ShahDozdAccentDark
import com.navidabbasian.kibord.core.ui.theme.SpyAccent
import com.navidabbasian.kibord.core.ui.theme.SpyAccentDark
import com.navidabbasian.kibord.core.ui.theme.TabooAccent
import com.navidabbasian.kibord.core.ui.theme.TabooAccentDark
import com.navidabbasian.kibord.core.ui.theme.PantomimeRivalAccentDark

@Immutable
data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val emoji: String,
    val accent: Color,
    val accentDark: Color,
    val players: String,
    /** null یعنی هنوز منتشر نشده — نشان «به زودی» می‌گیرد */
    val route: String?,
)

object Routes {
    const val HUB = "hub"
    const val KALAMZ = "game/kalamz"
    const val DOR = "game/dor"
    const val GANDEGOO = "game/gandegoo"
    const val PANTOMIME_CLASSIC = "game/pantomime_classic"
    const val PANTOMIME_RIVAL = "game/pantomime_rival"
    const val ESM_FAMIL = "game/esm_famil"
    const val MORE_GAMES = "more_games"
    const val TABOO = "game/taboo"
    const val SPY = "game/spy"
    const val FOREHEAD = "game/forehead"
    const val SHAH_DOZD = "game/shah_dozd"
    const val PROVERB = "game/proverb"
    const val NOFOOZI = "game/nofoozi"
    const val MAFIA = "game/mafia"
}

/** چهار بازی اصلی صفحه‌ی خانه — بقیه در «بازی‌های بیشتر» زندگی می‌کنند */
val gameCatalog = listOf(
    GameInfo(
        id = "kalamz",
        title = "کلمز",
        tagline = "سه راند حدس کلمه: توضیح، یک کلمه، پانتومیم",
        emoji = "🗣️",
        accent = KalamzAccent,
        accentDark = KalamzAccentDark,
        players = "۴ تا ۲۰ نفر",
        route = Routes.KALAMZ,
    ),
    GameInfo(
        id = "dor",
        title = "دور",
        tagline = "بمب داره تیک‌تاک می‌کنه! کلمه رو برسون و رد کن",
        emoji = "💣",
        accent = DorAccent,
        accentDark = DorAccentDark,
        players = "۴ تا ۱۲ نفر",
        route = Routes.DOR,
    ),
    GameInfo(
        id = "gandegoo",
        title = "گنده گو",
        tagline = "بلوف بزن، گنده‌شو بگو، مچشونو بگیر!",
        emoji = "🎭",
        accent = GandeGooAccent,
        accentDark = GandeGooAccentDark,
        players = "۴ یا ۶ نفر",
        route = Routes.GANDEGOO,
    ),
    GameInfo(
        id = "esm_famil",
        title = "اسم فامیل",
        tagline = "چندنفره با گوشی‌های خودتون! حرف بیار، بنویس، استپ!",
        emoji = "✍️",
        accent = EsmFamilAccent,
        accentDark = EsmFamilAccentDark,
        players = "۲ تا ۸ نفر — هر کی با گوشی خودش",
        route = Routes.ESM_FAMIL,
    ),
)

/** بازی‌های بخش «بازی‌های بیشتر» — با هر بازی تازه، این فهرست بلندتر می‌شود */
val moreGamesCatalog = listOf(
    GameInfo(
        id = "pantomime_classic",
        title = "پانتومیم کلاسیک",
        tagline = "بدون کلام! با موضوع طلایی ۳۰ امتیازی پرریسک",
        emoji = "🤫",
        accent = PantomimeClassicAccent,
        accentDark = PantomimeClassicAccentDark,
        players = "۴+ نفر — ۲ تیم",
        route = Routes.PANTOMIME_CLASSIC,
    ),
    GameInfo(
        id = "pantomime_rival",
        title = "پانتومیم رقابتی",
        tagline = "جدول امتیازها — اجرا کن و خانه‌ها رو فتح کن",
        emoji = "⚔️",
        accent = PantomimeRivalAccent,
        accentDark = PantomimeRivalAccentDark,
        players = "۴ یا ۶ نفر",
        route = Routes.PANTOMIME_RIVAL,
    ),
    GameInfo(
        id = "taboo",
        title = "کلمه ممنوعه",
        tagline = "کلمه رو برسون بدون گفتنِ پنج کلمه‌ی قدغن!",
        emoji = "🤐",
        accent = TabooAccent,
        accentDark = TabooAccentDark,
        players = "۴+ نفر — ۲ تیم",
        route = Routes.TABOO,
    ),
    GameInfo(
        id = "spy",
        title = "جاسوس",
        tagline = "همه می‌دونن کجایید جز یه نفر… پیداش کنید!",
        emoji = "🕵️",
        accent = SpyAccent,
        accentDark = SpyAccentDark,
        players = "۳ تا ۸ نفر",
        route = Routes.SPY,
    ),
    GameInfo(
        id = "forehead",
        title = "حدس روی پیشونی",
        tagline = "گوشی رو پیشونیت! توضیح می‌دن، تو حدس بزن",
        emoji = "🤳",
        accent = ForeheadAccent,
        accentDark = ForeheadAccentDark,
        players = "۲+ نفر",
        route = Routes.FOREHEAD,
    ),
    GameInfo(
        id = "shah_dozd",
        title = "شاه دزد وزیر",
        tagline = "نوستالژی! وزیر باید مچ دزد رو بگیره",
        emoji = "👑",
        accent = ShahDozdAccent,
        accentDark = ShahDozdAccentDark,
        players = "۴ تا ۸ نفر",
        route = Routes.SHAH_DOZD,
    ),
    GameInfo(
        id = "proverb",
        title = "ضرب‌المثل نصفه",
        tagline = "نصفش رو بگو، تیمت کاملش کنه!",
        emoji = "📜",
        accent = ProverbAccent,
        accentDark = ProverbAccentDark,
        players = "۴+ نفر (دو تیم)",
        route = Routes.PROVERB,
    ),
    GameInfo(
        id = "nofoozi",
        title = "کلمه‌ی نفوذی",
        tagline = "شبکه‌ای! نفوذی رو از توصیف‌هاش پیدا کن",
        emoji = "🥸",
        accent = NofooziAccent,
        accentDark = NofooziAccentDark,
        players = "۳ تا ۸ نفر",
        route = Routes.NOFOOZI,
    ),
    GameInfo(
        id = "mafia",
        title = "شب مافیا",
        tagline = "شبکه‌ای و بدون گرداننده — شهر خوابه!",
        emoji = "🌙",
        accent = MafiaAccent,
        accentDark = MafiaAccentDark,
        players = "۵ تا ۸ نفر",
        route = Routes.MAFIA,
    ),
)
