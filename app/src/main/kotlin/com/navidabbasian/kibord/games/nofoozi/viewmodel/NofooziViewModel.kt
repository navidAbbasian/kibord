package com.navidabbasian.kibord.games.nofoozi.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.net.HostKeepAlive
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.games.esmfamil.model.nameKey
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.nofoozi.model.NF_CITIZEN_SCORE
import com.navidabbasian.kibord.games.nofoozi.model.NF_MAX_PLAYERS
import com.navidabbasian.kibord.games.nofoozi.model.undercoverCountFor
import com.navidabbasian.kibord.games.nofoozi.model.NF_MIN_PLAYERS
import com.navidabbasian.kibord.games.nofoozi.model.NF_UNDERCOVER_SCORE
import com.navidabbasian.kibord.games.nofoozi.model.NfPair
import com.navidabbasian.kibord.games.nofoozi.model.NfPhase
import com.navidabbasian.kibord.games.nofoozi.model.NfPlayer
import com.navidabbasian.kibord.games.nofoozi.model.NfSnapshot
import com.navidabbasian.kibord.games.nofoozi.net.NfClient
import com.navidabbasian.kibord.games.nofoozi.net.NfDiscoveredGame
import com.navidabbasian.kibord.games.nofoozi.net.NfMessage
import com.navidabbasian.kibord.games.nofoozi.net.NfNsd
import com.navidabbasian.kibord.games.nofoozi.net.NfServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** نقش این گوشی در بازی */
enum class NfRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class NfLocalScreen { ENTRY, JOIN, IN_GAME }

data class NofooziUiState(
    val role: NfRole = NfRole.NONE,
    val localScreen: NfLocalScreen = NfLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: NfSnapshot = NfSnapshot(),
    /** بازی‌های پیداشده در شبکه برای پیوستن */
    val discovered: List<NfDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** آدرس این گوشی برای اتصال دستی مهمان‌ها */
    val hostAddress: String = "",
    val hostPort: Int = 0,
    /** ارتباط با میزبان قطع شد */
    val lostConnection: Boolean = false,
) {
    val isHost: Boolean get() = role == NfRole.HOST
    val me: NfPlayer? get() = snapshot.player(myName)
    val myWord: String get() = snapshot.wordOf(myName)
    val iHaveSeen: Boolean get() = snapshot.hasSeen(myName)
    val myVote: String? get() = snapshot.voteOf(myName)
    /** من نفوذیِ این راند بودم؟ فقط صفحه‌ی نتیجه از این استفاده می‌کند */
    val iWasUndercover: Boolean get() = snapshot.isUndercover(myName)
}

/**
 * موتور کلمه‌ی نفوذی — دو نقش در یک ویومدل:
 * میزبان: مرجع حقیقت؛ جفت کلمه را قرعه می‌زند، رای‌ها را می‌شمارد و وضعیت را پخش می‌کند.
 * مهمان: فرمان می‌فرستد و هرچه از میزبان رسید همان را نشان می‌دهد.
 */
class NofooziViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(NofooziUiState())
    val uiState: StateFlow<NofooziUiState> = _uiState.asStateFlow()

    private val nsd = NfNsd(application)
    private val keepAlive = HostKeepAlive(application)
    private var server: NfServer? = null
    private var client: NfClient? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(application)
    private var allPairs: List<NfPair> = emptyList()

    init {
        allPairs = try {
            json.decodeFromString<List<NfPair>>(ContentBank.open(application, "nofoozi.json"))
        } catch (_: Exception) {
            emptyList()
        }.filter { it.word.isNotBlank() && it.similar.isNotBlank() }
    }

    // ================= ورود =================

    fun setMyName(name: String) {
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = NfServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { NfMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        keepAlive.acquire()
        nsd.register(name, srv.port)
        val snapshot = NfSnapshot(
            phase = NfPhase.LOBBY,
            players = listOf(NfPlayer(name = name, colorIndex = 0)),
            hostName = name,
        )
        _uiState.update {
            it.copy(
                role = NfRole.HOST,
                localScreen = NfLocalScreen.IN_GAME,
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد وگرنه
                // مقایسه‌ی رای/کلمه با فاصله‌ی انتهایی کیبورد به هم می‌ریزد
                myName = name,
                hostAddress = NfNsd.localIpAddress() ?: "",
                hostPort = srv.port,
                connectError = null,
            )
        }
        setSnapshot(snapshot)
    }

    /** بررسی ورود مهمان جدید — تهی یعنی پذیرفته شد */
    private fun acceptJoin(name: String): String? {
        val snapshot = _uiState.value.snapshot
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
            snapshot.phase != NfPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= NF_MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + NfPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != NfRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        // با رفتن یک نفر، شرط «همه دیدند/همه رای دادند» شاید همین حالا برقرار شود
        maybeFinishReveal()
        maybeFinishVote()
    }

    // ================= تنظیمات میزبان =================

    fun setTotalRounds(rounds: Int) = hostOnly {
        mutateSnapshot { s -> s.copy(totalRounds = rounds.coerceIn(1, 10)) }
    }

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.players.count { it.connected } < NF_MIN_PLAYERS) return@hostOnly
        dealRound(roundIndex = 1, resetScores = true)
    }

    /** قرعه‌ی راند: جفت کلمه‌ی تازه + نفوذی تصادفی */
    private fun dealRound(roundIndex: Int, resetScores: Boolean = false) {
        val s = _uiState.value.snapshot
        val pair = drawPair() ?: return
        val connected = s.players.filter { it.connected }
        val undercovers = connected.shuffled().take(undercoverCountFor(connected.size))
        val undercoverKeys = undercovers.map { nameKey(it.name) }.toSet()
        // گاهی کلمه‌ها جابه‌جا می‌شوند تا نشود از خودِ کلمه حدس زد کی نفوذی است
        val swap = listOf(true, false).random()
        val citizenWord = if (swap) pair.similar else pair.word
        val undercoverWord = if (swap) pair.word else pair.similar
        mutateSnapshot { snap ->
            snap.copy(
                phase = NfPhase.REVEAL,
                roundIndex = roundIndex,
                players = if (resetScores) snap.players.map { it.copy(totalScore = 0) } else snap.players,
                words = snap.players.associate { p ->
                    p.name to if (nameKey(p.name) in undercoverKeys) undercoverWord else citizenWord
                },
                undercoverNames = undercovers.map { it.name },
                seen = emptyList(),
                votes = emptyMap(),
                accusedName = "",
                caught = false,
            )
        }
    }

    private fun drawPair(): NfPair? {
        if (allPairs.isEmpty()) return null
        val played = playedStore.played(PLAYED_KEY)
        var fresh = allPairs.filter { it.word !in played }
        if (fresh.isEmpty()) {
            playedStore.clear(PLAYED_KEY)
            fresh = allPairs
        }
        val pair = fresh.random()
        playedStore.markPlayed(PLAYED_KEY, pair.word)
        return pair
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = NfLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
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

    fun joinGame(address: String, port: Int = NfServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        // اسم محلی از همین‌جا با اسمِ ارسالی به میزبان یکسان می‌شود (بدون فاصله‌های سر و ته)
        _uiState.update { it.copy(myName = name, connecting = true, connectError = null) }
        val c = NfClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == NfRole.CLIENT) {
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
                        role = NfRole.CLIENT,
                        localScreen = NfLocalScreen.IN_GAME,
                        connecting = false,
                        connectError = null,
                    )
                }
            }
        }
    }

    private fun handleServerMessage(msg: NfMessage) {
        when (msg) {
            is NfMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= فرمان‌های داخل بازی =================

    /** «کلمه‌مو دیدم» */
    fun markSeen() {
        val st = _uiState.value
        if (st.snapshot.phase != NfPhase.REVEAL || st.iHaveSeen) return
        if (st.isHost) hostMarkSeen(st.myName) else client?.send(NfMessage.Seen)
    }

    /** میزبان: بحث تمام، بریم رای‌گیری */
    fun startVoting() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.DISCUSSION) return@hostOnly
        mutateSnapshot { it.copy(phase = NfPhase.VOTE, votes = emptyMap()) }
    }

    /** رای به متهم — تا وقتی همه رای نداده‌اند قابل عوض‌کردن است */
    fun vote(target: String) {
        val st = _uiState.value
        if (st.snapshot.phase != NfPhase.VOTE || sameName(target, st.myName)) return
        if (st.isHost) hostApplyVote(st.myName, target) else client?.send(NfMessage.Vote(target))
    }

    /** میزبان: راند بعد یا اعلام برنده */
    fun proceedFromResult() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.ROUND_RESULT) return@hostOnly
        if (s.roundIndex >= s.totalRounds) {
            mutateSnapshot { it.copy(phase = NfPhase.GAME_OVER) }
        } else {
            dealRound(roundIndex = s.roundIndex + 1)
        }
    }

    /** میزبان: بازی دوباره با همان جمع */
    fun playAgain() = hostOnly {
        mutateSnapshot { s ->
            s.copy(
                phase = NfPhase.LOBBY,
                roundIndex = 0,
                words = emptyMap(),
                undercoverNames = emptyList(),
                seen = emptyList(),
                votes = emptyMap(),
                accusedName = "",
                caught = false,
                players = s.players.map { it.copy(totalScore = 0) },
            )
        }
    }

    fun winners(): List<NfPlayer> {
        val players = _uiState.value.snapshot.players
        val max = players.maxOfOrNull { it.totalScore } ?: return emptyList()
        return players.filter { it.totalScore == max }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: NfMessage) {
        when (msg) {
            is NfMessage.Seen -> hostMarkSeen(playerName)
            is NfMessage.Vote -> hostApplyVote(playerName, msg.target)
            else -> Unit
        }
    }

    private fun hostMarkSeen(playerName: String) {
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.REVEAL) return
        mutateSnapshot { snap ->
            snap.copy(seen = snap.seen.filterNot { sameName(it, playerName) } + playerName)
        }
        maybeFinishReveal()
    }

    private fun maybeFinishReveal() {
        if (_uiState.value.role != NfRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.REVEAL) return
        val waiting = s.players.filter { it.connected && !s.hasSeen(it.name) }
        if (waiting.isEmpty()) {
            mutateSnapshot { it.copy(phase = NfPhase.DISCUSSION) }
        }
    }

    private fun hostApplyVote(voter: String, target: String) {
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.VOTE || sameName(voter, target)) return
        if (s.player(target) == null) return
        mutateSnapshot { snap ->
            snap.copy(votes = snap.votes.filterKeys { !sameName(it, voter) } + (voter to target))
        }
        maybeFinishVote()
    }

    private fun maybeFinishVote() {
        if (_uiState.value.role != NfRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != NfPhase.VOTE) return
        val waiting = s.players.filter { it.connected && s.voteOf(it.name) == null }
        if (waiting.isNotEmpty()) return

        // شمارش رای‌ها: اکثریت نسبی؛ تساوی یعنی نفوذی قِسِر در رفت
        val tally = s.votes.values.groupingBy { nameKey(it) }.eachCount()
        val top = tally.maxByOrNull { it.value }
        val accusedKey = top?.takeIf { t -> tally.count { it.value == t.value } == 1 }?.key ?: ""
        val accused = s.players.firstOrNull { nameKey(it.name) == accusedKey }?.name ?: ""
        val caught = accused.isNotBlank() && s.isUndercover(accused)

        mutateSnapshot { snap ->
            snap.copy(
                phase = NfPhase.ROUND_RESULT,
                accusedName = accused,
                caught = caught,
                players = snap.players.map { p ->
                    val isUndercover = snap.isUndercover(p.name)
                    val gain = when {
                        caught && !isUndercover -> NF_CITIZEN_SCORE
                        !caught && isUndercover -> NF_UNDERCOVER_SCORE
                        else -> 0
                    }
                    p.copy(totalScore = p.totalScore + gain)
                },
            )
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    /** تغییر وضعیت توسط میزبان + پخش برای همه */
    private fun mutateSnapshot(transform: (NfSnapshot) -> NfSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        server?.broadcast(NfMessage.State(next))
    }

    private fun setSnapshot(next: NfSnapshot) {
        _uiState.update { it.copy(snapshot = next) }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == NfRole.HOST) block()
    }

    // ================= خروج و پاک‌سازی =================

    fun leaveGame() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        keepAlive.release()
        val name = _uiState.value.myName
        _uiState.value = NofooziUiState(myName = name)
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = NfLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        leaveGame()
    }

    companion object {
        const val PLAYED_KEY = "nofoozi"
    }
}
