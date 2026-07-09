package com.navidabbasian.kibord.games.dor.data

import android.content.Context
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.CustomContentStore
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.dor.model.DorCategory
import com.navidabbasian.kibord.games.dor.model.DorWord
import kotlinx.serialization.json.Json

/**
 * مخزن کلمات دور: بارگذاری دسته‌ها از بانک محتوا (کش دانلودی یا assets)،
 * ادغام کلمات سفارشی و چرخه‌ی بی‌پایان کلمات به‌هم‌ریخته.
 *
 * سابقه‌ی کلمات بازی‌شده روی دیسک می‌ماند: تا وقتی همه‌ی کلماتِ
 * دسته‌های انتخابی یک بار نیامده‌اند، کلمه‌ی تکراری شروعِ بازی نمی‌شود.
 */
class DorWordRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(context)

    var categories: List<DorCategory> = emptyList()
        private set

    /** کل کلمات دسته‌های انتخابی بازی جاری — مرجع ریست سابقه */
    private var poolWords: List<DorWord> = emptyList()
    private var availableWords: MutableList<DorWord> = mutableListOf()
    private var currentWordIndex = 0

    fun loadCategories(customWordsJson: String = "") {
        if (categories.isNotEmpty()) return
        categories = try {
            json.decodeFromString<List<DorCategory>>(ContentBank.open(context, "words.json"))
        } catch (_: Exception) {
            fallbackCategories
        }
        mergeCustomCategories()
        mergeCustomWords(customWordsJson)
    }

    /** کتگوری‌های سفارشیِ ساخته‌شده در تنظیمات را به بانک اضافه می‌کند */
    private fun mergeCustomCategories() {
        val text = CustomContentStore(context).getJson(CustomContentStore.DOR)
        if (text.isBlank()) return
        val custom = try {
            json.decodeFromString<List<DorCategory>>(text)
        } catch (_: Exception) {
            return
        }
        categories = categories + custom.filter { c -> categories.none { it.id == c.id } }
    }

    /** کلمات سفارشی ذخیره‌شده در DataStore را به دسته‌های مربوط اضافه می‌کند */
    fun mergeCustomWords(customWordsJson: String) {
        if (customWordsJson.isBlank()) return
        val custom = try {
            json.decodeFromString<List<DorWord>>(customWordsJson)
        } catch (_: Exception) {
            return
        }
        categories = categories.map { cat ->
            val extras = custom.filter { it.category == cat.id }
                .filter { extra -> cat.words.none { it.text == extra.text } }
            if (extras.isEmpty()) cat else cat.copy(words = cat.words + extras)
        }
    }

    fun prepareWordsForGame(selectedCategoryIds: Set<String>) {
        poolWords = categories
            .filter { it.id in selectedCategoryIds }
            .flatMap { it.words }

        val played = playedStore.played(PlayedContentStore.GAME_DOR)
        var unplayed = poolWords.filter { wordKey(it) !in played }
        if (unplayed.isEmpty()) {
            // همه‌ی کلمات این دسته‌ها یک دور کامل آمده‌اند — سابقه‌شان پاک می‌شود
            playedStore.forget(PlayedContentStore.GAME_DOR, poolWords.map(::wordKey))
            unplayed = poolWords
        }
        availableWords = unplayed.shuffled().toMutableList()
        currentWordIndex = 0
    }

    fun nextWord(): DorWord? {
        if (poolWords.isEmpty()) return null
        if (currentWordIndex >= availableWords.size) {
            // وسط بازی کلمات تازه تمام شد: دور جدید از کل بانک انتخابی
            playedStore.forget(PlayedContentStore.GAME_DOR, poolWords.map(::wordKey))
            availableWords = poolWords.shuffled().toMutableList()
            currentWordIndex = 0
        }
        val word = availableWords[currentWordIndex++]
        playedStore.markPlayed(PlayedContentStore.GAME_DOR, wordKey(word))
        return word
    }

    fun reset() {
        poolWords = emptyList()
        availableWords.clear()
        currentWordIndex = 0
    }

    private fun wordKey(word: DorWord): String = "${word.category}:${word.text}"

    private val fallbackCategories = listOf(
        DorCategory(
            id = "general",
            name = "عمومی",
            emoji = "🎲",
            words = listOf(
                DorWord("تلویزیون", "general"), DorWord("کتاب", "general"),
                DorWord("دوچرخه", "general"), DorWord("آفتاب", "general"),
                DorWord("دریا", "general"), DorWord("کوه", "general"),
                DorWord("قطار", "general"), DorWord("سیب", "general"),
                DorWord("مدرسه", "general"), DorWord("پنجره", "general"),
            )
        )
    )
}
