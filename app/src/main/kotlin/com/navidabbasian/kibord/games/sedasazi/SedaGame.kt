package com.navidabbasian.kibord.games.sedasazi

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.pantomime.classic.ClassicPantomimeGame
import com.navidabbasian.kibord.games.pantomime.classic.ClassicSpec
import com.navidabbasian.kibord.games.pantomime.classic.ClassicViewModel

/**
 * «صداشو درار» — پانتومیمِ برعکس: حرف زدن و اشاره ممنوع، فقط صداسازی!
 *
 * قواعد، راندها و ریسک طلایی همان موتور پانتومیم کلاسیک است؛ این‌جا فقط
 * بانک کلمات صداپذیر، سابقه‌ی جداگانه و متن‌های خودش تزریق می‌شود.
 */
val SedaSpec = ClassicSpec(
    sessionKey = "session_sedasazi",
    assetName = "sedasazi.json",
    customKey = null,
    playedWordsKey = PlayedContentStore.GAME_SEDASAZI,
    playedCategoriesKey = PlayedContentStore.GAME_SEDASAZI_CATEGORIES,
    goldenName = "صدای غیرممکن",
    goldenEmoji = "⭐",
    goldenHint = "ریسک بزرگ: صدای چیزِ بی‌صدا رو دربیار! شکست = باخت کل بازی",
    lockPlayedCells = true,
    helpGameId = "sedasazi",
    shareTitle = "صداشو درار",
    shareEmoji = "🔊",
)

class SedaViewModel(application: Application) : ClassicViewModel(application, SedaSpec)

/** ریشه‌ی «صداشو درار» — موتور کلاسیک با مشخصات خودش */
@Composable
fun SedaGame(onExitToHub: () -> Unit) {
    val viewModel: SedaViewModel = viewModel()
    ClassicPantomimeGame(onExitToHub = onExitToHub, viewModel = viewModel)
}
