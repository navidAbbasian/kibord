package com.navidabbasian.kibord

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.audio.SoundManager
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.settings.LocalSettingsRepository
import com.navidabbasian.kibord.core.settings.SettingsRepository
import com.navidabbasian.kibord.core.settings.ThemeMode
import com.navidabbasian.kibord.core.ui.theme.KiBordTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var soundManager: SoundManager
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        soundManager = SoundManager(this)
        settingsRepository = SettingsRepository(this)

        // به‌روزرسانی دوره‌ای بانک کلمات و سوالات از مخزن گیت‌هاب (حداکثر روزی یک بار)
        lifecycleScope.launch {
            ContentBank.refreshIfStale(applicationContext)
        }

        // همگام‌سازی پرچم‌های صدا/موسیقی/لرزش با تنظیمات ذخیره‌شده
        lifecycleScope.launch {
            settingsRepository.soundEnabled.collect { soundManager.isSoundEnabled = it }
        }
        lifecycleScope.launch {
            settingsRepository.musicEnabled.collect { soundManager.isMusicEnabled = it }
        }
        lifecycleScope.launch {
            settingsRepository.vibrationEnabled.collect { soundManager.isVibrationEnabled = it }
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // آیکون‌های نوار وضعیت تابع تمِ اپ‌اند نه تم سیستم:
            // تم تیره → آیکون سفید، تم روشن → آیکون تیره
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            KiBordTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(
                    LocalSoundManager provides soundManager,
                    LocalSettingsRepository provides settingsRepository,
                    LocalLayoutDirection provides LayoutDirection.Rtl
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        KiBordApp()
                    }
                }
            }
        }
        soundManager.startBackgroundMusic(MusicTrack.HUB)
    }

    override fun onResume() {
        super.onResume()
        soundManager.resumeBackgroundMusic()
    }

    override fun onPause() {
        super.onPause()
        soundManager.pauseBackgroundMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }
}
