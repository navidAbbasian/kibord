package com.navidabbasian.kibord.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.settings.LocalSettingsRepository
import com.navidabbasian.kibord.core.stats.GameStats
import com.navidabbasian.kibord.core.settings.ThemeMode
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.rememberMorphingBlobShape
import com.navidabbasian.kibord.core.ui.theme.VioletDeep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** تب تنظیمات هاب — کلیدهای بلابی، انتخاب تم سنگریزه‌ای و بلیت درباره */
@Composable
fun SettingsScreen() {
    val repo = LocalSettingsRepository.current
    val sound = LocalSoundManager.current
    val scope = rememberCoroutineScope()

    val soundEnabled by (repo?.soundEnabled ?: MutableStateFlow(true)).collectAsState(true)
    val musicEnabled by (repo?.musicEnabled ?: MutableStateFlow(true)).collectAsState(true)
    val vibrationEnabled by (repo?.vibrationEnabled ?: MutableStateFlow(true)).collectAsState(true)
    val themeMode by (repo?.themeMode ?: MutableStateFlow(ThemeMode.SYSTEM)).collectAsState(ThemeMode.SYSTEM)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "⚙️", fontSize = 52.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = "تنظیمات", accent = VioletPrimary, rotation = -2f)
            }
        }

        // ---- صدا و لرزش: هر کلید یک بلاب مستقل ----
        item {
            BlobToggleRow(
                emoji = "🔊",
                label = "افکت‌های صوتی",
                checked = soundEnabled,
                index = 0,
                onCheckedChange = {
                    sound?.playButtonClick()
                    scope.launch { repo?.setSoundEnabled(it) }
                }
            )
        }
        item {
            BlobToggleRow(
                emoji = "🎵",
                label = "موسیقی پس‌زمینه",
                checked = musicEnabled,
                index = 1,
                onCheckedChange = {
                    sound?.playButtonClick()
                    scope.launch { repo?.setMusicEnabled(it) }
                }
            )
        }
        item {
            BlobToggleRow(
                emoji = "📳",
                label = "لرزش",
                checked = vibrationEnabled,
                index = 2,
                onCheckedChange = {
                    if (it) sound?.vibrate(40)
                    scope.launch { repo?.setVibrationEnabled(it) }
                }
            )
        }

        // ---- ظاهر: سه سنگریزه‌ی تم ----
        item {
            Text(
                text = "ظاهر",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        item {
            ThemePebbles(
                current = themeMode,
                onSelect = { mode ->
                    sound?.playButtonClick()
                    scope.launch { repo?.setThemeMode(mode) }
                }
            )
        }

        // ---- آمار و نشان‌ها ----
        item {
            Text(
                text = "آمار و نشان‌ها",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        item {
            StatsSection()
        }

        // ---- بانک محتوا: به‌روزرسانی و افزودن کتگوری ----
        item {
            Text(
                text = "بانک محتوا",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        item {
            ContentStudioSection()
        }

        // ---- صدای شما: گزارش خطا و پیشنهاد ----
        item {
            Text(
                text = "صدای شما",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )
        }
        item {
            FeedbackSection()
        }

        // ---- درباره: بلیت ----
        item {
            TicketCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                accent = VioletPrimary,
                tilt = 1.2f
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    AboutRow(emoji = "🏆", label = "نام اپ", value = "کی برد؟")
                    AboutRow(emoji = "🔖", label = "نسخه", value = "۰.۳.۰")
                    AboutRow(emoji = "🎲", label = "بازی‌ها", value = "۱۲ بازی دورهمی")
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "❤️", fontSize = 26.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ساخته شده با عشق برای شب‌نشینی‌های ایرانی",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/** ردیف کلید بلابی: آواتار ایموجی سنگریزه‌ای + کلید لغزنده‌ی ارگانیک */
@Composable
private fun BlobToggleRow(
    emoji: String,
    label: String,
    checked: Boolean,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extras = kiExtras
    val shape = rememberMorphingBlobShape(phase = index * 1.8f)
    val tilt = if (index % 2 == 0) -1f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = if (index % 2 == 0) (-5).dp else 5.dp)
            .graphicsLayer { rotationZ = tilt }
            .background(extras.glassStrong, shape)
            .border(1.5.dp, if (checked) VioletPrimary.copy(alpha = 0.55f) else extras.glassBorder, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .graphicsLayer { rotationZ = -tilt * 5f }
                .background(
                    if (checked) {
                        Brush.radialGradient(
                            colors = listOf(lerp(VioletPrimary, Color.White, 0.25f), VioletPrimary),
                            center = Offset(0.3f, 0.25f),
                            radius = 130f
                        )
                    } else {
                        Brush.radialGradient(listOf(extras.glass, extras.glass))
                    },
                    blobShape(seed = index * 13 + 5)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        BlobSwitch(checked = checked, seed = index)
    }
}

/** کلید لغزنده‌ی سنگریزه‌ای: بدنه‌ی بلاب و گره‌ی سفیدی که با فنر می‌لغزد */
@Composable
private fun BlobSwitch(checked: Boolean, seed: Int) {
    val extras = kiExtras
    val bias by animateFloatAsState(
        targetValue = if (checked) -1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "switch_bias"
    )
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(32.dp)
            .then(if (checked) Modifier.breathing(intensity = 0.03f, periodMs = 2000, phase = seed * 1f) else Modifier)
            .background(
                if (checked) {
                    Brush.horizontalGradient(listOf(VioletPrimary, VioletDeep))
                } else {
                    Brush.horizontalGradient(listOf(extras.glass, extras.glass))
                },
                blobShape(seed = seed * 17 + 2)
            )
            .border(
                1.5.dp,
                if (checked) Color.White.copy(alpha = 0.4f) else extras.glassBorder,
                blobShape(seed = seed * 17 + 2)
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .align(BiasAlignment(bias, 0f))
                .size(24.dp)
                .background(Color.White, blobShape(seed = seed * 23 + 7)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (checked) "✓" else "",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = VioletDeep
            )
        }
    }
}

/** سه سنگریزه‌ی انتخاب تم: سیستم / روشن / تیره */
@Composable
private fun ThemePebbles(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val extras = kiExtras
    val options = listOf(
        Triple(ThemeMode.SYSTEM, "🌗", "سیستم"),
        Triple(ThemeMode.LIGHT, "☀️", "روشن"),
        Triple(ThemeMode.DARK, "🌙", "تیره"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
    ) {
        options.forEachIndexed { i, (mode, emoji, label) ->
            val selected = mode == current
            val shape = rememberMorphingBlobShape(phase = i * 2.2f)
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.08f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "theme_scale"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = if (i == 1) 10.dp else 0.dp)
                    .graphicsLayer {
                        rotationZ = if (i % 2 == 0) -2.5f else 2.5f
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(92.dp)
                    .background(
                        if (selected) {
                            Brush.radialGradient(
                                colors = listOf(lerp(VioletPrimary, Color.White, 0.2f), VioletPrimary, VioletDeep),
                                center = Offset(0.35f, 0.25f),
                                radius = 260f
                            )
                        } else {
                            Brush.radialGradient(listOf(extras.glassStrong, extras.glassStrong))
                        },
                        shape
                    )
                    .border(
                        2.dp,
                        if (selected) Color.White.copy(alpha = 0.5f) else extras.glassBorder,
                        shape
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(mode) }
                    .padding(top = 18.dp)
            ) {
                Text(text = emoji, fontSize = 24.sp)
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** مقصد پیام‌های بازیکن‌ها */
private const val FEEDBACK_EMAIL = "abbasian.navid@gmail.com"

/**
 * بخش «صدای شما»: بازیکن نوع پیام را انتخاب می‌کند، متنش را می‌نویسد و
 * با اپ ایمیل گوشی برای سازنده می‌فرستد؛ مشخصات دستگاه هم ضمیمه می‌شود.
 */
@Composable
private fun FeedbackSection() {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val context = LocalContext.current
    var isBug by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(extras.glassStrong, RoundedCornerShape(24.dp))
            .border(1.5.dp, extras.glassBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeedbackPill(
                text = "🐞 گزارش خطا",
                selected = isBug,
                onClick = { sound?.playButtonClick(); isBug = true },
            )
            FeedbackPill(
                text = "💡 پیشنهاد و نظر",
                selected = !isBug,
                onClick = { sound?.playButtonClick(); isBug = false },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        BasicTextField(
            value = message,
            onValueChange = {
                message = it.take(2000)
                sent = false
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(VioletPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 110.dp)
                .background(extras.glass, RoundedCornerShape(18.dp))
                .border(1.5.dp, extras.glassBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
            decorationBox = { inner ->
                if (message.isEmpty()) {
                    Text(
                        text = if (isBug) "چی خراب بود؟ کجای بازی؟ چطور پیش اومد؟"
                        else "چه بازی یا امکانی دوست داری اضافه بشه؟",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
        Spacer(modifier = Modifier.height(12.dp))

        KButton(
            text = if (isBug) "ارسال گزارش ✉️" else "ارسال پیشنهاد ✉️",
            enabled = message.isNotBlank(),
            onClick = {
                sound?.playButtonClick()
                if (sendFeedback(context, isBug, message)) {
                    sent = true
                    message = ""
                }
            },
        )
        if (sent) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "مرسی! پیامت توی اپ ایمیل آماده‌ست — فقط دکمه‌ی ارسالش رو بزن 💌",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = extras.success,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** تراشه‌ی قرصی انتخاب نوع پیام */
@Composable
private fun FeedbackPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val extras = kiExtras
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(
                if (selected) Brush.verticalGradient(listOf(lerp(VioletPrimary, Color.White, 0.12f), VioletPrimary))
                else Brush.verticalGradient(listOf(extras.glass, extras.glass)),
                RoundedCornerShape(50)
            )
            .border(
                1.5.dp,
                if (selected) Color.White.copy(alpha = 0.5f) else extras.glassBorder,
                RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

/**
 * باز کردن اپ ایمیل با پیام آماده؛ اگر اپ ایمیلی نبود،
 * از پنجره‌ی اشتراک‌گذاری (پیام‌رسان‌ها) استفاده می‌شود.
 */
private fun sendFeedback(context: Context, isBug: Boolean, message: String): Boolean {
    val version = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) {
        "?"
    }
    val subject = (if (isBug) "گزارش خطا" else "پیشنهاد") + " — کی برد؟ نسخه $version"
    val body = message +
        "\n\n—\nدستگاه: ${Build.MANUFACTURER} ${Build.MODEL}" +
        "\nاندروید: ${Build.VERSION.RELEASE}" +
        "\nنسخه اپ: $version"

    val email = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    return try {
        context.startActivity(email)
        true
    } catch (_: ActivityNotFoundException) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "$subject\n\n$body\n\nگیرنده: $FEEDBACK_EMAIL")
        }
        try {
            context.startActivity(Intent.createChooser(share, "ارسال با…"))
            true
        } catch (_: Exception) {
            false
        }
    }
}

@Composable
private fun AboutRow(emoji: String, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 17.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/** یک نشان: باز یا قفل */
private data class KiBadge(
    val emoji: String,
    val title: String,
    val hint: String,
    val unlocked: Boolean,
)

/**
 * بخش آمار و نشان‌ها: خلاصه‌ی بازی‌های انجام‌شده، پرافتخارترین اسم‌ها و
 * نشان‌هایی که با بازی کردن باز می‌شوند — همه محلی و بدون سرور.
 */
@Composable
private fun StatsSection() {
    val extras = kiExtras
    val context = LocalContext.current

    val total = GameStats.totalPlays(context)
    val distinct = GameStats.distinctGamesPlayed(context)
    val bestWin = GameStats.bestWinCount(context)
    val topGames = GameStats.playsByGame(context).entries.sortedByDescending { it.value }.take(3)
    val topWinners = GameStats.topWinners(context, 3)
    val allGames = gameCatalog + moreGamesCatalog

    val badges = listOf(
        KiBadge("🎈", "قدم اول", "اولین بازی", total >= 1),
        KiBadge("🌙", "شب‌نشین", "۱۰ بازی", total >= 10),
        KiBadge("🏠", "پاتوق‌دار", "۵۰ بازی", total >= 50),
        KiBadge("🎪", "جشنواره", "۱۰۰ بازی", total >= 100),
        KiBadge("🧭", "کاشف", "۵ بازی متفاوت", distinct >= 5),
        KiBadge("🃏", "همه‌فن‌حریف", "همه‌ی بازی‌ها", distinct >= 12),
        KiBadge("⭐", "ستاره‌ی جمع", "۵ برد با یک اسم", bestWin >= 5),
        KiBadge("👑", "اسطوره", "۲۰ برد با یک اسم", bestWin >= 20),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kiExtras.glassStrong, RoundedCornerShape(24.dp))
            .border(1.5.dp, kiExtras.glassBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
    ) {
        if (total == 0) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "📊", fontSize = 34.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "هنوز بازی‌ای ثبت نشده — برید بترکونید تا اینجا پر شه! 🎉",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // ---- سه شاخص اصلی ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatPebble(value = total.toPersianDigits(), label = "بازی")
                StatPebble(value = distinct.toPersianDigits(), label = "بازی متفاوت")
                StatPebble(value = bestWin.toPersianDigits(), label = "رکورد برد")
            }

            if (topGames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "پُرطرفدارهای جمع:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                topGames.forEach { (id, count) ->
                    val game = allGames.firstOrNull { it.id == id }
                    Text(
                        text = "${game?.emoji ?: "🎲"} ${game?.title ?: id} — ${count.toPersianDigits()} بار",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (topWinners.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "پرافتخارترین‌ها:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                topWinners.forEachIndexed { i, (name, wins) ->
                    Text(
                        text = "${listOf("🥇", "🥈", "🥉").getOrElse(i) { "🏅" }} $name — ${wins.toPersianDigits()} برد",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "نشان‌ها (${badges.count { it.unlocked }.toPersianDigits()} از ${badges.size.toPersianDigits()})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        badges.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { badge ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = if (badge.unlocked) 1f else 0.38f }
                            .background(
                                if (badge.unlocked) VioletPrimary.copy(alpha = 0.16f) else kiExtras.glass,
                                RoundedCornerShape(16.dp)
                            )
                            .border(
                                1.5.dp,
                                if (badge.unlocked) VioletPrimary.copy(alpha = 0.5f) else kiExtras.glassBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = if (badge.unlocked) badge.emoji else "🔒", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = badge.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = badge.hint,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** سنگریزه‌ی یک شاخص آماری */
@Composable
private fun StatPebble(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = VioletPrimary,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
