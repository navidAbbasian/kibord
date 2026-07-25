package com.navidabbasian.kibord.games.esmfamilsorati.data

import android.content.Context
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsTopic
import kotlinx.serialization.json.Json

/**
 * بارگذاری بانک موضوع‌های اسم فامیل سرعتی از بانک محتوا (کش دانلودی یا assets).
 * موضوع را خود بازیکن‌ها از لیست انتخاب می‌کنند؛ نیازی به قرعه و سابقه نیست.
 */
class EfsTopicRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    var topics: List<EfsTopic> = emptyList()
        private set

    fun load() {
        if (topics.isNotEmpty()) return
        topics = try {
            json.decodeFromString<List<EfsTopic>>(ContentBank.open(context, "esmfamilsorati.json"))
                .ifEmpty { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private val fallback = listOf(
        EfsTopic("boy_name", "اسم پسر", "👦"),
        EfsTopic("girl_name", "اسم دختر", "👧"),
        EfsTopic("family_name", "فامیلی", "👨‍👩‍👧"),
        EfsTopic("city", "شهر ایران", "🏙️"),
        EfsTopic("country", "کشور", "🌍"),
        EfsTopic("animal", "حیوان", "🐾"),
        EfsTopic("fruit", "میوه", "🍎"),
        EfsTopic("food", "غذای ایرانی", "🍲"),
        EfsTopic("object", "اشیا", "📦"),
        EfsTopic("job", "شغل", "🧑‍🔧"),
        EfsTopic("celebrity", "سلبریتی", "📸"),
        EfsTopic("football_team", "تیم فوتبال", "🏟️"),
    )
}
