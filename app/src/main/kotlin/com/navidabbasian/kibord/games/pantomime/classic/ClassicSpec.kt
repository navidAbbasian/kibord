package com.navidabbasian.kibord.games.pantomime.classic

import com.navidabbasian.kibord.core.content.CustomContentStore
import com.navidabbasian.kibord.core.content.PlayedContentStore

/**
 * مشخصات یک بازیِ سوار بر موتور پانتومیم کلاسیک.
 *
 * موتور (وی‌مدل + صفحه‌ها) بین پانتومیم کلاسیک و بازی‌های هم‌قاعده‌اش
 * (مثل «صداشو درار») مشترک است؛ هر بازی فقط بانک کلمات، سابقه‌ی جداگانه
 * و متن‌های خودش را از این‌جا تزریق می‌کند.
 */
data class ClassicSpec(
    /** کلید نشستِ ذخیره‌شده برای مقاومت در برابر مرگ پروسه */
    val sessionKey: String,
    /** نام فایل بانک کلمات در بانک محتوا */
    val assetName: String,
    /** کلید محتوای سفارشی کاربر — null یعنی این بازی محتوای سفارشی ندارد */
    val customKey: String?,
    val playedWordsKey: String,
    val playedCategoriesKey: String,
    /** نام و ایموجی کارت ریسک طلایی (مثلاً «موضوع طلایی» یا «صدای طلایی») */
    val goldenName: String,
    val goldenEmoji: String,
    /** توضیح زیر حباب طلایی در صفحه‌ی انتخاب */
    val goldenHint: String,
    /** هر تیم هر خانه‌ی «کتگوری+امتیاز» را فقط یک بار بتواند بازی کند */
    val lockPlayedCells: Boolean = false,
    /** شناسه‌ی راهنما و مشخسات کارت اشتراک برد */
    val helpGameId: String,
    val shareTitle: String,
    val shareEmoji: String,
) {
    companion object {
        val PANTOMIME = ClassicSpec(
            sessionKey = "session_pantomime_classic",
            assetName = "pantomime.json",
            customKey = CustomContentStore.PANTOMIME,
            playedWordsKey = PlayedContentStore.GAME_PANTOMIME,
            playedCategoriesKey = PlayedContentStore.GAME_PANTOMIME_CATEGORIES,
            goldenName = "موضوع طلایی",
            goldenEmoji = "⭐",
            goldenHint = "ریسک بزرگ: ۳ دقیقه، شکست = باخت کل بازی",
            helpGameId = "pantomime_classic",
            shareTitle = "پانتومیم کلاسیک",
            shareEmoji = "🤫",
        )
    }
}
