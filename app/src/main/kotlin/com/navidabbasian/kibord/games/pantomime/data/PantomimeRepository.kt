package com.navidabbasian.kibord.games.pantomime.data

import android.content.Context
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.CustomContentStore
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.pantomime.model.PCategory
import kotlinx.serialization.json.Json

/**
 * بانک کلمات پانتومیم از بانک محتوا (کش دانلودی یا assets) + قرعه‌کشی کلمه.
 *
 * دو لایه‌ی ضد تکرار:
 * - usedWords (حافظه‌ی موقت): در طول یک بازی هیچ کلمه‌ای دو بار نمی‌آید و
 *   خالی‌شدنِ یک رده همان «خانه‌ی سوخته»ی قبلی می‌ماند.
 * - سابقه‌ی دیسکی: بین بازی‌ها اول کلماتی می‌آیند که هنوز هیچ‌وقت بازی
 *   نشده‌اند؛ وقتی همه‌ی کلمات یک رده یک دور کامل آمدند، سابقه‌شان ریست می‌شود.
 */
class PantomimeRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val usedWords = mutableSetOf<String>()
    private val playedStore = PlayedContentStore(context)

    var categories: List<PCategory> = emptyList()
        private set

    /** بانک کلمات انتزاعی موضوع طلایی — جدا از کتگوری‌ها */
    var goldenBank: List<String> = emptyList()
        private set

    fun load() {
        if (categories.isNotEmpty()) return
        val base = try {
            json.decodeFromString<List<PCategory>>(ContentBank.open(context, "pantomime.json"))
        } catch (_: Exception) {
            fallback
        }
        val custom = try {
            val text = CustomContentStore(context).getJson(CustomContentStore.PANTOMIME)
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<PCategory>>(text)
        } catch (_: Exception) {
            emptyList()
        }
        val all = base + custom.filter { c -> base.none { it.id == c.id } }
        // بانک طلایی از کتگوری ویژه‌ی golden؛ اگر بانک قدیمی بود، از فیلدهای golden هر کتگوری
        goldenBank = all.firstOrNull { it.id == GOLDEN_ID }?.goldenWords?.ifEmpty { null }
            ?: all.map { it.golden }.filter { it.isNotBlank() }.distinct()
        categories = all.filter { it.id != GOLDEN_ID }
    }

    /** کلمه‌ی تصادفی استفاده‌نشده از یک کتگوری و رده‌ی امتیازی؛ اگر تمام شده بود null */
    fun drawWord(category: PCategory, points: Int): String? {
        val candidates = category.wordsFor(points).filter { it !in usedWords }
        if (candidates.isEmpty()) return null

        val played = playedStore.played(PlayedContentStore.GAME_PANTOMIME)
        val fresh = candidates.filter { wordKey(category, it) !in played }
        val word = if (fresh.isNotEmpty()) {
            fresh.random()
        } else {
            // همه‌ی گزینه‌های این رده قبلاً بازی شده‌اند — دور تازه برای این رده
            playedStore.forget(
                PlayedContentStore.GAME_PANTOMIME,
                category.wordsFor(points).map { wordKey(category, it) },
            )
            candidates.random()
        }
        usedWords.add(word)
        playedStore.markPlayed(PlayedContentStore.GAME_PANTOMIME, wordKey(category, word))
        return word
    }

    /** کتگوری‌های جدول رقابتی — اول بازی‌نشده‌ها، با ریست سابقه پس از یک دور کامل */
    fun pickCategories(count: Int): List<PCategory> {
        val n = count.coerceAtMost(categories.size)
        val played = playedStore.played(PlayedContentStore.GAME_PANTOMIME_CATEGORIES)
        val fresh = categories.filter { it.id !in played }.shuffled()

        val picked = fresh.take(n).toMutableList()
        if (picked.size < n) {
            playedStore.clear(PlayedContentStore.GAME_PANTOMIME_CATEGORIES)
            val rest = categories
                .filter { cat -> picked.none { it.id == cat.id } }
                .shuffled()
            picked += rest.take(n - picked.size)
        }
        playedStore.markPlayed(PlayedContentStore.GAME_PANTOMIME_CATEGORIES, picked.map { it.id })
        return picked.shuffled()
    }

    /** کلمه‌ی طلایی یک‌بارمصرف از بانک انتزاعی — اول کلمات هرگز بازی‌نشده */
    fun drawGoldenWord(): String? {
        val candidates = goldenBank.filter { it !in usedWords }
        if (candidates.isEmpty()) return null

        val played = playedStore.played(PlayedContentStore.GAME_PANTOMIME)
        val fresh = candidates.filter { goldenKey(it) !in played }
        val word = if (fresh.isNotEmpty()) {
            fresh.random()
        } else {
            playedStore.forget(PlayedContentStore.GAME_PANTOMIME, goldenBank.map(::goldenKey))
            candidates.random()
        }
        usedWords.add(word)
        playedStore.markPlayed(PlayedContentStore.GAME_PANTOMIME, goldenKey(word))
        return word
    }

    fun hasWords(category: PCategory, points: Int): Boolean =
        category.wordsFor(points).any { it !in usedWords }

    fun hasGoldenWord(): Boolean = goldenBank.any { it !in usedWords }

    fun resetUsed() {
        usedWords.clear()
    }

    private fun wordKey(category: PCategory, word: String): String = "${category.id}:$word"

    private fun goldenKey(word: String): String = "$GOLDEN_ID:$word"

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

    companion object {
        const val GOLDEN_ID = "golden"
    }
}
