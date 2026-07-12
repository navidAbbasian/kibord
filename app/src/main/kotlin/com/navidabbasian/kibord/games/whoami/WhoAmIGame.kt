package com.navidabbasian.kibord.games.whoami

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
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

// ================= مدل =================

sealed class WaPhase {
    data object PlayerCount : WaPhase()
    data object PlayerNames : WaPhase()
    /** نویسنده اسم مخفی را برای حدس‌زننده می‌نویسد */
    data object Write : WaPhase()
    /** گوشی به حدس‌زننده می‌رسد */
    data object Ready : WaPhase()
    data object Play : WaPhase()
    data class Result(val guessed: Boolean) : WaPhase()
    data object Winner : WaPhase()
}

data class WaUiState(
    val phase: WaPhase = WaPhase.PlayerCount,
    val playerCount: Int = 2,
    val playerNames: List<String> = emptyList(),
    /** حدس‌زننده‌ی این نوبت */
    val currentPlayer: Int = 0,
    val totalScores: List<Int> = emptyList(),
    /** اسم مخفی که نویسنده وارد کرده */
    val secretName: String = "",
    val turnSeconds: Int = 120,
    val secondsLeft: Int = 0,
    /** جمع دستکاری داورانه‌ی امتیازِ همین نوبت در بخش بررسی */
    val turnBonus: Int = 0,
) {
    fun playerDisplayName(index: Int): String =
        playerNames.getOrNull(index)?.ifBlank { "بازیکن ${(index + 1).toPersianDigits()}" }
            ?: "بازیکن ${(index + 1).toPersianDigits()}"

    /** نویسنده‌ی این نوبت: نفر بعد از حدس‌زننده */
    val writerIndex: Int get() = (currentPlayer + 1) % playerCount
}

enum class WaSoundEvent { TICK, TICK_WARNING, TIME_UP, CORRECT }

// ================= موتور =================

/**
 * من کی‌ام؟ — نویسنده اسم یک آدم معروف (یا آشنا) را مخفیانه می‌نویسد،
 * حدس‌زننده گوشی را افقی روی پیشونی می‌گذارد و با سوال‌های بله/نه
 * باید بفهمد کیست. حدس درست ۱ امتیاز؛ خم به جلو = حدس زد.
 */
class WhoAmIViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WaUiState())
    val uiState: StateFlow<WaUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<WaSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<WaSoundEvent> = _soundEvents.asSharedFlow()

    private var tickerJob: Job? = null

    // ---- حسگر حرکت: مثل حدس روی پیشونی — خم به جلو = حدس زد ----
    private val sensorManager =
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var lastGestureAt = 0L
    private var neutral = true
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (_uiState.value.phase != WaPhase.Play) return
            val z = event.values.getOrNull(2) ?: return
            val now = SystemClock.elapsedRealtime()
            when {
                neutral && z < -7f && now - lastGestureAt > 800 -> {
                    lastGestureAt = now; neutral = false
                    markGuessed()
                }
                kotlin.math.abs(z) < 4f -> neutral = true
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun emit(e: WaSoundEvent) = _soundEvents.tryEmit(e)

    // ---- راه‌اندازی ----

    fun setPlayerCount(count: Int) {
        _uiState.update {
            it.copy(
                playerCount = count,
                playerNames = List(count) { "" },
                totalScores = List(count) { 0 },
                phase = WaPhase.PlayerNames,
            )
        }
    }

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(playerNames = s.playerNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmNames() =
        _uiState.update { it.copy(phase = WaPhase.Write, currentPlayer = 0, secretName = "") }

    fun setTurnSeconds(seconds: Int) = _uiState.update { it.copy(turnSeconds = seconds) }

    // ---- نوبت ----

    fun updateSecretName(name: String) = _uiState.update { it.copy(secretName = name.take(30)) }

    fun confirmSecret() {
        if (_uiState.value.secretName.isBlank()) return
        _uiState.update { it.copy(phase = WaPhase.Ready) }
    }

    fun startTurn() {
        neutral = true
        _uiState.update {
            it.copy(phase = WaPhase.Play, secondsLeft = it.turnSeconds, turnBonus = 0)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value
                if (s.phase != WaPhase.Play) break
                val left = s.secondsLeft - 1
                emit(if (left <= 10) WaSoundEvent.TICK_WARNING else WaSoundEvent.TICK)
                if (left <= 0) {
                    emit(WaSoundEvent.TIME_UP)
                    endTurn(guessed = false)
                    break
                }
                _uiState.update { it.copy(secondsLeft = left) }
            }
        }
    }

    /** حدس زد! (حسگر یا لمس) */
    fun markGuessed() {
        if (_uiState.value.phase != WaPhase.Play) return
        emit(WaSoundEvent.CORRECT)
        endTurn(guessed = true)
    }

    /** تسلیم شد — نوبت بدون امتیاز تمام می‌شود */
    fun giveUp() {
        if (_uiState.value.phase != WaPhase.Play) return
        endTurn(guessed = false)
    }

    private fun endTurn(guessed: Boolean) {
        tickerJob?.cancel()
        sensorManager?.unregisterListener(sensorListener)
        _uiState.update { it.copy(phase = WaPhase.Result(guessed)) }
    }

    // ---- بررسی و ثبت امتیاز ----

    /** بررسی امتیاز نوبت: جمع می‌تواند حکم نهایی را کم و زیاد کند */
    fun adjustTurnScore(delta: Int) {
        if (_uiState.value.phase !is WaPhase.Result) return
        _uiState.update { it.copy(turnBonus = it.turnBonus + delta) }
    }

    private fun turnScore(): Int {
        val s = _uiState.value
        val base = if ((s.phase as? WaPhase.Result)?.guessed == true) 1 else 0
        return base + s.turnBonus
    }

    /** ثبت امتیاز نوبت برای حدس‌زننده + نوبت نفر بعد */
    fun confirmTurn() {
        val s = _uiState.value
        if (s.phase !is WaPhase.Result) return
        val score = turnScore()
        _uiState.update {
            it.copy(
                totalScores = it.totalScores.mapIndexed { i, v ->
                    if (i == it.currentPlayer) v + score else v
                },
                currentPlayer = (it.currentPlayer + 1) % it.playerCount,
                secretName = "",
                phase = WaPhase.Write,
            )
        }
    }

    /** ثبت امتیاز آخرین نوبت و اعلام برنده */
    fun finishGame() {
        val s = _uiState.value
        if (s.phase !is WaPhase.Result) return
        val score = turnScore()
        _uiState.update {
            it.copy(
                totalScores = it.totalScores.mapIndexed { i, v ->
                    if (i == it.currentPlayer) v + score else v
                },
                phase = WaPhase.Winner,
            )
        }
    }

    fun winners(): List<Int> {
        val scores = _uiState.value.totalScores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        _uiState.update {
            it.copy(
                totalScores = List(it.playerCount) { 0 },
                currentPlayer = 0,
                secretName = "",
                phase = WaPhase.Write,
            )
        }
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                WaPhase.PlayerNames -> s.copy(phase = WaPhase.PlayerCount)
                WaPhase.Ready -> s.copy(phase = WaPhase.Write)
                else -> s
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        sensorManager?.unregisterListener(sensorListener)
    }
}

// ================= ریشه و صفحه‌ها =================

/** ریشه‌ی من کی‌ام؟ */
@Composable
fun WhoAmIGame(
    onExitToHub: () -> Unit,
    viewModel: WhoAmIViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val context = LocalContext.current

    // هنگام بازی گوشی افقی می‌شود تا صاف روی پیشونی بنشیند
    LaunchedEffect(state.phase) {
        (context as? Activity)?.requestedOrientation =
            if (state.phase == WaPhase.Play) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    DisposableEffect(Unit) {
        onDispose {
            (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                WaSoundEvent.TICK -> sound?.playTimerTick()
                WaSoundEvent.TICK_WARNING -> sound?.playTimerWarning()
                WaSoundEvent.TIME_UP -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(200)
                }
                WaSoundEvent.CORRECT -> {
                    sound?.playCorrectWord()
                    sound?.vibrate(80)
                }
            }
        }
    }

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            WaPhase.Play -> MusicTrack.KALAMZ_ROUND_3
            else -> MusicTrack.HUB
        }
        sound?.switchMusic(track)
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                WaPhase.PlayerCount -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    WaPlayerCountScreen(onSelected = viewModel::setPlayerCount)
                }

                WaPhase.PlayerNames -> {
                    BackHandler { viewModel.navigateBack() }
                    WaPlayerNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updatePlayerName,
                        onConfirm = viewModel::confirmNames,
                    )
                }

                WaPhase.Write -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    WaWriteScreen(
                        state = state,
                        onSecretChanged = viewModel::updateSecretName,
                        onSeconds = viewModel::setTurnSeconds,
                        onConfirm = viewModel::confirmSecret,
                    )
                }

                WaPhase.Ready -> {
                    BackHandler { viewModel.navigateBack() }
                    WaReadyScreen(state = state, onStart = viewModel::startTurn)
                }

                WaPhase.Play -> {
                    BackHandler { }
                    WaPlayScreen(
                        state = state,
                        onGuessed = viewModel::markGuessed,
                        onGiveUp = viewModel::giveUp,
                    )
                }

                is WaPhase.Result -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    WaResultScreen(
                        state = state,
                        guessed = phase.guessed,
                        onAdjust = viewModel::adjustTurnScore,
                        onNext = viewModel::confirmTurn,
                        onFinish = viewModel::finishGame,
                    )
                }

                WaPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    WaWinnerScreen(
                        state = state,
                        winners = viewModel.winners(),
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = onExitToHub,
                    )
                }
            }
        }
    }
}

@Composable
private fun WaPlayerCountScreen(onSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "🏷️", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "من کی‌ام؟", rotation = -2f)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "برات یه اسم می‌نویسن، می‌ذاری رو پیشونیت و با سوال‌های بله/نه باید بفهمی کی هستی!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        listOf(2, 3, 4, 5, 6, 7).chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)
            ) {
                row.forEachIndexed { colIndex, count ->
                    val i = rowIndex * 2 + colIndex
                    val zig = i % 2 == 0
                    ChoiceBubble(
                        main = count.toPersianDigits(),
                        sub = "نفر",
                        size = 104.dp,
                        mainFontSize = 30.sp,
                        accent = kiExtras.teamColors.teamColorFor(i + 1),
                        tilt = if (zig) -3f else 3f,
                        phase = i * 1.3f,
                        modifier = Modifier.offset(y = if (zig) 0.dp else 12.dp),
                        onClick = { onSelected(count) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun WaPlayerNamesScreen(
    state: WaUiState,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StickerTitle(text = "اسم بازیکن‌ها", rotation = 2f, fontSize = 24.sp)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            repeat(state.playerCount) { i ->
                BlobTextField(
                    value = state.playerNames.getOrElse(i) { "" },
                    onValueChange = { onNameChanged(i, it.take(16)) },
                    placeholder = "بازیکن ${(i + 1).toPersianDigits()}",
                    color = teamColors.teamColorFor(i),
                    badge = (i + 1).toPersianDigits(),
                    tilt = if (i % 2 == 0) -1f else 1f,
                    phase = i * 1.4f,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "بریم بازی!", onClick = onConfirm)
        }
    }
}

@Composable
private fun WaWriteScreen(
    state: WaUiState,
    onSecretChanged: (String) -> Unit,
    onSeconds: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val writerColor = kiExtras.teamColors.teamColorFor(state.writerIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "🤫", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "گوشی دست ${state.playerDisplayName(state.writerIndex)}",
            style = MaterialTheme.typography.headlineMedium,
            color = writerColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "یواشکی اسم یه آدم معروف (یا یکی از جمع!) رو بنویس تا " +
                "${state.playerDisplayName(state.currentPlayer)} حدسش بزنه — نبینه!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))

        BlobTextField(
            value = state.secretName,
            onValueChange = onSecretChanged,
            placeholder = "مثلاً: فردوسی، مسی، عمه فخری…",
            badge = "🏷️",
            tilt = -1f,
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(60, 120, 180).forEach { sec ->
                val selected = state.turnSeconds == sec
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = "${sec.toPersianDigits()} ثانیه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(if (selected) accent else extras.glassStrong, RoundedCornerShape(50))
                        .clickable(interactionSource = interaction, indication = null) {
                            sound?.playButtonClick()
                            onSeconds(sec)
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        KButton(
            text = "نوشتم — بده به ${state.playerDisplayName(state.currentPlayer)}",
            enabled = state.secretName.isNotBlank(),
            onClick = onConfirm,
        )
    }
}

@Composable
private fun WaReadyScreen(
    state: WaUiState,
    onStart: () -> Unit,
) {
    val playerColor = kiExtras.teamColors.teamColorFor(state.currentPlayer)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "🏷️", fontSize = 52.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "نوبت ${state.playerDisplayName(state.currentPlayer)}",
            style = MaterialTheme.typography.displayMedium,
            fontSize = 30.sp,
            color = playerColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "اسمت آماده‌ست! گوشی خودش افقی می‌شه — بذارش عرضی روی پیشونیت تا بقیه ببینن کی هستی.\n" +
                "با سوال‌های بله/نه بفهم کی هستی: «زنده‌ام؟»، «ورزشکارم؟»، «ایرانی‌ام؟»\n" +
                "حدس زدی؟ گوشی رو به جلو خم کن ✅",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        KButton(text = "بذار رو پیشونی و شروع کن!", onClick = onStart, accent = playerColor)
    }
}

@Composable
private fun WaPlayScreen(
    state: WaUiState,
    onGuessed: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val urgent = state.secondsLeft <= 10

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- نیمه‌ی بالا: تسلیم ----
        val giveUpInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(Brush.verticalGradient(listOf(extras.warning.copy(alpha = 0.25f), Color.Transparent)))
                .clickable(interactionSource = giveUpInteraction, indication = null, onClick = onGiveUp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏳️ تسلیم — لمس بالا",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = state.secondsLeft.toPersianDigits(),
                    fontSize = if (urgent) 44.sp else 34.sp,
                    fontWeight = FontWeight.Black,
                    color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // ---- اسم بزرگ وسط: جمع می‌بینند، خودش نه! ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 1800)) {
                Text(
                    text = state.secretName,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = accent,
                    textAlign = TextAlign.Center,
                    lineHeight = 66.sp,
                )
            }
        }

        // ---- نیمه‌ی پایین: حدس زد ----
        val guessedInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, extras.success.copy(alpha = 0.30f))))
                .clickable(interactionSource = guessedInteraction, indication = null, onClick = onGuessed),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                text = "✅ حدس زد! — خم به جلو",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}

@Composable
private fun WaResultScreen(
    state: WaUiState,
    guessed: Boolean,
    onAdjust: (Int) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val playerColor = kiExtras.teamColors.teamColorFor(state.currentPlayer)
    val turnScore = (if (guessed) 1 else 0) + state.turnBonus

    Box(modifier = Modifier.fillMaxSize()) {
        if (guessed) ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = if (guessed) "🎉" else "⏰", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (guessed) "${state.playerDisplayName(state.currentPlayer)} حدس زد!"
                else "وقت تموم شد!",
                style = MaterialTheme.typography.displayMedium,
                fontSize = 30.sp,
                color = if (guessed) extras.success else extras.danger,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))

            TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.2f) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "اسم مخفی این بود:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.secretName,
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = 32.sp,
                        color = playerColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- بررسی امتیاز: جمع می‌تواند حکم نهایی را کم و زیاد کند ----
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "بررسی امتیاز این نوبت",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        val minus = remember { MutableInteractionSource() }
                        Text(
                            text = "−۱",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(extras.glassStrong, RoundedCornerShape(50))
                                .clickable(interactionSource = minus, indication = null) {
                                    sound?.playButtonClick(); onAdjust(-1)
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                        Text(
                            text = turnScore.toPersianDigits(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = if (turnScore >= 0) extras.success else extras.danger,
                        )
                        val plus = remember { MutableInteractionSource() }
                        Text(
                            text = "+۱",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(extras.glassStrong, RoundedCornerShape(50))
                                .clickable(interactionSource = plus, indication = null) {
                                    sound?.playButtonClick(); onAdjust(1)
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                    if (state.turnBonus != 0) {
                        Text(
                            text = "حکم داورها: ${if (state.turnBonus > 0) "+" else "−"}${kotlin.math.abs(state.turnBonus).toPersianDigits()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // ---- جدول امتیازها ----
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    (0 until state.playerCount)
                        .sortedByDescending { state.totalScores[it] }
                        .forEach { i ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.playerDisplayName(i),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = kiExtras.teamColors.teamColorFor(i),
                                )
                                Text(
                                    text = state.totalScores[i].toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            KButton(text = "ثبت — نفر بعد", onClick = onNext, accent = playerColor)
            Spacer(modifier = Modifier.height(8.dp))
            KButton(
                text = "کی برد؟ 🏆",
                onClick = onFinish,
                style = KButtonStyle.Glass,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun WaWinnerScreen(
    state: WaUiState,
    winners: List<Int>,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🏆", fontSize = 76.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = winners.joinToString(" و ") { state.playerDisplayName(it) },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull() ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    (0 until state.playerCount)
                        .sortedByDescending { state.totalScores[it] }
                        .forEachIndexed { rank, i ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (i in winners) "🥇" else (rank + 1).toPersianDigits(),
                                        fontSize = 17.sp,
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.playerDisplayName(i),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = kiExtras.teamColors.teamColorFor(i),
                                    )
                                }
                                Text(
                                    text = state.totalScores[i].toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
