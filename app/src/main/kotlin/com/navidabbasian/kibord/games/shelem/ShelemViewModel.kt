package com.navidabbasian.kibord.games.shelem

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.shelem.engine.ShelemBot
import com.navidabbasian.kibord.games.shelem.engine.ShelemEngine
import com.navidabbasian.kibord.games.shelem.engine.ShelemPhase
import com.navidabbasian.kibord.games.shelem.engine.ShelemRules
import com.navidabbasian.kibord.games.shelem.engine.ShelemSettings
import com.navidabbasian.kibord.games.shelem.engine.ShelemState
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

/** صندلی انسان همیشه ۰ است؛ یارش ۲؛ حریف‌ها ۱ و ۳ */
const val SHELEM_HUMAN = 0

enum class ShelemStage { Setup, Playing }

enum class ShelemSoundEvent { DEAL, BID, PASS, CARD, TRICK_WON, TRICK_LOST, HAND_END, MATCH_WON, MATCH_LOST }

/** اسم‌های ربات‌ها — سه تا از این‌ها تصادفی انتخاب می‌شود */
private val BOT_NAMES = listOf(
    "کامران", "سهراب", "نرگس", "بهرام", "مهتاب", "فرهاد", "شیرین", "داریوش", "پریسا", "کیوان", "لاله", "آرش",
)

data class ShelemUiState(
    val stage: ShelemStage = ShelemStage.Setup,
    val playerName: String = "",
    /** اسم صندلی‌های ۱، ۲، ۳ (به ترتیب) */
    val botNames: List<String> = listOf("سهراب", "کامران", "نرگس"),
    val target: Int = ShelemRules.DEFAULT_TARGET,
    val shelemBonus: Boolean = true,
    val game: ShelemState? = null,
    /** کارت‌های انتخاب‌شده برای خواباندن (فقط وقتی انسان حاکم است) */
    val selectedDiscards: Set<Card> = emptySet(),
    /** انیمیشن پخش کارت در جریان است؛ ورودی‌ها قفل */
    val dealing: Boolean = false,
    /** حباب اعلام شرط هر صندلی در دور شرط‌بندی: «۱۲۰» یا «پاس» */
    val bidBubbles: Map<Int, String> = emptyMap(),
    /** صندلی رباتی که الان دارد فکر می‌کند (برای نشانگر) */
    val thinkingSeat: Int? = null,
    /** بعد از پایان مسابقه: صفحه‌ی برنده نشان داده شود (بعد از دیدن خلاصه‌ی دست آخر) */
    val showFinal: Boolean = false,
) {
    fun seatName(seat: Int): String = when (seat) {
        SHELEM_HUMAN -> playerName.ifBlank { "تو" }
        else -> botNames.getOrElse(seat - 1) { "ربات" }
    }

    fun teamName(team: Int): String = if (team == 0) "ما" else "اون‌ها"
}

class ShelemViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ShelemUiState())
    val uiState: StateFlow<ShelemUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<ShelemSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<ShelemSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random(System.nanoTime())
    private var botJob: Job? = null

    init {
        val app = getApplication<Application>()
        _uiState.update {
            it.copy(
                playerName = GamePrefs.getString(app, "shelem_name", "") ?: "",
                target = GamePrefs.getInt(app, "shelem_target", ShelemRules.DEFAULT_TARGET)
                    .takeIf { t -> t in ShelemRules.TARGETS } ?: ShelemRules.DEFAULT_TARGET,
                shelemBonus = GamePrefs.getBool(app, "shelem_bonus", true),
            )
        }
    }

    private fun emit(event: ShelemSoundEvent) {
        _soundEvents.tryEmit(event)
    }

    // ------------------------------------------------------------------
    // تنظیمات
    // ------------------------------------------------------------------

    fun setPlayerName(name: String) {
        _uiState.update { it.copy(playerName = name.take(20)) }
    }

    fun setTarget(target: Int) {
        _uiState.update { it.copy(target = target) }
    }

    fun setShelemBonus(on: Boolean) {
        _uiState.update { it.copy(shelemBonus = on) }
    }

    /** شروع مسابقه از صفحه‌ی تنظیمات */
    fun startMatch() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, "shelem_name", s.playerName.trim())
        GamePrefs.setInt(app, "shelem_target", s.target)
        GamePrefs.setBool(app, "shelem_bonus", s.shelemBonus)
        Analytics.gameSetup("target" to s.target, "shelem_bonus" to s.shelemBonus)
        launchMatch()
    }

    private fun launchMatch() {
        val s = _uiState.value
        val names = BOT_NAMES.shuffled(random).take(3)
        val game = ShelemEngine.newMatch(ShelemSettings(targetScore = s.target, shelemBonus = s.shelemBonus), random)
        _uiState.update {
            it.copy(
                stage = ShelemStage.Playing,
                botNames = names,
                game = game,
                selectedDiscards = emptySet(),
                dealing = true,
                bidBubbles = emptyMap(),
                thinkingSeat = null,
                showFinal = false,
            )
        }
        emit(ShelemSoundEvent.DEAL)
        runBots()
    }

    /** «دوباره بازی» از صفحه‌ی برنده */
    fun playAgain() {
        Analytics.gameReplay()
        launchMatch()
    }

    /** بازگشت به تنظیمات (ترک مسابقه) */
    fun backToSetup() {
        botJob?.cancel()
        _uiState.update { it.copy(stage = ShelemStage.Setup, game = null, dealing = false, thinkingSeat = null, showFinal = false) }
    }

    // ------------------------------------------------------------------
    // اعمال انسان
    // ------------------------------------------------------------------

    fun humanBid(amount: Int) {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing || amount !in ShelemEngine.availableBids(g, SHELEM_HUMAN)) return
        applyBid(SHELEM_HUMAN, amount)
        runBots()
    }

    fun humanPass() {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing || !ShelemEngine.canPass(g, SHELEM_HUMAN)) return
        applyPass(SHELEM_HUMAN)
        runBots()
    }

    fun toggleDiscard(card: Card) {
        _uiState.update { s ->
            val g = s.game ?: return@update s
            if (g.phase != ShelemPhase.DISCARDING || g.declarer != SHELEM_HUMAN) return@update s
            val sel = s.selectedDiscards
            val next = when {
                card in sel -> sel - card
                sel.size >= ShelemRules.KITTY_SIZE -> sel
                else -> sel + card
            }
            s.copy(selectedDiscards = next)
        }
    }

    fun confirmDiscard() {
        val s = _uiState.value
        val g = s.game ?: return
        if (g.phase != ShelemPhase.DISCARDING || g.declarer != SHELEM_HUMAN) return
        if (s.selectedDiscards.size != ShelemRules.KITTY_SIZE) return
        val next = runCatching { ShelemEngine.discard(g, s.selectedDiscards.toList()) }.getOrNull() ?: return
        _uiState.update { it.copy(game = next, selectedDiscards = emptySet()) }
        emit(ShelemSoundEvent.CARD)
    }

    fun humanChooseTrump(suit: Suit) {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.TRUMP || g.declarer != SHELEM_HUMAN) return
        val next = runCatching { ShelemEngine.chooseTrump(g, suit) }.getOrNull() ?: return
        _uiState.update { it.copy(game = next) }
        emit(ShelemSoundEvent.BID)
        runBots()
    }

    fun humanPlay(card: Card) {
        val g = _uiState.value.game ?: return
        if (_uiState.value.dealing) return
        if (card !in ShelemEngine.legalPlaysFor(g, SHELEM_HUMAN)) return
        val next = runCatching { ShelemEngine.play(g, SHELEM_HUMAN, card) }.getOrNull() ?: return
        _uiState.update { it.copy(game = next) }
        emit(ShelemSoundEvent.CARD)
        runBots()
    }

    /** دست بعدی بعد از دیدن خلاصه */
    fun nextHand() {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.HAND_OVER) return
        val next = ShelemEngine.nextHand(g, random)
        _uiState.update { it.copy(game = next, dealing = true, bidBubbles = emptyMap(), selectedDiscards = emptySet()) }
        emit(ShelemSoundEvent.DEAL)
        runBots()
    }

    /** بعد از خلاصه‌ی دست آخر: برو به صفحه‌ی برنده */
    fun showFinal() {
        val g = _uiState.value.game ?: return
        if (g.phase != ShelemPhase.MATCH_OVER) return
        _uiState.update { it.copy(showFinal = true) }
        emit(if (g.matchWinner == 0) ShelemSoundEvent.MATCH_WON else ShelemSoundEvent.MATCH_LOST)
    }

    // ------------------------------------------------------------------
    // حرکت‌های مشترک
    // ------------------------------------------------------------------

    private fun applyBid(seat: Int, amount: Int) {
        val g = _uiState.value.game ?: return
        val next = runCatching { ShelemEngine.bid(g, seat, amount) }.getOrNull() ?: return
        _uiState.update { it.copy(game = next, bidBubbles = it.bidBubbles + (seat to amount.toString())) }
        emit(ShelemSoundEvent.BID)
    }

    private fun applyPass(seat: Int) {
        val g = _uiState.value.game ?: return
        val next = runCatching { ShelemEngine.pass(g, seat) }.getOrNull() ?: return
        _uiState.update { it.copy(game = next, bidBubbles = it.bidBubbles + (seat to "پاس")) }
        emit(ShelemSoundEvent.PASS)
    }

    // ------------------------------------------------------------------
    // ربات‌ها — یک حلقه‌ی واحد که تا رسیدن نوبت انسان جلو می‌رود
    // ------------------------------------------------------------------

    private fun runBots() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            try {
                if (_uiState.value.dealing) {
                    delay(1400)
                    _uiState.update { it.copy(dealing = false) }
                }
                while (true) {
                    val g = _uiState.value.game ?: return@launch
                    when {
                        g.phase == ShelemPhase.BIDDING && g.bidTurn != SHELEM_HUMAN -> {
                            think(g.bidTurn, 750)
                            val amount = ShelemBot.decideBid(g, g.bidTurn, random)
                            if (amount == null) applyPass(g.bidTurn) else applyBid(g.bidTurn, amount)
                        }

                        g.phase == ShelemPhase.DISCARDING && g.declarer != SHELEM_HUMAN -> {
                            val d = g.declarer!!
                            think(d, 1100)
                            val trump = ShelemBot.chooseTrump(g.hands[d])
                            val discards = ShelemBot.chooseDiscards(g.hands[d], trump)
                            val next = runCatching { ShelemEngine.discard(g, discards) }.getOrNull() ?: return@launch
                            _uiState.update { it.copy(game = next) }
                            emit(ShelemSoundEvent.CARD)
                        }

                        g.phase == ShelemPhase.TRUMP && g.declarer != SHELEM_HUMAN -> {
                            val d = g.declarer!!
                            think(d, 700)
                            val trump = ShelemBot.chooseTrump(g.hands[d])
                            val next = runCatching { ShelemEngine.chooseTrump(g, trump) }.getOrNull() ?: return@launch
                            _uiState.update { it.copy(game = next, bidBubbles = emptyMap()) }
                            emit(ShelemSoundEvent.BID)
                        }

                        g.phase == ShelemPhase.PLAYING && g.trickWinner != null -> {
                            // کارت‌ها کمی روی میز بمانند تا همه ببینند کی برد
                            delay(1150)
                            val next = runCatching { ShelemEngine.collectTrick(g) }.getOrNull() ?: return@launch
                            _uiState.update { it.copy(game = next) }
                            val won = ShelemRules.teamOf(g.trickWinner) == ShelemRules.teamOf(SHELEM_HUMAN)
                            emit(if (won) ShelemSoundEvent.TRICK_WON else ShelemSoundEvent.TRICK_LOST)
                            if (next.phase == ShelemPhase.HAND_OVER || next.phase == ShelemPhase.MATCH_OVER) {
                                delay(350)
                                emit(ShelemSoundEvent.HAND_END)
                            }
                        }

                        g.phase == ShelemPhase.PLAYING && g.turn != SHELEM_HUMAN -> {
                            think(g.turn, 700)
                            val card = ShelemBot.choosePlay(g, g.turn, random)
                            val next = runCatching { ShelemEngine.play(g, g.turn, card) }.getOrNull() ?: return@launch
                            _uiState.update { it.copy(game = next) }
                            emit(ShelemSoundEvent.CARD)
                        }

                        else -> {
                            // نوبت انسان یا پایان دست؛ حباب‌های شرط بعد از شروع بازی پاک می‌شوند
                            if (g.phase == ShelemPhase.PLAYING && _uiState.value.bidBubbles.isNotEmpty()) {
                                _uiState.update { it.copy(bidBubbles = emptyMap()) }
                            }
                            return@launch
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(thinkingSeat = null) }
            }
        }
    }

    private suspend fun think(seat: Int, ms: Long) {
        _uiState.update { it.copy(thinkingSeat = seat) }
        delay(ms)
        _uiState.update { it.copy(thinkingSeat = null) }
    }
}
