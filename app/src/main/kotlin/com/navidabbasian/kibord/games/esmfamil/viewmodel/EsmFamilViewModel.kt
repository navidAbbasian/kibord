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
import com.navidabbasian.kibord.games.esmfamil.model.COUNTDOWN_SECONDS
import com.navidabbasian.kibord.games.esmfamil.model.EfSnapshot
import com.navidabbasian.kibord.games.esmfamil.model.JUDGE_SCORES
import com.navidabbasian.kibord.games.esmfamil.model.MAX_PLAYERS
import com.navidabbasian.kibord.games.esmfamil.model.MIN_PLAYERS
import com.navidabbasian.kibord.games.esmfamil.model.REVIEW_SECONDS
import com.navidabbasian.kibord.games.esmfamil.model.nameKey
import com.navidabbasian.kibord.games.esmfamil.model.sameName
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
    val myTurnToPick: Boolean get() = sameName(snapshot.pickerName, myName)
    /** من در فاز اعتراض «اعتراضی ندارم» زده‌ام */
    val iAmDoneReviewing: Boolean get() = snapshot.reviewDone.any { sameName(it, myName) }
    /** همه‌ی خانه‌ها با کلمه‌ی حداقل دوحرفی پر شده‌اند — شرط فعال‌شدن استپ */
    val allMyFieldsFilled: Boolean
        get() = snapshot.settings.topics.isNotEmpty() &&
            snapshot.settings.topics.all { (myAnswers[it]?.trim()?.length ?: 0) >= 2 }
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
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد وگرنه
                // مقایسه‌ی نوبت/رای با فاصله‌ی انتهایی کیبورد به هم می‌ریزد
                myName = name,
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
                    s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = true) else it })
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
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        val s = _uiState.value.snapshot
        // اگر نوبت انتخاب حرفِ همین بازیکن بود، نوبت به نفر بعدی متصل می‌رسد
        if (s.phase == EfPhase.LETTER_PICK && sameName(s.pickerName, name)) {
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
        // اسم محلی از همین‌جا با اسمِ ارسالی به میزبان یکسان می‌شود (بدون فاصله‌های سر و ته)
        _uiState.update { it.copy(myName = name, connecting = true, connectError = null) }
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
        // کلمه‌ای که با حرفِ راند شروع نشود از همان اول قابل تایپ نیست
        val letter = _uiState.value.snapshot.currentLetter
        if (text.isNotBlank() && !EfScoring.startsWithLetter(text, letter)) return
        _uiState.update { it.copy(myAnswers = it.myAnswers + (topic to text)) }
    }

    fun pressStop() {
        val st = _uiState.value
        if (st.snapshot.phase != EfPhase.PLAYING || !st.allMyFieldsFilled) return
        if (st.isHost) hostEndRound(stopper = st.myName) else client?.send(EfMessage.StopRound)
    }

    fun voteReject(topic: String, owner: String, reject: Boolean) {
        val st = _uiState.value
        if (st.snapshot.phase != EfPhase.REVIEW || sameName(owner, st.myName)) return
        if (st.isHost) hostApplyVote(st.myName, topic, owner, reject)
        else client?.send(EfMessage.Vote(topic, owner, reject))
    }

    /** «اعتراضی ندارم» — همه بزنند، فاز اعتراض زودتر بسته می‌شود */
    fun markReviewDone() {
        val st = _uiState.value
        if (st.snapshot.phase != EfPhase.REVIEW || st.iAmDoneReviewing) return
        if (st.isHost) hostMarkReviewDone(st.myName) else client?.send(EfMessage.ReviewDone)
    }

    /** میزبان در فاز داوری: حکم امتیازی یک جواب (۰/۵/۱۰/۲۰) — دوباره زدن همان حکم، آن را برمی‌دارد */
    fun judgeSetScore(topic: String, owner: String, score: Int) = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.JUDGE || score !in JUDGE_SCORES) return@hostOnly
        mutateSnapshot { snap ->
            val updated = snap.answers.map { a ->
                if (a.topic == topic && sameName(a.player, owner)) {
                    val newJudged = if (a.judgedScore == score) null else score
                    a.copy(judgedScore = newJudged, rejected = newJudged == 0)
                } else a
            }
            snap.copy(answers = EfScoring.computeScores(updated, snap.currentLetter))
        }
    }

    /** میزبان: پایان داوری و اعمال امتیازهای راند */
    fun proceedFromJudge() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.JUDGE || s.answers.isEmpty()) return@hostOnly
        applyRoundResult()
    }

    private fun applyRoundResult() {
        val s = _uiState.value.snapshot
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
            is EfMessage.ReviewDone -> hostMarkReviewDone(playerName)
            else -> Unit
        }
    }

    private fun hostPickLetter(playerName: String, letter: String) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.LETTER_PICK || !sameName(s.pickerName, playerName)) return
        if (letter in s.usedLetters || letter.isBlank()) return
        collected.clear()
        roundApplied = false
        mutateSnapshot { snap ->
            snap.copy(
                phase = EfPhase.COUNTDOWN,
                currentLetter = letter,
                usedLetters = snap.usedLetters + letter,
                secondsLeft = COUNTDOWN_SECONDS,
                stopperName = "",
                answers = emptyList(),
                roundScores = emptyMap(),
            )
        }
        startCountdownTicker()
    }

    /** شمارش معکوس اعلام حرف — در صفر، راند واقعی شروع می‌شود */
    private fun startCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.snapshot
                if (s.phase != EfPhase.COUNTDOWN) break
                val left = s.secondsLeft - 1
                if (left <= 0) {
                    mutateSnapshot {
                        it.copy(phase = EfPhase.PLAYING, secondsLeft = it.settings.roundSeconds)
                    }
                    startTicker()
                    break
                }
                mutateSnapshot { it.copy(secondsLeft = left) }
            }
        }
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
        collected[nameKey(playerName)] = answers
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
        val answers = s.players.filter { it.connected || collected.containsKey(nameKey(it.name)) }.flatMap { p ->
            s.settings.topics.map { topic ->
                EfAnswer(
                    player = p.name,
                    topic = topic,
                    text = collected[nameKey(p.name)]?.get(topic)?.trim().orEmpty(),
                )
            }
        }
        mutateSnapshot {
            it.copy(
                answers = EfScoring.computeScores(answers, it.currentLetter),
                secondsLeft = REVIEW_SECONDS,
                reviewDone = emptyList(),
            )
        }
        startReviewTicker()
    }

    /** شمارش معکوس ۳۰ ثانیه‌ی فاز اعتراض؛ پایانش فاز داوری میزبان است */
    private fun startReviewTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value.snapshot
                if (s.phase != EfPhase.REVIEW) break
                val left = s.secondsLeft - 1
                if (left <= 0) {
                    finishReviewPhase()
                    break
                }
                mutateSnapshot { it.copy(secondsLeft = left) }
            }
        }
    }

    private fun hostMarkReviewDone(playerName: String) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW || s.answers.isEmpty()) return
        mutateSnapshot { snap ->
            snap.copy(reviewDone = snap.reviewDone.filterNot { sameName(it, playerName) } + playerName)
        }
        val snap = _uiState.value.snapshot
        val connectedNames = snap.players.filter { it.connected }.map { it.name }
        if (connectedNames.all { c -> snap.reviewDone.any { sameName(it, c) } }) {
            tickerJob?.cancel()
            finishReviewPhase()
        }
    }

    /** پایان فاز اعتراض: اگر اعتراضی نیست یکراست نتیجه، وگرنه داوری میزبان */
    private fun finishReviewPhase() {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW) return
        if (s.answers.none { it.rejectVotes.isNotEmpty() }) {
            applyRoundResult()
        } else {
            mutateSnapshot { it.copy(phase = EfPhase.JUDGE, secondsLeft = 0) }
        }
    }

    private fun hostApplyVote(voter: String, topic: String, owner: String, reject: Boolean) {
        val s = _uiState.value.snapshot
        if (s.phase != EfPhase.REVIEW || sameName(voter, owner)) return
        // در فاز اعتراض فقط اعتراض ثبت می‌شود؛ حکم نهایی با میزبان در فاز داوری است
        mutateSnapshot { snap ->
            snap.copy(
                answers = snap.answers.map { a ->
                    if (a.topic != topic || !sameName(a.player, owner)) a
                    else a.copy(
                        rejectVotes = if (reject) a.rejectVotes.filterNot { sameName(it, voter) } + voter
                        else a.rejectVotes.filterNot { sameName(it, voter) }
                    )
                }
            )
        }
    }

    private fun nextConnectedAfter(s: EfSnapshot, name: String): String {
        if (s.players.isEmpty()) return ""
        val start = s.players.indexOfFirst { sameName(it.name, name) }
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
