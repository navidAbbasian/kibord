package com.navidabbasian.kibord.games.forehead

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TeamMedallions
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ================= مدل =================

@Serializable
private data class FhWord(val text: String)

@Serializable
private data class FhCategory(
    val id: String,
    val name: String,
    val emoji: String = "🎲",
    val words: List<FhWord> = emptyList(),
)

@Serializable
sealed class FhPhase {
    /** انتخاب دو یا سه تیم با حباب */
    @Serializable data object TeamCount : FhPhase()
    @Serializable data object TeamNames : FhPhase()
    @Serializable data object Category : FhPhase()
    @Serializable data object Ready : FhPhase()
    @Serializable data object Play : FhPhase()
    @Serializable data object Result : FhPhase()
    @Serializable data object Winner : FhPhase()
}

@Serializable
data class FhUiState(
    val phase: FhPhase = FhPhase.TeamCount,
    /** دو یا سه تیم */
    val teamCount: Int = 2,
    val teamNames: List<String> = List(2) { "" },
    /** نوبت کدام تیم است */
    val currentTeam: Int = 0,
    val totalScores: List<Int> = List(2) { 0 },
    /** راند جاری از ۱ — هر راند یک نوبت برای هر تیم */
    val roundIndex: Int = 1,
    val totalRounds: Int = 3,
    val categories: List<Pair<String, String>> = emptyList(), // (نام، ایموجی)
    val selectedCategory: String = "",
    val turnSeconds: Int = 60,
    val secondsLeft: Int = 0,
    val currentWord: String = "",
    val correctWords: List<String> = emptyList(),
    val passedWords: List<String> = emptyList(),
    /** جمع دستکاری داورانه‌ی امتیازِ همین نوبت در بخش بررسی */
    val turnBonus: Int = 0,
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"

    /** امتیاز نهایی این نوبت پس از بررسی جمع */
    val turnScore: Int get() = correctWords.size + turnBonus
}

enum class FhSoundEvent { TICK, TICK_WARNING, TIME_UP, CORRECT, PASS }

// ================= موتور =================

/**
 * حدس روی پیشونی: گوشی افقی روی پیشونی، بقیه توضیح می‌دهند.
 * خم به جلو = درست، خم به عقب = رد؛ لمس نیمه‌های صفحه هم کار می‌کند.
 */
class ForeheadViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(FhUiState())
    val uiState: StateFlow<FhUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<FhSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<FhSoundEvent> = _soundEvents.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(application)
    private var bank: List<FhCategory> = emptyList()
    private var deck: ArrayDeque<String> = ArrayDeque()
    private var tickerJob: Job? = null

    // ---- حسگر حرکت: گوشی افقی روی پیشونی، صفحه رو به جمع ----
    // خم به جلو → صفحه رو به زمین → محور z منفی = درست
    // خم به عقب → صفحه رو به آسمان → محور z مثبت = رد
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var lastGestureAt = 0L
    private var neutral = true
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (_uiState.value.phase != FhPhase.Play) return
            val z = event.values.getOrNull(2) ?: return
            val now = SystemClock.elapsedRealtime()
            when {
                neutral && z < -7f && now - lastGestureAt > 800 -> {
                    lastGestureAt = now; neutral = false
                    markCorrect()
                }
                neutral && z > 7f && now - lastGestureAt > 800 -> {
                    lastGestureAt = now; neutral = false
                    markPass()
                }
                kotlin.math.abs(z) < 4f -> neutral = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    init {
        // بانک کلمات پیش از بازیابی نشست لازم است تا دسته‌ی جاری بازسازی شود
        bank = try {
            json.decodeFromString<List<FhCategory>>(ContentBank.open(application, "forehead.json"))
        } catch (_: Exception) {
            emptyList()
        }.filter { it.words.isNotEmpty() }
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        if (!restoreSession()) {
            val count = GamePrefs.getInt(application, "forehead_teams", 2).coerceIn(2, 3)
            val names = GamePrefs.getNames(application, "forehead_names")
            _uiState.update {
                it.copy(
                    teamCount = count,
                    teamNames = List(count) { i -> names.getOrElse(i) { "" } },
                    totalScores = List(count) { 0 },
                    turnSeconds = GamePrefs.getInt(application, "forehead_seconds", 60),
                    totalRounds = GamePrefs.getInt(application, "forehead_rounds", 3),
                )
            }
            _uiState.update { s -> s.copy(categories = bank.map { it.name to it.emoji }) }
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun FhUiState.isResumable(): Boolean =
        phase == FhPhase.Ready || phase == FhPhase.Play || phase == FhPhase.Result

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), SESSION_KEY, json.encodeToString(FhUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), SESSION_KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), SESSION_KEY) ?: return false
        val saved = try {
            json.decodeFromString(FhUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), SESSION_KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), SESSION_KEY)
            return false
        }
        // دسته‌ی کلمات گذرا است؛ کلمه‌ی جاری در وضعیت هست و بقیه از بانک تازه می‌آیند
        bank.firstOrNull { it.name == saved.selectedCategory }?.let { cat ->
            val played = playedStore.played("$KEY:${cat.id}")
            val used = (saved.correctWords + saved.passedWords + saved.currentWord).toSet()
            val fresh = cat.words.map { it.text }.filter { it !in played && it !in used }
            deck = ArrayDeque(fresh.shuffled())
        }
        _uiState.value = saved
        // اگر در حال شمارش بودیم، تایمر و حسگر از نو راه می‌افتند تا نوبت کامل ادامه یابد
        if (saved.phase == FhPhase.Play) {
            neutral = true
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
                sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            startTicker()
        }
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), SESSION_KEY)

    private fun emit(e: FhSoundEvent) = _soundEvents.tryEmit(e)

    // ---- راه‌اندازی بازیکن‌ها ----

    /** انتخاب دو یا سه تیم — اسم‌های قبلی حفظ می‌شوند و می‌رویم سراغ اسم‌ها */
    fun setTeamCount(count: Int) {
        val c = count.coerceIn(2, 3)
        _uiState.update { s ->
            s.copy(
                teamCount = c,
                teamNames = List(c) { i -> s.teamNames.getOrElse(i) { "" } },
                totalScores = List(c) { 0 },
                phase = FhPhase.TeamNames,
            )
        }
    }

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(teamNames = s.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeams() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setInt(app, "forehead_teams", s.teamCount)
        GamePrefs.setNames(app, "forehead_names", s.teamNames)
        GamePrefs.setInt(app, "forehead_rounds", s.totalRounds)
        _uiState.update { it.copy(phase = FhPhase.Category, currentTeam = 0, roundIndex = 1) }
    }

    fun selectCategory(name: String) {
        _uiState.update { it.copy(selectedCategory = name, phase = FhPhase.Ready) }
    }

    fun setTurnSeconds(seconds: Int) = _uiState.update { it.copy(turnSeconds = seconds) }

    fun setTotalRounds(rounds: Int) =
        _uiState.update { it.copy(totalRounds = rounds.coerceIn(1, 10)) }

    // ---- نوبت ----

    fun startTurn() {
        val cat = bank.firstOrNull { it.name == _uiState.value.selectedCategory } ?: return
        // کلمات بازی‌نشده؛ بعد از دور کامل، سابقه‌ی همان دسته ریست می‌شود
        val key = "$KEY:${cat.id}"
        val played = playedStore.played(key)
        var fresh = cat.words.map { it.text }.filter { it !in played }
        if (fresh.size < 5) {
            playedStore.clear(key)
            fresh = cat.words.map { it.text }
        }
        GamePrefs.setInt(getApplication(), "forehead_seconds", _uiState.value.turnSeconds)
        deck = ArrayDeque(fresh.shuffled())
        neutral = true
        _uiState.update {
            it.copy(
                phase = FhPhase.Play,
                secondsLeft = it.turnSeconds,
                correctWords = emptyList(),
                passedWords = emptyList(),
                turnBonus = 0,
                currentWord = deck.removeFirstOrNull() ?: "",
            )
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value
                if (s.phase != FhPhase.Play) break
                val left = s.secondsLeft - 1
                emit(if (left <= 5) FhSoundEvent.TICK_WARNING else FhSoundEvent.TICK)
                if (left <= 0) {
                    emit(FhSoundEvent.TIME_UP)
                    endTurn()
                    break
                }
                _uiState.update { it.copy(secondsLeft = left) }
            }
        }
    }

    fun markCorrect() {
        val s = _uiState.value
        if (s.phase != FhPhase.Play || s.currentWord.isBlank()) return
        emit(FhSoundEvent.CORRECT)
        bank.firstOrNull { it.name == s.selectedCategory }?.let { cat ->
            playedStore.markPlayed("$KEY:${cat.id}", s.currentWord)
        }
        _uiState.update {
            it.copy(
                correctWords = it.correctWords + it.currentWord,
                currentWord = deck.removeFirstOrNull() ?: "",
            )
        }
        if (_uiState.value.currentWord.isBlank()) endTurn()
    }

    fun markPass() {
        val s = _uiState.value
        if (s.phase != FhPhase.Play || s.currentWord.isBlank()) return
        emit(FhSoundEvent.PASS)
        _uiState.update {
            it.copy(
                passedWords = it.passedWords + it.currentWord,
                currentWord = deck.removeFirstOrNull() ?: "",
            )
        }
        if (_uiState.value.currentWord.isBlank()) endTurn()
    }

    private fun endTurn() {
        tickerJob?.cancel()
        sensorManager?.unregisterListener(sensorListener)
        _uiState.update { it.copy(phase = FhPhase.Result) }
    }

    // ---- بررسی و ثبت امتیاز ----

    /** بررسی امتیاز نوبت: جمع می‌تواند حکم نهایی را کم و زیاد کند */
    fun adjustTurnScore(delta: Int) {
        if (_uiState.value.phase != FhPhase.Result) return
        _uiState.update { it.copy(turnBonus = it.turnBonus + delta) }
    }

    /** ثبت امتیاز نوبت؛ تیم بعد، راند بعد یا اعلام خودکار برنده */
    fun confirmTurn(changeCategory: Boolean) {
        val s = _uiState.value
        if (s.phase != FhPhase.Result) return
        val lastTeamOfRound = s.currentTeam == s.teamCount - 1
        val gameOver = lastTeamOfRound && s.roundIndex >= s.totalRounds
        if (gameOver) clearSession()
        _uiState.update {
            it.copy(
                totalScores = it.totalScores.mapIndexed { i, v ->
                    if (i == it.currentTeam) v + it.turnScore else v
                },
                currentTeam = if (gameOver) it.currentTeam else (it.currentTeam + 1) % it.teamCount,
                roundIndex = if (!gameOver && lastTeamOfRound) it.roundIndex + 1 else it.roundIndex,
                phase = when {
                    gameOver -> FhPhase.Winner
                    changeCategory -> FhPhase.Category
                    else -> FhPhase.Ready
                },
            )
        }
    }

    fun winners(): List<Int> {
        val scores = _uiState.value.totalScores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        clearSession()
        _uiState.update {
            it.copy(
                totalScores = List(it.teamCount) { 0 },
                currentTeam = 0,
                roundIndex = 1,
                phase = FhPhase.Category,
            )
        }
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        tickerJob?.cancel()
        sensorManager?.unregisterListener(sensorListener)
        clearSession()
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                FhPhase.TeamNames -> s.copy(phase = FhPhase.TeamCount)
                FhPhase.Ready -> s.copy(phase = FhPhase.Category)
                else -> s
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        sensorManager?.unregisterListener(sensorListener)
    }

    companion object {
        const val KEY = "forehead"
        private const val SESSION_KEY = "session_forehead"
    }
}

// ================= ریشه و صفحه‌ها =================

/** ریشه‌ی حدس روی پیشونی */
@Composable
fun ForeheadGame(
    onExitToHub: () -> Unit,
    viewModel: ForeheadViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val context = LocalContext.current

    // مقاوم‌سازی در برابر مرگ پروسه: هنگام رفتن به پس‌زمینه وضعیت ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    // هنگام بازی گوشی افقی می‌شود تا صاف روی پیشونی بنشیند
    LaunchedEffect(state.phase) {
        (context as? Activity)?.requestedOrientation =
            if (state.phase == FhPhase.Play) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                FhSoundEvent.TICK -> sound?.playTimerTick()
                FhSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                FhSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                FhSoundEvent.CORRECT -> {
                    sound?.playCorrectWord()
                    sound?.vibrate(60)
                }
                FhSoundEvent.PASS -> sound?.playWordSkip()
            }
        }
    }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            FhPhase.Play -> sound?.stopBackgroundMusic()
            else -> sound?.switchMusic(MusicTrack.HUB)
        }
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == FhPhase.TeamCount || state.phase == FhPhase.TeamNames || state.phase == FhPhase.Category || state.phase == FhPhase.Ready) {
            GameHelpButton(gameId = "forehead", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (state.phase) {
                FhPhase.TeamCount -> {
                    BackHandler { onExitToHub() }
                    FhTeamCountScreen(onSelected = viewModel::setTeamCount)
                }

                FhPhase.TeamNames -> {
                    BackHandler { viewModel.navigateBack() }
                    FhTeamNamesScreen(
                        state = state,
                        onRounds = viewModel::setTotalRounds,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeams,
                    )
                }

                FhPhase.Category -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    FhCategoryScreen(state = state, onSelect = viewModel::selectCategory)
                }

                FhPhase.Ready -> {
                    BackHandler { viewModel.navigateBack() }
                    FhReadyScreen(
                        state = state,
                        onSeconds = viewModel::setTurnSeconds,
                        onStart = viewModel::startTurn,
                    )
                }

                FhPhase.Play -> {
                    BackHandler { }
                    FhPlayScreen(
                        state = state,
                        onCorrect = viewModel::markCorrect,
                        onPass = viewModel::markPass,
                    )
                }

                FhPhase.Result -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    FhResultScreen(
                        state = state,
                        onAdjust = viewModel::adjustTurnScore,
                        onNextPlayer = { viewModel.confirmTurn(changeCategory = false) },
                        onCategories = { viewModel.confirmTurn(changeCategory = true) },
                    )
                }

                FhPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    FhWinnerScreen(
                        state = state,
                        winners = viewModel.winners(),
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.leaveGame()
                            onExitToHub()
                        },
                    )
                }
            }
        }
    }
}

/** انتخاب دو یا سه تیم — حباب‌های آشنای اپ */
@Composable
private fun FhTeamCountScreen(onSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "🤳", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "حدس روی پیشونی", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "تیمی بازی کنید! هر نوبت یکی گوشی رو می‌ذاره رو پیشونیش — چند تیم هستید؟",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)
        ) {
            listOf(2, 3).forEachIndexed { i, count ->
                ChoiceBubble(
                    main = count.toPersianDigits(),
                    sub = "تیم",
                    size = 132.dp,
                    mainFontSize = 36.sp,
                    accent = kiExtras.teamColors.teamColorFor(i),
                    tilt = if (i == 0) -3f else 3f,
                    phase = i * 1.5f,
                    modifier = if (i == 1) Modifier.offset(y = 18.dp) else Modifier,
                    onClick = { onSelected(count) }
                )
            }
        }
    }
}

/** ورود نام تیم‌ها + تعداد راند */
@Composable
private fun FhTeamNamesScreen(
    state: FhUiState,
    onRounds: (Int) -> Unit,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "🤳", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "اسم تیم‌ها", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(14.dp))

        repeat(state.teamCount) { i ->
            BlobTextField(
                value = state.teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = "تیم ${(i + 1).toPersianDigits()}",
                color = teamColors.teamColorFor(i),
                badge = (i + 1).toPersianDigits(),
                tilt = if (i % 2 == 0) -1.2f else 1.2f,
                phase = i * 1.6f,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "🔁 تعداد راند (هر راند یک نوبت برای هر تیم)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            FhPill(text = "−", selected = false, onClick = { onRounds(state.totalRounds - 1) })
            Text(
                text = state.totalRounds.toPersianDigits(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            FhPill(text = "+", selected = false, onClick = { onRounds(state.totalRounds + 1) })
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "بریم بازی!", onClick = onConfirm)
        }
    }
}

/** تراشه‌ی قرصی کوچک */
@Composable
private fun FhPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(if (selected) accent else extras.glassStrong, RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null) {
                sound?.playButtonClick()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun FhCategoryScreen(state: FhUiState, onSelect: (String) -> Unit) {
    val extras = kiExtras
    val sound = LocalSoundManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        BobbingEmoji(emoji = "🤳", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "حدس روی پیشونی", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "نوبت ${state.teamDisplayName(state.currentTeam)} — یه دسته انتخاب کنید:",
            style = MaterialTheme.typography.bodyMedium,
            color = kiExtras.teamColors.teamColorFor(state.currentTeam),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(count = state.categories.size, key = { state.categories[it].first }) { i ->
                val (name, emoji) = state.categories[i]
                val interaction = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(extras.glassStrong, blobShape(seed = i))
                        .clickable(interactionSource = interaction, indication = null) {
                            sound?.playButtonClick()
                            onSelect(name)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun FhReadyScreen(
    state: FhUiState,
    onSeconds: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val playerColor = kiExtras.teamColors.teamColorFor(state.currentTeam)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (state.roundIndex >= state.totalRounds) {
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1300)) {
                Text(
                    text = "🤫 راند آخره — مجموع‌ها مخفیه!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = extras.gold,
                )
            }
        } else {
            TeamMedallions(
                count = state.teamCount,
                nameOf = state::teamDisplayName,
                scoreOf = { state.totalScores[it] },
                highlight = state.currentTeam,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "راند ${state.roundIndex.toPersianDigits()} از ${state.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BobbingEmoji(emoji = "🤳", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "نوبت ${state.teamDisplayName(state.currentTeam)}",
            style = MaterialTheme.typography.displayMedium,
            fontSize = 30.sp,
            color = playerColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "دسته: ${state.selectedCategory}",
            style = MaterialTheme.typography.titleLarge,
            color = accent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "یکی از تیم گوشی رو می‌ذاره رو پیشونیش — گوشی خودش افقی می‌شه.\n" +
                "خم به جلو = درست ✅   خم به عقب = رد ⏭\n(لمس نیمه‌های صفحه هم کار می‌کنه)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(60, 90).forEach { sec ->
                val selected = state.turnSeconds == sec
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = "${sec.toPersianDigits()} ثانیه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(if (selected) accent else extras.glassStrong, RoundedCornerShape(50))
                        .clickable(interactionSource = interaction, indication = null) {
                            sound?.playButtonClick()
                            onSeconds(sec)
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        KButton(text = "بذار رو پیشونی و شروع کن!", onClick = onStart, accent = playerColor)
    }
}

@Composable
private fun FhPlayScreen(
    state: FhUiState,
    onCorrect: () -> Unit,
    onPass: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val urgent = state.secondsLeft <= 5

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- نیمه‌ی بالا: رد (خم به عقب) ----
        val passInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(Brush.verticalGradient(listOf(extras.warning.copy(alpha = 0.25f), Color.Transparent)))
                .clickable(interactionSource = passInteraction, indication = null, onClick = onPass),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏭ رد — خم به عقب",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = state.secondsLeft.toPersianDigits(),
                    fontSize = if (urgent) 44.sp else 34.sp,
                    fontWeight = FontWeight.Black,
                    color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // ---- کلمه‌ی بزرگ وسط ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 1800)) {
                Text(
                    text = state.currentWord,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    textAlign = TextAlign.Center,
                    lineHeight = 68.sp,
                )
            }
        }

        // ---- نیمه‌ی پایین: درست (خم به جلو) ----
        val okInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, extras.success.copy(alpha = 0.30f))))
                .clickable(interactionSource = okInteraction, indication = null, onClick = onCorrect),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✅ ${state.correctWords.size.toPersianDigits()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = extras.success,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "درست — خم به جلو",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FhResultScreen(
    state: FhUiState,
    onAdjust: (Int) -> Unit,
    onNextPlayer: () -> Unit,
    onCategories: () -> Unit,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val playerColor = kiExtras.teamColors.teamColorFor(state.currentTeam)

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.correctWords.size >= 5) ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            BobbingEmoji(emoji = "🎯", fontSize = 44.sp)
            Spacer(modifier = Modifier.height(6.dp))
            StickerTitle(text = "نوبت ${state.teamDisplayName(state.currentTeam)}", rotation = -2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "✅ ${state.correctWords.size.toPersianDigits()} درست    ⏭ ${state.passedWords.size.toPersianDigits()} رد",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            // ---- بررسی امتیاز: جمع می‌تواند حکم نهایی را کم و زیاد کند ----
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "بررسی امتیاز این نوبت",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        val minus = remember { MutableInteractionSource() }
                        Text(
                            text = "−۱",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(extras.glassStrong, RoundedCornerShape(50))
                                .clickable(interactionSource = minus, indication = null) {
                                    sound?.playButtonClick(); onAdjust(-1)
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                        Text(
                            text = state.turnScore.toPersianDigits(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (state.turnScore >= 0) extras.success else extras.danger,
                        )
                        val plus = remember { MutableInteractionSource() }
                        Text(
                            text = "+۱",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(extras.glassStrong, RoundedCornerShape(50))
                                .clickable(interactionSource = plus, indication = null) {
                                    sound?.playButtonClick(); onAdjust(1)
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    if (state.turnBonus != 0) {
                        Text(
                            text = "حکم داورها: ${if (state.turnBonus > 0) "+" else "−"}${kotlin.math.abs(state.turnBonus).toPersianDigits()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            GlassCard(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                LazyColumn(modifier = Modifier.padding(14.dp)) {
                    items(count = state.correctWords.size) { i ->
                        Text(
                            text = "✅ ${state.correctWords[i]}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = extras.success,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    items(count = state.passedWords.size) { i ->
                        Text(
                            text = "⏭ ${state.passedWords[i]}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            KButton(
                text = if (state.currentTeam == state.teamCount - 1 && state.roundIndex >= state.totalRounds)
                    "ثبت — کی برد؟ 🏆" else "ثبت — تیم بعد",
                onClick = onNextPlayer,
                accent = playerColor,
            )
            Spacer(modifier = Modifier.height(8.dp))
            KButton(
                text = "ثبت و دسته‌ی دیگه",
                onClick = onCategories,
                style = KButtonStyle.Glass,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun FhWinnerScreen(
    state: FhUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🏆", fontSize = 76.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = winners.joinToString(" و ") { state.teamDisplayName(it) },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull() ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    (0 until state.teamCount)
                        .sortedByDescending { state.totalScores[it] }
                        .forEachIndexed { rank, i ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (i in winners) "🥇" else (rank + 1).toPersianDigits(),
                                        fontSize = 17.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.teamDisplayName(i),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = kiExtras.teamColors.teamColorFor(i),
                                    )
                                }
                                Text(
                                    text = state.totalScores[i].toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            ShareWinButton(
                gameId = "forehead",
                gameTitle = "حدس روی پیشونی",
                gameEmoji = "🤳",
                winnerText = winners.joinToString(" و ") { state.teamDisplayName(it) },
                scoreLines = (0 until state.teamCount).map { state.teamDisplayName(it) to state.totalScores[it].toPersianDigits() },
                winnerNames = winners.map { state.teamDisplayName(it) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
