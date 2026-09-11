package com.navidabbasian.kibord.games.uno

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.uno.engine.UnoBot
import com.navidabbasian.kibord.games.uno.engine.UnoCard
import com.navidabbasian.kibord.games.uno.engine.UnoColor
import com.navidabbasian.kibord.games.uno.engine.UnoEngine
import com.navidabbasian.kibord.games.uno.engine.UnoKind
import com.navidabbasian.kibord.games.uno.engine.UnoMode
import com.navidabbasian.kibord.games.uno.engine.UnoPhase
import com.navidabbasian.kibord.games.uno.engine.UnoRules
import com.navidabbasian.kibord.games.uno.engine.UnoSettings
import com.navidabbasian.kibord.games.uno.engine.UnoState
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
import kotlin.random.Random

/** صندلی انسان همیشه ۰ است */
const val UNO_HUMAN = 0

enum class UnoStage { Setup, Playing }

enum class UnoSoundEvent { PLAY, DRAW, HIT, UNO_CALL, CAUGHT, ROUND_END, MATCH_WON, MATCH_LOST }

/** انیمیشن جابه‌جایی دست‌ها در هفت-صفر */
data class UnoHandAnim(
    val id: Int,
    /** جفت‌های (از، به) که کارت‌های بسته بینشان پرواز می‌کنند */
    val moves: List<Pair<Int, Int>>,
)

private val BOT_NAMES = listOf(
    "سارا", "نیما", "مهسا", "رضا", "شبنم", "پیمان", "غزل", "بابک", "ترانه", "امید", "نگار", "کاوه",
)

/** مهلت اعلام «اونو!» برای انسان */
const val UNO_WINDOW_MS = 2500L

data class UnoUiState(
    val stage: UnoStage = UnoStage.Setup,
    val playerName: String = "",
    val mode: UnoMode = UnoMode.CLASSIC,
    val players: Int = 4,
    val target: Int = UnoRules.DEFAULT_TARGET,
    /** اسم صندلی‌های ۱ به بعد */
    val botNames: List<String> = listOf("سارا", "نیما", "مهسا"),
    val game: UnoState? = null,
    val thinkingSeat: Int? = null,
    /** پیام گذرای رویدادها */
    val toast: String? = null,
    val toastId: Int = 0,
    /** حباب «اونو!» روی صندلی */
    val unoBubbleSeat: Int? = null,
    val handAnim: UnoHandAnim? = null,
    val dealing: Boolean = false,
    /** بعد از رویت خلاصه‌ی دست آخر، صفحه‌ی برنده */
    val showFinal: Boolean = false,
) {
    fun seatName(seat: Int): String = when (seat) {
        UNO_HUMAN -> playerName.ifBlank { "تو" }
        else -> botNames.getOrElse(seat - 1) { "ربات" }
    }
}

class UnoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UnoUiState())
    val uiState: StateFlow<UnoUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<UnoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<UnoSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random(System.nanoTime())
    private var botJob: Job? = null
    private var unoTimerJob: Job? = null
    private var toastJob: Job? = null
    private var animJob: Job? = null
    private var animId = 0

    init {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                playerName = GamePrefs.getString(app, "uno_name", "") ?: "",
                mode = runCatching { UnoMode.valueOf(GamePrefs.getString(app, "uno_mode", UnoMode.CLASSIC.name) ?: "") }
                    .getOrDefault(UnoMode.CLASSIC),
                players = GamePrefs.getInt(app, "uno_players", 4).coerceIn(2, 4),
                target = GamePrefs.getInt(app, "uno_target", UnoRules.DEFAULT_TARGET)
                    .takeIf { t -> t in UnoRules.TARGETS } ?: UnoRules.DEFAULT_TARGET,
            )
        }
    }

    private fun emit(event: UnoSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toast = message, toastId = it.toastId + 1) }
        toastJob = viewModelScope.launch {
            delay(2300)
            _uiState.update { it.copy(toast = null) }
        }
    }

    // ------------------------------------------------------------------
    // تنظیمات
    // ------------------------------------------------------------------

    fun setPlayerName(name: String) = _uiState.update { it.copy(playerName = name.take(20)) }

    fun setMode(mode: UnoMode) = _uiState.update { it.copy(mode = mode) }

    fun setPlayers(count: Int) = _uiState.update { it.copy(players = count.coerceIn(2, 4)) }

    fun setTarget(target: Int) = _uiState.update { it.copy(target = target) }

    fun startMatch() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, "uno_name", s.playerName.trim())
        GamePrefs.setString(app, "uno_mode", s.mode.name)
        GamePrefs.setInt(app, "uno_players", s.players)
        GamePrefs.setInt(app, "uno_target", s.target)
        Analytics.gameSetup(
            "variant" to s.mode.name.lowercase(),
            "players" to s.players,
            "target" to s.target,
        )
        launchMatch()
    }

    private fun launchMatch() {
        val s = _uiState.value
        val game = UnoEngine.newMatch(UnoSettings(mode = s.mode, players = s.players, target = s.target), random)
        _uiState.update {
            it.copy(
                stage = UnoStage.Playing,
                botNames = BOT_NAMES.shuffled(random).take(s.players - 1),
                game = game,
                thinkingSeat = null,
                toast = null,
                unoBubbleSeat = null,
                handAnim = null,
                dealing = true,
                showFinal = false,
            )
        }
        runBots()
    }

    fun playAgain() {
        Analytics.gameReplay()
        launchMatch()
    }

    fun backToSetup() {
        botJob?.cancel()
        unoTimerJob?.cancel()
        _uiState.update {
            it.copy(stage = UnoStage.Setup, game = null, thinkingSeat = null, dealing = false, showFinal = false)
        }
    }

    // ------------------------------------------------------------------
    // اعمال انسان
    // ------------------------------------------------------------------

    /** بازی یک برگ از دست (وایلدها با رنگ انتخابی می‌آیند) */
    fun humanPlay(card: UnoCard, chosenColor: UnoColor? = null) {
        val g = gameOrNull() ?: return
        if (_uiState.value.dealing || g.turn != UNO_HUMAN || g.phase != UnoPhase.PLAYING) return
        if (g.drawnCard != null) return
        val next = runCatching { UnoEngine.playCard(g, UNO_HUMAN, card, chosenColor) }.getOrNull() ?: return
        afterPlay(g, next, UNO_HUMAN, card)
    }

    /** لمس دسته: یا کشیدن عادی، یا قبول کل جریمه‌ی انباشته */
    fun humanDrawTap() {
        val g = gameOrNull() ?: return
        if (_uiState.value.dealing || g.turn != UNO_HUMAN || g.phase != UnoPhase.PLAYING) return
        if (g.pendingDraw > 0) {
            val amount = g.pendingDraw
            val next = runCatching { UnoEngine.resolvePendingDraw(g) }.getOrNull() ?: return
            updateGame(next)
            emit(UnoSoundEvent.HIT)
            showToast("+${amount.toPersianDigits()} خوردی! 😵")
            runBots()
            return
        }
        if (g.drawnCard != null) return
        val next = runCatching { UnoEngine.drawCard(g, UNO_HUMAN) }.getOrNull() ?: return
        updateGame(next)
        emit(UnoSoundEvent.DRAW)
        runBots()
    }

    /** «بازی کن» برای برگ تازه‌کشیده */
    fun humanPlayDrawn(chosenColor: UnoColor? = null) {
        val g = gameOrNull() ?: return
        val card = g.drawnCard ?: return
        if (g.turn != UNO_HUMAN) return
        val next = runCatching { UnoEngine.playDrawn(g, chosenColor) }.getOrNull() ?: return
        afterPlay(g, next, UNO_HUMAN, card)
    }

    /** «نگه دار» برای برگ تازه‌کشیده */
    fun humanKeepDrawn() {
        val g = gameOrNull() ?: return
        if (g.turn != UNO_HUMAN || g.drawnCard == null) return
        val next = runCatching { UnoEngine.keepDrawn(g) }.getOrNull() ?: return
        updateGame(next)
        runBots()
    }

    /** انتخاب رنگ برای برگ شروع وایلد */
    fun humanChooseStartColor(color: UnoColor) {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.CHOOSE_COLOR || g.turn != UNO_HUMAN) return
        updateGame(UnoEngine.chooseStartColor(g, color))
        runBots()
    }

    /** انتخاب هم‌بازی برای تعویض دست بعد از ۷ */
    fun humanChooseSwap(target: Int) {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.CHOOSE_SWAP || g.swapSeat != UNO_HUMAN) return
        val next = runCatching { UnoEngine.chooseSwap(g, target) }.getOrNull() ?: return
        playHandAnim(listOf(UNO_HUMAN to target, target to UNO_HUMAN))
        updateGame(next)
        showToast("دستت با ${_uiState.value.seatName(target)} عوض شد! 🔄")
        runBots()
    }

    /** دکمه‌ی «اونو!» انسان */
    fun humanCallUno() {
        val g = gameOrNull() ?: return
        if (g.unoPending != UNO_HUMAN) return
        unoTimerJob?.cancel()
        updateGame(UnoEngine.callUno(g, UNO_HUMAN))
        emit(UnoSoundEvent.UNO_CALL)
        bubble(UNO_HUMAN)
    }

    /** «دست بعدی» از خلاصه‌ی دست */
    fun nextRound() {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.ROUND_OVER) return
        _uiState.update { it.copy(game = UnoEngine.newRound(g, random), dealing = true, handAnim = null) }
        runBots()
    }

    /** بعد از خلاصه‌ی دست آخر: صفحه‌ی برنده */
    fun showFinal() {
        val g = gameOrNull() ?: return
        if (g.phase != UnoPhase.MATCH_OVER) return
        _uiState.update { it.copy(showFinal = true) }
        emit(if (g.matchWinner == UNO_HUMAN) UnoSoundEvent.MATCH_WON else UnoSoundEvent.MATCH_LOST)
    }

    // ------------------------------------------------------------------
    // جریان مشترک بعد از هر بازی برگ
    // ------------------------------------------------------------------

    private fun afterPlay(before: UnoState, next: UnoState, seat: Int, card: UnoCard) {
        // انیمیشن چرخش دست‌ها برای صفرِ هفت-صفر
        if (before.settings.mode == UnoMode.SEVEN_ZERO &&
            card.kind == UnoKind.NUMBER && card.number == 0 &&
            next.phase == UnoPhase.PLAYING
        ) {
            val n = before.players
            playHandAnim(List(n) { i -> i to before.nextSeat(i) })
            showToast("همه‌ی دست‌ها چرخید! 🔄")
        }
        updateGame(next)
        emit(UnoSoundEvent.PLAY)
        when (next.phase) {
            UnoPhase.ROUND_OVER, UnoPhase.MATCH_OVER -> emit(UnoSoundEvent.ROUND_END)
            else -> Unit
        }
        runBots()
    }

    private fun gameOrNull(): UnoState? = _uiState.value.game

    private fun updateGame(next: UnoState) {
        _uiState.update { it.copy(game = next) }
        armUnoTimer(next)
    }

    /** پنجره‌ی «اونو!»: ربات‌ها فوری می‌گویند؛ انسان ~۲.۵ ثانیه فرصت دارد */
    private fun armUnoTimer(g: UnoState) {
        val seat = g.unoPending
        if (seat == null) {
            unoTimerJob?.cancel()
            return
        }
        if (seat != UNO_HUMAN) return // ربات‌ها در حلقه‌ی خودشان اعلام می‌کنند
        if (unoTimerJob?.isActive == true) return
        unoTimerJob = viewModelScope.launch {
            delay(UNO_WINDOW_MS)
            val now = gameOrNull() ?: return@launch
            if (now.unoPending == UNO_HUMAN) {
                val caught = runCatching { UnoEngine.penalizeUno(now) }.getOrNull() ?: return@launch
                _uiState.update { it.copy(game = caught) }
                emit(UnoSoundEvent.CAUGHT)
                showToast("مچت رو گرفتن! +۲ 😱")
            }
        }
    }

    private fun bubble(seat: Int) {
        _uiState.update { it.copy(unoBubbleSeat = seat) }
        viewModelScope.launch {
            delay(1500)
            _uiState.update { if (it.unoBubbleSeat == seat) it.copy(unoBubbleSeat = null) else it }
        }
    }

    private fun playHandAnim(moves: List<Pair<Int, Int>>) {
        animJob?.cancel()
        val anim = UnoHandAnim(id = ++animId, moves = moves)
        _uiState.update { it.copy(handAnim = anim) }
        animJob = viewModelScope.launch {
            delay(950)
            _uiState.update { if (it.handAnim?.id == anim.id) it.copy(handAnim = null) else it }
        }
    }

    // ------------------------------------------------------------------
    // حلقه‌ی ربات‌ها
    // ------------------------------------------------------------------

    private fun runBots() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            try {
                if (_uiState.value.dealing) {
                    delay(1100)
                    _uiState.update { it.copy(dealing = false) }
                }
                while (true) {
                    val g = gameOrNull() ?: return@launch
                    val ui = _uiState.value
                    when {
                        g.phase == UnoPhase.ROUND_OVER || g.phase == UnoPhase.MATCH_OVER -> return@launch

                        // ربات به یک برگ رسیده: همیشه فوری «اونو!» می‌گوید
                        g.unoPending != null && g.unoPending != UNO_HUMAN -> {
                            val seat = g.unoPending!!
                            _uiState.update { it.copy(game = UnoEngine.callUno(g, seat)) }
                            emit(UnoSoundEvent.UNO_CALL)
                            bubble(seat)
                        }

                        g.phase == UnoPhase.CHOOSE_COLOR -> {
                            if (g.turn == UNO_HUMAN) return@launch
                            think(g.turn)
                            updateGame(UnoEngine.chooseStartColor(g, UnoBot.pickColor(g.hands[g.turn], random)))
                        }

                        g.phase == UnoPhase.CHOOSE_SWAP -> {
                            val seat = g.swapSeat!!
                            if (seat == UNO_HUMAN) return@launch
                            think(seat)
                            val target = UnoBot.chooseSwapTarget(g, seat)
                            playHandAnim(listOf(seat to target, target to seat))
                            updateGame(UnoEngine.chooseSwap(g, target))
                            showToast("${ui.seatName(seat)} دستش رو با ${ui.seatName(target)} عوض کرد! 🔄")
                        }

                        g.pendingDraw > 0 && g.turn != UNO_HUMAN -> {
                            think(g.turn)
                            val stack = UnoBot.chooseStack(g, g.turn, random)
                            if (stack != null) {
                                val color = if (stack.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                                val next = UnoEngine.playCard(g, g.turn, stack, color)
                                updateGame(next)
                                emit(UnoSoundEvent.PLAY)
                                showToast("${ui.seatName(g.turn)} سوارش کرد: +${next.pendingDraw.toPersianDigits()}! 🔥")
                                if (next.phase != UnoPhase.PLAYING) emit(UnoSoundEvent.ROUND_END)
                            } else {
                                val amount = g.pendingDraw
                                updateGame(UnoEngine.resolvePendingDraw(g))
                                emit(UnoSoundEvent.DRAW)
                                showToast("${ui.seatName(g.turn)} +${amount.toPersianDigits()} خورد!")
                            }
                        }

                        g.pendingDraw > 0 && g.turn == UNO_HUMAN -> {
                            // در بی‌رحم اگر برگی برای سوار کردن هست، انتخاب با انسان است
                            if (UnoEngine.legalPlays(g, UNO_HUMAN).isNotEmpty()) return@launch
                            delay(900)
                            val amount = g.pendingDraw
                            updateGame(UnoEngine.resolvePendingDraw(g))
                            emit(UnoSoundEvent.HIT)
                            showToast("+${amount.toPersianDigits()} خوردی! 😵")
                        }

                        g.drawnCard != null -> {
                            if (g.turn == UNO_HUMAN) return@launch
                            think(g.turn, short = true)
                            val card = g.drawnCard!!
                            val color = if (card.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                            val next = UnoEngine.playDrawn(g, color)
                            afterBotPlay(g, next, g.turn, card)
                        }

                        g.turn != UNO_HUMAN -> {
                            think(g.turn)
                            val card = UnoBot.choosePlay(g, g.turn, random)
                            if (card == null) {
                                updateGame(UnoEngine.drawCard(g, g.turn))
                                emit(UnoSoundEvent.DRAW)
                            } else {
                                val color = if (card.isWild) UnoBot.pickColor(g.hands[g.turn], random) else null
                                val next = UnoEngine.playCard(g, g.turn, card, color)
                                afterBotPlay(g, next, g.turn, card)
                            }
                        }

                        else -> return@launch
                    }
                }
            } finally {
                _uiState.update { it.copy(thinkingSeat = null) }
            }
        }
    }

    /** اثرهای جانبی بازی ربات: صدا، انیمیشن صفر، اعلام رنگ وایلد */
    private fun afterBotPlay(before: UnoState, next: UnoState, seat: Int, card: UnoCard) {
        val ui = _uiState.value
        if (before.settings.mode == UnoMode.SEVEN_ZERO &&
            card.kind == UnoKind.NUMBER && card.number == 0 &&
            next.phase == UnoPhase.PLAYING
        ) {
            playHandAnim(List(before.players) { i -> i to before.nextSeat(i) })
            showToast("همه‌ی دست‌ها چرخید! 🔄")
        }
        if (card.isWild && next.currentColor != null) {
            showToast("${ui.seatName(seat)} رنگ رو ${unoColorNameFa(next.currentColor!!)} کرد")
        }
        updateGame(next)
        emit(UnoSoundEvent.PLAY)
        if (next.phase == UnoPhase.ROUND_OVER || next.phase == UnoPhase.MATCH_OVER) {
            emit(UnoSoundEvent.ROUND_END)
        }
    }

    private suspend fun think(seat: Int, short: Boolean = false) {
        _uiState.update { it.copy(thinkingSeat = seat) }
        delay(if (short) 450 else 600L + random.nextLong(300))
        _uiState.update { it.copy(thinkingSeat = null) }
    }
}

private fun unoColorNameFa(color: UnoColor): String = when (color) {
    UnoColor.RED -> "قرمز"
    UnoColor.YELLOW -> "زرد"
    UnoColor.GREEN -> "سبز"
    UnoColor.BLUE -> "آبی"
}
