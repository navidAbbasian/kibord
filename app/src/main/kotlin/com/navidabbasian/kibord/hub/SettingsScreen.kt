package com.navidabbasian.kibord.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.settings.LocalSettingsRepository
import com.navidabbasian.kibord.core.settings.ThemeMode
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** تب تنظیمات هاب — کلیدهای سراسری صدا/موسیقی/لرزش و درباره‌ی اپ */
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "تنظیمات",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
            }
        }

        item {
            SettingsSection(title = "صدا و لرزش") {
                ToggleRow(
                    icon = Icons.Default.VolumeUp,
                    label = "افکت‌های صوتی",
                    checked = soundEnabled,
                    onCheckedChange = {
                        sound?.playButtonClick()
                        scope.launch { repo?.setSoundEnabled(it) }
                    }
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.MusicNote,
                    label = "موسیقی پس‌زمینه",
                    checked = musicEnabled,
                    onCheckedChange = {
                        sound?.playButtonClick()
                        scope.launch { repo?.setMusicEnabled(it) }
                    }
                )
                SettingsDivider()
                ToggleRow(
                    icon = Icons.Default.Vibration,
                    label = "لرزش",
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        if (it) sound?.vibrate(40)
                        scope.launch { repo?.setVibrationEnabled(it) }
                    }
                )
            }
        }

        item {
            SettingsSection(title = "ظاهر") {
                ThemeModeRow(
                    current = themeMode,
                    onSelect = { mode ->
                        sound?.playButtonClick()
                        scope.launch { repo?.setThemeMode(mode) }
                    }
                )
            }
        }

        item {
            SettingsSection(title = "درباره") {
                InfoRow(icon = Icons.Default.SportsEsports, label = "نام اپ", value = "کی برد؟")
                SettingsDivider()
                InfoRow(icon = Icons.Default.SportsEsports, label = "نسخه", value = "۱.۰")
                SettingsDivider()
                InfoRow(icon = Icons.Default.SportsEsports, label = "بازی‌ها", value = "۵ بازی دورهمی")
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = kiExtras.danger,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun ThemeModeRow(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to "🌓 سیستم",
        ThemeMode.LIGHT to "☀️ روشن",
        ThemeMode.DARK to "🌙 تیره",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == current
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else kiExtras.glass,
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else kiExtras.glassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = kiExtras.glassBorder,
        thickness = 0.5.dp
    )
}
