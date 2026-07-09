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
 * برای هر بازی از هر سطح یک سوالِ بازی‌نشده قرعه می‌خورد و سابقه‌اش روی
 * دیسک می‌ماند — سوال تکراری نمی‌آید تا همه‌ی سوال‌های آن سطح یک دور
 * کامل بازی شوند. خود کتگوری‌ها هم چرخشی انتخاب می‌شوند.
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
        allCategories = (base + custom.filter { c -> base.none { it.id == c.id } })
            .filter { cat -> TIERS.all { tier -> cat.questions.any { it.points == tier } } }
    }

    /** انتخاب کتگوری‌های این بازی — اول بازی‌نشده‌ها، با ریست سابقه پس از یک دور کامل */
    fun pickCategories(count: Int): List<GgCategory> {
        val n = count.coerceAtMost(allCategories.size)
        val played = playedStore.played(PlayedContentStore.GAME_GANDEGOO)
        val fresh = allCategories.filter { it.id !in played }.shuffled()

        val picked = fresh.take(n).toMutableList()
        if (picked.size < n) {
            // دور کامل شد: سابقه پاک و کسری از بقیه‌ی بانک (بدون تکرارِ همین انتخاب)
            playedStore.clear(PlayedContentStore.GAME_GANDEGOO)
            val rest = allCategories
                .filter { cat -> picked.none { it.id == cat.id } }
                .shuffled()
            picked += rest.take(n - picked.size)
        }
        playedStore.markPlayed(PlayedContentStore.GAME_GANDEGOO, picked.map { it.id })
        return picked.shuffled().map { it.copy(questions = pickQuestions(it)) }
    }

    /** از هر سطح امتیازی یک سوالِ بازی‌نشده؛ پس از اتمام سوال‌های یک سطح، سابقه‌ی همان سطح ریست می‌شود */
    private fun pickQuestions(cat: GgCategory): List<GgQuestion> {
        val played = playedStore.played(PlayedContentStore.GAME_GANDEGOO_QUESTIONS)
        return TIERS.map { tier ->
            val candidates = cat.questions.filter { it.points == tier }
            var freshQuestions = candidates.filter { questionKey(cat, it) !in played }
            if (freshQuestions.isEmpty()) {
                playedStore.forget(
                    PlayedContentStore.GAME_GANDEGOO_QUESTIONS,
                    candidates.map { questionKey(cat, it) },
                )
                freshQuestions = candidates
            }
            val question = freshQuestions.random()
            playedStore.markPlayed(PlayedContentStore.GAME_GANDEGOO_QUESTIONS, questionKey(cat, question))
            question
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
