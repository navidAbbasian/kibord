package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.rate.RateApp
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import kotlinx.coroutines.delay

/**
 * درخواست امتیاز در مایکت روی صفحه‌ی برنده.
 *
 * چند لحظه صبر می‌کند تا جشنِ برد دیده شود، بعد بالا می‌آید. با چرخش صفحه
 * یا بازساخت اکتیویتی دوباره ظاهر نمی‌شود چون تصمیمِ گرفته‌شده ذخیره می‌ماند.
 */
@Composable
fun RatePromptDialog(delayMillis: Long = 1400) {
    val context = LocalContext.current
    val sound = LocalSoundManager.current
    val extras = kiExtras

    // تصمیمِ همین نمایش: تا وقتی صفحه‌ی برنده باز است دیگر برنمی‌گردد
    var handled by rememberSaveable { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(handled) {
        if (handled) return@LaunchedEffect
        delay(delayMillis)
        if (RateApp.shouldPrompt(context)) visible = true
    }

    if (!visible) return

    val close: () -> Unit = {
        visible = false
        handled = true
    }

    Dialog(onDismissRequest = {
        // بستن با لمس بیرون = «بعداً»
        RateApp.postpone(context)
        close()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .border(2.dp, extras.glassBorderStrong, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                repeat(5) { i ->
                    BobbingEmoji(emoji = "⭐", fontSize = 26.sp, phase = i * 1.1f)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = "خوش گذشت؟", accent = extras.gold, rotation = -2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "اگه دورهمیتون با «کی برد؟» حال داد، یه امتیاز توی مایکت بهمون بده — همین یه کار باعث می‌شه بازیکن‌های بیشتری پیدامون کنن 💜",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))
            KButton(
                text = "امتیاز می‌دم ⭐",
                onClick = {
                    sound?.playButtonClick()
                    RateApp.openRating(context)
                    close()
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(
                text = "بعداً",
                style = KButtonStyle.Glass,
                onClick = {
                    sound?.playButtonClick()
                    RateApp.postpone(context)
                    close()
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "دیگه نپرس",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        sound?.playButtonClick()
                        RateApp.stopAsking(context)
                        close()
                    }
                    .padding(6.dp),
            )
        }
    }
}
