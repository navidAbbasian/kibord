package com.navidabbasian.kibord.hub

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.settings.LocalSettingsRepository
import com.navidabbasian.kibord.core.settings.ThemeMode
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.rememberMorphingBlobShape
import com.navidabbasian.kibord.core.ui.theme.VioletDeep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
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
                    AboutRow(emoji = "🔖", label = "نسخه", value = "۰.۱.۷")
                    AboutRow(emoji = "🎲", label = "بازی‌ها", value = "۵ بازی دورهمی")
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
