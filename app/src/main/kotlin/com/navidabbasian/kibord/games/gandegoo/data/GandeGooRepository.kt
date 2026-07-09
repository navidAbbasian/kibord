package com.navidabbasian.kibord.games.gandegoo.data

import android.content.Context
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.CustomContentStore
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.gandegoo.model.GgCategory
import com.navidabbasian.kibord.games.gandegoo.model.GgQuestion
import kotlinx.serialization.json.Json

/**
 * بارگذاری بانک کتگوری‌ها و سوالات گنده‌گو از بانک محتوا (کش دانلودی یا assets)
 * به‌علاوه کتگوری‌های سفارشی کاربر.
 *
 * هر کتگوری می‌تواند ده‌ها سوال در سه سطح امتیازی (۲۰/۴۰/۶۰) داشته باشد؛
 * دسته‌ها را خود بازیکن‌ها از کل بانک انتخاب می‌کنند و برای هر سطح یک
 * سوالِ بازی‌نشده قرعه می‌خورد. سابقه‌ی «بازی‌شده» فقط برای سوالی ثبت
 * می‌شود که واقعاً بازی شود — سوال تعویض‌شده در بازی‌های بعد دوباره می‌آید.
 * سوال تکراری نمی‌آید تا همه‌ی سوال‌های آن سطح یک دور کامل بازی شوند.
 */
class GandeGooRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(context)
    private val customStore = CustomContentStore(context)

    var allCategories: List<GgCategory> = emptyList()
        private set

    fun load() {
        if (allCategories.isNotEmpty()) return
        val base = try {
            json.decodeFromString<List<GgCategory>>(ContentBank.open(context, "gandegoo.json"))
        } catch (_: Exception) {
            fallback
        }
        val custom = try {
            val text = customStore.getJson(CustomContentStore.GANDEGOO)
            if (text.isBlank()) emptyList()
            else json.decodeFromString<List<GgCategory>>(text)
        } catch (_: Exception) {
            emptyList()
        }
        // سوال‌های سفارشیِ هم‌شناسه با دسته‌های بانک، داخل همان دسته ادغام می‌شوند
        val merged = base.map { b ->
            val extra = custom.firstOrNull { it.id == b.id } ?: return@map b
            b.copy(questions = (b.questions + extra.questions).distinct())
        }
        allCategories = (merged + custom.filter { c -> base.none { it.id == c.id } })
            .filter { cat -> TIERS.all { tier -> cat.questions.any { it.points == tier } } }
    }

    /** ساخت جدول بازی از دسته‌های انتخابیِ کاربر — از هر سطحِ خواسته‌شده یک سوال تازه، بدون ثبت سابقه */
    fun buildGameCategories(ids: Collection<String>, tiers: List<Int> = TIERS): List<GgCategory> {
        val byId = allCategories.associateBy { it.id }
        val picked = ids.mapNotNull { byId[it] }
        playedStore.markPlayed(PlayedContentStore.GAME_GANDEGOO, picked.map { it.id })
        return picked.map { cat ->
            cat.copy(questions = tiers.mapNotNull { tier -> pickFreshQuestion(cat.id, tier, emptySet()) })
        }
    }

    /**
     * یک سوال از سطح داده‌شده: سوال‌های excludeTexts (نمایش‌داده‌شده در همین خانه)
     * کنار می‌روند و سابقه‌ی دیسک فقط اولویت می‌دهد — اگر همه‌ی نامزدها بازی
     * شده باشند، از میانشان قرعه می‌خورد. نبودِ نامزد یعنی تعویض ممکن نیست.
     */
    fun pickFreshQuestion(categoryId: String, tier: Int, excludeTexts: Set<String>): GgQuestion? {
        val cat = allCategories.firstOrNull { it.id == categoryId } ?: return null
        val candidates = cat.questions.filter { it.points == tier && it.text !in excludeTexts }
        if (candidates.isEmpty()) return null
        val played = playedStore.played(PlayedContentStore.GAME_GANDEGOO_QUESTIONS)
        val fresh = candidates.filter { questionKey(cat, it) !in played }
        return fresh.ifEmpty { candidates }.random()
    }

    /**
     * ثبت سوالی که واقعاً بازی شد؛ وقتی همه‌ی سوال‌های آن سطح یک دور کامل
     * بازی شدند، سابقه‌ی سطح (به‌جز همین سوال) ریست می‌شود تا چرخه از نو بچرخد.
     */
    fun markQuestionPlayed(categoryId: String, question: GgQuestion) {
        val cat = allCategories.firstOrNull { it.id == categoryId } ?: return
        val key = questionKey(cat, question)
        playedStore.markPlayed(PlayedContentStore.GAME_GANDEGOO_QUESTIONS, key)
        val siblings = cat.questions.filter { it.points == question.points }.map { questionKey(cat, it) }
        val played = playedStore.played(PlayedContentStore.GAME_GANDEGOO_QUESTIONS)
        if (siblings.all { it in played }) {
            playedStore.forget(PlayedContentStore.GAME_GANDEGOO_QUESTIONS, siblings - key)
        }
    }

    private fun questionKey(cat: GgCategory, q: GgQuestion): String = "${cat.id}:${q.points}:${q.text}"

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

    companion object {
        val TIERS = listOf(20, 40, 60)
    }
}
