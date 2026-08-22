package com.navidabbasian.kibord.games.backgammon

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.games.backgammon.engine.BgEngine
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgRules
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.engine.relToAbs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** مرحله‌های صفحه‌ای بازی تخته‌نرد */
enum class BgStage { VariantSelect, Playing }

/** رویدادهای صوتی که رابط کاربری به صدای مناسب ترجمه می‌کند */
enum class BgSoundEvent { DICE, MOVE, HIT, BEAR_OFF, SKIP, WIN }

/**
 * وضعیت رابط کاربری تخته‌نرد — مبدأ و مقصدهای مجاز به شماره‌ی مطلق صفحه
 * ترجمه شده‌اند تا صفحه‌ی نقاشی مستقیم مصرف‌شان کند.
 */
data class BgUiState(
    val stage: BgStage = BgStage.VariantSelect,
    val game: BgState? = null,
    /** مبدأ انتخاب‌شده از دید بازیکن نوبت — ۲۵ یعنی ورود از بار/بیرون */
    val selectedSource: Int? = null,
    /** خانه‌های مطلقِ مبدأ مجاز */
    val sourcesAbs: Set<Int> = emptySet(),
    /** آیا ورود (بار یا مهره‌های بیرون) مبدأ مجاز است؟ */
    val entryIsSource: Boolean = false,
    /** خانه‌های مطلقِ مقصد مجاز برای مبدأ انتخاب‌شده */
    val destsAbs: Set<Int> = emptySet(),
    /** آیا خارج‌کردن مهره مقصد مجاز مبدأ انتخاب‌شده است؟ */
    val offIsDest: Boolean = false,
    /** پیام رد شدن نوبت — null یعنی پیامی نیست */
    val skipMessage: String? = null,
)

/**
 * موتورگردان تخته‌نرد در حالت دو نفره روی یک گوشی:
 * سیم‌کشی بین BgEngine خالص و صفحه‌های کامپوز.
 */
class BackgammonViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BgUiState())
    val uiState: StateFlow<BgUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<BgSoundEvent>(extraBufferCapacity = 8)
    val soundEvents: SharedFlow<BgSoundEvent> = _soundEvents.asSharedFlow()

    private var engine: BgEngine? = null

    /** حرکت‌های آغازین مجاز این لحظه — با هر تغییر وضعیت تازه می‌شود */
    private var currentMoves: List<BgMove> = emptyList()

    private fun emitSound(event: BgSoundEvent) {
        viewModelScope.launch { _soundEvents.emit(event) }
    }

    /** انتخاب روش بازی و ساخت بازی تازه */
    fun chooseVariant(variant: BgVariant) {
        val e = BgEngine(BgRules.of(variant))
        engine = e
        currentMoves = emptyList()
        _uiState.value = BgUiState(stage = BgStage.Playing, game = e.createGame())
    }

    /** پرتاب تک‌تاس شروع — در صورت تساوی همان فاز می‌ماند و دوباره صدا زده می‌شود */
    fun rollOpening() {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        if (game.phase != BgPhase.OPENING_ROLL) return
        emitSound(BgSoundEvent.DICE)
        val next = e.rollOpening(game)
        setGame(next)
        if (next.phase == BgPhase.MOVING) afterDiceReady()
    }

    /** پرتاب دو تاس نوبت */
    fun rollDice() {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        if (game.phase != BgPhase.ROLLING) return
        emitSound(BgSoundEvent.DICE)
        setGame(e.rollTurn(game))
        afterDiceReady()
    }

    /** لمس یک خانه‌ی مطلق صفحه: انتخاب مبدأ یا اجرای حرکت به مقصد */
    fun tapPoint(abs: Int) {
        val game = _uiState.value.game ?: return
        val player = game.turn ?: return
        if (game.phase != BgPhase.MOVING) return
        val rel = relToAbs(player, abs)
        val selected = _uiState.value.selectedSource
        if (selected != null) {
            val move = currentMoves.firstOrNull { it.from == selected && it.to == rel }
            if (move != null) {
                applyMove(move)
                return
            }
        }
        if (currentMoves.any { it.from == rel }) {
            select(rel)
        } else {
            clearSelection()
        }
    }

    /** لمس بار یا انبار مهره‌های واردنشده */
    fun tapEntry() {
        if (currentMoves.any { it.from == BgMove.ENTRY }) select(BgMove.ENTRY)
    }

    /** لمس سینی مهره‌های خارج‌شده — اجرای خروج اگر مجاز باشد */
    fun tapOff() {
        val selected = _uiState.value.selectedSource ?: return
        val move = currentMoves.firstOrNull { it.from == selected && it.to == BgMove.OFF } ?: return
        applyMove(move)
    }

    /** تایید پیام «حرکتی نداری» و واگذاری نوبت */
    fun confirmSkip() {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        currentMoves = emptyList()
        _uiState.value = _uiState.value.copy(
            game = e.endTurn(game),
            skipMessage = null,
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
        )
    }

    /** بازی دوباره با همان روش */
    fun playAgain() {
        val e = engine ?: return
        currentMoves = emptyList()
        _uiState.value = BgUiState(stage = BgStage.Playing, game = e.createGame())
    }

    /** برگشت به صفحه‌ی انتخاب روش */
    fun backToVariants() {
        engine = null
        currentMoves = emptyList()
        _uiState.value = BgUiState()
    }

    // ---- درون‌ریزها ----

    private fun setGame(game: BgState) {
        _uiState.value = _uiState.value.copy(
            game = game,
            selectedSource = null,
            sourcesAbs = emptySet(),
            entryIsSource = false,
            destsAbs = emptySet(),
            offIsDest = false,
        )
    }

    /** بعد از آماده‌شدن تاس‌ها: حرکت‌ها را بساز و اگر هیچ نبود پیام رد شدن بده */
    private fun afterDiceReady() {
        refreshMoves()
        if (currentMoves.isEmpty()) {
            emitSound(BgSoundEvent.SKIP)
            _uiState.value = _uiState.value.copy(
                skipMessage = "هیچ حرکت قانونی‌ای نداری! نوبتت می‌سوزه 😬",
            )
        }
    }

    private fun refreshMoves() {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        val player = game.turn
        currentMoves = if (player == null) emptyList() else e.legalMoves(game)
        val sourcesAbs = currentMoves
            .filter { it.from != BgMove.ENTRY }
            .map { relToAbs(player!!, it.from) }
            .toSet()
        val entryIsSource = currentMoves.any { it.from == BgMove.ENTRY }
        _uiState.value = _uiState.value.copy(
            selectedSource = null,
            sourcesAbs = sourcesAbs,
            entryIsSource = entryIsSource,
            destsAbs = emptySet(),
            offIsDest = false,
        )
        // وقتی تنها مبدأ ممکن ورود است، خودکار انتخابش کن تا مقصدها فوری دیده شوند
        if (entryIsSource) select(BgMove.ENTRY)
        else {
            val distinctSources = currentMoves.map { it.from }.distinct()
            if (distinctSources.size == 1) select(distinctSources.first())
        }
    }

    private fun select(source: Int) {
        val game = _uiState.value.game ?: return
        val player = game.turn ?: return
        val moves = currentMoves.filter { it.from == source }
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            destsAbs = moves.filter { it.to != BgMove.OFF }.map { relToAbs(player, it.to) }.toSet(),
            offIsDest = moves.any { it.to == BgMove.OFF },
        )
    }

    private fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedSource = null,
            destsAbs = emptySet(),
            offIsDest = false,
        )
    }

    private fun applyMove(move: BgMove) {
        val e = engine ?: return
        val game = _uiState.value.game ?: return
        val next = e.applyMove(game, move)
        emitSound(
            when {
                next.phase == BgPhase.FINISHED -> BgSoundEvent.WIN
                move.hit -> BgSoundEvent.HIT
                move.to == BgMove.OFF -> BgSoundEvent.BEAR_OFF
                else -> BgSoundEvent.MOVE
            },
        )
        setGame(next)
        if (next.phase == BgPhase.FINISHED) {
            currentMoves = emptyList()
            return
        }
        if (next.remainingDice.isEmpty()) {
            // همه‌ی تاس‌ها مصرف شد — نوبت خودکار عوض می‌شود
            currentMoves = emptyList()
            setGame(e.endTurn(next))
            return
        }
        refreshMoves()
        if (currentMoves.isEmpty()) {
            emitSound(BgSoundEvent.SKIP)
            _uiState.value = _uiState.value.copy(
                skipMessage = "با تاس‌های باقی‌مونده حرکتی نداری — نوبت رد می‌شه",
            )
        }
    }
}
