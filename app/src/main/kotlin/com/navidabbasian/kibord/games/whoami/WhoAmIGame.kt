package com.navidabbasian.kibord.games.whoami

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.whoami.model.WA_MAX_PLAYERS
import com.navidabbasian.kibord.games.whoami.model.WA_MIN_PLAYERS
import com.navidabbasian.kibord.games.whoami.model.WaPhase
import com.navidabbasian.kibord.games.whoami.model.WaPlayer
import com.navidabbasian.kibord.games.whoami.model.WaSnapshot
import com.navidabbasian.kibord.games.whoami.net.WaClient
import com.navidabbasian.kibord.games.whoami.net.WaDiscoveredGame
import com.navidabbasian.kibord.games.whoami.net.WaMessage
import com.navidabbasian.kibord.games.whoami.net.WaNsd
import com.navidabbasian.kibord.games.whoami.net.WaServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ================= وضعیت محلی =================

/** نقش این گوشی در ارتباط شبکه */
enum class WaNetRole { NONE, HOST, CLIENT }

/** صفحه‌های محلیِ قبل از ورود به جریان مشترک بازی */
enum class WaLocalScreen { ENTRY, JOIN, IN_GAME }

data class WhoAmIUiState(
    val role: WaNetRole = WaNetRole.NONE,
    val localScreen: WaLocalScreen = WaLocalScreen.ENTRY,
    val myName: String = "",
    val snapshot: WaSnapshot = WaSnapshot(),
    val discovered: List<WaDiscoveredGame> = emptyList(),
    val connecting: Boolean = false,
    val connectError: String? = null,
    /** اسمی که دارم برای هدفم می‌نویسم */
    val myDraft: String = "",
    val hostAddress: String = "",
    val hostPort: Int = 0,
    val lostConnection: Boolean = false,
) {
    val isHost: Boolean get() = role == WaNetRole.HOST
    val me: WaPlayer? get() = snapshot.player(myName)
    val myTarget: String get() = snapshot.targetOf(myName)
    val iHaveSubmitted: Boolean get() = snapshot.hasSubmitted(myName)
    val iHaveGuessed: Boolean get() = snapshot.hasGuessed(myName)
    /** اسمِ روی پیشانی من — نباید بخوانمش! */
    val myForeheadName: String get() = snapshot.assignmentOf(myName)
}

// ================= موتور =================

/**
 * من کی‌ام؟ — چندگوشی: هر کس مخفیانه برای نفر دیگری اسم می‌نویسد،
 * همه گوشی را روی پیشانی می‌گذارند و با سوال بله/نه حدس می‌زنند.
 * زمان ندارد؛ هر که زودتر «حدس زدم» بزند امتیاز بیشتری می‌گیرد و
 * پایان بازی هم دست خود جمع است.
 */
class WhoAmIViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WhoAmIUiState())
    val uiState: StateFlow<WhoAmIUiState> = _uiState.asStateFlow()

    private val nsd = WaNsd(application)
    private var server: WaServer? = null
    private var client: WaClient? = null

    // ================= ورود =================

    fun setMyName(name: String) {
        _uiState.update { it.copy(myName = name.take(16)) }
    }

    // ================= میزبانی =================

    fun startHosting() {
        val name = _uiState.value.myName.trim()
        if (name.isBlank()) return
        val srv = WaServer(
            scope = viewModelScope,
            onClientJoin = ::acceptJoin,
            onCommand = ::handleCommand,
            onClientDisconnected = ::handleDisconnect,
            latestState = { WaMessage.State(_uiState.value.snapshot) },
        )
        if (!srv.start()) {
            _uiState.update { it.copy(connectError = "سرور روی این گوشی راه نیفتاد") }
            return
        }
        server = srv
        nsd.register(name, srv.port)
        val snapshot = WaSnapshot(
            phase = WaPhase.LOBBY,
            players = listOf(WaPlayer(name = name, colorIndex = 0)),
            hostName = name,
        )
        _uiState.update {
            it.copy(
                role = WaNetRole.HOST,
                localScreen = WaLocalScreen.IN_GAME,
                // اسم محلی باید دقیقاً همان اسمِ ثبت‌شده در بازی باشد
                myName = name,
                hostAddress = WaNsd.localIpAddress() ?: "",
                hostPort = srv.port,
                connectError = null,
            )
        }
        setSnapshot(snapshot)
    }

    private fun acceptJoin(name: String): String? {
        val snapshot = _uiState.value.snapshot
        val existing = snapshot.player(name)
        return when {
            name.isBlank() -> "اسم خالی است"
            existing != null && !existing.connected -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = true) else it })
                }
                null
            }

            existing != null -> "این اسم الان توی بازیه — یک اسم دیگر انتخاب کن"
            snapshot.phase != WaPhase.LOBBY -> "بازی شروع شده — منتظر دست بعدی باش"
            snapshot.players.size >= WA_MAX_PLAYERS -> "ظرفیت بازی پر است"
            else -> {
                mutateSnapshot { s ->
                    s.copy(players = s.players + WaPlayer(name = name, colorIndex = s.players.size))
                }
                null
            }
        }
    }

    private fun handleDisconnect(name: String) {
        if (_uiState.value.role != WaNetRole.HOST) return
        mutateSnapshot { s ->
            s.copy(players = s.players.map { if (sameName(it.name, name)) it.copy(connected = false) else it })
        }
        maybeFinishWrite()
        maybeFinishRound()
    }

    // ================= پیوستن مهمان =================

    fun openJoinScreen() {
        if (_uiState.value.myName.isBlank()) return
        _uiState.update { it.copy(localScreen = WaLocalScreen.JOIN, discovered = emptyList(), connectError = null) }
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

    fun joinGame(address: String, port: Int = WaServer.BASE_PORT) {
        val name = _uiState.value.myName.trim()
        if (name.isBlank() || address.isBlank()) return
        _uiState.update { it.copy(myName = name, connecting = true, connectError = null) }
        val c = WaClient(
            scope = viewModelScope,
            onMessage = ::handleServerMessage,
            onDisconnected = {
                if (_uiState.value.role == WaNetRole.CLIENT) {
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
                        role = WaNetRole.CLIENT,
                        localScreen = WaLocalScreen.IN_GAME,
                        connecting = false,
                        connectError = null,
                    )
                }
            }
        }
    }

    private fun handleServerMessage(msg: WaMessage) {
        when (msg) {
            is WaMessage.State -> setSnapshot(msg.snapshot)
            else -> Unit
        }
    }

    // ================= فرمان‌های داخل بازی =================

    fun startGame() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.players.count { it.connected } < WA_MIN_PLAYERS) return@hostOnly
        dealRound(roundIndex = 1, resetScores = true)
    }

    /** قرعه‌ی راند: هر نویسنده برای نفرِ چندخانه‌بعدی می‌نویسد تا هر راند عوض شود */
    private fun dealRound(roundIndex: Int, resetScores: Boolean = false) {
        val s = _uiState.value.snapshot
        val connected = s.players.filter { it.connected }
        if (connected.size < WA_MIN_PLAYERS) return
        val shift = ((roundIndex - 1) % (connected.size - 1)) + 1
        val targets = connected.mapIndexed { i, writer ->
            writer.name to connected[(i + shift) % connected.size].name
        }.toMap()
        mutateSnapshot { snap ->
            snap.copy(
                phase = WaPhase.WRITE,
                roundIndex = roundIndex,
                players = if (resetScores) snap.players.map { it.copy(totalScore = 0) } else snap.players,
                targets = targets,
                assignments = emptyMap(),
                submitted = emptyList(),
                guessedOrder = emptyList(),
            )
        }
    }

    fun updateDraft(text: String) = _uiState.update { it.copy(myDraft = text.take(30)) }

    /** اسم مخفی را برای هدفم می‌فرستم */
    fun submitName() {
        val st = _uiState.value
        val text = st.myDraft.trim()
        if (st.snapshot.phase != WaPhase.WRITE || st.iHaveSubmitted || text.isBlank()) return
        if (st.isHost) hostReceiveName(st.myName, text) else client?.send(WaMessage.SubmitName(text))
        _uiState.update { it.copy(myDraft = "") }
    }

    /** «حدس زدم!» — فقط لمسی، بدون حسگر و بدون زمان */
    fun markGuessed() {
        val st = _uiState.value
        if (st.snapshot.phase != WaPhase.PLAY || st.iHaveGuessed) return
        if (st.isHost) hostMarkGuessed(st.myName) else client?.send(WaMessage.Guessed)
    }

    /** میزبان: راند بعد */
    fun nextRound() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.ROUND_RESULT) return@hostOnly
        dealRound(roundIndex = s.roundIndex + 1)
    }

    /** میزبان: پایان بازی و اعلام برنده — پایان دست خود جمع است */
    fun finishGame() = hostOnly {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.ROUND_RESULT) return@hostOnly
        mutateSnapshot { it.copy(phase = WaPhase.GAME_OVER) }
    }

    fun playAgain() = hostOnly {
        mutateSnapshot { s ->
            s.copy(
                phase = WaPhase.LOBBY,
                roundIndex = 0,
                targets = emptyMap(),
                assignments = emptyMap(),
                submitted = emptyList(),
                guessedOrder = emptyList(),
                players = s.players.map { it.copy(totalScore = 0) },
            )
        }
    }

    fun winners(): List<WaPlayer> {
        val players = _uiState.value.snapshot.players
        val max = players.maxOfOrNull { it.totalScore } ?: return emptyList()
        return players.filter { it.totalScore == max }
    }

    // ================= منطق میزبان =================

    private fun handleCommand(playerName: String, msg: WaMessage) {
        when (msg) {
            is WaMessage.SubmitName -> hostReceiveName(playerName, msg.text.trim())
            is WaMessage.Guessed -> hostMarkGuessed(playerName)
            else -> Unit
        }
    }

    private fun hostReceiveName(writer: String, text: String) {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.WRITE || text.isBlank()) return
        val target = s.targetOf(writer)
        if (target.isBlank() || s.hasSubmitted(writer)) return
        mutateSnapshot { snap ->
            snap.copy(
                assignments = snap.assignments + (target to text),
                submitted = snap.submitted + writer,
            )
        }
        maybeFinishWrite()
    }

    private fun maybeFinishWrite() {
        if (_uiState.value.role != WaNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.WRITE) return
        val waiting = s.players.filter {
            it.connected && s.targets.containsKey(it.name) && !s.hasSubmitted(it.name)
        }
        if (waiting.isEmpty() && s.submitted.isNotEmpty()) {
            mutateSnapshot { it.copy(phase = WaPhase.PLAY) }
        }
    }

    private fun hostMarkGuessed(playerName: String) {
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.PLAY || s.hasGuessed(playerName)) return
        // هر چه زودتر حدس بزنی، امتیاز بیشتر: نفر اول بیشترین را می‌گیرد
        val playing = s.players.count { p ->
            p.connected && s.assignments.keys.any { k -> sameName(k, p.name) }
        }
        val gained = (playing - s.guessedOrder.size).coerceAtLeast(1)
        mutateSnapshot { snap ->
            snap.copy(
                guessedOrder = snap.guessedOrder + playerName,
                players = snap.players.map { p ->
                    if (sameName(p.name, playerName)) p.copy(totalScore = p.totalScore + gained) else p
                },
            )
        }
        maybeFinishRound()
    }

    private fun maybeFinishRound() {
        if (_uiState.value.role != WaNetRole.HOST) return
        val s = _uiState.value.snapshot
        if (s.phase != WaPhase.PLAY) return
        val waiting = s.players.filter {
            it.connected &&
                s.assignments.keys.any { k -> sameName(k, it.name) } &&
                !s.hasGuessed(it.name)
        }
        if (waiting.isEmpty()) {
            mutateSnapshot { it.copy(phase = WaPhase.ROUND_RESULT) }
        }
    }

    // ================= هسته‌ی همگام‌سازی =================

    private fun mutateSnapshot(transform: (WaSnapshot) -> WaSnapshot) {
        val next = transform(_uiState.value.snapshot)
        setSnapshot(next)
        server?.broadcast(WaMessage.State(next))
    }

    private fun setSnapshot(next: WaSnapshot) {
        _uiState.update { it.copy(snapshot = next) }
    }

    private inline fun hostOnly(block: () -> Unit) {
        if (_uiState.value.role == WaNetRole.HOST) block()
    }

    // ================= خروج =================

    fun leaveGame() {
        nsd.release()
        client?.close()
        client = null
        server?.stop()
        server = null
        val name = _uiState.value.myName
        _uiState.value = WhoAmIUiState(myName = name)
    }

    fun backToEntryFromJoin() {
        nsd.stopDiscovery()
        _uiState.update { it.copy(localScreen = WaLocalScreen.ENTRY, connectError = null, connecting = false) }
    }

    override fun onCleared() {
        super.onCleared()
        leaveGame()
    }
}

// ================= ریشه =================

/** ریشه‌ی من کی‌ام؟ — بازی چندگوشی روی شبکه‌ی محلی */
@Composable
fun WhoAmIGame(
    onExitToHub: () -> Unit,
    viewModel: WhoAmIViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    LaunchedEffect(state.localScreen, snapshot.phase) {
        val track = when {
            state.localScreen != WaLocalScreen.IN_GAME -> MusicTrack.HUB
            snapshot.phase == WaPhase.LOBBY -> MusicTrack.HUB
            else -> MusicTrack.ESM_FAMIL
        }
        sound?.switchMusic(track)
    }

    val leaveAndExit = {
        viewModel.leaveGame()
        onExitToHub()
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.localScreen != WaLocalScreen.IN_GAME || state.snapshot.phase == WaPhase.LOBBY) {
            GameHelpButton(gameId = "who_am_i", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.localScreen to snapshot.phase) {
            when (state.localScreen) {
                WaLocalScreen.ENTRY -> {
                    BackHandler { onExitToHub() }
                    WaEntryScreen(
                        state = state,
                        onNameChanged = viewModel::setMyName,
                        onHost = viewModel::startHosting,
                        onJoin = viewModel::openJoinScreen,
                    )
                }

                WaLocalScreen.JOIN -> {
                    BackHandler { viewModel.backToEntryFromJoin() }
                    WaJoinScreen(
                        state = state,
                        onJoin = { game -> viewModel.joinGame(game.address, game.port) },
                        onManualJoin = { address -> viewModel.joinGame(address) },
                    )
                }

                WaLocalScreen.IN_GAME -> {
                    BackHandler { pendingExit = { leaveAndExit() } }
                    when (snapshot.phase) {
                        WaPhase.LOBBY -> WaLobbyScreen(state = state, onStart = viewModel::startGame)

                        WaPhase.WRITE -> WaWriteScreen(
                            state = state,
                            onDraftChanged = viewModel::updateDraft,
                            onSubmit = viewModel::submitName,
                        )

                        WaPhase.PLAY -> WaPlayScreen(state = state, onGuessed = viewModel::markGuessed)

                        WaPhase.ROUND_RESULT -> WaRoundResultScreen(
                            state = state,
                            onNextRound = viewModel::nextRound,
                            onFinish = viewModel::finishGame,
                        )

                        WaPhase.GAME_OVER -> {
                            LaunchedEffect(Unit) { sound?.playGameOver() }
                            WaGameOverScreen(
                                state = state,
                                winners = viewModel.winners(),
                                onPlayAgain = viewModel::playAgain,
                                onExit = leaveAndExit,
                            )
                        }
                    }
                }
            }
        }

        // ---- ارتباط با میزبان قطع شد ----
        if (state.lostConnection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                contentAlignment = Alignment.Center
            ) {
                TicketCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    tilt = -1.5f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BobbingEmoji(emoji = "📴", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ارتباط با میزبان قطع شد!",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "وای‌فای رو چک کنید؛ اگر میزبان برگشت، دوباره با همون اسم بپیوندید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        KButton(text = "باشه", onClick = leaveAndExit)
                    }
                }
            }
        }
    }
}

// ================= صفحه‌ها =================

@Composable
private fun WaEntryScreen(
    state: WhoAmIUiState,
    onNameChanged: (String) -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
) {
    var showNameError by remember { mutableStateOf(false) }
    val guardName: (() -> Unit) -> Unit = { action ->
        if (state.myName.isBlank()) showNameError = true else action()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "🏷️", fontSize = 58.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "من کی‌ام؟", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "هر کی برای یکی دیگه اسم می‌نویسه؛ گوشی می‌ره رو پیشونی و با سوال بله/نه باید بفهمی کی هستی! همه روی یک وای‌فای یا هات‌اسپات باشید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        BlobTextField(
            value = state.myName,
            onValueChange = {
                onNameChanged(it)
                if (it.isNotBlank()) showNameError = false
            },
            placeholder = "اسمت چیه؟ (شناسه‌ی تو در بازی)",
            badge = "👤",
            tilt = -1f,
        )
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            ChoiceBubble(
                main = "میزبان شو",
                sub = "بازی بساز و\nرفقا رو دعوت کن",
                emoji = "👑",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = -3f,
                onClick = { guardName(onHost) },
            )
            ChoiceBubble(
                main = "بپیوند",
                sub = "به بازیِ ساخته‌شده\nوصل شو",
                emoji = "🚪",
                size = 148.dp,
                mainFontSize = 22.sp,
                tilt = 3f,
                phase = 1.5f,
                modifier = Modifier.offset(y = 26.dp),
                onClick = { guardName(onJoin) },
            )
        }

        if (showNameError && state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.04f, periodMs = 1100)) {
                StickerTitle(
                    text = "✋ اول اسمت رو بنویس!",
                    accent = kiExtras.danger,
                    rotation = -2f,
                    fontSize = 22.sp,
                )
            }
        } else if (state.myName.isBlank()) {
            Spacer(modifier = Modifier.height(34.dp))
            Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 2000)) {
                StickerTitle(
                    text = "✍️ اول اسمت رو بنویس",
                    accent = LocalGameAccent.current,
                    rotation = -2f,
                    fontSize = 18.sp,
                )
            }
        }
        state.connectError?.let {
            Spacer(modifier = Modifier.height(14.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger)
        }
    }
}

@Composable
private fun WaJoinScreen(
    state: WhoAmIUiState,
    onJoin: (WaDiscoveredGame) -> Unit,
    onManualJoin: (String) -> Unit,
) {
    var manualAddress by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "📡", fontSize = 46.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(text = "به کدوم بازی بپیوندیم؟", rotation = 2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (state.discovered.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.breathing(intensity = 0.05f, periodMs = 1800)) {
                        Text(text = "🔎", fontSize = 30.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "دنبال بازی می‌گردم…\nمیزبان باید بازی رو ساخته باشه و روی همین شبکه باشید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(count = state.discovered.size, key = { state.discovered[it].hostName }) { i ->
                    val game = state.discovered[i]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 22.dp,
                        strong = true,
                        tilt = if (i % 2 == 0) -1f else 1f,
                        onClick = { onJoin(game) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🏷️", fontSize = 26.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "بازیِ ${game.hostName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "لمس کن تا وصل شی",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(text = "🚪", fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "پیدا نشد؟ آدرسِ نمایش‌داده‌شده در لابی میزبان رو بزن:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(
            value = manualAddress,
            onValueChange = { manualAddress = it },
            placeholder = "مثلاً 192.168.1.5",
            badge = "🔗",
            tilt = 0.8f,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(
            text = if (state.connecting) "در حال اتصال…" else "اتصال دستی",
            enabled = !state.connecting && manualAddress.isNotBlank(),
            onClick = { onManualJoin(manualAddress.trim()) },
        )
        state.connectError?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = it, style = MaterialTheme.typography.labelLarge, color = kiExtras.danger, textAlign = TextAlign.Center)
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun WaLobbyScreen(
    state: WhoAmIUiState,
    onStart: () -> Unit,
) {
    val snapshot = state.snapshot
    val connected = snapshot.players.count { it.connected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(18.dp))
        StickerTitle(text = "لابی من کی‌ام؟", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    text = "بازیکن‌ها (${snapshot.players.size.toPersianDigits()} از ۸)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                snapshot.players.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)
                    ) {
                        row.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "🏷️", fontSize = 26.sp)
                                Text(
                                    text = if (sameName(p.name, snapshot.hostName)) "${p.name} 👑" else p.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "هر راند، هر کی برای یکی دیگه اسم می‌نویسه. زمان نداره — هر کی زودتر حدس بزنه امتیاز بیشتری می‌گیره و پایان بازی هم دست خودتونه!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (state.isHost) {
            if (state.hostAddress.isNotBlank()) {
                Text(
                    text = "اتصال دستی مهمان‌ها: ${state.hostAddress}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            KButton(
                text = if (connected < WA_MIN_PLAYERS) "منتظر حداقل ${WA_MIN_PLAYERS.toPersianDigits()} بازیکن…"
                else "شروع بازی!",
                enabled = connected >= WA_MIN_PLAYERS,
                onClick = onStart,
            )
        } else {
            BobbingEmoji(emoji = "🍿", fontSize = 40.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "منتظریم ${snapshot.hostName} بازی رو شروع کنه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun WaWriteScreen(
    state: WhoAmIUiState,
    onDraftChanged: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val snapshot = state.snapshot
    val target = state.myTarget
    val waitingCount = snapshot.connectedPlayers.count {
        snapshot.targets.containsKey(it.name) && !snapshot.hasSubmitted(it.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "راند ${snapshot.roundIndex.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        BobbingEmoji(emoji = "🤫", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (!state.iHaveSubmitted) {
            Text(
                text = "یواشکی برای ${target.ifBlank { "…" }} یه اسم بنویس!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = kiExtras.teamColors.teamColorFor(snapshot.player(target)?.colorIndex ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "آدم معروف، شخصیت کارتونی یا حتی یکی از همین جمع — فقط خودش نفهمه!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            BlobTextField(
                value = state.myDraft,
                onValueChange = onDraftChanged,
                placeholder = "مثلاً: فردوسی، باب اسفنجی…",
                badge = "🏷️",
                tilt = -1f,
            )
            Spacer(modifier = Modifier.height(16.dp))
            KButton(
                text = "فرستادم 🤐",
                enabled = state.myDraft.isNotBlank(),
                onClick = onSubmit,
            )
        } else {
            BobbingEmoji(emoji = "⏳", fontSize = 36.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "اسمت رسید! منتظر ${waitingCount.toPersianDigits()} نویسنده‌ی دیگه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WaPlayScreen(
    state: WhoAmIUiState,
    onGuessed: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val snapshot = state.snapshot
    val stillPlaying = snapshot.connectedPlayers.filter {
        snapshot.assignments.keys.any { k -> sameName(k, it.name) } && !snapshot.hasGuessed(it.name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "راند ${snapshot.roundIndex.toPersianDigits()} — بدون زمان، با خیال راحت!",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (!state.iHaveGuessed) {
            TicketCard(modifier = Modifier.fillMaxWidth(), tilt = -1.5f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 26.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "نخونش! 🙈 بگیرش بالا رو پیشونیت تا بقیه ببینن",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = extras.danger,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.breathing(intensity = 0.03f, periodMs = 1800)) {
                        Text(
                            text = state.myForeheadName.ifBlank { "…" },
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Black,
                            color = accent,
                            textAlign = TextAlign.Center,
                            lineHeight = 58.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "با سوال‌های بله/نه بفهم کی هستی: «زنده‌ام؟»، «ایرانی‌ام؟»، «ورزشکارم؟»",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            BobbingEmoji(emoji = "😎", fontSize = 52.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "آفرین! تو لیست انتظارِ راند بعدی",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = extras.success,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "به بقیه کمک بده — فقط لو نده! 🤐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---- لیست انتظار ----
        if (snapshot.guessedOrder.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🏁 حدس زدن (به ترتیب):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = snapshot.guessedOrder.joinToString("  ←  "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Text(
            text = "هنوز دارن فکر می‌کنن: ${stillPlaying.joinToString("، ") { it.name }.ifBlank { "—" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.weight(1f))
        if (!state.iHaveGuessed) {
            KButton(
                text = "حدس زدم! ✅",
                onClick = onGuessed,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        } else {
            Spacer(modifier = Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

@Composable
private fun WaRoundResultScreen(
    state: WhoAmIUiState,
    onNextRound: () -> Unit,
    onFinish: () -> Unit,
) {
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        BobbingEmoji(emoji = "🎉", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(6.dp))
        StickerTitle(text = "راند ${snapshot.roundIndex.toPersianDigits()} تموم شد!", rotation = -2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(14.dp))

        // ---- رو شدن اسم‌ها ----
        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🏷️ کی، کی بود؟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                snapshot.players.filter { snapshot.assignmentOf(it.name).isNotBlank() }.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                        )
                        Text(
                            text = snapshot.assignmentOf(p.name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ---- جدول امتیازها ----
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "💯 امتیازها",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                snapshot.players.sortedByDescending { it.totalScore }.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                        )
                        Text(
                            text = p.totalScore.toPersianDigits(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        if (state.isHost) {
            KButton(text = "راند بعد! 🔁", onClick = onNextRound)
            Spacer(modifier = Modifier.height(8.dp))
            KButton(
                text = "بسه دیگه — کی برد؟ 🏆",
                style = KButtonStyle.Glass,
                onClick = onFinish,
                modifier = Modifier.navigationBarsPadding(),
            )
        } else {
            Text(
                text = "${snapshot.hostName} تصمیم می‌گیره: راند بعد یا اعلام برنده…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun WaGameOverScreen(
    state: WhoAmIUiState,
    winners: List<WaPlayer>,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val snapshot = state.snapshot

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(30.dp))
            Text(text = "🏆", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کی برد؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = winners.joinToString(" و ") { it.name },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull()?.colorIndex ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    snapshot.players.sortedByDescending { it.totalScore }.forEachIndexed { rank, p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (p in winners) "🥇" else (rank + 1).toPersianDigits(),
                                    fontSize = 17.sp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = kiExtras.teamColors.teamColorFor(p.colorIndex),
                                )
                            }
                            Text(
                                text = p.totalScore.toPersianDigits(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            ShareWinButton(
                gameId = "who_am_i",
                gameTitle = "من کی‌ام؟",
                gameEmoji = "🏷️",
                winnerText = winners.joinToString(" و ") { it.name },
                scoreLines = snapshot.players.sortedByDescending { it.totalScore }
                    .map { it.name to it.totalScore.toPersianDigits() },
                winnerNames = winners.map { it.name },
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (state.isHost) {
                KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
                Spacer(modifier = Modifier.height(10.dp))
            }
            KButton(text = "بازگشت به خانه", onClick = onExit, style = KButtonStyle.Glass)
        }
    }
}
