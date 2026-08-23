package com.navidabbasian.kibord.games.hokm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.cards.Card
import com.navidabbasian.kibord.core.cards.Suit
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.hokm.engine.AceDeal
import com.navidabbasian.kibord.games.hokm.engine.HokmBot
import com.navidabbasian.kibord.games.hokm.engine.HokmPhase
import com.navidabbasian.kibord.games.hokm.engine.HokmRules
import com.navidabbasian.kibord.games.hokm.engine.HokmState
import com.navidabbasian.kibord.games.hokm.engine.HokmVariant
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

/** صفحه‌های بازی حکم */
enum class HokmStage { Setup, AceDeal, Playing, MatchOver }

/** رویدادهای صوتی که رابط کاربری به صدا ترجمه می‌کند */
enum class HokmSoundEvent { CARD, TRUMP, TRICK_WON, TRICK_LOST, HAND_WON, HAND_LOST, MATCH_OVER, YOUR_TURN }

/** وضعیت رابط کاربری حکم */
data class HokmUiState(
    val stage: HokmStage = HokmStage.Setup,
    val variant: HokmVariant = HokmVariant.FOUR,
    val target: Int = 7,
    val playerName: String = "",
    val game: HokmState? = null,
    /** آس‌کِشی برای حاکم اول و تعداد کارت‌های رو‌شده تا این لحظه */
    val aceDeal: AceDeal? = null,
    val aceRevealed: Int = 0,
    /** کارت‌های میز دارند به سمت برنده جمع می‌شوند */
    val sweeping: Boolean = false,
    /** پیام گذرای وسط میز (مثلاً اعلام حکم) */
    val notice: String? = null,
) {
    /** اسم‌های صندلی‌ها: ۰ بازیکن، بقیه ربات */
    val names: List<String>
        get() {
            val me = playerName.trim().ifBlank { "تو" }
            return (listOf(me) + BOT_NAMES).take(variant.playerCount)
        }

    fun nameOf(seat: Int): String = names.getOrElse(seat) { "؟" }

    /** اسم تیم از دید بازیکن: در چهار نفره «ما/اون‌ها»، وگرنه اسم بازیکن */
    fun teamName(team: Int): String = when (variant) {
        HokmVariant.FOUR -> if (team == 0) "ما" else "اون‌ها"
        else -> nameOf(team)
    }

    val humanTeam: Int get() = variant.teamOf(0)

    companion object {
        val BOT_NAMES = listOf("رضا", "سارا", "نیما")
    }
}

/**
 * موتورگردان حکم: وضعیت بازی، نوبت ربات‌ها (با تأخیر کوتاه)، جمع‌کردن میز
 * و پخش کارت — همه در viewModelScope تا چرخش صفحه چیزی را نخورد.
 */
class HokmViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HokmUiState())
    val uiState: StateFlow<HokmUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<HokmSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<HokmSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random.Default
    private var driver: Job? = null
    private var noticeJob: Job? = null

    init {
        val app = getApplication<Application>()
        val variant = GamePrefs.getString(app, KEY_VARIANT)
            ?.let { v -> HokmVariant.entries.firstOrNull { it.name == v } } ?: HokmVariant.FOUR
        _uiState.value = HokmUiState(
            variant = variant,
            target = GamePrefs.getInt(app, KEY_TARGET, 7).takeIf { it in listOf(3, 5, 7) } ?: 7,
            playerName = GamePrefs.getString(app, KEY_NAME, "") ?: "",
        )
    }

    private fun emit(event: HokmSoundEvent) {
        viewModelScope.launch { _soundEvents.emit(event) }
    }

    // ---------- تنظیمات ----------

    fun setPlayerName(name: String) {
        _uiState.update { it.copy(playerName = name.take(14)) }
    }

    fun setVariant(variant: HokmVariant) {
        _uiState.update { it.copy(variant = variant) }
    }

    fun setTarget(target: Int) {
        _uiState.update { it.copy(target = target) }
    }

    /** شروع مسابقه: تنظیمات ذخیره و آس‌کِشی آغاز می‌شود */
    fun startMatch() {
        val s = _uiState.value
        val app = getApplication<Application>()
        GamePrefs.setString(app, KEY_NAME, s.playerName.trim())
        GamePrefs.setString(app, KEY_VARIANT, s.variant.name)
        GamePrefs.setInt(app, KEY_TARGET, s.target)
        Analytics.gameSetup("variant" to s.variant.analyticsName, "target" to s.target)
        beginAceDeal()
    }

    private fun beginAceDeal() {
        driver?.cancel()
        val s = _uiState.value
        val deal = HokmRules.firstHakemDeal(s.variant, random)
        _uiState.update {
            it.copy(
                stage = HokmStage.AceDeal,
                aceDeal = deal,
                aceRevealed = 0,
                game = null,
                sweeping = false,
                notice = null,
            )
        }
        driver = viewModelScope.launch {
            while (true) {
                val st = _uiState.value
                val d = st.aceDeal ?: return@launch
                if (st.aceRevealed >= d.cards.size) break
                delay(if (st.aceRevealed == 0) 500 else 170)
                _uiState.update { it.copy(aceRevealed = it.aceRevealed + 1) }
                emit(HokmSoundEvent.CARD)
            }
            delay(1600)
            startPlaying()
        }
    }

    /** رد کردن انیمیشن آس‌کِشی */
    fun skipAceDeal() {
        val st = _uiState.value
        val d = st.aceDeal ?: return
        if (st.aceRevealed >= d.cards.size) return
        driver?.cancel()
        _uiState.update { it.copy(aceRevealed = d.cards.size) }
        driver = viewModelScope.launch {
            delay(1200)
            startPlaying()
        }
    }

    private fun startPlaying() {
        val s = _uiState.value
        val hakem = s.aceDeal?.hakem ?: 0
        val match = HokmRules.newMatch(s.variant, s.target, hakem)
        val hand = HokmRules.startHand(match, random)
        _uiState.update { it.copy(stage = HokmStage.Playing, game = hand, sweeping = false, notice = null) }
        drive()
    }

    // ---------- بازی ----------

    /** حلقه‌ی پیش‌برنده: هر کاری که به انسان وابسته نیست را با تأخیر انجام می‌دهد */
    private fun drive() {
        driver?.cancel()
        driver = viewModelScope.launch {
            while (true) {
                val st = _uiState.value
                val g = st.game ?: return@launch
                if (st.stage != HokmStage.Playing) return@launch
                when {
                    g.phase == HokmPhase.CHOOSE_TRUMP && g.hakem != 0 -> {
                        delay(1100)
                        val suit = HokmBot.chooseTrump(g.hands[g.hakem])
                        applyTrump(suit)
                    }

                    g.phase == HokmPhase.CHOOSE_TRUMP -> return@launch

                    g.trickComplete -> {
                        delay(900)
                        _uiState.update { it.copy(sweeping = true) }
                        delay(320)
                        collect()
                    }

                    g.phase == HokmPhase.PLAYING && g.turn != 0 -> {
                        delay(700)
                        val card = HokmBot.choosePlay(g, g.turn)
                        applyPlay(g.turn, card)
                    }

                    else -> return@launch
                }
            }
        }
    }

    /** حاکمِ انسانی حکم را انتخاب کرد */
    fun chooseTrump(suit: Suit) {
        val g = _uiState.value.game ?: return
        if (g.phase != HokmPhase.CHOOSE_TRUMP || g.hakem != 0) return
        applyTrump(suit)
        drive()
    }

    private fun applyTrump(suit: Suit) {
        val st = _uiState.value
        val g = st.game ?: return
        val next = HokmRules.chooseTrump(g, suit)
        _uiState.update { it.copy(game = next) }
        emit(HokmSoundEvent.TRUMP)
        showNotice("${st.nameOf(g.hakem)} حکم کرد: ${suit.symbol} ${suit.persian}", 1800)
    }

    /** بازیکن انسانی کارتی را لمس کرد */
    fun playCard(card: Card) {
        val g = _uiState.value.game ?: return
        if (!HokmRules.isLegal(g, 0, card)) return
        applyPlay(0, card)
        drive()
    }

    private fun applyPlay(seat: Int, card: Card) {
        val g = _uiState.value.game ?: return
        if (!HokmRules.isLegal(g, seat, card)) return
        val next = HokmRules.play(g, seat, card)
        _uiState.update { it.copy(game = next) }
        emit(HokmSoundEvent.CARD)
        if (!next.trickComplete && next.turn == 0 && seat != 0) emit(HokmSoundEvent.YOUR_TURN)
    }

    private fun collect() {
        val st = _uiState.value
        val g = st.game ?: return
        if (!g.trickComplete) return
        val winner = g.trickLeader ?: return
        val next = HokmRules.collectTrick(g)
        _uiState.update { it.copy(game = next, sweeping = false) }
        val myTeam = st.humanTeam
        when (next.phase) {
            HokmPhase.HAND_OVER -> emit(
                if (next.lastResult?.winnerTeam == myTeam) HokmSoundEvent.HAND_WON else HokmSoundEvent.HAND_LOST,
            )
            HokmPhase.MATCH_OVER -> {
                emit(HokmSoundEvent.MATCH_OVER)
                viewModelScope.launch {
                    delay(1400)
                    _uiState.update {
                        if (it.stage == HokmStage.Playing && it.game?.phase == HokmPhase.MATCH_OVER) {
                            it.copy(stage = HokmStage.MatchOver)
                        } else it
                    }
                }
            }
            else -> emit(if (g.teamOf(winner) == myTeam) HokmSoundEvent.TRICK_WON else HokmSoundEvent.TRICK_LOST)
        }
        if (next.phase == HokmPhase.PLAYING && next.turn == 0) emit(HokmSoundEvent.YOUR_TURN)
    }

    /** از پرده‌ی پایان دست: دست بعدی */
    fun nextHand() {
        val g = _uiState.value.game ?: return
        if (g.phase != HokmPhase.HAND_OVER) return
        val hand = HokmRules.startHand(g, random)
        _uiState.update { it.copy(game = hand, sweeping = false, notice = null) }
        drive()
    }

    private fun showNotice(text: String, ms: Long) {
        noticeJob?.cancel()
        _uiState.update { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(ms)
            _uiState.update { if (it.notice == text) it.copy(notice = null) else it }
        }
    }

    // ---------- پایان ----------

    /** دوباره بازی با همان تنظیمات */
    fun playAgain() {
        Analytics.gameReplay()
        beginAceDeal()
    }

    /** برگشت به صفحه‌ی تنظیمات (و لغو همه‌ی کارهای پس‌زمینه) */
    fun backToSetup() {
        driver?.cancel()
        noticeJob?.cancel()
        _uiState.update {
            it.copy(stage = HokmStage.Setup, game = null, aceDeal = null, aceRevealed = 0, sweeping = false, notice = null)
        }
    }

    companion object {
        private const val KEY_NAME = "hokm_player_name"
        private const val KEY_VARIANT = "hokm_variant"
        private const val KEY_TARGET = "hokm_target"
    }
}
