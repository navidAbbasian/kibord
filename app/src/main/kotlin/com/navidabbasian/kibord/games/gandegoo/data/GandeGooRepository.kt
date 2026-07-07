package com.navidabbasian.kibord.games.gandegoo.data

import android.content.Context
import com.navidabbasian.kibord.games.gandegoo.model.GgCategory
import com.navidabbasian.kibord.games.gandegoo.model.GgQuestion
import kotlinx.serialization.json.Json

/** بارگذاری بانک کتگوری‌ها و سوالات گنده‌گو از assets/gandegoo.json */
class GandeGooRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    var allCategories: List<GgCategory> = emptyList()
        private set

    fun load() {
        if (allCategories.isNotEmpty()) return
        allCategories = try {
            val text = context.assets.open("gandegoo.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<GgCategory>>(text)
                .filter { it.questions.size >= 3 }
                .map { it.copy(questions = it.questions.sortedBy { q -> q.points }.take(3)) }
        } catch (_: Exception) {
            fallback
        }
    }

    /** انتخاب تصادفی کتگوری‌های این بازی */
    fun pickCategories(count: Int): List<GgCategory> =
        allCategories.shuffled().take(count.coerceAtMost(allCategories.size))

    private val fallback = listOf(
        GgCategory(
            id = "general",
            name = "عمومی",
            emoji = "🎲",
            questions = listOf(
                GgQuestion(20, "رنگ‌ها نام ببرید"),
                GgQuestion(40, "میوه‌ها نام ببرید"),
                GgQuestion(60, "شهرهای ایران نام ببرید"),
            )
        )
    )
}
