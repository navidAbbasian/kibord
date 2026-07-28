package com.navidabbasian.kibord.games.esmfamilsorati.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.games.esmfamilsorati.data.EfsTopicRepository
import com.navidabbasian.kibord.games.esmfamilsorati.model.EFS_LETTER_CARDS
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsConstants
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsLetterCard
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsPhase
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsPlayer
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsSoundEvent
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsTopic
import com.navidabbasian.kibord.games.esmfamilsorati.model.EfsUiState
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
import kotlinx.serialization.json.Json

/**
 * موتور اسم فامیل سرعتی: تیکِر کوروتینی ۱۰۰ میلی‌ثانیه‌ای که بمب مشترک،
 * ذخیره‌ی شخصی بازیکنِ نوبت‌دار و خنک‌شدنِ پاس را با هم پیش می‌برد.
 *
 * قواعد کلیدی:
 * - پاس‌دادن نوبت بمب را ریست نمی‌کند؛ فقط انفجار (و شروع پس از حذف) ریستش می‌کند.
 * - انفجار = ۵ ثانیه جریمه از ذخیره‌ی نوبت‌دار؛ همان بازیکن با همان حرف ادامه می‌دهد.
 * - ساعت نفر بعدی بلافاصله با پاس‌دادن راه می‌افتد — زمان دست‌به‌دست عمداً حساب می‌شود.
 * - در هم‌زمانی صفرشدنِ ذخیره و بمب در یک تیک، حذف مقدم است و بمب هم ریست می‌شود.
 */
class EfsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EfsUiState())
    val uiState: StateFlow<EfsUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<EfsSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<EfsSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = EfsTopicRepository(application)

    private var tickerJob: Job? = null
    private var turnStartElapsed = 0L
    private var lastWholeBombSecond = -1
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            repository.load()
            _uiState.update { it.copy(topics = repository.topics) }
            restoreSession()
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** فقط وسطِ بازیِ واقعی ارزش ذخیره‌شدن دارد */
    private fun EfsUiState.isResumable(): Boolean =
        phase == EfsPhase.Playing || phase is EfsPhase.PlayerEliminated

    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(EfsUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(EfsUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        turnStartElapsed = SystemClock.elapsedRealtime()
        lastWholeBombSecond = (saved.bombTimeLeftMillis / 1000).toInt()
        // بانک موضوع تازه بارگذاری شده؛ لیست ذخیره‌شده با نسخه‌ی فعلی جایگزین می‌شود
        _uiState.value = saved.copy(topics = repository.topics, showPauseDialog = false, isPaused = false)
        if (saved.showExplosion) {
            // اگر وسط روکش انفجار کشته شده، دنباله‌ی انفجار از نو اجرا می‌شود
            _uiState.update { it.copy(showExplosion = false) }
            startTicker()
            resolveExplosionAftermath()
        } else if (saved.phase == EfsPhase.Playing && saved.isPlaying) {
            startTicker()
        }
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

    private fun emitSound(event: EfsSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    // ---- راه‌اندازی ----

    fun setPlayerCount(count: Int) {
        _uiState.update {
            it.copy(
                playerCount = count,
                playerNames = List(count) { i -> it.playerNames.getOrElse(i) { "" } },
                phase = EfsPhase.PlayerNames,
            )
        }
    }

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { state ->
            state.copy(playerNames = state.playerNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmNames() {
        val state = _uiState.value
        val players = state.playerNames.mapIndexed { i, n ->
            EfsPlayer(id = i, name = n.ifBlank { "بازیکن ${toPersian(i + 1)}" })
        }
        _uiState.update { it.copy(players = players, topic = null, phase = EfsPhase.TopicSelect) }
    }

    fun selectTopic(topic: EfsTopic) {
        _uiState.update { it.copy(topic = topic) }
    }

    /** شروع بازی از صفحه‌ی انتخاب موضوع — ساعت نفر اول از همین لحظه راه می‌افتد */
    fun startGame() {
        val state = _uiState.value
        if (state.topic == null || state.players.isEmpty()) return
        val deck = EFS_LETTER_CARDS.shuffled()
        turnStartElapsed = SystemClock.elapsedRealtime()
        lastWholeBombSecond = (EfsConstants.BOMB_MILLIS / 1000).toInt()
        _uiState.update {
            it.copy(
                players = it.players.map { p ->
                    p.copy(remainingTimeMillis = EfsConstants.PLAYER_BANK_MILLIS, eliminated = false)
                },
                currentPlayerIndex = 0,
                currentCard = deck.first(),
                deck = deck.drop(1),
                bombTimeLeftMillis = EfsConstants.BOMB_MILLIS,
                passCooldownLeftMillis = 0,
                canPass = true,
                isPlaying = true,
                isPaused = false,
                phase = EfsPhase.Playing,
                passCounts = emptyMap(),
                removedCardKeys = emptySet(),
            )
        }
        startTicker()
    }

    // ---- جریان بازی ----

    /**
     * کشیدن کارت بعدی از دسته؛ دسته که خالی شد بُر مجدد کامل —
     * اگر کارت اولِ دسته‌ی تازه همان کارت نمایش‌داده‌شده باشد، جای آن عوض می‌شود
     * تا در مرز بُر تکرار پشت‌سرهم پیش نیاید.
     * کارت‌های ۲ بار پاس‌شده از چرخه‌ی این دست حذف‌اند و دیگر برنمی‌گردند.
     */
    private fun drawCard(state: EfsUiState): Pair<EfsLetterCard, List<EfsLetterCard>> {
        var deck = state.deck.filterNot { it.key in state.removedCardKeys }
        if (deck.isEmpty()) {
            deck = EFS_LETTER_CARDS.filterNot { it.key in state.removedCardKeys }
                .ifEmpty { EFS_LETTER_CARDS }
                .shuffled()
            if (deck.size > 1 && deck.first() == state.currentCard) {
                deck = deck.drop(1) + deck.first()
            }
        }
        return deck.first() to deck.drop(1)
    }

    /** لمس کارت مرکزی: در حالت آماده‌باش ادامه‌ی نوبت، وگرنه گفتم — نوبت به نفر بعد */
    fun onCenterTap() {
        val state = _uiState.value
        if (state.phase != EfsPhase.Playing || state.isPaused || state.showExplosion) return
        if (!state.isPlaying) {
            // آماده‌باش پس از انفجار: همان بازیکن با همان حرف با لمس خودش ادامه می‌دهد
            turnStartElapsed = SystemClock.elapsedRealtime()
            emitSound(EfsSoundEvent.VIBRATE_SHORT)
            _uiState.update { it.copy(isPlaying = true) }
            // اگر از نشستِ بازیابی‌شده وسط آماده‌باش آمده باشیم، تیکِر خاموش است
            startTicker()
            return
        }
        // ضد دابل‌تپ: لمس ناخواسته نباید نوبت نفر بعد را بسوزاند
        if (SystemClock.elapsedRealtime() - turnStartElapsed < 300) return

        val next = nextActive(state.players, state.currentPlayerIndex)
        val (card, deck) = drawCard(state)
        turnStartElapsed = SystemClock.elapsedRealtime()
        // مثل بازی دور: اول صدای «گفت!» بعد صدای تعویض نوبت
        emitSound(EfsSoundEvent.WORD_CORRECT)
        emitSound(EfsSoundEvent.VIBRATE_SHORT)
        emitSound(EfsSoundEvent.NEXT_TURN)
        _uiState.update {
            it.copy(
                currentPlayerIndex = next,
                currentCard = card,
                deck = deck,
                passCooldownLeftMillis = 0,
                canPass = true,
            )
        }
    }

    /** پاس حرف: ۱ ثانیه جریمه از ذخیره‌ی شخصی و کارت تازه — بمب دست نمی‌خورد.
     *  حرفی که در یک دست ۲ بار پاس شود از چرخه حذف می‌شود: یعنی دسته آن حرف را ندارد. */
    fun passLetter() {
        val state = _uiState.value
        if (state.phase != EfsPhase.Playing || !state.isPlaying || state.isPaused || !state.canPass) return

        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayerIndex) {
                p.copy(remainingTimeMillis = (p.remainingTimeMillis - EfsConstants.PASS_PENALTY_MILLIS).coerceAtLeast(0))
            } else p
        }
        emitSound(EfsSoundEvent.PASS_LETTER)

        // شمارش پاس‌های این کارت؛ با پاس دوم از چرخه‌ی این دست حذف می‌شود
        val passedKey = state.currentCard?.key
        val passCounts = if (passedKey != null) {
            state.passCounts + (passedKey to (state.passCounts[passedKey] ?: 0) + 1)
        } else state.passCounts
        val removedCardKeys = if (passedKey != null && (passCounts[passedKey] ?: 0) >= 2) {
            state.removedCardKeys + passedKey
        } else state.removedCardKeys

        if (players[state.currentPlayerIndex].remainingTimeMillis <= 0) {
            // خودحذفی با پاس — همان مسیر اتمام وقت
            _uiState.update { it.copy(players = players, passCounts = passCounts, removedCardKeys = removedCardKeys) }
            onPlayerTimeExpired()
            return
        }

        val stateForDraw = state.copy(removedCardKeys = removedCardKeys)
        val (card, deck) = drawCard(stateForDraw)
        _uiState.update {
            it.copy(
                players = players,
                currentCard = card,
                deck = deck,
                passCooldownLeftMillis = EfsConstants.PASS_COOLDOWN_MILLIS,
                canPass = false,
                passCounts = passCounts,
                removedCardKeys = removedCardKeys,
            )
        }
    }

    // ---- تیکِر ----

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                val state = _uiState.value
                if (!state.isPlaying || state.isPaused || state.phase != EfsPhase.Playing) continue
                onTick(delta)
            }
        }
    }

    private fun onTick(delta: Long) {
        val state = _uiState.value

        // ۱) بمب
        val newBombLeft = (state.bombTimeLeftMillis - delta).coerceAtLeast(0)
        val bombSecond = (newBombLeft / 1000).toInt()
        if (bombSecond != lastWholeBombSecond) {
            lastWholeBombSecond = bombSecond
            emitSound(if (bombSecond <= 10) EfsSoundEvent.TICK_FAST else EfsSoundEvent.TICK_NORMAL)
        }

        // ۲) ذخیره‌ی شخصی بازیکن نوبت‌دار
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayerIndex) {
                p.copy(remainingTimeMillis = (p.remainingTimeMillis - delta).coerceAtLeast(0))
            } else p
        }

        // ۳) خنک‌شدن پاس
        val newPassLeft = (state.passCooldownLeftMillis - delta).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                bombTimeLeftMillis = newBombLeft,
                players = players,
                passCooldownLeftMillis = newPassLeft,
                canPass = newPassLeft <= 0,
            )
        }

        // حذف بر انفجار مقدم است؛ در حذف، بمب هم ریست می‌شود
        when {
            players[state.currentPlayerIndex].remainingTimeMillis <= 0 -> onPlayerTimeExpired()
            newBombLeft <= 0 -> onBombExploded()
        }
    }

    // ---- انفجار و حذف ----

    private fun onBombExploded() {
        val state = _uiState.value
        emitSound(EfsSoundEvent.EXPLOSION)
        emitSound(EfsSoundEvent.VIBRATE_LONG)

        // جریمه‌ی انفجار به ذخیره‌ی شخصی نوبت‌دار
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayerIndex) {
                val newTime = (p.remainingTimeMillis - EfsConstants.BOMB_PENALTY_MILLIS).coerceAtLeast(0)
                p.copy(remainingTimeMillis = newTime, eliminated = p.eliminated || newTime <= 0)
            } else p
        }

        _uiState.update { it.copy(isPlaying = false, showExplosion = true, players = players) }

        viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(showExplosion = false) }
            resolveExplosionAftermath()
        }
    }

    /**
     * دنباله‌ی انفجار پس از روکش — جدا شده تا بازیابیِ نشستِ وسطِ روکش هم
     * بتواند همین منطق را از نو اجرا کند.
     */
    private fun resolveExplosionAftermath() {
        val current = _uiState.value
        val exploded = current.players.getOrNull(current.currentPlayerIndex) ?: return

        if (exploded.eliminated) {
            if (activeCount(current.players) <= 1) {
                finishGame(current.players)
            } else {
                emitSound(EfsSoundEvent.PLAYER_ELIMINATED)
                val next = nextActive(current.players, current.currentPlayerIndex)
                _uiState.update {
                    it.copy(
                        currentPlayerIndex = next,
                        bombTimeLeftMillis = EfsConstants.BOMB_MILLIS,
                        phase = EfsPhase.PlayerEliminated(exploded),
                    )
                }
            }
        } else {
            // به خواست ارباب: بعد از انفجار بازی نگه داشته می‌شود و همان بازیکن
            // با دکمه‌ی «آماده‌ام» روی کارت مرکزی، با همان حرف ادامه می‌دهد
            lastWholeBombSecond = (EfsConstants.BOMB_MILLIS / 1000).toInt()
            _uiState.update {
                it.copy(bombTimeLeftMillis = EfsConstants.BOMB_MILLIS, isPlaying = false)
            }
        }
    }

    private fun onPlayerTimeExpired() {
        val state = _uiState.value
        val players = state.players.mapIndexed { i, p ->
            if (i == state.currentPlayerIndex) p.copy(eliminated = true, remainingTimeMillis = 0) else p
        }
        val eliminated = players[state.currentPlayerIndex]

        _uiState.update { it.copy(isPlaying = false, players = players) }

        if (activeCount(players) <= 1) {
            finishGame(players)
            return
        }

        emitSound(EfsSoundEvent.PLAYER_ELIMINATED)
        val next = nextActive(players, state.currentPlayerIndex)
        _uiState.update {
            it.copy(
                currentPlayerIndex = next,
                bombTimeLeftMillis = EfsConstants.BOMB_MILLIS,
                phase = EfsPhase.PlayerEliminated(eliminated),
            )
        }
    }

    /** ادامه پس از صفحه‌ی حذف — کارت تازه برای نفر بعدی و شروع بلافاصله‌ی ساعتش */
    fun continueAfterElimination() {
        val state = _uiState.value
        if (state.phase !is EfsPhase.PlayerEliminated) return
        val (card, deck) = drawCard(state)
        turnStartElapsed = SystemClock.elapsedRealtime()
        lastWholeBombSecond = (EfsConstants.BOMB_MILLIS / 1000).toInt()
        _uiState.update {
            it.copy(
                currentCard = card,
                deck = deck,
                bombTimeLeftMillis = EfsConstants.BOMB_MILLIS,
                passCooldownLeftMillis = 0,
                canPass = true,
                isPlaying = true,
                phase = EfsPhase.Playing,
            )
        }
        startTicker()
    }

    private fun finishGame(players: List<EfsPlayer>) {
        tickerJob?.cancel()
        clearSession()
        emitSound(EfsSoundEvent.GAME_OVER)
        val winner = players.firstOrNull { !it.eliminated }
        _uiState.update { it.copy(isPlaying = false, players = players, phase = EfsPhase.Winner(winner)) }
    }

    // ---- مکث و خروج ----

    fun pauseGame() {
        _uiState.update { it.copy(isPaused = true, showPauseDialog = true) }
    }

    fun resumeGame() {
        _uiState.update { it.copy(isPaused = false, showPauseDialog = false) }
    }

    /** دوباره بازی: همان بازیکن‌ها با ذخیره‌ی پر — مستقیم به انتخاب موضوع تازه */
    fun playAgain() {
        tickerJob?.cancel()
        clearSession()
        _uiState.update {
            it.copy(
                players = it.players.map { p ->
                    p.copy(remainingTimeMillis = EfsConstants.PLAYER_BANK_MILLIS, eliminated = false)
                },
                currentPlayerIndex = 0,
                currentCard = null,
                deck = emptyList(),
                topic = null,
                bombTimeLeftMillis = EfsConstants.BOMB_MILLIS,
                passCooldownLeftMillis = 0,
                canPass = true,
                isPlaying = false,
                isPaused = false,
                showExplosion = false,
                showPauseDialog = false,
                phase = EfsPhase.TopicSelect,
                passCounts = emptyMap(),
                removedCardKeys = emptySet(),
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                EfsPhase.PlayerNames -> state.copy(phase = EfsPhase.PlayerCount)
                EfsPhase.TopicSelect -> state.copy(phase = EfsPhase.PlayerNames)
                else -> state
            }
        }
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        tickerJob?.cancel()
        clearSession()
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    // ---- کمکی‌ها ----

    private fun activeCount(players: List<EfsPlayer>) = players.count { !it.eliminated }

    /** بازیکن بعدیِ حذف‌نشده در جهت دایره */
    private fun nextActive(players: List<EfsPlayer>, index: Int): Int {
        var i = index
        do {
            i = (i + 1) % players.size
        } while (players[i].eliminated && i != index)
        return i
    }

    /** نام بازیکن بعدی برای نمایش روی دکمه‌ها */
    fun nextPlayerName(): String {
        val state = _uiState.value
        if (state.players.isEmpty()) return ""
        val next = nextActive(state.players, state.currentPlayerIndex)
        return state.players[next].name
    }

    private fun toPersian(n: Int): String =
        n.toString().map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    companion object {
        private const val KEY = "session_esm_famil_sorati"
    }
}
