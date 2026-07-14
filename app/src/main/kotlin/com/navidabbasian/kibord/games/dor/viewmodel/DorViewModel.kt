package com.navidabbasian.kibord.games.dor.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.core.settings.SettingsRepository
import com.navidabbasian.kibord.games.dor.data.DorWordRepository
import com.navidabbasian.kibord.games.dor.model.DorGameEvent
import com.navidabbasian.kibord.games.dor.model.DorGameMode
import com.navidabbasian.kibord.games.dor.model.DorPhase
import com.navidabbasian.kibord.games.dor.model.DorSeat
import com.navidabbasian.kibord.games.dor.model.DorSoundEvent
import com.navidabbasian.kibord.games.dor.model.DorTeam
import com.navidabbasian.kibord.games.dor.model.DorUiState
import com.navidabbasian.kibord.games.dor.model.DorWord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * موتور بازی دور: یک تیکِر کوروتینی ۱۰۰ میلی‌ثانیه‌ای که تایمر بمب،
 * زمان تیم، خنک‌شدنِ رد کردن و صداهای تیک را با هم پیش می‌برد.
 *
 * قواعد ظریف حفظ‌شده از نسخه‌ی اصلی:
 * - حدس درست کلمه نوبت را می‌چرخاند اما بمب ادامه می‌دهد (ریست نمی‌شود).
 * - انفجار بمب نوبت را رد نمی‌کند؛ همان بازیکن دور تازه را شروع می‌کند —
 *   مگر این‌که تیمش با جریمه حذف شده باشد.
 * - زمان تیم فقط وقتی کم می‌شود که واقعاً در حال بازی باشند.
 */
class DorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DorUiState())
    val uiState: StateFlow<DorUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<DorSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<DorSoundEvent> = _soundEvents.asSharedFlow()

    private val repository = DorWordRepository(application)
    private val settings = SettingsRepository(application)

    private var tickerJob: Job? = null
    private var wordStartElapsed = 0L
    private var lastWholeBombSecond = -1
    private var tensionStarted = false
    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            // بارگذاری دسته‌ها پیش‌نیازِ هم شروع تازه و هم بازیابی نشست است
            val customJson = settings.dorCustomWordsJson.first()
            repository.loadCategories(customJson)
            _uiState.update { it.copy(categories = repository.categories) }
            // اگر نشستی از پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
            restoreSession()
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی */
    private fun DorUiState.isResumable(): Boolean =
        phase == DorPhase.Playing || phase is DorPhase.TeamEliminated

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(DorUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(DorUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        // صف کلمات گذرا است؛ از دسته‌های انتخابیِ همان بازی دوباره ساخته می‌شود
        repository.prepareWordsForGame(saved.selectedCategoryIds)
        // متغیرهای گذرای تیکِر بازسازی می‌شوند
        wordStartElapsed = SystemClock.elapsedRealtime()
        lastWholeBombSecond = (saved.bombTimeLeftMillis / 1000).toInt()
        tensionStarted = false
        _uiState.value = saved
        // اگر نوبت در حال اجرا بود، تیکِر دوباره روشن می‌شود
        if (saved.phase == DorPhase.Playing && saved.isPlaying) startTicker()
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

    private fun emitSound(event: DorSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    // ---- راه‌اندازی ----

    fun setPlayerCount(count: Int) {
        _uiState.update {
            it.copy(
                playerCount = count,
                playerNames = List(count) { i -> it.playerNames.getOrElse(i) { "" } },
                phase = DorPhase.PlayerNames,
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
        val teamCount = state.playerCount / 2
        val names = state.playerNames.mapIndexed { i, n -> n.ifBlank { "بازیکن ${toPersian(i + 1)}" } }
        // چینش دایره‌ای: نفر i به تیم i % teamCount می‌رود
        val teams = (0 until teamCount).map { t ->
            DorTeam(
                id = t,
                players = names.filterIndexed { i, _ -> i % teamCount == t },
                remainingTimeMillis = state.mode.teamTimeMillis,
            )
        }
        val order = buildCircularOrder(teams)
        _uiState.update { it.copy(teams = teams, circularOrder = order, phase = DorPhase.Categories) }
    }

    private fun buildCircularOrder(teams: List<DorTeam>): List<DorSeat> {
        val order = mutableListOf<DorSeat>()
        for (slot in 0..1) {
            for (t in teams.indices) {
                teams[t].players.getOrNull(slot)?.let { order.add(DorSeat(it, t)) }
            }
        }
        return order
    }

    fun toggleCategory(id: String) {
        _uiState.update { state ->
            val selected = if (id in state.selectedCategoryIds) {
                state.selectedCategoryIds - id
            } else {
                state.selectedCategoryIds + id
            }
            state.copy(selectedCategoryIds = selected)
        }
    }

    fun selectAllCategories(select: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectedCategoryIds = if (select) state.categories.map { it.id }.toSet() else emptySet()
            )
        }
    }

    fun confirmCategories() {
        if (_uiState.value.selectedCategoryIds.isEmpty()) return
        _uiState.update { it.copy(phase = DorPhase.Mode) }
    }

    fun selectMode(mode: DorGameMode) {
        val state = _uiState.value
        repository.prepareWordsForGame(state.selectedCategoryIds)
        _uiState.update {
            it.copy(
                mode = mode,
                teams = it.teams.map { team ->
                    team.copy(remainingTimeMillis = mode.teamTimeMillis, eliminated = false, events = emptyList())
                },
                currentTeamIndex = 0,
                currentPlayerSlot = 0,
                bombTimeLeftMillis = mode.bombTimeMillis,
                isPlaying = false,
                isPaused = false,
                phase = DorPhase.Playing,
            )
        }
    }

    /** افزودن کلمه‌ی سفارشی — هم به بازی فعلی و هم به DataStore برای دفعات بعد */
    fun addCustomWord(text: String, categoryId: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val current = settings.dorCustomWordsJson.first()
            val json = Json { ignoreUnknownKeys = true }
            val list = try {
                if (current.isBlank()) emptyList()
                else json.decodeFromString<List<DorWord>>(current)
            } catch (_: Exception) {
                emptyList()
            }
            val newWord = DorWord(trimmed, categoryId)
            settings.setDorCustomWordsJson(json.encodeToString(list + newWord))
            repository.mergeCustomWords(json.encodeToString(listOf(newWord)))
            _uiState.update { it.copy(categories = repository.categories) }
        }
    }

    // ---- جریان بازی ----

    /** لمس کارت مرکزی: شروع نوبت یا اعلام حدسِ درست */
    fun onCenterTap() {
        val state = _uiState.value
        if (state.phase != DorPhase.Playing || state.isPaused) return
        if (!state.isPlaying) startTurn() else onWordGuessed()
    }

    private fun startTurn() {
        val state = _uiState.value
        val word = repository.nextWord()
        wordStartElapsed = SystemClock.elapsedRealtime()
        lastWholeBombSecond = (state.mode.bombTimeMillis / 1000).toInt()
        tensionStarted = false
        _uiState.update {
            it.copy(
                isPlaying = true,
                currentWord = word?.text ?: "کلمه‌ای نیست!",
                bombTimeLeftMillis = it.mode.bombTimeMillis,
                skipCooldownLeftMillis = it.mode.skipCooldownMillis,
                canSkip = false,
            )
        }
        startTicker()
    }

    private fun onWordGuessed() {
        val state = _uiState.value
        emitSound(DorSoundEvent.WORD_CORRECT)
        emitSound(DorSoundEvent.VIBRATE_SHORT)

        // ثبت رویداد کلمه برای تیم فعلی
        val timeSpent = SystemClock.elapsedRealtime() - wordStartElapsed
        var teams = state.teams.mapIndexed { i, team ->
            if (i == state.currentTeamIndex && !state.currentWord.isNullOrEmpty()) {
                team.copy(
                    events = team.events + DorGameEvent(
                        DorGameEvent.EventType.WORD_GUESSED, state.currentWord, timeSpent
                    )
                )
            } else team
        }

        emitSound(DorSoundEvent.NEXT_TURN)

        // چرخش نوبت به تیم بعدی؛ بمب ادامه می‌دهد!
        val (nextTeam, nextSlot) = nextActive(teams, state.currentTeamIndex, state.currentPlayerSlot)

        if (activeCount(teams) <= 1) {
            finishGame(teams)
            return
        }

        val word = repository.nextWord()
        wordStartElapsed = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                teams = teams,
                currentTeamIndex = nextTeam,
                currentPlayerSlot = nextSlot,
                currentWord = word?.text ?: "کلمه‌ای نیست!",
                skipCooldownLeftMillis = it.mode.skipCooldownMillis,
                canSkip = false,
            )
        }
    }

    fun skipWord() {
        val state = _uiState.value
        if (!state.canSkip || !state.isPlaying || state.isPaused) return
        val word = repository.nextWord()
        wordStartElapsed = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                currentWord = word?.text ?: it.currentWord,
                skipCooldownLeftMillis = it.mode.skipCooldownMillis,
                canSkip = false,
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
                if (!state.isPlaying || state.isPaused || state.phase != DorPhase.Playing) continue
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
            emitSound(if (bombSecond <= 10) DorSoundEvent.TICK_FAST else DorSoundEvent.TICK_NORMAL)
        }
        if (bombSecond <= 10 && !tensionStarted) {
            tensionStarted = true
            emitSound(DorSoundEvent.START_TENSION)
        }

        // ۲) زمان تیم فعلی
        val teams = state.teams.mapIndexed { i, team ->
            if (i == state.currentTeamIndex) {
                team.copy(remainingTimeMillis = (team.remainingTimeMillis - delta).coerceAtLeast(0))
            } else team
        }

        // ۳) خنک‌شدن رد کردن
        val newSkipLeft = (state.skipCooldownLeftMillis - delta).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                bombTimeLeftMillis = newBombLeft,
                teams = teams,
                skipCooldownLeftMillis = newSkipLeft,
                canSkip = newSkipLeft <= 0,
            )
        }

        val currentTeam = teams[state.currentTeamIndex]
        when {
            currentTeam.remainingTimeMillis <= 0 -> onTeamTimeExpired()
            newBombLeft <= 0 -> onBombExploded()
        }
    }

    // ---- انفجار و حذف ----

    private fun onBombExploded() {
        val state = _uiState.value
        stopTension()
        emitSound(DorSoundEvent.EXPLOSION)
        emitSound(DorSoundEvent.VIBRATE_LONG)

        val timeSpent = SystemClock.elapsedRealtime() - wordStartElapsed
        val penalty = state.mode.penaltyMillis

        // ثبت رویداد انفجار + اعمال جریمه به تیم فعلی
        var teams = state.teams.mapIndexed { i, team ->
            if (i == state.currentTeamIndex) {
                val withEvent = if (!state.currentWord.isNullOrEmpty()) {
                    team.events + DorGameEvent(
                        DorGameEvent.EventType.BOMB_EXPLODED, state.currentWord, timeSpent, penalty
                    )
                } else team.events
                val newTime = (team.remainingTimeMillis - penalty).coerceAtLeast(0)
                team.copy(
                    events = withEvent,
                    remainingTimeMillis = newTime,
                    eliminated = team.eliminated || newTime <= 0,
                )
            } else team
        }

        _uiState.update { it.copy(isPlaying = false, showExplosion = true, teams = teams) }

        viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(showExplosion = false) }

            val current = _uiState.value
            val exploded = current.teams[current.currentTeamIndex]

            if (exploded.eliminated) {
                // تیم با جریمه حذف شد → نوبت به تیم بعدی
                val (nextTeam, nextSlot) = nextActive(current.teams, current.currentTeamIndex, current.currentPlayerSlot)
                if (activeCount(current.teams) <= 1) {
                    finishGame(current.teams)
                } else {
                    emitSound(DorSoundEvent.TEAM_ELIMINATED)
                    _uiState.update {
                        it.copy(
                            currentTeamIndex = nextTeam,
                            currentPlayerSlot = nextSlot,
                            bombTimeLeftMillis = it.mode.bombTimeMillis,
                            phase = DorPhase.TeamEliminated(exploded),
                        )
                    }
                }
            } else if (activeCount(current.teams) <= 1) {
                finishGame(current.teams)
            } else {
                // همان بازیکن دور تازه را شروع می‌کند
                _uiState.update { it.copy(bombTimeLeftMillis = it.mode.bombTimeMillis) }
            }
        }
    }

    private fun onTeamTimeExpired() {
        val state = _uiState.value
        stopTension()

        val teams = state.teams.mapIndexed { i, team ->
            if (i == state.currentTeamIndex) team.copy(eliminated = true, remainingTimeMillis = 0) else team
        }
        val eliminated = teams[state.currentTeamIndex]
        val (nextTeam, nextSlot) = nextActive(teams, state.currentTeamIndex, state.currentPlayerSlot)

        _uiState.update { it.copy(isPlaying = false, teams = teams) }

        if (activeCount(teams) <= 1) {
            finishGame(teams)
            return
        }

        emitSound(DorSoundEvent.TEAM_ELIMINATED)
        _uiState.update {
            it.copy(
                currentTeamIndex = nextTeam,
                currentPlayerSlot = nextSlot,
                bombTimeLeftMillis = it.mode.bombTimeMillis,
                phase = DorPhase.TeamEliminated(eliminated),
            )
        }
    }

    /** ادامه پس از دیالوگ حذف تیم — بازیکن بعدی با لمس، دور تازه را شروع می‌کند */
    fun continueAfterElimination() {
        _uiState.update {
            it.copy(phase = DorPhase.Playing, isPlaying = false, bombTimeLeftMillis = it.mode.bombTimeMillis)
        }
    }

    private fun finishGame(teams: List<DorTeam>) {
        tickerJob?.cancel()
        stopTension()
        clearSession()
        emitSound(DorSoundEvent.GAME_OVER)
        val winner = teams.firstOrNull { !it.eliminated }
        _uiState.update { it.copy(isPlaying = false, teams = teams, phase = DorPhase.Winner(winner)) }
    }

    // ---- مکث و خروج ----

    fun pauseGame() {
        stopTension()
        _uiState.update { it.copy(isPaused = true, showPauseDialog = true) }
    }

    fun resumeGame() {
        // تیک بعدی از زمان فعلی ادامه می‌دهد؛ تنش در صورت نیاز دوباره فعال می‌شود
        tensionStarted = false
        _uiState.update { it.copy(isPaused = false, showPauseDialog = false) }
    }

    fun playAgain() {
        tickerJob?.cancel()
        stopTension()
        clearSession()
        repository.reset()
        val old = _uiState.value
        _uiState.update {
            DorUiState(
                playerCount = old.playerCount,
                playerNames = old.playerNames,
                categories = old.categories,
                selectedCategoryIds = old.selectedCategoryIds,
                mode = old.mode,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { state ->
            when (state.phase) {
                DorPhase.PlayerNames -> state.copy(phase = DorPhase.PlayerCount)
                DorPhase.Categories -> state.copy(phase = DorPhase.PlayerNames)
                DorPhase.Mode -> state.copy(phase = DorPhase.Categories)
                else -> state
            }
        }
    }

    private fun stopTension() {
        if (tensionStarted) {
            tensionStarted = false
        }
        emitSound(DorSoundEvent.STOP_TENSION)
    }

    // ---- کمکی‌ها ----

    private fun activeCount(teams: List<DorTeam>) = teams.count { !it.eliminated }

    /** پورت moveToNextTeam: تیم بعدیِ حذف‌نشده؛ با هر دور کامل، بازیکنِ تیم‌ها عوض می‌شود */
    private fun nextActive(teams: List<DorTeam>, teamIndex: Int, playerSlot: Int): Pair<Int, Int> {
        var t = teamIndex
        var slot = playerSlot
        val startT = teamIndex
        val startSlot = playerSlot
        do {
            t = (t + 1) % teams.size
            if (t == 0) slot = (slot + 1) % 2
        } while (teams[t].eliminated && !(t == startT && slot == startSlot))
        return t to slot
    }

    /** نام بازیکن بعدی برای نمایش «نوبت بعدی» */
    fun nextPlayerName(): String {
        val state = _uiState.value
        if (state.teams.isEmpty()) return ""
        val (t, slot) = nextActive(state.teams, state.currentTeamIndex, state.currentPlayerSlot)
        if (state.teams[t].eliminated) return ""
        return state.teams[t].players.getOrNull(slot) ?: ""
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        tickerJob?.cancel()
        stopTension()
        clearSession()
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    private fun toPersian(n: Int): String =
        n.toString().map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    companion object {
        private const val KEY = "session_dor"
    }
}
