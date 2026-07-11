package com.navidabbasian.kibord.games.esmfamil.model

import kotlinx.serialization.Serializable

/** الفبای کامل فارسی برای کاشی‌های انتخاب حرف */
val PERSIAN_LETTERS = listOf(
    "ا", "ب", "پ", "ت", "ث", "ج", "چ", "ح",
    "خ", "د", "ذ", "ر", "ز", "ژ", "س", "ش",
    "ص", "ض", "ط", "ظ", "ع", "غ", "ف", "ق",
    "ک", "گ", "ل", "م", "ن", "و", "ه", "ی",
)

/** موضوعات کلاسیک — میزبان از این‌ها تیک می‌زند و می‌تواند موضوع دلخواه اضافه کند */
val DEFAULT_TOPICS = listOf(
    "اسم", "فامیل", "شهر", "کشور", "حیوان", "میوه",
    "غذا", "رنگ", "اشیا", "شغل", "گل", "ماشین",
)

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 8

/** مهلت فاز اعتراض بعد از امتیازشماری هر راند */
const val REVIEW_SECONDS = 60

/** شمارش معکوس اعلام حرف پیش از شروع راند */
const val COUNTDOWN_SECONDS = 5

/** حالت‌های امتیازی که میزبان در داوری می‌تواند حکم بدهد */
val JUDGE_SCORES = listOf(0, 5, 10, 20)

private val EXTRA_WHITESPACE = Regex("\\s+")

/**
 * کلید مقایسه‌ی آسان‌گیر اسم‌ها — بی‌تفاوت به بزرگی حروف انگلیسی،
 * فاصله‌های اضافه‌ی کیبورد و ی/ک عربی؛ تا هیچ بازیکنی سر جزئیات تایپ گم نشود.
 */
fun nameKey(name: String): String = name
    .trim()
    .replace(EXTRA_WHITESPACE, " ")
    .replace('ي', 'ی')
    .replace('ك', 'ک')
    .lowercase()

/** آیا این دو اسم همان یک نفرند؟ */
fun sameName(a: String, b: String): Boolean = nameKey(a) == nameKey(b)

@Serializable
data class EfPlayer(
    val name: String,
    val colorIndex: Int,
    val connected: Boolean = true,
    val totalScore: Int = 0,
)

@Serializable
data class EfSettings(
    val topics: List<String> = DEFAULT_TOPICS.take(6),
    val roundSeconds: Int = 90,
    val totalRounds: Int = 5,
)

/** یک جواب یک بازیکن برای یک موضوع در راند جاری */
@Serializable
data class EfAnswer(
    val player: String,
    val topic: String,
    val text: String,
    /** امتیاز محاسبه‌شده پس از اعمال رای‌های رد */
    val score: Int = 0,
    /** نام اعتراض‌کنندگان به این جواب */
    val rejectVotes: List<String> = emptyList(),
    /** ردشده با حکم میزبان در فاز داوری */
    val rejected: Boolean = false,
    /** حکم امتیازی میزبان (۰/۵/۱۰/۲۰) — تهی یعنی امتیاز خودکار */
    val judgedScore: Int? = null,
)

@Serializable
enum class EfPhase { LOBBY, LETTER_PICK, COUNTDOWN, PLAYING, REVIEW, JUDGE, ROUND_RESULT, GAME_OVER }

/**
 * عکس لحظه‌ای کامل وضعیت بازی. میزبان تنها مرجع حقیقت است؛
 * بعد از هر تغییر این عکس برای همه‌ی مهمان‌ها پخش می‌شود.
 */
@Serializable
data class EfSnapshot(
    val phase: EfPhase = EfPhase.LOBBY,
    val players: List<EfPlayer> = emptyList(),
    val hostName: String = "",
    val settings: EfSettings = EfSettings(),
    /** شماره‌ی راند جاری از ۱ */
    val roundIndex: Int = 0,
    /** چه کسی این راند حرف را انتخاب می‌کند */
    val pickerName: String = "",
    val usedLetters: List<String> = emptyList(),
    val currentLetter: String = "",
    val secondsLeft: Int = 0,
    /** چه کسی استپ زد — خالی یعنی وقت تمام شد */
    val stopperName: String = "",
    /** جواب‌های راند برای بازبینی؛ تا رسیدن همه، خالی است */
    val answers: List<EfAnswer> = emptyList(),
    /** بازیکن‌هایی که در فاز اعتراض «اعتراضی ندارم» زده‌اند */
    val reviewDone: List<String> = emptyList(),
    /** جمع امتیاز هر بازیکن در همین راند */
    val roundScores: Map<String, Int> = emptyMap(),
) {
    fun player(name: String): EfPlayer? = players.firstOrNull { sameName(it.name, name) }

    val remainingLetters: List<String>
        get() = PERSIAN_LETTERS.filter { it !in usedLetters }
}
