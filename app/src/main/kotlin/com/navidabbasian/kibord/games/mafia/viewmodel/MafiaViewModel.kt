package com.navidabbasian.kibord.games.mafia.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.games.esmfamil.model.nameKey
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.mafia.model.MF_MAX_PLAYERS
import com.navidabbasian.kibord.games.mafia.model.MF_MIN_PLAYERS
import com.navidabbasian.kibord.games.mafia.model.MfPhase
import com.navidabbasian.kibord.games.mafia.model.MfPlayer
import com.navidabbasian.kibord.games.mafia.model.MfRole
import com.navidabbasian.kibord.games.mafia.model.MfSnapshot
import com.navidabbasian.kibord.games.mafia.model.MfWinner
import com.navidabbasian.kibord.games.mafia.model.mafiaCountFor
import com.navidabbasian.kibord.games.mafia.net.MfClient
import com.navidabbasian.kibord.games.mafia.net.MfDiscoveredGame
import com.navidabbasian.kibord.games.mafia.net.MfMessage
import com.navidabbasian.kibord.games.mafia.net.MfNsd
import com.navidabbasian.kibord.games.mafia.net.MfServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** نقش این گوشی در ارتباط شبکه */
enum class MfNetRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class MfLocalScreen { ENTRY, JOIN, IN_GAME }

data class MafiaUiState(
    val role: MfNetRole = MfNetRole.NONE,
    val localScreen: MfLocalScreen = MfLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: MfSnapshot = MfSnapshot(),
    /** بازی‌های پیداشده در شبکه برای پیوستن */
    val discovered: List<MfDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** آدرس این گوشی برای اتصال دستی مهمان‌ها */
    val hostAddress: String = "",
    val hostPort: Int = 0,
    /** ارتباط با میزبان قطع شد */
    val lostConnection: Boolean = false,
) {
    val isHost: Boolean get() = role == MfNetRole.HOST
    val me: MfPlayer? get() = snapshot.player(myName)
    val myRole: MfRole? get() = snapshot.roleOf(myName)
    val iAmAlive: Boolean get() = me?.alive == true
    val iHaveSeen: Boolean get() = snapshot.hasSeen(myName)
    val myDayVote: String? get() = snapshot.dayVoteOf(myName)
    val myMafiaVote: String? get() = snapshot.mafiaVoteOf(myName)

    /** من امشب اقدامم را انجام داده‌ام؟ */
    val iActedTonight: Boolean
        get() = when (myRole) {
            MfRole.MAFIA -> myMafiaVote != null
            MfRole.DOCTOR -> snapshot.doctorSave.isNotBlank()
            MfRole.DETECTIVE -> snapshot.detectiveCheck.isNotBlank()
            else -> true
        }
}

/**
 * موتور شب مافیا — دو نقش در یک ویومدل:
 * میزبان: مرجع حقیقت؛ نقش‌ها را قرعه می‌زند، شب را جمع‌بندی و رای روز را می‌شمارد.
 * مهمان: فرمان می‌فرستد و هرچه از میزبان رسید همان را نشان می‌دهد.
 */
class MafiaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MafiaUiState())
    val uiState: StateFlow<MafiaUiState> = _uiState.asStateFlow()

    private val nsd = MfNsd(application)
    private var server: MfServer? = null
    private var client: MfClient? = null

    // ================= ورود =================

    fun setMyName(name: String) {
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = MfServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { MfMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        nsd.register(name, srv.port)
        val snapshot = MfSnapshot(
            phase = MfPhase.LOBBY,
            players = listOf(MfPlayer(name = name, colorIndex = 0)),
            hostName = name,
        )
        _uiState.update {
            it.copy(
                role = MfNetRole.HOST,
                localScreen = MfLocalScreen.IN_GAME,
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد وگرنه
                // مقایسه‌ی نقش/رای با فاصله‌ی انتهایی کیبورد به هم می‌ریزد
                myName = name,
                hostAddress = MfNsd.localIpAddress() ?: "",
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
            snapshot.phase != MfPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= MF_MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + MfPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != MfNetRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        // با رفتن یک نفر، شرط «همه دیدند/همه اقدام کردند» شاید همین حالا برقرار شود
        maybeFinishReveal()
        maybeResolveNight()
        maybeFinishDayVote()
    }

    // ================= شروع بازی =================

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        val connected = s.players.filter { it.connected }
        if (connected.size < MF_MIN_PLAYERS) return@hostOnly
        // قرعه‌ی نقش‌ها: مافیا(ها)، دکتر، کارآگاه و بقیه شهروند
        val shuffled = connected.shuffled()
        val mafiaCount = mafiaCountFor(connected.size)
        val roles = mutableMapOf<String, MfRole>()
        shuffled.forEachIndexed { i, p ->
            roles[p.name] = when {
                i < mafiaCount -> MfRole.MAFIA
                i == mafiaCount -> MfRole.DOCTOR
                i == mafiaCount + 1 -> MfRole.DETECTIVE
                else -> MfRole.CITIZEN
            }
        }
        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.ROLE_REVEAL,
                players = snap.players.map { it.copy(alive = it.connected) },
                roles = roles,
                nightIndex = 1,
                seen = emptyList(),
                mafiaVotes = emptyMap(),
                doctorSave = "",
                detectiveCheck = "",
                lastKilled = "",
                lastSaved = false,
                dayVotes = emptyMap(),
                lastLynched = "",
                winner = MfWinner.NONE,
            )
        }
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = MfLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
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

    fun joinGame(address: String, port: Int = MfServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        // اسم محلی از همین‌جا با اسمِ ارسالی به میزبان یکسان می‌شود (بدون فاصله‌های سر و ته)
        _uiState.update { it.copy(myName = name, connecting = true, connectError = null) }
        val c = MfClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == MfNetRole.CLIENT) {
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
                        role = MfNetRole.CLIENT,
                        localScreen = MfLocalScreen.IN_GAME,
                        connecting = false,
                        connectError = null,
                    )
                }
            }
        }
    }

    private fun handleServerMessage(msg: MfMessage) {
        when (msg) {
            is MfMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= فرمان‌های داخل بازی =================

    /** «نقشم رو دیدم» */
    fun markSeen() {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.ROLE_REVEAL || st.iHaveSeen) return
        if (st.isHost) hostMarkSeen(st.myName) else client?.send(MfMessage.Seen)
    }

    /** اقدام شبانه‌ی من — معنایش را نقش من تعیین می‌کند */
    fun nightAction(target: String) {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.NIGHT || !st.iAmAlive) return
        if (st.isHost) hostNightAction(st.myName, target) else client?.send(MfMessage.NightAction(target))
    }

    /** میزبان: بحث روز تمام، بریم رای‌گیری */
    fun startDayVote() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_ANNOUNCE) return@hostOnly
        mutateSnapshot { it.copy(phase = MfPhase.DAY_VOTE, dayVotes = emptyMap()) }
    }

    /** رای روز — تا بسته‌شدن رای‌گیری قابل عوض‌کردن است */
    fun dayVote(target: String) {
        val st = _uiState.value
        if (st.snapshot.phase != MfPhase.DAY_VOTE || !st.iAmAlive || sameName(target, st.myName)) return
        if (st.isHost) hostApplyDayVote(st.myName, target) else client?.send(MfMessage.DayVote(target))
    }

    /** میزبان: از نتیجه‌ی روز به شب بعد (یا پایان اگر برنده مشخص شد) */
    fun proceedFromDayResult() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_RESULT) return@hostOnly
        val winner = computeWinner(s)
        if (winner != MfWinner.NONE) {
            mutateSnapshot { it.copy(phase = MfPhase.GAME_OVER, winner = winner) }
        } else {
            mutateSnapshot {
                it.copy(
                    phase = MfPhase.NIGHT,
                    nightIndex = it.nightIndex + 1,
                    mafiaVotes = emptyMap(),
                    doctorSave = "",
                    detectiveCheck = "",
                    lastKilled = "",
                    lastSaved = false,
                    dayVotes = emptyMap(),
                    lastLynched = "",
                )
            }
        }
    }

    /** میزبان: بازی دوباره با همان جمع */
    fun playAgain() = hostOnly {
        mutateSnapshot { s ->
            s.copy(
                phase = MfPhase.LOBBY,
                players = s.players.map { it.copy(alive = true) },
                roles = emptyMap(),
                nightIndex = 0,
                seen = emptyList(),
                mafiaVotes = emptyMap(),
                doctorSave = "",
                detectiveCheck = "",
                lastKilled = "",
                lastSaved = false,
                dayVotes = emptyMap(),
                lastLynched = "",
                winner = MfWinner.NONE,
            )
        }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: MfMessage) {
        when (msg) {
            is MfMessage.Seen -> hostMarkSeen(playerName)
            is MfMessage.NightAction -> hostNightAction(playerName, msg.target)
            is MfMessage.DayVote -> hostApplyDayVote(playerName, msg.target)
            else -> Unit
        }
    }

    private fun hostMarkSeen(playerName: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.ROLE_REVEAL) return
        mutateSnapshot { snap ->
            snap.copy(seen = snap.seen.filterNot { sameName(it, playerName) } + playerName)
        }
        maybeFinishReveal()
    }

    private fun maybeFinishReveal() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.ROLE_REVEAL) return
        val waiting = s.players.filter { it.alive && it.connected && !s.hasSeen(it.name) }
        if (waiting.isEmpty()) {
            mutateSnapshot { it.copy(phase = MfPhase.NIGHT) }
        }
    }

    private fun hostNightAction(playerName: String, target: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.NIGHT) return
        val actor = s.player(playerName) ?: return
        val victim = s.player(target) ?: return
        if (!actor.alive || !victim.alive) return
        when (s.roleOf(playerName)) {
            MfRole.MAFIA -> {
                if (sameName(target, playerName)) return
                mutateSnapshot { snap ->
                    snap.copy(mafiaVotes = snap.mafiaVotes.filterKeys { !sameName(it, playerName) } + (playerName to target))
                }
            }

            MfRole.DOCTOR -> mutateSnapshot { it.copy(doctorSave = target) }

            MfRole.DETECTIVE -> {
                if (sameName(target, playerName)) return
                mutateSnapshot { it.copy(detectiveCheck = target) }
            }

            else -> return
        }
        maybeResolveNight()
    }

    /** وقتی همه‌ی نقش‌های فعالِ زنده اقدام کردند، شب جمع‌بندی می‌شود */
    private fun maybeResolveNight() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.NIGHT) return

        fun aliveConnectedWith(role: MfRole) =
            s.players.filter { it.alive && it.connected && s.roleOf(it.name) == role }

        val mafiaPending = aliveConnectedWith(MfRole.MAFIA).any { s.mafiaVoteOf(it.name) == null }
        val doctorPending = aliveConnectedWith(MfRole.DOCTOR).isNotEmpty() && s.doctorSave.isBlank()
        val detectivePending = aliveConnectedWith(MfRole.DETECTIVE).isNotEmpty() && s.detectiveCheck.isBlank()
        if (mafiaPending || doctorPending || detectivePending) return
        if (s.mafiaVotes.isEmpty()) return

        // قربانی: بیشترین رای مافیا؛ تساوی → اولین انتخاب بین پررای‌ترین‌ها
        val tally = s.mafiaVotes.values.groupingBy { nameKey(it) }.eachCount()
        val maxVotes = tally.values.max()
        val topKeys = tally.filterValues { it == maxVotes }.keys
        val victimKey = s.mafiaVotes.values.map { nameKey(it) }.first { it in topKeys }
        val victim = s.players.firstOrNull { nameKey(it.name) == victimKey }?.name ?: return
        val saved = s.doctorSave.isNotBlank() && sameName(s.doctorSave, victim)

        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.DAY_ANNOUNCE,
                lastKilled = if (saved) "" else victim,
                lastSaved = saved,
                players = if (saved) snap.players
                else snap.players.map { if (sameName(it.name, victim)) it.copy(alive = false) else it },
            )
        }

        // شاید مافیا با همین کشتن برنده شده باشد
        val after = _uiState.value.snapshot
        val winner = computeWinner(after)
        if (winner != MfWinner.NONE) {
            mutateSnapshot { it.copy(phase = MfPhase.GAME_OVER, winner = winner) }
        }
    }

    private fun hostApplyDayVote(voter: String, target: String) {
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_VOTE || sameName(voter, target)) return
        val v = s.player(voter) ?: return
        val t = s.player(target) ?: return
        if (!v.alive || !t.alive) return
        mutateSnapshot { snap ->
            snap.copy(dayVotes = snap.dayVotes.filterKeys { !sameName(it, voter) } + (voter to target))
        }
        maybeFinishDayVote()
    }

    private fun maybeFinishDayVote() {
        if (_uiState.value.role != MfNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != MfPhase.DAY_VOTE) return
        val waiting = s.players.filter { it.alive && it.connected && s.dayVoteOf(it.name) == null }
        if (waiting.isNotEmpty()) return

        // اکثریت نسبی؛ تساوی یعنی امروز کسی اعدام نمی‌شود
        val tally = s.dayVotes.values.groupingBy { nameKey(it) }.eachCount()
        val top = tally.maxByOrNull { it.value }
        val lynchedKey = top?.takeIf { t -> tally.count { it.value == t.value } == 1 }?.key ?: ""
        val lynched = s.players.firstOrNull { nameKey(it.name) == lynchedKey }?.name ?: ""

        mutateSnapshot { snap ->
            snap.copy(
                phase = MfPhase.DAY_RESULT,
                lastLynched = lynched,
                players = if (lynched.isBlank()) snap.players
                else snap.players.map { if (sameName(it.name, lynched)) it.copy(alive = false) else it },
            )
        }
    }

    private fun computeWinner(s: MfSnapshot): MfWinner {
        val aliveMafia = s.players.count { it.alive && s.roleOf(it.name) == MfRole.MAFIA }
        val aliveOthers = s.players.count { it.alive && s.roleOf(it.name) != MfRole.MAFIA }
        return when {
            aliveMafia == 0 -> MfWinner.CITIZENS
            aliveMafia >= aliveOthers -> MfWinner.MAFIA
            else -> MfWinner.NONE
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    /** تغییر وضعیت توسط میزبان + پخش برای همه */
    private fun mutateSnapshot(transform: (MfSnapshot) -> MfSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        server?.broadcast(MfMessage.State(next))
    }

    private fun setSnapshot(next: MfSnapshot) {
        _uiState.update { it.copy(snapshot = next) }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == MfNetRole.HOST) block()
    }

    // ================= خروج و پاک‌سازی =================

    fun leaveGame() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        val name = _uiState.value.myName
        _uiState.value = MafiaUiState(myName = name)
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = MfLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        leaveGame()
    }
}
