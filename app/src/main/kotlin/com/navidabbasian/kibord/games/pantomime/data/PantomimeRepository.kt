package com.navidabbasian.kibord.games.pantomime.data

import android.content.Context
import com.navidabbasian.kibord.games.pantomime.model.PCategory
import kotlinx.serialization.json.Json

/**
 * بانک کلمات پانتومیم از assets/pantomime.json + قرعه‌کشی کلمه‌ی استفاده‌نشده.
 * کلمات استفاده‌شده در طول عمر یک ViewModel (یک بازی) تکرار نمی‌شوند.
 */
class PantomimeRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val usedWords = mutableSetOf<String>()

    var categories: List<PCategory> = emptyList()
        private set

    fun load() {
        if (categories.isNotEmpty()) return
        categories = try {
            val text = context.assets.open("pantomime.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<PCategory>>(text)
        } catch (_: Exception) {
            fallback
        }
    }

    /** کلمه‌ی تصادفی استفاده‌نشده از یک کتگوری و رده‌ی امتیازی؛ اگر تمام شده بود null */
    fun drawWord(category: PCategory, points: Int): String? {
        val candidates = category.wordsFor(points).filter { it !in usedWords }
        val word = candidates.randomOrNull() ?: return null
        usedWords.add(word)
        return word
    }

    fun drawGolden(category: PCategory): String? {
        if (category.golden.isBlank() || category.golden in usedWords) return null
        usedWords.add(category.golden)
        return category.golden
    }

    fun hasWords(category: PCategory, points: Int): Boolean =
        category.wordsFor(points).any { it !in usedWords }

    fun hasGolden(category: PCategory): Boolean =
        category.golden.isNotBlank() && category.golden !in usedWords

    fun resetUsed() {
        usedWords.clear()
    }

    private val fallback = listOf(
        PCategory(
            id = "general",
            name = "عمومی",
            emoji = "🎲",
            words2 = listOf("خوابیدن", "دویدن", "گربه"),
            words4 = listOf("آشپزی", "رانندگی", "ماهیگیری"),
            words6 = listOf("کوهنوردی", "عکاسی", "نقاشی"),
            golden = "شعبده‌بازی",
        )
    )
}
