package com.navidabbasian.kibord.games.esmfamil.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.games.esmfamil.logic.EfScoring
import com.navidabbasian.kibord.games.esmfamil.model.DEFAULT_TOPICS
import com.navidabbasian.kibord.games.esmfamil.model.EfAnswer
import com.navidabbasian.kibord.games.esmfamil.model.EfPhase
import com.navidabbasian.kibord.games.esmfamil.model.EfPlayer
import com.navidabbasian.kibord.games.esmfamil.model.EfSettings
import com.navidabbasian.kibord.games.esmfamil.model.EfSnapshot
import com.navidabbasian.kibord.games.esmfamil.model.MAX_PLAYERS
import com.navidabbasian.kibord.games.esmfamil.model.MIN_PLAYERS
import com.navidabbasian.kibord.games.esmfamil.net.EfClient
import com.navidabbasian.kibord.games.esmfamil.net.EfDiscoveredGame
import com.navidabbasian.kibord.games.esmfamil.net.EfMessage
import com.navidabbasian.kibord.games.esmfamil.net.EfNsd
import com.navidabbasian.kibord.games.esmfamil.net.EfServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** نقش این گوشی در بازی */
enum class EfRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class EfLocalScreen { ENTRY, JOIN, IN_GAME }

data class EsmFamilUiState(
    val role: EfRole = EfRole.NONE,
    val localScreen: EfLocalScreen = EfLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: EfSnapshot = EfSnapshot(),
    /** بازی‌های پیداشده در شبکه برای پیوستن */
    val discovered: List<EfDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** فرم محلی جواب‌های من: موضوع → کلمه */
    val myAnswers: Map<String, String> = emptyMap(),
    /** آدرس این گوشی برای اتصال دستی مهمان‌ها */
    val hostAddress: String = "",
    val hostPort: Int = 0,
    /** ارتباط با میزبان قطع شد */
    val lostConnection: Boolean = false,
) {
    val isHost: Boolean get() = role == EfRole.HOST
    val me: EfPlayer? get() = snapshot.player(myName)
    val myTurnToPick: Boolean get() = snapshot.pickerName == myName
    val allMyFieldsFilled: Boolean
        get() = snapshot.settings.topics.isNotEmpty() &&
            snapshot.settings.topics.all { !myAnswers[it].isNullOrBlank() }
}

/**
 * موتور اسم فامیل — دو نقش در یک ویومدل:
 * میزبان: مرجع حقیقت؛ منطق بازی را اجرا و عکس وضعیت را پخش می‌کند.
 * مهمان: فرمان می‌فرستد و هرچه از میزبان رسید همان را نشان می‌دهد.
 */
class EsmFamilViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EsmFamilUiState())
    val uiState: StateFlow<EsmFamilUiState> = _uiState.asStateFlow()

    private val nsd = EfNsd(application)
    private var server: EfServer? = null
    private var client: EfClient? = null
    private var tickerJob: Job? = null
    private var collectJob: Job? = null

    /** جواب‌های رسیده‌ی راند جاری (فقط میزبان) */
    private val collected = mutableMapOf<String, Map<String, String>>()
    private var roundApplied = false

    // ================= ورود =================

    fun setMyName(name: String) {
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = EfServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { EfMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        nsd.register(name, srv.port)
        val snapshot = EfSnapshot(
            phase = EfPhase.LOBBY,
            players = listOf(EfPlayer(name = name, colorIndex = 0)),
            hostName = name,
            settings = EfSettings(),
        )
        _uiState.update {
            it.copy(
                role = EfRole.HOST,
                localScreen = EfLocalScreen.IN_GAME,
                hostAddress = EfNsd.localIpAddress() ?: "",
                hostPort = srv.port,
                connectError = null,
            )
        }
        setSnapshot(snapshot)
    }

    /** بررسی ورود مهمان جدید — تهی یعنی پذیرفته شد */
    private fun acceptJoin(name: String): String? {
        val state = _uiState.value
        val snapshot = state.snapshot
        val existing = snapshot.player(name)
        return when {
            name.isBlank() -> "اسم خالی است"
            existing != null && !existing.connected -> {
                // برگشتِ بازیکن قطع‌شده با همان اسم
                mutateSnapshot { s ->
                    s.copy(players = s.players.map { if (it.name == name) it.copy(connected = true) else it })
                }
                null
            }

            existing != null -> "این اسم الان توی بازیه — یک اسم دیگر انتخاب کن"
            snapshot.phase != EfPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + EfPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != EfRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (it.name == name) it.copy(connected = false) else it })
        }
        val s = _uiState.value.snapshot
        // اگر نوبت انتخاب حرفِ همین بازیکن بود، نوبت به نفر بعدی متصل می‌رسد
        if (s.phase == EfPhase.LETTER_PICK && s.pickerName == name) {
            mutateSnapshot { snap -> snap.copy(pickerName = nextConnectedAfter(snap, name)) }
        }
    }

    // ================= تنظیمات میزبان =================

    fun toggleTopic(topic: String) = hostOnly {
        mutateSnapshot { s ->
            val topics = if (topic in s.settings.topics) s.settings.topics - topic
            else s.settings.topics + topic
            s.copy(settings = s.settings.copy(topics = topics))
        }
    }

    fun addCustomTopic(topic: String) = hostOnly {
        val clean = topic.trim()
        if (clean.isBlank()) return@hostOnly
        mutateSnapshot { s ->
            if (clean in s.settings.topics) s
            else s.copy(settings = s.settings.copy(topics = s.settings.topics + clean))
        }
    }

    fun setRoundSeconds(seconds: Int) = hostOnly {
        mutateSnapshot { s -> s.copy(settings = s.settings.copy(roundSeconds = seconds)) }
    }

    fun setTotalRounds(rounds: Int) = hostOnly {
        mutateSnapshot { s -> s.copy(settings = s.settings.copy(totalRounds = rounds.coerceIn(1, 15))) }
    }

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.players.count { it.connected } < MIN_PLAYERS) return@hostOnly
        if (s.settings.topics.size < 2) return@hostOnly
        mutateSnapshot { snap ->
            snap.copy(
                phase = EfPhase.LETTER_PICK,
                roundIndex = 1,
                pickerName = snap.hostName,
                usedLetters = emptyList(),
                currentLetter = "",
                answers = emptyList(),
                roundScores = emptyMap(),
            )
        }
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = EfLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
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

    fun joinGame(address: String, port: Int = EfServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        _uiState.update { it.copy(connecting = true, connectError = null) }
        val c = EfClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == EfRole.CLIENT) {
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
                        role = EfRole.CLIENT,
                        localScreen = EfLocalScreen.IN_GAME,
                        connecting = false,
                        connectError = null,
                    )
                }
            }
        }
    }

    private fun handleServerMessage(msg: EfMessage) {
        when (msg) {
            is EfMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= فرمان‌های داخل بازی =================

    fun pickLetter(letter: String) {
        val st = _uiState.value
        if (!st.myTurnToPick || st.snapshot.phase != EfPhase.LETTER_PICK) return
        if (st.isHost) hostPickLetter(st.myName, letter) else client?.send(EfMessage.PickLetter(letter))
    }

    fun updateAnswer(topic: String, text: String) {
        _uiState.update { it.copy(myAnswers = it.myAnswers + (topic to text)) }
    }

    fun pressStop() {
        val st = _uiState.value
        if (st.snapshot.phase != EfPhase.PLAYING || !st.allMyFieldsFilled) return
        if (st.isHost) hostEndRound(stopper = st.myName) else client?.send(EfMessage.StopRound)
    }

    fun voteReject(topic: String, owner: String, reject: Boolean) {
        val st = _uiState.value
        if (st.snapshot.phase != EfPhase.REVIEW || owner == st.myName) return
        if (st.isHost) hostApplyVote(st.myName, topic, owner, reject)
        else client?.send(EfMessage.Vote(topic, owner, reject))
    }

    /** میزبان: از بازبینی به جدول راند */
    fun proceedFromReview() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW || s.answers.isEmpty()) return@hostOnly
        val roundScores = EfScoring.roundTotals(s.answers)
        mutateSnapshot { snap ->
            snap.copy(
                phase = EfPhase.ROUND_RESULT,
                roundScores = roundScores,
                players = snap.players.map { p ->
                    p.copy(totalScore = p.totalScore + (roundScores[p.name] ?: 0))
                },
            )
        }
    }

    /** میزبان: راند بعد یا اعلام برنده */
    fun proceedFromResult() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.ROUND_RESULT) return@hostOnly
        if (s.roundIndex >= s.settings.totalRounds || s.remainingLetters.isEmpty()) {
            mutateSnapshot { it.copy(phase = EfPhase.GAME_OVER) }
        } else {
            mutateSnapshot { snap ->
                snap.copy(
                    phase = EfPhase.LETTER_PICK,
                    roundIndex = snap.roundIndex + 1,
                    pickerName = nextConnectedAfter(snap, snap.pickerName),
                    currentLetter = "",
                    stopperName = "",
                    answers = emptyList(),
                    roundScores = emptyMap(),
                )
            }
        }
    }

    /** میزبان: بازی دوباره با همان جمع */
    fun playAgain() = hostOnly {
        mutateSnapshot { s ->
            s.copy(
                phase = EfPhase.LOBBY,
                roundIndex = 0,
                usedLetters = emptyList(),
                currentLetter = "",
                stopperName = "",
                answers = emptyList(),
                roundScores = emptyMap(),
                players = s.players.map { it.copy(totalScore = 0) },
            )
        }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: EfMessage) {
        when (msg) {
            is EfMessage.PickLetter -> hostPickLetter(playerName, msg.letter)
            is EfMessage.StopRound -> hostEndRound(stopper = playerName)
            is EfMessage.Submit -> hostReceiveAnswers(playerName, msg.answers)
            is EfMessage.Vote -> hostApplyVote(playerName, msg.topic, msg.owner, msg.reject)
            else -> Unit
        }
    }

    private fun hostPickLetter(playerName: String, letter: String) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.LETTER_PICK || s.pickerName != playerName) return
        if (letter in s.usedLetters || letter.isBlank()) return
        collected.clear()
        roundApplied = false
        mutateSnapshot { snap ->
            snap.copy(
                phase = EfPhase.PLAYING,
                currentLetter = letter,
                usedLetters = snap.usedLetters + letter,
                secondsLeft = snap.settings.roundSeconds,
                stopperName = "",
                answers = emptyList(),
                roundScores = emptyMap(),
            )
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.snapshot
                if (s.phase != EfPhase.PLAYING) break
                val left = s.secondsLeft - 1
                if (left <= 0) {
                    hostEndRound(stopper = "")
                    break
                }
                mutateSnapshot { it.copy(secondsLeft = left) }
            }
        }
    }

    private fun hostEndRound(stopper: String) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.PLAYING) return
        if (stopper.isNotBlank()) {
            // استپ فقط از بازیکنی پذیرفته می‌شود که واقعاً همه را پر کرده — میزبان جواب‌هایش را بعداً می‌گیرد
            tickerJob?.cancel()
        } else {
            tickerJob?.cancel()
        }
        mutateSnapshot { it.copy(phase = EfPhase.REVIEW, secondsLeft = 0, stopperName = stopper) }
        // مهلت کوتاه برای رسیدن جواب‌های همه؛ بعدش با هرچه رسیده حساب می‌کنیم
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            delay(2500)
            hostComputeReview()
        }
    }

    private fun hostReceiveAnswers(playerName: String, answers: Map<String, String>) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW || roundApplied) return
        collected[playerName] = answers
        val connectedCount = s.players.count { it.connected }
        if (collected.size >= connectedCount) {
            collectJob?.cancel()
            hostComputeReview()
        }
    }

    private fun hostComputeReview() {
        if (roundApplied) return
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW) return
        roundApplied = true
        val answers = s.players.filter { it.connected || collected.containsKey(it.name) }.flatMap { p ->
            s.settings.topics.map { topic ->
                EfAnswer(
                    player = p.name,
                    topic = topic,
                    text = collected[p.name]?.get(topic)?.trim().orEmpty(),
                )
            }
        }
        mutateSnapshot { it.copy(answers = EfScoring.computeScores(answers, it.currentLetter)) }
    }

    private fun hostApplyVote(voter: String, topic: String, owner: String, reject: Boolean) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW || voter == owner) return
        val playerCount = s.players.size
        mutateSnapshot { snap ->
            val updated = snap.answers.map { a ->
                if (a.topic != topic || a.player != owner) a
                else {
                    val votes = if (reject) (a.rejectVotes + voter).distinct() else a.rejectVotes - voter
                    a.copy(rejectVotes = votes, rejected = EfScoring.isRejected(votes.size, playerCount))
                }
            }
            snap.copy(answers = EfScoring.computeScores(updated, snap.currentLetter))
        }
    }

    private fun nextConnectedAfter(s: EfSnapshot, name: String): String {
        if (s.players.isEmpty()) return ""
        val start = s.players.indexOfFirst { it.name == name }
        for (i in 1..s.players.size) {
            val candidate = s.players[(start + i) % s.players.size]
            if (candidate.connected) return candidate.name
        }
        return s.players.first().name
    }

    // ================= هسته‌ی همگام‌سازی =================

    /** تغییر وضعیت توسط میزبان + پخش برای همه */
    private fun mutateSnapshot(transform: (EfSnapshot) -> EfSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        server?.broadcast(EfMessage.State(next))
    }

    /**
     * اعمال عکس جدید وضعیت (میزبان و مهمان از همین در می‌آیند) +
     * عوارض جانبی گذار فازها: خالی‌کردن فرم سر راند نو و ارسال خودکار جواب‌ها.
     */
    private fun setSnapshot(next: EfSnapshot) {
        val prev = _uiState.value.snapshot
        _uiState.update { it.copy(snapshot = next) }

        // شروع راند تازه: فرم من از نو
        if (next.phase == EfPhase.PLAYING && prev.phase != EfPhase.PLAYING) {
            _uiState.update { st ->
                st.copy(myAnswers = next.settings.topics.associateWith { "" })
            }
        }

        // پایان راند: جواب‌های من خودکار ارسال می‌شود
        if (next.phase == EfPhase.REVIEW && prev.phase == EfPhase.PLAYING) {
            val st = _uiState.value
            if (st.isHost) hostReceiveAnswers(st.myName, st.myAnswers)
            else client?.send(EfMessage.Submit(st.myAnswers))
        }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == EfRole.HOST) block()
    }

    // ================= خروج و پاک‌سازی =================

    fun leaveGame() {
        tickerJob?.cancel()
        collectJob?.cancel()
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        collected.clear()
        val name = _uiState.value.myName
        _uiState.value = EsmFamilUiState(myName = name)
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = EfLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        leaveGame()
    }
}
