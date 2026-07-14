package com.navidabbasian.kibord.games.proverb

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
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
import com.navidabbasian.kibord.core.ui.components.TicketCard
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
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ================= مدل =================

/** یک ضرب‌المثل کامل — سر بازی با نسبت ۳۵ تا ۴۵ درصد بریده می‌شود */
@Serializable
data class ProverbCard(
    val text: String = "",
)

/** کارت آماده‌ی نمایش: بخش خواندنیِ گوینده و ادامه‌ای که تیم باید کامل کند */
data class PvShown(
    val start: String,
    val end: String,
    val full: String,
)

sealed class PvPhase {
    data object TeamNames : PvPhase()
    data object Settings : PvPhase()
    data class TurnReady(val team: Int) : PvPhase()
    data object Turn : PvPhase()
    data class TurnEnd(val team: Int, val correct: Int, val missed: Int) : PvPhase()
    data object Winner : PvPhase()
}

data class PvUiState(
    val phase: PvPhase = PvPhase.TeamNames,
    val teamNames: List<String> = List(2) { "" },
    val scores: List<Int> = List(2) { 0 },
    val turnSeconds: Int = 60,
    val totalRounds: Int = 3,
    val roundIndex: Int = 1,
    val currentTeam: Int = 0,
    val secondsLeft: Int = 0,
    val currentCard: PvShown? = null,
    val turnCorrect: Int = 0,
    val turnMissed: Int = 0,
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { "تیم ${(index + 1).toPersianDigits()}" }
            ?: "تیم ${(index + 1).toPersianDigits()}"
}

enum class PvSoundEvent { TICK, TICK_WARNING, TIME_UP, CORRECT, MISS, GAME_OVER }

// ================= موتور =================

/**
 * ضرب‌المثل نصفه: گوینده نیمه‌ی اول را می‌خواند و تیمش باید ادامه را کامل کند.
 * درست ۱+، نشد بدون امتیاز (جواب اعلام می‌شود و کارت بعدی می‌آید).
 */
class ProverbViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PvUiState())
    val uiState: StateFlow<PvUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<PvSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<PvSoundEvent> = _soundEvents.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(application)
    private var allCards: List<ProverbCard> = emptyList()
    private var deck: ArrayDeque<ProverbCard> = ArrayDeque()
    private var tickerJob: Job? = null

    init {
        val names = GamePrefs.getNames(application, "proverb_names")
        _uiState.update {
            it.copy(
                teamNames = List(2) { i -> names.getOrElse(i) { "" } },
                turnSeconds = GamePrefs.getInt(application, "proverb_seconds", 60),
                totalRounds = GamePrefs.getInt(application, "proverb_rounds", 3),
            )
        }
        // اگر کشِ دانلودی فرمت قدیمی داشته باشد نتیجه خالی می‌شود؛
        // آن‌وقت بانکِ داخل خود اپ ملاک است تا بازی هرگز بی‌کارت نماند
        allCards = decodeCards(ContentBank.open(application, "proverbs.json"))
        if (allCards.isEmpty()) {
            allCards = decodeCards(
                try {
                    application.assets.open("proverbs.json").bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
            )
        }
    }

    private fun decodeCards(text: String): List<ProverbCard> = try {
        json.decodeFromString<List<ProverbCard>>(text)
    } catch (_: Exception) {
        emptyList()
    }.filter { it.text.trim().split(WHITESPACE).size >= 3 }

    private fun emit(e: PvSoundEvent) = _soundEvents.tryEmit(e)

    /** دسته‌ی بُرخورده‌ی مثل‌های بازی‌نشده؛ با اتمام بانک، سابقه ریست می‌شود */
    private fun prepareDeck(): List<ProverbCard> {
        val played = playedStore.played(PLAYED_KEY)
        var fresh = allCards.filter { it.text !in played }
        if (fresh.isEmpty()) {
            playedStore.clear(PLAYED_KEY)
            fresh = allCards
        }
        return fresh.shuffled()
    }

    /** گوینده فقط ۳۵ تا ۴۵ درصد اول مثل را می‌خواند؛ باقی‌اش با تیم است */
    private fun splitCard(card: ProverbCard): PvShown {
        val words = card.text.trim().split(WHITESPACE)
        val fraction = listOf(0.35, 0.40, 0.45).random()
        val show = (words.size * fraction).roundToInt().coerceIn(1, words.size - 1)
        return PvShown(
            start = words.take(show).joinToString(" "),
            end = words.drop(show).joinToString(" "),
            full = card.text,
        )
    }

    // ---- راه‌اندازی ----

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(teamNames = s.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeamNames() = _uiState.update { it.copy(phase = PvPhase.Settings) }

    fun setTurnSeconds(seconds: Int) = _uiState.update { it.copy(turnSeconds = seconds) }

    fun setTotalRounds(rounds: Int) =
        _uiState.update { it.copy(totalRounds = rounds.coerceIn(1, 10)) }

    fun startGame() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setNames(app, "proverb_names", s.teamNames)
        GamePrefs.setInt(app, "proverb_seconds", s.turnSeconds)
        GamePrefs.setInt(app, "proverb_rounds", s.totalRounds)
        deck = ArrayDeque(prepareDeck())
        _uiState.update {
            it.copy(
                phase = PvPhase.TurnReady(0),
                scores = List(2) { 0 },
                roundIndex = 1,
                currentTeam = 0,
            )
        }
    }

    // ---- نوبت ----

    fun startTurn() {
        val s = _uiState.value
        val team = (s.phase as? PvPhase.TurnReady)?.team ?: return
        _uiState.update {
            it.copy(
                phase = PvPhase.Turn,
                currentTeam = team,
                secondsLeft = it.turnSeconds,
                turnCorrect = 0,
                turnMissed = 0,
                currentCard = drawCard(),
            )
        }
        startTicker()
    }

    private fun drawCard(): PvShown? {
        if (deck.isEmpty()) deck = ArrayDeque(prepareDeck())
        return deck.removeFirstOrNull()?.let(::splitCard)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value
                if (s.phase != PvPhase.Turn) break
                val left = s.secondsLeft - 1
                emit(if (left <= 5) PvSoundEvent.TICK_WARNING else PvSoundEvent.TICK)
                if (left <= 0) {
                    emit(PvSoundEvent.TIME_UP)
                    endTurn()
                    break
                }
                _uiState.update { it.copy(secondsLeft = left) }
            }
        }
    }

    /** کامل گفت: ۱+ و مثل بعدی */
    fun markCorrect() {
        val s = _uiState.value
        if (s.phase != PvPhase.Turn) return
        emit(PvSoundEvent.CORRECT)
        s.currentCard?.let { playedStore.markPlayed(PLAYED_KEY, it.full) }
        _uiState.update {
            it.copy(
                scores = it.scores.mapIndexed { i, v -> if (i == it.currentTeam) v + 1 else v },
                turnCorrect = it.turnCorrect + 1,
                currentCard = drawCard(),
            )
        }
    }

    /** بلد نبودن: بدون امتیاز، مثل بعدی (جواب روی کارت بود) */
    fun markMissed() {
        val s = _uiState.value
        if (s.phase != PvPhase.Turn) return
        emit(PvSoundEvent.MISS)
        s.currentCard?.let { playedStore.markPlayed(PLAYED_KEY, it.full) }
        _uiState.update {
            it.copy(turnMissed = it.turnMissed + 1, currentCard = drawCard())
        }
    }

    private fun endTurn() {
        tickerJob?.cancel()
        val s = _uiState.value
        _uiState.update {
            it.copy(phase = PvPhase.TurnEnd(s.currentTeam, s.turnCorrect, s.turnMissed))
        }
    }

    fun proceedAfterTurn() {
        val s = _uiState.value
        if (s.currentTeam == 0) {
            _uiState.update { it.copy(phase = PvPhase.TurnReady(1)) }
        } else if (s.roundIndex >= s.totalRounds) {
            emit(PvSoundEvent.GAME_OVER)
            _uiState.update { it.copy(phase = PvPhase.Winner) }
        } else {
            _uiState.update {
                it.copy(roundIndex = it.roundIndex + 1, phase = PvPhase.TurnReady(0))
            }
        }
    }

    fun winners(): List<Int> {
        val scores = _uiState.value.scores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        tickerJob?.cancel()
        val old = _uiState.value
        _uiState.value = PvUiState(
            teamNames = old.teamNames,
            turnSeconds = old.turnSeconds,
            totalRounds = old.totalRounds,
            phase = PvPhase.Settings,
        )
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                PvPhase.Settings -> s.copy(phase = PvPhase.TeamNames)
                else -> s
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    companion object {
        const val PLAYED_KEY = "proverbs"
        private val WHITESPACE = Regex("\\s+")
    }
}

// ================= ریشه =================

/** ریشه‌ی ضرب‌المثل نصفه — ماشین فاز، موسیقی و رویدادهای صوتی */
@Composable
fun ProverbGame(
    onExitToHub: () -> Unit,
    viewModel: ProverbViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                PvSoundEvent.TICK -> sound?.playTimerTick()
                PvSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                PvSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                PvSoundEvent.CORRECT -> sound?.playCorrectWord()
                PvSoundEvent.MISS -> sound?.playWordSkip()
                PvSoundEvent.GAME_OVER -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            PvPhase.TeamNames, PvPhase.Settings -> MusicTrack.HUB
            else -> MusicTrack.DOR
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == PvPhase.TeamNames || state.phase == PvPhase.Settings) {
            GameHelpButton(gameId = "proverb", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                PvPhase.TeamNames -> {
                    BackHandler { onExitToHub() }
                    PvTeamNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames,
                    )
                }

                PvPhase.Settings -> {
                    BackHandler { viewModel.navigateBack() }
                    PvSettingsScreen(
                        state = state,
                        onSeconds = viewModel::setTurnSeconds,
                        onRounds = viewModel::setTotalRounds,
                        onStart = viewModel::startGame,
                    )
                }

                is PvPhase.TurnReady -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    PvTurnReadyScreen(state = state, team = phase.team, onStart = viewModel::startTurn)
                }

                PvPhase.Turn -> {
                    BackHandler { }
                    PvTurnScreen(
                        state = state,
                        onCorrect = viewModel::markCorrect,
                        onMissed = viewModel::markMissed,
                    )
                }

                is PvPhase.TurnEnd -> {
                    BackHandler { viewModel.proceedAfterTurn() }
                    PvTurnEndScreen(
                        state = state,
                        team = phase.team,
                        correct = phase.correct,
                        missed = phase.missed,
                        onProceed = viewModel::proceedAfterTurn,
                    )
                }

                PvPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    PvWinnerScreen(
                        state = state,
                        winners = viewModel.winners(),
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.playAgain()
                            onExitToHub()
                        },
                    )
                }
            }
        }
    }
}

// ================= صفحه‌ها =================

@Composable
private fun PvTeamNamesScreen(
    state: PvUiState,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "📜", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "ضرب‌المثل نصفه", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "گوینده نصف اول مثل رو می‌خونه — تیمش باید ادامه‌ش رو کامل کنه!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))

        repeat(2) { i ->
            BlobTextField(
                value = state.teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = "تیم ${(i + 1).toPersianDigits()}",
                color = teamColors.teamColorFor(i),
                badge = (i + 1).toPersianDigits(),
                tilt = if (i % 2 == 0) -1.2f else 1.2f,
                phase = i * 1.6f,
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .offset(x = if (i % 2 == 0) (-6).dp else 6.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "ادامه", onClick = onConfirm)
        }
    }
}

/** تراشه‌ی قرصی انتخاب‌شدنی تنظیمات */
@Composable
private fun PvPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = text,
        fontSize = 14.sp,
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
private fun PvSettingsScreen(
    state: PvUiState,
    onSeconds: (Int) -> Unit,
    onRounds: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "⏱", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "تنظیمات بازی", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "مدت هر نوبت",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(45, 60, 90).forEach { sec ->
                PvPill(
                    text = "${sec.toPersianDigits()} ثانیه",
                    selected = state.turnSeconds == sec,
                    onClick = { onSeconds(sec) },
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "تعداد راند (هر راند یک نوبت برای هر تیم)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PvPill(text = "−", selected = false, onClick = { onRounds(state.totalRounds - 1) })
            Text(
                text = state.totalRounds.toPersianDigits(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            PvPill(text = "+", selected = false, onClick = { onRounds(state.totalRounds + 1) })
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "شروع بازی!", onClick = onStart)
        }
    }
}

@Composable
private fun PvTurnReadyScreen(
    state: PvUiState,
    team: Int,
    onStart: () -> Unit,
) {
    val color = kiExtras.teamColors.teamColorFor(team)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TeamMedallions(
            count = 2,
            nameOf = state::teamDisplayName,
            scoreOf = { state.scores[it] },
            highlight = team,
        )
        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "راند ${state.roundIndex.toPersianDigits()} از ${state.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BobbingEmoji(emoji = "📜", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "نوبت ${state.teamDisplayName(team)}",
            style = MaterialTheme.typography.displayMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "گوشی دست گوینده — نصف اول مثل رو بخون،\nتیمت باید ادامه‌ش رو درست کامل کنه!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        KButton(text = "آماده‌ام — شروع!", onClick = onStart, accent = color)
    }
}

@Composable
private fun PvTurnScreen(
    state: PvUiState,
    onCorrect: () -> Unit,
    onMissed: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val urgent = state.secondsLeft <= 5

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val progress by animateFloatAsState(
        targetValue = if (state.turnSeconds == 0) 0f else state.secondsLeft.toFloat() / state.turnSeconds,
        animationSpec = tween(300),
        label = "proverb_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .then(if (urgent) Modifier.breathing(intensity = 0.06f, periodMs = 600) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(84.dp)) {
                drawArc(
                    color = extras.glassStrong,
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = if (urgent) extras.danger else extras.gold,
                    startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = state.secondsLeft.toPersianDigits(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "✅ ${state.turnCorrect.toPersianDigits()}   ❌ ${state.turnMissed.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ---- کارت مثل ----
        val card = state.currentCard
        TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.2f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "بلند بخون 📢",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "«${card?.start ?: "—"}…»",
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = 30.sp,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "جواب — فقط خودت ببین 🤫",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.danger,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card?.end ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ---- دکمه‌ها ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            KButton(text = "کامل کرد! ✅", onClick = onCorrect, accent = extras.success)
            Spacer(modifier = Modifier.height(8.dp))
            KButton(text = "بلد نبودن ❌", onClick = onMissed, style = KButtonStyle.Glass)
        }
    }
}

@Composable
private fun PvTurnEndScreen(
    state: PvUiState,
    team: Int,
    correct: Int,
    missed: Int,
    onProceed: () -> Unit,
) {
    val color = kiExtras.teamColors.teamColorFor(team)
    val extras = kiExtras
    // نوبت آخرِ راند آخر: مجموع‌ها تا لحظه‌ی «کی برد؟» مخفی می‌مانند
    val suspense = state.roundIndex >= state.totalRounds && team == 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "⏰", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "وقت تموم شد!", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.teamDisplayName(team),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "✅ ${correct.toPersianDigits()} کامل    ❌ ${missed.toPersianDigits()} جاموند",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "امتیاز این نوبت: ${correct.toPersianDigits()}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = extras.success,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (suspense) {
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1300)) {
                Text(
                    text = "🤫 مجموع امتیازها مخفیه… بزن ببین کی برد!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = extras.gold,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            TeamMedallions(
                count = 2,
                nameOf = state::teamDisplayName,
                scoreOf = { state.scores[it] },
            )
        }
        Spacer(modifier = Modifier.height(26.dp))
        KButton(
            text = if (suspense) "کی برد؟ 🏆" else "ادامه",
            onClick = onProceed,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun PvWinnerScreen(
    state: PvUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

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
            Text(text = "🏆", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (winners.size > 1) "مساوی! ${winners.joinToString(" و ") { state.teamDisplayName(it) }}"
                else state.teamDisplayName(winners.firstOrNull() ?: 0),
                style = MaterialTheme.typography.displayMedium,
                color = teamColors.teamColorFor(winners.firstOrNull() ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            TeamMedallions(
                count = 2,
                nameOf = state::teamDisplayName,
                scoreOf = { state.scores[it] },
            )
            Spacer(modifier = Modifier.height(28.dp))
            ShareWinButton(
                gameId = "proverb",
                gameTitle = "ضرب‌المثل نصفه",
                gameEmoji = "📜",
                winnerText = if (winners.size > 1) "مساوی!" else state.teamDisplayName(winners.firstOrNull() ?: 0),
                scoreLines = (0 until 2).map { state.teamDisplayName(it) to state.scores[it].toPersianDigits() },
                winnerNames = winners.map { state.teamDisplayName(it) },
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
