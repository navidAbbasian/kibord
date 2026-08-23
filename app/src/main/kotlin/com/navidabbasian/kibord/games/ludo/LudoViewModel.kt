package com.navidabbasian.kibord.games.ludo

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.navidabbasian.kibord.core.analytics.Analytics
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.games.ludo.engine.LudoBot
import com.navidabbasian.kibord.games.ludo.engine.LudoCapture
import com.navidabbasian.kibord.games.ludo.engine.LudoColor
import com.navidabbasian.kibord.games.ludo.engine.LudoEngine
import com.navidabbasian.kibord.games.ludo.engine.LudoEvent
import com.navidabbasian.kibord.games.ludo.engine.LudoMove
import com.navidabbasian.kibord.games.ludo.engine.LudoPhase
import com.navidabbasian.kibord.games.ludo.engine.LudoRules
import com.navidabbasian.kibord.games.ludo.engine.LudoSeat
import com.navidabbasian.kibord.games.ludo.engine.LudoSeatKind
import com.navidabbasian.kibord.games.ludo.engine.LudoState
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

/** مرحله‌های صفحه‌ای منچ */
enum class LudoStage { Setup, Playing }

/** رویدادهای صوتی که رابط به صدای مناسب ترجمه می‌کند */
enum class LudoSoundEvent { DICE, HOP, ENTER, CAPTURE, GOAL, SKIP, TURN, BONUS, WIN }

/** تنظیم یک صندلی در صفحه‌ی شروع */
data class LudoSeatConfig(
    val kind: LudoSeatKind,
    val name: String,
)

/** زمان هر پرش مهره روی یک خانه — رابط و ویومدل هر دو همین را می‌شناسند */
const val LUDO_HOP_MS = 130L
/** مدت انیمیشن تاس */
const val LUDO_DICE_MS = 650L
/** مدت «پوف» مهره‌ی زده‌شده */
const val LUDO_POOF_MS = 480L

/** انیمیشن در جریان: مهره‌ای که دارد می‌پرد و مهره‌ای که قرار است زده شود */
data class LudoMoveAnim(
    val nonce: Int,
    val move: LudoMove,
    val captured: LudoCapture?,
    /** زمان شروع (elapsedRealtime) تا بعد از چرخش صفحه از نو پخش نشود */
    val startedAt: Long,
) {
    val durationMs: Long get() = move.hops * LUDO_HOP_MS + (if (captured != null) LUDO_POOF_MS else 0L)
}

data class LudoUiState(
    val stage: LudoStage = LudoStage.Setup,
    val seatConfigs: List<LudoSeatConfig> = defaultSeatConfigs(),
    val tripleSixRule: Boolean = true,
    val safeStart: Boolean = false,
    val game: LudoState? = null,
    /** هر پرتاب یکی بالا می‌رود تا انیمیشن تاس دوباره اجرا شود */
    val rollNonce: Int = 0,
    /** تاس در حال غلتیدن است */
    val rolling: Boolean = false,
    /** انیمیشن حرکت جاری */
    val anim: LudoMoveAnim? = null,
    /** مهره‌های مجاز برای لمس (شماره‌ی مهره‌ی بازیکن نوبت) */
    val legalTokens: Set<Int> = emptySet(),
    /** مهره‌ای که به‌زودی خودکار حرکت می‌کند (تنها حرکت مجاز) */
    val autoToken: Int? = null,
    /** پیام کوتاه زیر صفحه */
    val message: String? = null,
    /** موتور مشغول است (انیمیشن، ربات، مکث) — دکمه‌ها قفل */
    val busy: Boolean = false,
) {
    val activeSeatCount: Int get() = seatConfigs.count { it.kind != LudoSeatKind.EMPTY }
    val botCount: Int get() = seatConfigs.count { it.kind == LudoSeatKind.BOT }
    val canStart: Boolean get() = activeSeatCount >= 2

    /** اسم نمایشی صندلی (پیش‌فرض اگر خالی بود) */
    fun displayName(color: LudoColor): String {
        val seat = game?.seat(color)
        val name = seat?.name ?: seatConfigs[color.ordinal].name
        return name.ifBlank { defaultName(color, seatConfigs[color.ordinal].kind) }
    }
}

private fun defaultName(color: LudoColor, kind: LudoSeatKind): String =
    if (kind == LudoSeatKind.BOT) "ربات" else "بازیکن ${(color.ordinal + 1).toPersianDigits()}"

private fun defaultSeatConfigs(): List<LudoSeatConfig> = listOf(
    LudoSeatConfig(LudoSeatKind.HUMAN, ""),
    LudoSeatConfig(LudoSeatKind.BOT, ""),
    LudoSeatConfig(LudoSeatKind.EMPTY, ""),
    LudoSeatConfig(LudoSeatKind.EMPTY, ""),
)

/**
 * ویومدل منچ: تنظیم صندلی‌ها، جریان نوبت‌ها (تاس → حرکت → پرتاب اضافه/نوبت بعدی)،
 * ربات‌ها و زمان‌بندی انیمیشن‌ها — همه در یک Job دنباله‌دار تا با چرخش صفحه زنده بماند.
 */
class LudoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LudoUiState())
    val uiState: StateFlow<LudoUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<LudoSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<LudoSoundEvent> = _soundEvents.asSharedFlow()

    private val random = Random.Default
    private var flowJob: Job? = null
    private var animNonce = 0

    init {
        val app = getApplication<Application>()
        val seats = LudoColor.entries.map { c ->
            val kindName = GamePrefs.getString(app, "ludo_seat_${c.ordinal}", null)
            val kind = LudoSeatKind.entries.firstOrNull { it.name == kindName }
                ?: defaultSeatConfigs()[c.ordinal].kind
            LudoSeatConfig(kind = kind, name = GamePrefs.getString(app, "ludo_name_${c.ordinal}", "") ?: "")
        }
        _uiState.update {
            it.copy(
                seatConfigs = seats,
                tripleSixRule = GamePrefs.getBool(app, "ludo_triple_six", true),
                safeStart = GamePrefs.getBool(app, "ludo_safe_start", false),
            )
        }
    }

    private fun sound(e: LudoSoundEvent) {
        _soundEvents.tryEmit(e)
    }

    // ---- صفحه‌ی تنظیم ----

    fun setSeatKind(color: LudoColor, kind: LudoSeatKind) {
        _uiState.update { s ->
            s.copy(seatConfigs = s.seatConfigs.mapIndexed { i, cfg -> if (i == color.ordinal) cfg.copy(kind = kind) else cfg })
        }
    }

    fun setSeatName(color: LudoColor, name: String) {
        _uiState.update { s ->
            s.copy(seatConfigs = s.seatConfigs.mapIndexed { i, cfg -> if (i == color.ordinal) cfg.copy(name = name.take(14)) else cfg })
        }
    }

    fun setTripleSixRule(on: Boolean) = _uiState.update { it.copy(tripleSixRule = on) }
    fun setSafeStart(on: Boolean) = _uiState.update { it.copy(safeStart = on) }

    private fun savePrefs() {
        val app = getApplication<Application>()
        val s = _uiState.value
        s.seatConfigs.forEachIndexed { i, cfg ->
            GamePrefs.setString(app, "ludo_seat_$i", cfg.kind.name)
            GamePrefs.setString(app, "ludo_name_$i", cfg.name.trim())
        }
        GamePrefs.setBool(app, "ludo_triple_six", s.tripleSixRule)
        GamePrefs.setBool(app, "ludo_safe_start", s.safeStart)
    }

    /** شروع دست از صفحه‌ی تنظیم */
    fun startGame() {
        val s = _uiState.value
        if (!s.canStart) return
        savePrefs()
        Analytics.gameSetup(
            "players" to s.activeSeatCount,
            "bots" to s.botCount,
            "triple_six_rule" to s.tripleSixRule,
            "safe_start" to s.safeStart,
        )
        launchGame()
    }

    /** دوباره بازی با همان تنظیم‌ها */
    fun playAgain() {
        Analytics.gameReplay()
        launchGame()
    }

    private fun launchGame() {
        flowJob?.cancel()
        val s = _uiState.value
        val seats = LudoColor.entries.map { c ->
            val cfg = s.seatConfigs[c.ordinal]
            LudoSeat(color = c, kind = cfg.kind, name = cfg.name.trim().ifBlank { defaultName(c, cfg.kind) })
        }
        val active = seats.filter { it.active }.map { it.color }
        val game = LudoEngine.newGame(
            seats = seats,
            rules = LudoRules(tripleSixLosesTurn = s.tripleSixRule, safeStartSquares = s.safeStart),
            firstTurn = active.random(random),
        )
        _uiState.update {
            it.copy(
                stage = LudoStage.Playing,
                game = game,
                rollNonce = 0,
                rolling = false,
                anim = null,
                legalTokens = emptySet(),
                autoToken = null,
                message = null,
                busy = false,
            )
        }
        runFlow { onTurnStart(announce = false) }
    }

    /** برگشت به صفحه‌ی تنظیم (از صفحه‌ی برنده یا خروج) */
    fun backToSetup() {
        flowJob?.cancel()
        _uiState.update {
            it.copy(stage = LudoStage.Setup, game = null, anim = null, legalTokens = emptySet(), autoToken = null, message = null, busy = false, rolling = false)
        }
    }

    // ---- جریان نوبت ----

    private fun runFlow(block: suspend () -> Unit) {
        flowJob?.cancel()
        flowJob = viewModelScope.launch { block() }
    }

    /** آغاز نوبت بازیکن فعلی: آدم منتظر لمس «تاس بریز» می‌ماند، ربات خودش می‌ریزد */
    private suspend fun onTurnStart(announce: Boolean = true) {
        val game = _uiState.value.game ?: return
        // لرزش کوتاه فقط وقتی نوبت به یک آدم می‌رسد — نشانه‌ی «گوشی دست توئه»
        if (announce && !game.currentSeat.isBot) sound(LudoSoundEvent.TURN)
        _uiState.update { it.copy(message = null, legalTokens = emptySet(), autoToken = null, busy = false) }
        if (game.currentSeat.isBot) {
            _uiState.update { it.copy(busy = true) }
            delay(if (announce) 850 else 600)
            doRoll()
        }
    }

    /** لمس «تاس بریز» توسط آدم */
    fun rollDice() {
        val s = _uiState.value
        val game = s.game ?: return
        if (s.busy || game.phase != LudoPhase.ROLLING || game.currentSeat.isBot) return
        runFlow { doRoll() }
    }

    private suspend fun doRoll() {
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.ROLLING) return
        val value = random.nextInt(1, 7)
        val rolled = LudoEngine.roll(game, value)
        sound(LudoSoundEvent.DICE)
        _uiState.update {
            it.copy(game = rolled, rollNonce = it.rollNonce + 1, rolling = true, busy = true, message = null, legalTokens = emptySet(), autoToken = null)
        }
        delay(LUDO_DICE_MS)
        _uiState.update { it.copy(rolling = false) }
        resolveAfterRoll()
    }

    private suspend fun resolveAfterRoll() {
        val s = _uiState.value
        val game = s.game ?: return
        val name = s.displayName(game.turn)
        when (game.phase) {
            LudoPhase.PASSING -> {
                val msg = when (game.event) {
                    LudoEvent.TRIPLE_SIX -> "سه تا شش پشت هم! نوبت $name سوخت 😅"
                    else -> "$name حرکتی نداره — نوبت بعدی"
                }
                sound(LudoSoundEvent.SKIP)
                _uiState.update { it.copy(message = msg, busy = true) }
                delay(1400)
                val g = _uiState.value.game ?: return
                if (g.phase != LudoPhase.PASSING) return
                _uiState.update { it.copy(game = LudoEngine.endTurn(g)) }
                onTurnStart()
            }

            LudoPhase.MOVING -> {
                val moves = LudoEngine.legalMoves(game)
                if (moves.isEmpty()) {
                    // نباید پیش بیاید (roll خودش PASSING می‌کند) — ایمنی
                    _uiState.update { it.copy(game = LudoEngine.endTurn(game)) }
                    onTurnStart()
                    return
                }
                if (game.currentSeat.isBot) {
                    _uiState.update { it.copy(busy = true, legalTokens = moves.map { m -> m.token }.toSet()) }
                    delay(650)
                    val pick = LudoBot.choose(game, moves, random) ?: moves.first()
                    performMove(pick)
                } else if (moves.size == 1) {
                    // تنها حرکت مجاز: کمی برجسته کن و خودش برو
                    _uiState.update { it.copy(busy = true, legalTokens = setOf(moves[0].token), autoToken = moves[0].token) }
                    delay(520)
                    performMove(moves[0])
                } else {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            legalTokens = moves.map { m -> m.token }.toSet(),
                            message = "یه مهره‌ی روشن رو لمس کن",
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    /** لمس یک مهره‌ی بازیکن نوبت توسط آدم */
    fun tapToken(token: Int) {
        val s = _uiState.value
        val game = s.game ?: return
        if (s.busy || game.phase != LudoPhase.MOVING || game.currentSeat.isBot) return
        val move = LudoEngine.legalMoves(game).firstOrNull { it.token == token } ?: return
        runFlow { performMove(move) }
    }

    private suspend fun performMove(move: LudoMove) {
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.MOVING) return
        val after = try {
            LudoEngine.applyMove(game, move)
        } catch (_: IllegalArgumentException) {
            return
        }
        val anim = LudoMoveAnim(
            nonce = ++animNonce,
            move = move,
            captured = after.lastMove?.captured,
            startedAt = SystemClock.elapsedRealtime(),
        )
        _uiState.update {
            it.copy(game = after, anim = anim, legalTokens = emptySet(), autoToken = null, busy = true, message = null)
        }
        sound(if (move.entersBoard) LudoSoundEvent.ENTER else LudoSoundEvent.HOP)
        delay(move.hops * LUDO_HOP_MS + 60)
        if (anim.captured != null) {
            sound(LudoSoundEvent.CAPTURE)
            delay(LUDO_POOF_MS)
        } else if (move.reachesGoal) {
            sound(LudoSoundEvent.GOAL)
        }
        val s = _uiState.value
        val name = s.displayName(move.color)
        when (after.phase) {
            LudoPhase.FINISHED -> {
                sound(LudoSoundEvent.WIN)
                _uiState.update { it.copy(busy = false, message = null) }
            }

            LudoPhase.ROLLING -> {
                // پرتاب اضافه: همان بازیکن
                sound(LudoSoundEvent.BONUS)
                val why = if (anim.captured != null) "زدی! 🎯" else "شش آوردی! 🎁"
                _uiState.update { it.copy(message = "$why یه تاس دیگه برای $name") }
                if (after.currentSeat.isBot) {
                    delay(900)
                    doRoll()
                } else {
                    _uiState.update { it.copy(busy = false) }
                }
            }

            else -> {
                delay(150)
                onTurnStart()
            }
        }
    }

    /** از صفحه‌ی برنده: بقیه برای رتبه‌های بعدی ادامه می‌دهند */
    fun continueForOthers() {
        val game = _uiState.value.game ?: return
        if (game.phase != LudoPhase.FINISHED || game.gameOver) return
        _uiState.update { it.copy(game = LudoEngine.continueAfterFinish(game), anim = null) }
        runFlow { onTurnStart() }
    }

    override fun onCleared() {
        flowJob?.cancel()
        super.onCleared()
    }
}
