package com.navidabbasian.kibord.games.taboo.data

import android.content.Context
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.taboo.model.TabooCard
import kotlinx.serialization.json.Json

/**
 * بانک کارت‌های تابو از بانک محتوا (کش دانلودی یا فایل داخلی) —
 * کارت تکراری نمی‌آید تا کل بانک یک دور کامل بازی شود.
 */
class TabooRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(context)

    private var allCards: List<TabooCard> = emptyList()

    fun load() {
        if (allCards.isNotEmpty()) return
        allCards = try {
            json.decodeFromString<List<TabooCard>>(ContentBank.open(context, "taboo.json"))
        } catch (_: Exception) {
            emptyList()
        }.filter { it.word.isNotBlank() && it.forbidden.isNotEmpty() }
    }

    /** دسته‌ی بُرخورده‌ی کارت‌های بازی‌نشده؛ با اتمام بانک، سابقه ریست می‌شود */
    fun prepareDeck(): List<TabooCard> {
        val played = playedStore.played(KEY)
        var fresh = allCards.filter { it.word !in played }
        if (fresh.isEmpty()) {
            playedStore.clear(KEY)
            fresh = allCards
        }
        return fresh.shuffled()
    }

    fun markPlayed(card: TabooCard) {
        playedStore.markPlayed(KEY, card.word)
    }

    companion object {
        const val KEY = "taboo"
    }
}
