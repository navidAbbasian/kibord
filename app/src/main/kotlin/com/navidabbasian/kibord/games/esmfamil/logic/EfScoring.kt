package com.navidabbasian.kibord.games.esmfamil.logic

import com.navidabbasian.kibord.games.esmfamil.model.EfAnswer

/**
 * امتیازدهی خودکار اسم فامیل:
 * - کلمه‌ی یکتا در موضوع: ۱۰
 * - کلمه‌ی مشترک بین چند نفر: ۵
 * - تنها جواب‌دهنده‌ی یک موضوع: ۲۰
 * - خالی، ردشده با رای جمع، یا ناسازگار با حرف راند: ۰
 */
object EfScoring {

    /** نرمال‌سازی برای مقایسه: حذف فاصله و نیم‌فاصله، یکسان‌سازی حروف عربی/فارسی */
    fun normalize(text: String): String = text
        .trim()
        .replace("‌", "")
        .replace(" ", "")
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .lowercase()

    /** آیا کلمه با حرف راند شروع می‌شود؟ (آ و ا یکسان شمرده می‌شوند) */
    fun startsWithLetter(text: String, letter: String): Boolean {
        if (letter.isBlank()) return true
        val n = normalize(text)
        return n.isNotEmpty() && n.startsWith(normalize(letter))
    }

    /** رد بودن با رای اکثریتِ بازیکنانِ دیگر (صاحب کلمه رای ندارد) */
    fun isRejected(rejectVotes: Int, playerCount: Int): Boolean =
        rejectVotes * 2 > (playerCount - 1)

    /** محاسبه‌ی امتیاز همه‌ی جواب‌ها با درنظرگرفتن رای‌های رد */
    fun computeScores(answers: List<EfAnswer>, letter: String): List<EfAnswer> =
        answers.groupBy { it.topic }.flatMap { (_, topicAnswers) ->
            val valid = topicAnswers.filter {
                it.text.isNotBlank() && !it.rejected && startsWithLetter(it.text, letter)
            }
            val byNorm = valid.groupBy { normalize(it.text) }
            topicAnswers.map { a ->
                val invalid = a.text.isBlank() || a.rejected || !startsWithLetter(a.text, letter)
                a.copy(
                    score = when {
                        invalid -> 0
                        valid.size == 1 -> 20
                        byNorm.getValue(normalize(a.text)).size == 1 -> 10
                        else -> 5
                    }
                )
            }
        }

    /** جمع امتیاز راند به تفکیک بازیکن */
    fun roundTotals(answers: List<EfAnswer>): Map<String, Int> =
        answers.groupBy { it.player }.mapValues { (_, list) -> list.sumOf { it.score } }
}
