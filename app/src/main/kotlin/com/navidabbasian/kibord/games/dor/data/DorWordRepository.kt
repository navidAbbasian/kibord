package com.navidabbasian.kibord.games.dor.data

import android.content.Context
import com.navidabbasian.kibord.games.dor.model.DorCategory
import com.navidabbasian.kibord.games.dor.model.DorWord
import kotlinx.serialization.json.Json

/**
 * مخزن کلمات دور: بارگذاری دسته‌ها از assets/words.json،
 * ادغام کلمات سفارشی و چرخه‌ی بی‌پایان کلمات به‌هم‌ریخته.
 */
class DorWordRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    var categories: List<DorCategory> = emptyList()
        private set

    private var availableWords: MutableList<DorWord> = mutableListOf()
    private var currentWordIndex = 0

    fun loadCategories(customWordsJson: String = "") {
        if (categories.isNotEmpty()) return
        categories = try {
            val text = context.assets.open("words.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<DorCategory>>(text)
        } catch (_: Exception) {
            fallbackCategories
        }
        mergeCustomWords(customWordsJson)
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
        availableWords = categories
            .filter { it.id in selectedCategoryIds }
            .flatMap { it.words }
            .shuffled()
            .toMutableList()
        currentWordIndex = 0
    }

    fun nextWord(): DorWord? {
        if (availableWords.isEmpty()) return null
        if (currentWordIndex >= availableWords.size) {
            currentWordIndex = 0
            availableWords.shuffle()
        }
        return availableWords[currentWordIndex++]
    }

    fun reset() {
        availableWords.clear()
        currentWordIndex = 0
    }

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
