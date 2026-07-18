package com.navidabbasian.kibord.games.whoami

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.navidabbasian.kibord.core.net.HostKeepAlive
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.whoami.model.WA_MAX_PLAYERS
import com.navidabbasian.kibord.games.whoami.model.WA_MIN_PLAYERS
import com.navidabbasian.kibord.games.whoami.model.WA_QUESTION_CHOICES
import com.navidabbasian.kibord.games.whoami.model.WaPhase
import com.navidabbasian.kibord.games.whoami.model.WaPlayer
import com.navidabbasian.kibord.games.whoami.model.WaSnapshot
import com.navidabbasian.kibord.games.whoami.net.WaClient
import com.navidabbasian.kibord.games.whoami.net.WaDiscoveredGame
import com.navidabbasian.kibord.games.whoami.net.WaMessage
import com.navidabbasian.kibord.games.whoami.net.WaNsd
import com.navidabbasian.kibord.games.whoami.net.WaServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ================= وضعیت محلی =================

/** نقش این گوشی در ارتباط شبکه */
enum class WaNetRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class WaLocalScreen { ENTRY, JOIN, IN_GAME }

/** رویدادهای صوتی محلی — فقط روی گوشی خود بازیکن پخش می‌شوند */
enum class WaSoundEvent { NOD, BEEP, ROUND_START, ROUND_END, OUT }

data class WhoAmIUiState(
    val role: WaNetRole = WaNetRole.NONE,
    val localScreen: WaLocalScreen = WaLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: WaSnapshot = WaSnapshot(),
    val discovered: List<WaDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** اسمی که دارم برای هدف فعلی‌ام می‌نویسم */
    val myDraft: String = "",
    val hostAddress: String = "",
    val hostPort: Int = 0,
    val lostConnection: Boolean = false,
    /** ثانیه‌ی جاری شمارش معکوس شروع راند */
    val countdownLeft: Int = 5,
) {
    val isHost: Boolean get() = role == WaNetRole.HOST
    val me: WaPlayer? get() = snapshot.player(myName)
    /** هدف‌هایی که هنوز برایشان ننوشته‌ام — میزبان در تعداد فرد ممکن است دو تا داشته باشد */
    val myPendingTargets: List<String> get() = snapshot.pendingTargetsOf(myName)
    val myTargets: List<String> get() = snapshot.targetsOf(myName)
    val iHaveGuessed: Boolean get() = snapshot.hasGuessed(myName)
    val iAmOut: Boolean get() = snapshot.isOut(myName)
    val iAmStillPlaying: Boolean get() = snapshot.stillPlaying(myName)
    /** اسمِ روی پیشانی من — نباید بخوانمش! */
    val myForeheadName: String get() = snapshot.assignmentOf(myName)
    val myQuestionsLeft: Int get() = snapshot.questionsLeftOf(myName)
    val myQuestionsUsed: Int get() = snapshot.questionsUsedOf(myName)
}

// ================= موتور =================

/**
 * من کی‌ام؟ — چندگوشی: هر کس مخفیانه برای جفتش کلمه می‌نویسد،
 * همه گوشی را روی پیشانی می‌گذارند؛ بعد از شمارش معکوس کلمه‌ها ظاهر می‌شوند.
 * هر تکان سر به جلو یک سوال است؛ سوال‌ها که تمام شود می‌بازی و
 * هر که با سوال کمتر جواب بدهد امتیاز بیشتری می‌گیرد.
 */
class WhoAmIViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WhoAmIUiState())
    val uiState: StateFlow<WhoAmIUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<WaSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<WaSoundEvent> = _soundEvents.asSharedFlow()

    private val nsd = WaNsd(application)
    private val keepAlive = HostKeepAlive(application)
    private var server: WaServer? = null
    private var client: WaClient? = null

    // ---- حسگر تکان سر: گوشی افقی روی پیشانی، صفحه رو به جمع ----
    // خم شدن سر به جلو → صفحه رو به زمین → محور z منفی = یک سوال
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var sensorActive = false
    private var lastNodAt = 0L
    private var neutral = false
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val z = event.values.getOrNull(2) ?: return
            val now = SystemClock.elapsedRealtime()
            when {
                neutral && z < -7f && now - lastNodAt > 900 -> {
                    lastNodAt = now; neutral = false
                    onNodDetected()
                }

                kotlin.math.abs(z) < 4f -> neutral = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    // ================= ورود =================

    fun setMyName(name: String) {
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = WaServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { WaMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        keepAlive.acquire()
        nsd.register(name, srv.port)
        val app = getApplication<Application>()
        val snapshot = WaSnapshot(
            phase = WaPhase.LOBBY,
            players = listOf(WaPlayer(name = name, colorIndex = 0)),
            hostName = name,
            totalRounds = GamePrefs.getInt(app, "whoami_rounds", 3).coerceIn(1, 10),
            questionsTotal = GamePrefs.getInt(app, "whoami_questions", 10)
                .let { if (it in WA_QUESTION_CHOICES) it else 10 },
        )
        _uiState.update {
            it.copy(
                role = WaNetRole.HOST,
                localScreen = WaLocalScreen.IN_GAME,
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد
                myName = name,
                hostAddress = WaNsd.localIpAddress() ?: "",
                hostPort = srv.port,
                connectError = null,
            )
        }
        setSnapshot(snapshot)
    }

    private fun acceptJoin(name: String): String? {
        val snapshot = _uiState.value.snapshot
        val existing = snapshot.player(name)
        return when {
            name.isBlank() -> "اسم خالی است"
            existing != null && !existing.connected -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = true) else it })
                }
                null
            }

            existing != null -> "این اسم الان توی بازیه — یک اسم دیگر انتخاب کن"
            snapshot.phase != WaPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= WA_MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + WaPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != WaNetRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        maybeFinishWrite()
        maybeFinishRound()
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = WaLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
        nsd.discover(
            onFound = { game ->
                _uiState.update { st ->
                    st.copy(discovered = st.discovered.filter { it.hostName != game.hostName } + game)
                }
            },
            onLost = { name ->
                _uiState.update { st -> st.copy(discovered = st.discovered.filter { it.hostName != name }) }
            },
        )
    }

    fun joinGame(address: String, port: Int = WaServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        _uiState.update { it.copy(myName = name, connecting = true, connectError = null) }
        val c = WaClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == WaNetRole.CLIENT) {
                    _uiState.update { it.copy(lostConnection = true) }
                }
            },
        )
        client = c
        c.connect(address, port, name) { error ->
            if (error != null) {
                client = null
                _uiState.update { it.copy(connecting = false, connectError = error) }
            } else {
                nsd.stopDiscovery()
                _uiState.update {
                    it.copy(
                        role = WaNetRole.CLIENT,
                        localScreen = WaLocalScreen.IN_GAME,
                        connecting = false,
                        connectError = null,
                    )
                }
            }
        }
    }

    private fun handleServerMessage(msg: WaMessage) {
        when (msg) {
            is WaMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= تنظیمات میزبان در لابی =================

    /** بازی ۱۰ سوالی یا ۲۰ سوالی — فقط میزبان و فقط در لابی */
    fun setQuestionsTotal(count: Int) = hostOnly {
        if (count !in WA_QUESTION_CHOICES) return@hostOnly
        if (_uiState.value.snapshot.phase != WaPhase.LOBBY) return@hostOnly
        GamePrefs.setInt(getApplication(), "whoami_questions", count)
        mutateSnapshot { it.copy(questionsTotal = count) }
    }

    fun setTotalRounds(rounds: Int) = hostOnly {
        if (_uiState.value.snapshot.phase != WaPhase.LOBBY) return@hostOnly
        val r = rounds.coerceIn(1, 10)
        GamePrefs.setInt(getApplication(), "whoami_rounds", r)
        mutateSnapshot { it.copy(totalRounds = r) }
    }

    // ================= فرمان‌های داخل بازی =================

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.players.count { it.connected } < WA_MIN_PLAYERS) return@hostOnly
        dealRound(roundIndex = 1, resetScores = true)
    }

    /**
     * قرعه‌ی راند: بازیکن‌ها دوبه‌دو برای هم می‌نویسند.
     * اگر تعداد فرد باشد، میزبان برای دو نفر می‌نویسد و یک نفر این راند نمی‌نویسد.
     */
    private fun buildTargets(names: List<String>, host: String): Map<String, List<String>> {
        if (names.size < 2) return emptyMap()
        val others = names.filterNot { sameName(it, host) }.shuffled()
        val result = mutableMapOf<String, MutableList<String>>()
        var leftover: String? = null
        val paired = if (names.size % 2 == 0) {
            (others + host).shuffled()
        } else {
            leftover = others.first()
            (others.drop(1) + host).shuffled()
        }
        paired.chunked(2).forEach { (a, b) ->
            result.getOrPut(a) { mutableListOf() }.add(b)
            result.getOrPut(b) { mutableListOf() }.add(a)
        }
        leftover?.let { result.getOrPut(host) { mutableListOf() }.add(it) }
        return result
    }

    private fun dealRound(roundIndex: Int, resetScores: Boolean = false) {
        val s = _uiState.value.snapshot
        val connected = s.connectedPlayers
        if (connected.size < WA_MIN_PLAYERS) return
        val targets = buildTargets(connected.map { it.name }, s.hostName)
        mutateSnapshot { snap ->
            snap.copy(
                phase = WaPhase.WRITE,
                roundIndex = roundIndex,
                players = if (resetScores) snap.players.map { it.copy(totalScore = 0) } else snap.players,
                targets = targets,
                assignments = emptyMap(),
                questionsUsed = emptyMap(),
                guessedOrder = emptyList(),
                outPlayers = emptyList(),
            )
        }
    }

    fun updateDraft(text: String) = _uiState.update { it.copy(myDraft = text.take(30)) }

    /** کلمه‌ی مخفی را برای هدفِ فعلی‌ام می‌فرستم */
    fun submitName() {
        val st = _uiState.value
        val text = st.myDraft.trim()
        val target = st.myPendingTargets.firstOrNull() ?: return
        if (st.snapshot.phase != WaPhase.WRITE || text.isBlank()) return
        if (st.isHost) hostReceiveName(st.myName, target, text) else client?.send(WaMessage.SubmitName(target, text))
        _uiState.update { it.copy(myDraft = "") }
    }

    /**
     * شمارش معکوس ۵ ثانیه‌ای در موتور اجرا می‌شود تا با چرخیدن صفحه از نو شروع نشود؛
     * میزبان در پایان همه را وارد بازی می‌کند.
     */
    private var countdownJob: Job? = null

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (s in 5 downTo 1) {
                _uiState.update { it.copy(countdownLeft = s) }
                _soundEvents.tryEmit(WaSoundEvent.BEEP)
                delay(1000)
            }
            hostOnly {
                if (_uiState.value.snapshot.phase == WaPhase.COUNTDOWN) {
                    mutateSnapshot { it.copy(phase = WaPhase.PLAY) }
                }
            }
        }
    }

    /** تکان سر تشخیص داده شد — یک سوال از سهم من کم شود */
    private fun onNodDetected() {
        val st = _uiState.value
        if (st.snapshot.phase != WaPhase.PLAY || !st.iAmStillPlaying) return
        _soundEvents.tryEmit(WaSoundEvent.NOD)
        if (st.isHost) hostQuestionUsed(st.myName) else client?.send(WaMessage.QuestionUsed)
    }

    /** «جواب دادم!» — گوشی از روی پیشانی برداشته شده و دکمه لمس شده */
    fun markGuessed() {
        val st = _uiState.value
        if (st.snapshot.phase != WaPhase.PLAY || !st.iAmStillPlaying) return
        if (st.isHost) hostMarkGuessed(st.myName) else client?.send(WaMessage.Guessed)
    }

    /** میزبان: راند بعد؛ بعد از راند آخر اعلام برنده */
    fun nextRound() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.ROUND_RESULT) return@hostOnly
        if (s.roundIndex >= s.totalRounds) {
            mutateSnapshot { it.copy(phase = WaPhase.GAME_OVER) }
        } else {
            dealRound(roundIndex = s.roundIndex + 1)
        }
    }

    fun playAgain() = hostOnly {
        mutateSnapshot { s ->
            s.copy(
                phase = WaPhase.LOBBY,
                roundIndex = 0,
                targets = emptyMap(),
                assignments = emptyMap(),
                questionsUsed = emptyMap(),
                guessedOrder = emptyList(),
                outPlayers = emptyList(),
                players = s.players.map { it.copy(totalScore = 0) },
            )
        }
    }

    fun winners(): List<WaPlayer> {
        val players = _uiState.value.snapshot.players
        val max = players.maxOfOrNull { it.totalScore } ?: return emptyList()
        return players.filter { it.totalScore == max }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: WaMessage) {
        when (msg) {
            is WaMessage.SubmitName -> hostReceiveName(playerName, msg.target, msg.text.trim())
            is WaMessage.QuestionUsed -> hostQuestionUsed(playerName)
            is WaMessage.Guessed -> hostMarkGuessed(playerName)
            else -> Unit
        }
    }

    private fun hostReceiveName(writer: String, target: String, text: String) {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.WRITE || text.isBlank()) return
        if (s.pendingTargetsOf(writer).none { sameName(it, target) }) return
        mutateSnapshot { snap ->
            snap.copy(assignments = snap.assignments + (target to text))
        }
        maybeFinishWrite()
    }

    private fun maybeFinishWrite() {
        if (_uiState.value.role != WaNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.WRITE) return
        // نویسنده‌های وصل که هنوز برای هدفِ وصلی ننوشته‌اند
        val waiting = s.connectedPlayers.filter { w ->
            s.pendingTargetsOf(w.name).any { t -> s.player(t)?.connected == true }
        }
        if (waiting.isEmpty() && s.assignments.isNotEmpty()) {
            mutateSnapshot { it.copy(phase = WaPhase.COUNTDOWN) }
        }
    }

    private fun hostQuestionUsed(name: String) {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.PLAY || !s.stillPlaying(name)) return
        val used = s.questionsUsedOf(name) + 1
        mutateSnapshot { snap ->
            snap.copy(
                questionsUsed = snap.questionsUsed.filterKeys { !sameName(it, name) } + (name to used),
                // سوال‌ها تمام شد و جواب نداد — این راند را باخت
                outPlayers = if (used >= snap.questionsTotal) snap.outPlayers + name else snap.outPlayers,
            )
        }
        maybeFinishRound()
    }

    private fun hostMarkGuessed(name: String) {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.PLAY || !s.stillPlaying(name)) return
        // سوال کمتر = امتیاز بیشتر: هر سوالِ نپرسیده یک امتیاز است
        val gained = s.questionsLeftOf(name).coerceAtLeast(1)
        mutateSnapshot { snap ->
            snap.copy(
                guessedOrder = snap.guessedOrder + name,
                players = snap.players.map { p ->
                    if (sameName(p.name, name)) p.copy(totalScore = p.totalScore + gained) else p
                },
            )
        }
        maybeFinishRound()
    }

    private fun maybeFinishRound() {
        if (_uiState.value.role != WaNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.PLAY) return
        val waiting = s.connectedPlayers.filter { s.stillPlaying(it.name) }
        if (waiting.isEmpty()) {
            mutateSnapshot { it.copy(phase = WaPhase.ROUND_RESULT) }
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    private fun mutateSnapshot(transform: (WaSnapshot) -> WaSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        server?.broadcast(WaMessage.State(next))
    }

    private fun setSnapshot(next: WaSnapshot) {
        val prev = _uiState.value.snapshot
        val myName = _uiState.value.myName
        _uiState.update { it.copy(snapshot = next) }
        // رویدادهای صوتی فقط روی گذارِ واقعی وضعیت — نه بازساخت صفحه بعد از چرخش
        if (prev.phase != next.phase) {
            when (next.phase) {
                WaPhase.COUNTDOWN -> startCountdown()
                WaPhase.PLAY -> _soundEvents.tryEmit(WaSoundEvent.ROUND_START)
                WaPhase.ROUND_RESULT -> _soundEvents.tryEmit(WaSoundEvent.ROUND_END)
                else -> Unit
            }
            if (prev.phase == WaPhase.COUNTDOWN && next.phase != WaPhase.COUNTDOWN) {
                countdownJob?.cancel()
                countdownJob = null
            }
        }
        if (myName.isNotBlank() && !prev.isOut(myName) && next.isOut(myName)) {
            _soundEvents.tryEmit(WaSoundEvent.OUT)
        }
        updateNodSensor()
    }

    /** حسگر فقط وقتی فعال است که وسط راند باشم: کلمه دارم، نه جواب داده‌ام نه سوخته‌ام */
    private fun updateNodSensor() {
        val st = _uiState.value
        val shouldListen = st.localScreen == WaLocalScreen.IN_GAME &&
            st.snapshot.phase == WaPhase.PLAY &&
            st.iAmStillPlaying
        if (shouldListen == sensorActive) return
        sensorActive = shouldListen
        if (shouldListen) {
            // تا وقتی گوشی صاف روی پیشانی ننشسته، تکان‌های جابه‌جایی سوال حساب نشود
            neutral = false
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
                sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == WaNetRole.HOST) block()
    }

    // ================= خروج =================

    fun leaveGame() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        keepAlive.release()
        countdownJob?.cancel()
        countdownJob = null
        sensorManager?.unregisterListener(sensorListener)
        sensorActive = false
        val name = _uiState.value.myName
        _uiState.value = WhoAmIUiState(myName = name)
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = WaLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        leaveGame()
    }
}

// ================= ریشه =================

/** ریشه‌ی من کی‌ام؟ — بازی چندگوشی روی شبکه‌ی محلی */
@Composable
fun WhoAmIGame(
    onExitToHub: () -> Unit,
    viewModel: WhoAmIViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val context = LocalContext.current
    val snapshot = state.snapshot

    LaunchedEffect(state.localScreen, snapshot.phase) {
        when {
            state.localScreen != WaLocalScreen.IN_GAME -> sound?.switchMusic(MusicTrack.HUB)
            snapshot.phase == WaPhase.LOBBY -> sound?.switchMusic(MusicTrack.HUB)
            else -> sound?.stopBackgroundMusic()
        }
    }

    // شمارش معکوس و بازی روی پیشانی: گوشی افقی می‌شود تا صاف روی پیشانی بنشیند
    val landscape = state.localScreen == WaLocalScreen.IN_GAME && (
        snapshot.phase == WaPhase.COUNTDOWN ||
            (snapshot.phase == WaPhase.PLAY && state.iAmStillPlaying)
        )
    LaunchedEffect(landscape) {
        (context as? Activity)?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // رویدادهای صوتی موتور — بازیکن صفحه را نمی‌بیند، صدا و لرزش خبرش می‌کنند
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                WaSoundEvent.NOD -> {
                    sound?.playWordSkip()
                    sound?.vibrate(60)
                }

                WaSoundEvent.BEEP -> sound?.playCountdownBeep()
                WaSoundEvent.ROUND_START -> sound?.playRoundStart()
                WaSoundEvent.ROUND_END -> sound?.playRoundEnd()
                WaSoundEvent.OUT -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(400)
                }
            }
        }
    }

    val leaveAndExit = {
        viewModel.leaveGame()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.localScreen != WaLocalScreen.IN_GAME || state.snapshot.phase == WaPhase.LOBBY) {
            GameHelpButton(gameId = "who_am_i", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.localScreen to snapshot.phase) {
            when (state.localScreen) {
                WaLocalScreen.ENTRY -> {
                    BackHandler { onExitToHub() }
                    WaEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onHost = viewModel::startHosting,
                        onJoin = viewModel::openJoinScreen,
                    )
                }

                WaLocalScreen.JOIN -> {
                    BackHandler { viewModel.backToEntryFromJoin() }
                    WaJoinScreen(
                        state = state,
                        onJoin = { game -> viewModel.joinGame(game.address, game.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                    )
                }

                WaLocalScreen.IN_GAME -> {
                    BackHandler { pendingExit = { leaveAndExit() } }
                    when (snapshot.phase) {
                        WaPhase.LOBBY -> WaLobbyScreen(
                            state = state,
                            onStart = viewModel::startGame,
                            onQuestions = viewModel::setQuestionsTotal,
                            onRounds = viewModel::setTotalRounds,
                        )

                        WaPhase.WRITE -> WaWriteScreen(
                            state = state,
                            onDraftChanged = viewModel::updateDraft,
                            onSubmit = viewModel::submitName,
                        )

                        WaPhase.COUNTDOWN -> WaCountdownScreen(state = state)

                        WaPhase.PLAY -> WaPlayScreen(
                            state = state,
                            onGuessed = {
                                sound?.playCorrectWord()
                                viewModel.markGuessed()
                            },
                        )

                        WaPhase.ROUND_RESULT -> WaRoundResultScreen(
                            state = state,
                            onNextRound = viewModel::nextRound,
                        )

                        WaPhase.GAME_OVER -> {
                            LaunchedEffect(Unit) { sound?.playGameOver() }
                            WaGameOverScreen(
                                state = state,
                                winners = viewModel.winners(),
                                onPlayAgain = viewModel::playAgain,
                                onExit = leaveAndExit,
                            )
                        }
                    }
                }
            }
        }

        // ---- ارتباط با میزبان قطع شد ----
        if (state.lostConnection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                contentAlignment = Alignment.Center
            ) {
                TicketCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    tilt = -1.5f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BobbingEmoji(emoji = "📴", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ارتباط با میزبان قطع شد!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "وای‌فای رو چک کنید؛ اگر میزبان برگشت، دوباره با همون اسم بپیوندید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        KButton(text = "باشه", onClick = leaveAndExit)
                    }
                }
            }
        }
    }
}

// ================= صفحه‌ها =================

@Composable
private fun WaEntryScreen(
    state: WhoAmIUiState,
    onNameChanged: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    var showNameError by remember { mutableStateOf(false) }
    val guardName: (() -> Unit) -> Unit = { action ->
        if (state.myName.isBlank()) showNameError = true else action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "🏷️", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "من کی‌ام؟", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "کلمه می‌ره رو پیشونیت؛ با تکون سر سوال بپرس و قبل از تموم‌شدن سوال‌هات بفهم کی هستی! همه روی یک وای‌فای یا هات‌اسپات باشید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        BlobTextField(
            value = state.myName,
            onValueChange = {
                onNameChanged(it)
                if (it.isNotBlank()) showNameError = false
            },
            placeholder = "اسمت چیه؟ (شناسه‌ی تو در بازی)",
            badge = "👤",
            tilt = -1f,
        )
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            ChoiceBubble(
                main = "میزبان شو",
                sub = "بازی بساز و\nرفقا رو دعوت کن",
                emoji = "👑",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = { guardName(onHost) },
            )
            ChoiceBubble(
                main = "بپیوند",
                sub = "به بازیِ ساخته‌شده\nوصل شو",
                emoji = "🚪",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = { guardName(onJoin) },
            )
        }

        if (showNameError && state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1100)) {
                StickerTitle(
                    text = "✋ اول اسمت رو بنویس!",
                    accent = kiExtras.danger,
                    rotation = -2f,
                    fontSize = 22.sp,
                )
            }
        } else if (state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(34.dp))
            Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 2000)) {
                StickerTitle(
                    text = "✍️ اول اسمت رو بنویس",
                    accent = LocalGameAccent.current,
                    rotation = -2f,
                    fontSize = 18.sp,
                )
            }
        }
        state.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger)
        }
    }
}

@Composable
private fun WaJoinScreen(
    state: WhoAmIUiState,
    onJoin: (WaDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
) {
    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "📡", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "به کدوم بازی بپیوندیم؟", rotation = 2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.discovered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                        Text(text = "🔎", fontSize = 30.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دنبال بازی می‌گردم…\nمیزبان باید بازی رو ساخته باشه و روی همین شبکه باشید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = state.discovered.size, key = { state.discovered[it].hostName }) { i ->
                    val game = state.discovered[i]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        strong = true,
                        tilt = if (i % 2 == 0) -1f else 1f,
                        onClick = { onJoin(game) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🏷️", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بازیِ ${game.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "لمس کن تا وصل شی",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(text = "🚪", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "پیدا نشد؟ آدرسِ نمایش‌داده‌شده در لابی میزبان رو بزن:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            placeholder = "مثلاً 192.168.1.5",
            badge = "🔗",
            tilt = 0.8f,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(
            text = if (state.connecting) "در حال اتصال…" else "اتصال دستی",
            enabled = !state.connecting && manualAddress.isNotBlank(),
            onClick = { onManualJoin(manualAddress.trim()) },
        )
        state.connectError?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
    }
}

/** تراشه‌ی قرصی کوچک برای تنظیمات میزبان */
@Composable
private fun WaPill(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun WaLobbyScreen(
    state: WhoAmIUiState,
    onStart: () -> Unit,
    onQuestions: (Int) -> Unit,
    onRounds: (Int) -> Unit,
) {
    val accent = LocalGameAccent.current
    val snapshot = state.snapshot
    val connected = snapshot.players.count { it.connected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        StickerTitle(text = "لابی من کی‌ام؟", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    text = "بازیکن‌ها (${snapshot.players.size.toPersianDigits()} از ۸)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                snapshot.players.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🏷️", fontSize = 26.sp)
                                Text(
                                    text = if (sameName(p.name, snapshot.hostName)) "${p.name} 👑" else p.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---- تنظیمات بازی: میزبان انتخاب می‌کند، بقیه می‌بینند ----
        if (state.isHost) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "❓ هر بازیکن چند تا سوال داشته باشه؟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WA_QUESTION_CHOICES.forEach { q ->
                            WaPill(
                                text = "${q.toPersianDigits()} سوالی",
                                selected = snapshot.questionsTotal == q,
                                onClick = { onQuestions(q) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "🔁 چند راند بازی کنیم؟",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        WaPill(text = "−", selected = false, onClick = { onRounds(snapshot.totalRounds - 1) })
                        Text(
                            text = snapshot.totalRounds.toPersianDigits(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = accent,
                        )
                        WaPill(text = "+", selected = false, onClick = { onRounds(snapshot.totalRounds + 1) })
                    }
                }
            }
        } else {
            Text(
                text = "بازی ${snapshot.questionsTotal.toPersianDigits()} سوالیه و ${snapshot.totalRounds.toPersianDigits()} راند داره",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "هر راند دوبه‌دو برای هم کلمه می‌نویسید (اگه فرد باشید میزبان برای دو نفر می‌نویسه). کلمه می‌ره رو پیشونی؛ هر تکون سر یه سوال — سوال کمتر، امتیاز بیشتر!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (state.isHost) {
            if (state.hostAddress.isNotBlank()) {
                Text(
                    text = "اتصال دستی مهمان‌ها: ${state.hostAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            KButton(
                text = if (connected < WA_MIN_PLAYERS) "منتظر حداقل ${WA_MIN_PLAYERS.toPersianDigits()} بازیکن…"
                else "شروع بازی!",
                enabled = connected >= WA_MIN_PLAYERS,
                onClick = onStart,
            )
        } else {
            BobbingEmoji(emoji = "🍿", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "منتظریم ${snapshot.hostName} بازی رو شروع کنه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun WaWriteScreen(
    state: WhoAmIUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val snapshot = state.snapshot
    val pendingTarget = state.myPendingTargets.firstOrNull()
    val waitingCount = snapshot.connectedPlayers.count {
        snapshot.pendingTargetsOf(it.name).isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "راند ${snapshot.roundIndex.toPersianDigits()} از ${snapshot.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BobbingEmoji(emoji = "🤫", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))

        when {
            // این راند نوبت نوشتنِ من نیست (تعداد فرد بود و قرعه به من نیفتاد)
            state.myTargets.isEmpty() -> {
                Text(
                    text = "این راند تو نمی‌نویسی — قرعه‌ی نوشتن به بقیه افتاد!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "نگران نباش، برای تو هم دارن کلمه می‌نویسن 😏 منتظر ${waitingCount.toPersianDigits()} نویسنده…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            pendingTarget != null -> {
                if (state.myTargets.size > 1) {
                    Text(
                        text = "تعداد فرده — تو برای ${state.myTargets.size.toPersianDigits()} نفر می‌نویسی 👑",
                        style = MaterialTheme.typography.labelLarge,
                        color = kiExtras.gold,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = "یواشکی برای $pendingTarget یه کلمه بنویس!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = kiExtras.teamColors.teamColorFor(snapshot.player(pendingTarget)?.colorIndex ?: 0),
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "آدم معروف، شخصیت کارتونی یا حتی یکی از همین جمع — فقط خودش نفهمه!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(18.dp))
                BlobTextField(
                    value = state.myDraft,
                    onValueChange = onDraftChanged,
                    placeholder = "مثلاً: فردوسی، باب اسفنجی…",
                    badge = "🏷️",
                    tilt = -1f,
                )
                Spacer(modifier = Modifier.height(16.dp))
                KButton(
                    text = "فرستادم 🤐",
                    enabled = state.myDraft.isNotBlank(),
                    onClick = onSubmit,
                )
            }

            else -> {
                BobbingEmoji(emoji = "⏳", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "کلمه‌هات رسید! منتظر ${waitingCount.toPersianDigits()} نویسنده‌ی دیگه…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** شمارش معکوس ۵ ثانیه‌ای با صدا — گوشی‌ها می‌روند روی پیشانی */
@Composable
private fun WaCountdownScreen(state: WhoAmIUiState) {
    val accent = LocalGameAccent.current

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📱 گوشی رو بذار رو پیشونیت — صفحه رو به جمع!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "بعد از شمارش، کلمه‌ت ظاهر می‌شه؛ با تکون دادن سر به جلو سوال بپرس!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.breathing(intensity = 0.08f, periodMs = 1000)) {
            Text(
                text = state.countdownLeft.toPersianDigits(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
        }
    }
}

@Composable
private fun WaPlayScreen(
    state: WhoAmIUiState,
    onGuessed: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val snapshot = state.snapshot
    val stillPlaying = snapshot.connectedPlayers.filter { snapshot.stillPlaying(it.name) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        // ---- روی پیشانی: کلمه + شمارنده‌ی سوال، افقی ----
        state.iAmStillPlaying -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.statusBarsPadding().height(6.dp))
                Text(
                    text = "نخونش! 🙈 راند ${snapshot.roundIndex.toPersianDigits()} — هر تکون سر به جلو = یه سوال",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = extras.danger,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ---- کلمه‌ی روی پیشانی — بقیه می‌خوانند ----
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .breathing(intensity = 0.03f, periodMs = 1800),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.myForeheadName.ifBlank { "…" },
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = accent,
                            textAlign = TextAlign.Center,
                            lineHeight = 68.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    // ---- عدد سوال‌های باقی‌مانده کنار کلمه ----
                    Column(
                        modifier = Modifier
                            .background(extras.glassStrong, blobShape(seed = 3))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.myQuestionsLeft.toPersianDigits(),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = if (state.myQuestionsLeft <= 3) extras.danger else extras.success,
                        )
                        Text(
                            text = "سوال مونده",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "فهمیدی کی هستی؟ گوشی رو بردار و دکمه رو بزن!",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                KButton(
                    text = "جواب دادم! ✅",
                    onClick = onGuessed,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                )
            }
        }

        // ---- جواب دادم: توی لیست راند بعد ----
        state.iHaveGuessed -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                BobbingEmoji(emoji = "😎", fontSize = 52.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "آفرین! تو «${state.myForeheadName}» بودی",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = extras.success,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "با ${state.myQuestionsUsed.toPersianDigits()} سوال جواب دادی — +${state.myQuestionsLeft.coerceAtLeast(1).toPersianDigits()} امتیاز! رفتی لیست راند بعد 🏁",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                WaWaitingList(snapshot = snapshot, stillPlaying = stillPlaying)
            }
        }

        // ---- سوال‌ها تمام شد: باخت این راند ----
        state.iAmOut -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                BobbingEmoji(emoji = "😵", fontSize = 52.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "سوال‌هات تموم شد — این راند رو باختی!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = extras.danger,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "تو «${state.myForeheadName}» بودی! غصه نخور، راند بعد جبران کن 💪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                WaWaitingList(snapshot = snapshot, stillPlaying = stillPlaying)
            }
        }

        // ---- کلمه‌ای برای من نرسید (نویسنده‌ام قطع شد) ----
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BobbingEmoji(emoji = "🫥", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "این راند کلمه‌ای بهت نرسید — راند بعد حتماً بازی می‌کنی!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** لیست کسانی که جواب دادند و کسانی که هنوز روی پیشانی‌اند */
@Composable
private fun WaWaitingList(snapshot: WaSnapshot, stillPlaying: List<WaPlayer>) {
    Spacer(modifier = Modifier.height(14.dp))
    if (snapshot.guessedOrder.isNotEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🏁 لیست راند بعد (به ترتیب جواب):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snapshot.guessedOrder.joinToString("  ←  "),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Text(
        text = "هنوز رو پیشونی: ${stillPlaying.joinToString("، ") { it.name }.ifBlank { "—" }}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 8.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "به بقیه کمک بده — فقط لو نده! 🤐",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WaRoundResultScreen(
    state: WhoAmIUiState,
    onNextRound: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val isLastRound = snapshot.roundIndex >= snapshot.totalRounds
    // مرتب بر اساس سوال کمتر؛ بازنده‌ها ته لیست
    val played = snapshot.players
        .filter { snapshot.assignmentOf(it.name).isNotBlank() }
        .sortedWith(
            compareBy(
                { snapshot.isOut(it.name) },
                { snapshot.questionsUsedOf(it.name) },
            )
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        BobbingEmoji(emoji = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(6.dp))
        StickerTitle(
            text = "راند ${snapshot.roundIndex.toPersianDigits()} از ${snapshot.totalRounds.toPersianDigits()} تموم شد!",
            rotation = -2f,
            fontSize = 24.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // ---- رو شدن کلمه‌ها و امتیاز راند ----
        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🏷️ کی، کی بود؟ (سوال کمتر = امتیاز بیشتر)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                played.forEach { p ->
                    val out = snapshot.isOut(p.name)
                    val used = snapshot.questionsUsedOf(p.name)
                    val gained = snapshot.questionsLeftOf(p.name).coerceAtLeast(1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${p.name} ← ${snapshot.assignmentOf(p.name)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                            )
                            Text(
                                text = if (out) "سوال‌هاش تموم شد 😵" else "با ${used.toPersianDigits()} سوال جواب داد",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (out) "باخت!" else "+${gained.toPersianDigits()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = if (out) extras.danger else extras.success,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- جدول امتیازهای کل ----
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💯 مجموع امتیازها",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                snapshot.players.sortedByDescending { it.totalScore }.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                        )
                        Text(
                            text = p.totalScore.toPersianDigits(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        if (state.isHost) {
            KButton(
                text = if (isLastRound) "کی برد؟ 🏆" else "راند بعد! 🔁",
                onClick = onNextRound,
                modifier = Modifier.navigationBarsPadding(),
            )
        } else {
            Text(
                text = if (isLastRound) "الان ${snapshot.hostName} برنده رو اعلام می‌کنه…"
                else "منتظریم ${snapshot.hostName} راند بعد رو شروع کنه…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun WaGameOverScreen(
    state: WhoAmIUiState,
    winners: List<WaPlayer>,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val snapshot = state.snapshot

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(text = "🏆", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = winners.joinToString(" و ") { it.name },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull()?.colorIndex ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    snapshot.players.sortedByDescending { it.totalScore }.forEachIndexed { rank, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (p in winners) "🥇" else (rank + 1).toPersianDigits(),
                                    fontSize = 17.sp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                                )
                            }
                            Text(
                                text = p.totalScore.toPersianDigits(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ShareWinButton(
                gameId = "who_am_i",
                gameTitle = "من کی‌ام؟",
                gameEmoji = "🏷️",
                winnerText = winners.joinToString(" و ") { it.name },
                scoreLines = snapshot.players.sortedByDescending { it.totalScore }
                    .map { it.name to it.totalScore.toPersianDigits() },
                winnerNames = winners.map { it.name },
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.isHost) {
                KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
                Spacer(modifier = Modifier.height(10.dp))
            }
            KButton(text = "بازگشت به خانه", onClick = onExit, style = KButtonStyle.Glass)
        }
    }
}
