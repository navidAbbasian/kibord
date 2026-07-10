package com.navidabbasian.kibord.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf
import com.navidabbasian.kibord.R

/** موسیقی‌های پس‌زمینه‌ی بخش‌های مختلف اپ */
enum class MusicTrack {
    /** هاب و منوها */
    HUB,
    /** کلمز — راند اول: توضیح */
    KALAMZ_ROUND_1,
    /** کلمز — راند دوم: یک کلمه */
    KALAMZ_ROUND_2,
    /** کلمز — راند سوم: پانتومیم */
    KALAMZ_ROUND_3,
    /** دور — حین بازی */
    DOR,
    /** گنده‌گو — حین بازی */
    GANDEGOO,
    /** پانتومیم کلاسیک و رقابتی — حین بازی */
    PANTOMIME,
    /** اسم فامیل — حین بازی */
    ESM_FAMIL
}

/**
 * مدیر صدای واحد کل اپ: افکت‌ها، موسیقی پس‌زمینه، موسیقی تنش بمب و لرزش.
 * پرچم‌های فعال‌بودن از SettingsRepository همگام می‌شوند.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var tensionPlayer: MediaPlayer? = null
    private var currentTrack: MusicTrack? = null

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    // ---- افکت‌های کلمز ----
    private var idKalamzButtonClick = 0
    private var idKalamzCorrectWord = 0
    private var idKalamzWordSkip = 0
    private var idKalamzTimerTick = 0
    private var idKalamzTimerWarning = 0
    private var idKalamzTimerEnd = 0
    private var idKalamzTurnStart = 0
    private var idKalamzRoundStart = 0
    private var idKalamzRoundEnd = 0
    private var idKalamzGameOver = 0

    // ---- افکت‌های دور ----
    private var idDorTickNormal = 0
    private var idDorTickFast = 0
    private var idDorExplosion = 0
    private var idDorWordCorrect = 0
    private var idDorNextTurn = 0
    private var idDorTeamEliminated = 0
    private var idDorGameOver = 0
    private var idDorCountdownBeep = 0

    var isSoundEnabled = true
    var isVibrationEnabled = true
    var isMusicEnabled = true
        set(value) {
            field = value
            if (!value) {
                stopBombTension()
                try { mediaPlayer?.release() } catch (_: Exception) {}
                mediaPlayer = null
            } else {
                currentTrack?.let { startBackgroundMusic(it) }
            }
        }

    init {
        idKalamzButtonClick = load(R.raw.kalamz_button_click)
        idKalamzCorrectWord = load(R.raw.kalamz_correct_word)
        idKalamzWordSkip = load(R.raw.kalamz_word_skip)
        idKalamzTimerTick = load(R.raw.kalamz_timer_tick)
        idKalamzTimerWarning = load(R.raw.kalamz_timer_warning)
        idKalamzTimerEnd = load(R.raw.kalamz_timer_end)
        idKalamzTurnStart = load(R.raw.kalamz_turn_start)
        idKalamzRoundStart = load(R.raw.kalamz_round_start)
        idKalamzRoundEnd = load(R.raw.kalamz_round_end)
        idKalamzGameOver = load(R.raw.kalamz_game_over)

        idDorTickNormal = load(R.raw.dor_tick_normal)
        idDorTickFast = load(R.raw.dor_tick_fast)
        idDorExplosion = load(R.raw.dor_explosion)
        idDorWordCorrect = load(R.raw.dor_word_correct)
        idDorNextTurn = load(R.raw.dor_next_turn)
        idDorTeamEliminated = load(R.raw.dor_team_eliminated)
        idDorGameOver = load(R.raw.dor_game_over)
        idDorCountdownBeep = load(R.raw.dor_countdown_beep)
    }

    private fun load(resId: Int): Int =
        try { soundPool.load(appContext, resId, 1) } catch (_: Exception) { 0 }

    // ---- کلمز ----
    fun playButtonClick() = play(idKalamzButtonClick, 0.8f)
    fun playCorrectWord() = play(idKalamzCorrectWord, 0.9f, rate = 1.1f)
    fun playWordSkip() = play(idKalamzWordSkip, 0.65f)
    fun playTimerTick() = play(idKalamzTimerTick, 0.35f)
    fun playTimerWarning() = play(idKalamzTimerWarning, 1f, rate = 0.95f)
    fun playTimerEnd() = play(idKalamzTimerEnd, 1f)
    fun playTurnStart() = play(idKalamzTurnStart, 0.9f)
    fun playRoundStart() = play(idKalamzRoundStart, 1f)
    fun playRoundEnd() = play(idKalamzRoundEnd, 1f)
    fun playGameOver() = play(idKalamzGameOver, 1f)

    // ---- دور ----
    fun playDorTickNormal() = play(idDorTickNormal, 0.5f)
    fun playDorTickFast() = play(idDorTickFast, 0.7f)
    fun playDorExplosion() = play(idDorExplosion, 1f)
    fun playDorWordCorrect() = play(idDorWordCorrect, 0.9f)
    fun playDorNextTurn() = play(idDorNextTurn, 0.8f)
    fun playDorTeamEliminated() = play(idDorTeamEliminated, 1f)
    fun playDorGameOver() = play(idDorGameOver, 1f)
    fun playDorCountdownBeep() = play(idDorCountdownBeep, 0.8f)

    private fun play(id: Int, volume: Float = 1f, rate: Float = 1f) {
        if (!isSoundEnabled || id == 0) return
        try { soundPool.play(id, volume, volume, 1, 0, rate) } catch (_: Exception) {}
    }

    // ---- لرزش (با گارد نسخه تا API 21) ----
    fun vibrate(durationMs: Long = 50) {
        if (!isVibrationEnabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun vibratePattern(pattern: LongArray) {
        if (!isVibrationEnabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}
    }

    // ---- موسیقی پس‌زمینه ----
    /** اگر همان تِرَک در حال پخش باشد تغییری نمی‌دهد */
    fun switchMusic(track: MusicTrack) {
        if (currentTrack == track && mediaPlayer?.isPlaying == true) return
        startBackgroundMusic(track)
    }

    fun startBackgroundMusic(track: MusicTrack) {
        currentTrack = track
        if (!isMusicEnabled) return
        val resId = when (track) {
            MusicTrack.HUB -> R.raw.kalamz_music_menu
            MusicTrack.KALAMZ_ROUND_1 -> R.raw.kalamz_music_round1
            MusicTrack.KALAMZ_ROUND_2 -> R.raw.kalamz_music_round2
            MusicTrack.KALAMZ_ROUND_3 -> R.raw.kalamz_music_round3
            MusicTrack.DOR -> R.raw.dor_background_music
            MusicTrack.GANDEGOO -> R.raw.kalamz_music_round1
            MusicTrack.PANTOMIME -> R.raw.kalamz_music_round3
            MusicTrack.ESM_FAMIL -> R.raw.kalamz_music_round2
        }
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(appContext, resId)?.apply {
                isLooping = true
                setVolume(0.22f, 0.22f)
                start()
            }
        } catch (_: Exception) {}
    }

    fun pauseBackgroundMusic() {
        try { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() } catch (_: Exception) {}
        try { if (tensionPlayer?.isPlaying == true) tensionPlayer?.pause() } catch (_: Exception) {}
    }

    fun resumeBackgroundMusic() {
        if (!isMusicEnabled) return
        try { if (mediaPlayer?.isPlaying == false) mediaPlayer?.start() } catch (_: Exception) {}
        try { if (tensionPlayer?.isPlaying == false) tensionPlayer?.start() } catch (_: Exception) {}
    }

    // ---- موسیقی تنش بمب (دور — ده ثانیه‌ی آخر) ----
    fun startBombTension() {
        if (!isMusicEnabled || tensionPlayer != null) return
        try {
            tensionPlayer = MediaPlayer.create(appContext, R.raw.dor_bomb_tension)?.apply {
                isLooping = true
                setVolume(0.6f, 0.6f)
                start()
            }
        } catch (_: Exception) {}
    }

    fun stopBombTension() {
        try { tensionPlayer?.release() } catch (_: Exception) {}
        tensionPlayer = null
    }

    fun release() {
        stopBombTension()
        try { soundPool.release() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }
}

val LocalSoundManager = staticCompositionLocalOf<SoundManager?> { null }
