package com.navidabbasian.kibord.games.esmramz

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.session.SessionStore
import com.navidabbasian.kibord.core.settings.GamePrefs
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ================= مدل =================

@Serializable
private data class ErWord(val word: String)

/** نقش هر خانه‌ی تخته */
@Serializable
enum class ErRole { TEAM_A, TEAM_B, NEUTRAL, ASSASSIN }

/** یک خانه‌ی تخته‌ی ۵×۵ */
@Serializable
data class ErTile(
    val word: String,
    val role: ErRole,
    val revealed: Boolean = false,
)

@Serializable
sealed class ErPhase {
    @Serializable data object TeamNames : ErPhase()
    /** نقشه‌ی رنگی فقط برای دو سرگروه */
    @Serializable data class KeyReveal(val shown: Boolean) : ErPhase()
    @Serializable data object Play : ErPhase()
    @Serializable data class GameOver(val winner: Int, val byAssassin: Boolean) : ErPhase()
}

@Serializable
data class ErUiState(
    val phase: ErPhase = ErPhase.TeamNames,
    val teamNames: List<String> = List(2) { "" },
    val board: List<ErTile> = emptyList(),
    /** تیم شروع‌کننده ۹ کلمه دارد */
    val startingTeam: Int = 0,
    val currentTeam: Int = 0,
    /** خانه‌ی انتخاب‌شده در انتظار تایید (لمس دوم) */
    val selectedIndex: Int? = null,
    /** برد سری بازی‌ها در همین نشست */
    val matchWins: List<Int> = List(2) { 0 },
) {
    fun teamDisplayName(index: Int): String =
        teamNames.getOrNull(index)?.ifBlank { if (index == 0) "تیم قرمز" else "تیم آبی" }
            ?: if (index == 0) "تیم قرمز" else "تیم آبی"

    fun roleOfTeam(index: Int): ErRole = if (index == 0) ErRole.TEAM_A else ErRole.TEAM_B

    fun remainingOf(index: Int): Int =
        board.count { it.role == roleOfTeam(index) && !it.revealed }
}

enum class ErSoundEvent { OWN, MISS, ASSASSIN, WIN }

// ================= موتور =================

/**
 * اسم‌رمز: سرگروه‌ها نقشه‌ی رنگی را می‌دانند و با «رمزِ یک‌کلمه‌ای + عدد»
 * تیم‌شان را به کلمه‌های خودی می‌رسانند. کلمه‌ی سیاه = باخت فوری.
 */
class EsmRamzViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ErUiState())
    val uiState: StateFlow<ErUiState> = _uiState.asStateFlow()

    private val _soundEvents = MutableSharedFlow<ErSoundEvent>(extraBufferCapacity = 16)
    val soundEvents: SharedFlow<ErSoundEvent> = _soundEvents.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(application)
    private var allWords: List<String> = emptyList()

    init {
        // بارگذاری یک‌باره‌ی بانک کلمه‌ها — چه ادامه‌ی نشست، چه شروع نو
        allWords = decodeWords(ContentBank.open(application, "esmramz.json"))
        if (allWords.isEmpty()) {
            allWords = decodeWords(
                try {
                    application.assets.open("esmramz.json").bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
            )
        }
        // اگر نشستی از اجرای پیش از مرگ پروسه مانده، بازی از همان‌جا ادامه می‌یابد
        if (!restoreSession()) {
            val names = GamePrefs.getNames(application, "esmramz_names")
            if (names.isNotEmpty()) {
                _uiState.update { it.copy(teamNames = List(2) { i -> names.getOrElse(i) { "" } }) }
            }
        }
    }

    // ---- مقاوم‌سازی در برابر مرگ پروسه ----

    /** آیا این فاز ارزش ذخیره‌شدن دارد؟ فقط وسطِ بازیِ واقعی (تخته چیده شده) */
    private fun ErUiState.isResumable(): Boolean =
        phase is ErPhase.KeyReveal || phase == ErPhase.Play

    /** ذخیره‌ی وضعیت هنگام رفتن به پس‌زمینه (از ریشه صدا زده می‌شود) */
    /** پس از خروج عمدی به خانه دیگر ذخیره نمی‌کنیم تا نشستِ پاک‌شده دوباره برنگردد */
    private var leaving = false

    fun persistSession() {
        if (leaving) return
        val s = _uiState.value
        if (s.isResumable()) {
            try {
                SessionStore.save(getApplication(), KEY, json.encodeToString(ErUiState.serializer(), s))
            } catch (_: Exception) {
            }
        } else {
            SessionStore.clear(getApplication(), KEY)
        }
    }

    private fun restoreSession(): Boolean {
        val raw = SessionStore.load(getApplication(), KEY) ?: return false
        val saved = try {
            json.decodeFromString(ErUiState.serializer(), raw)
        } catch (_: Exception) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        if (!saved.isResumable()) {
            SessionStore.clear(getApplication(), KEY)
            return false
        }
        // تخته و همه‌ی وضعیت بازی داخل UiState است؛ چیز گذرایی برای بازسازی نیست
        // (بانک کلمه‌ها در init پیش از این بارگذاری شده و تایمری هم در کار نیست)
        _uiState.value = saved
        return true
    }

    private fun clearSession() = SessionStore.clear(getApplication(), KEY)

    private fun decodeWords(text: String): List<String> = try {
        json.decodeFromString<List<ErWord>>(text)
    } catch (_: Exception) {
        emptyList()
    }.map { it.word }.filter { it.isNotBlank() }

    private fun emit(e: ErSoundEvent) = _soundEvents.tryEmit(e)

    // ---- راه‌اندازی ----

    fun updateTeamName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(teamNames = s.teamNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmTeamNames() {
        GamePrefs.setNames(getApplication(), "esmramz_names", _uiState.value.teamNames)
        dealBoard()
        _uiState.update { it.copy(phase = ErPhase.KeyReveal(shown = false)) }
    }

    /** تخته‌ی تازه: ۲۵ کلمه‌ی بازی‌نشده + نقشه‌ی تصادفی نقش‌ها */
    private fun dealBoard() {
        val played = playedStore.played(PLAYED_KEY)
        var fresh = allWords.filter { it !in played }
        if (fresh.size < BOARD_SIZE) {
            playedStore.clear(PLAYED_KEY)
            fresh = allWords
        }
        val words = fresh.shuffled().take(BOARD_SIZE)
        words.forEach { playedStore.markPlayed(PLAYED_KEY, it) }

        val startingTeam = listOf(0, 1).random()
        val roles = buildList {
            repeat(9) { add(if (startingTeam == 0) ErRole.TEAM_A else ErRole.TEAM_B) }
            repeat(8) { add(if (startingTeam == 0) ErRole.TEAM_B else ErRole.TEAM_A) }
            repeat(7) { add(ErRole.NEUTRAL) }
            add(ErRole.ASSASSIN)
        }.shuffled()

        _uiState.update {
            it.copy(
                board = words.mapIndexed { i, w -> ErTile(word = w, role = roles[i]) },
                startingTeam = startingTeam,
                currentTeam = startingTeam,
                selectedIndex = null,
            )
        }
    }

    fun showKey() {
        val phase = _uiState.value.phase as? ErPhase.KeyReveal ?: return
        _uiState.update { it.copy(phase = phase.copy(shown = true)) }
    }

    fun startPlay() {
        if (_uiState.value.phase !is ErPhase.KeyReveal) return
        _uiState.update { it.copy(phase = ErPhase.Play) }
    }

    // ---- بازی ----

    /** لمس اول انتخاب می‌کند، لمس دوم روی همان خانه قطعی می‌کند */
    fun tapTile(index: Int) {
        val s = _uiState.value
        if (s.phase != ErPhase.Play) return
        val tile = s.board.getOrNull(index) ?: return
        if (tile.revealed) return
        if (s.selectedIndex != index) {
            _uiState.update { it.copy(selectedIndex = index) }
            return
        }
        revealTile(index)
    }

    private fun revealTile(index: Int) {
        val s = _uiState.value
        val tile = s.board[index]
        val board = s.board.mapIndexed { i, t -> if (i == index) t.copy(revealed = true) else t }
        val current = s.currentTeam

        // کلمه‌ی سیاه: باخت فوریِ تیمِ در نوبت
        if (tile.role == ErRole.ASSASSIN) {
            emit(ErSoundEvent.ASSASSIN)
            finishGame(board, winner = 1 - current, byAssassin = true)
            return
        }

        // شاید همین خانه، آخرین کلمه‌ی یکی از تیم‌ها بوده باشد
        val next = _uiState.value.copy(board = board)
        val finishedTeam = (0..1).firstOrNull { team ->
            board.none { it.role == next.roleOfTeam(team) && !it.revealed }
        }
        if (finishedTeam != null) {
            emit(ErSoundEvent.WIN)
            finishGame(board, winner = finishedTeam, byAssassin = false)
            return
        }

        val keepTurn = tile.role == s.roleOfTeam(current)
        emit(if (keepTurn) ErSoundEvent.OWN else ErSoundEvent.MISS)
        _uiState.update {
            it.copy(
                board = board,
                selectedIndex = null,
                currentTeam = if (keepTurn) current else 1 - current,
            )
        }
    }

    /** تیمِ در نوبت داوطلبانه دست نگه می‌دارد */
    fun endTurn() {
        val s = _uiState.value
        if (s.phase != ErPhase.Play) return
        _uiState.update { it.copy(currentTeam = 1 - s.currentTeam, selectedIndex = null) }
    }

    private fun finishGame(board: List<ErTile>, winner: Int, byAssassin: Boolean) {
        clearSession()
        _uiState.update {
            it.copy(
                board = board.map { t -> t.copy(revealed = true) },
                selectedIndex = null,
                matchWins = it.matchWins.mapIndexed { i, w -> if (i == winner) w + 1 else w },
                phase = ErPhase.GameOver(winner = winner, byAssassin = byAssassin),
            )
        }
    }

    /** دست بعدی با همان تیم‌ها و تخته‌ی تازه */
    fun playAgain() {
        clearSession()
        dealBoard()
        _uiState.update { it.copy(phase = ErPhase.KeyReveal(shown = false)) }
    }

    /** خروج به خانه: نشست پاک می‌شود تا دفعه‌ی بعد از نو شروع شود */
    fun leaveGame() {
        leaving = true
        clearSession()
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                is ErPhase.KeyReveal -> s.copy(phase = ErPhase.TeamNames)
                else -> s
            }
        }
    }

    companion object {
        const val PLAYED_KEY = "esmramz"
        const val BOARD_SIZE = 25
        private const val KEY = "session_esmramz"
    }
}

// ================= ریشه =================

/** ریشه‌ی اسم‌رمز — ماشین فاز، موسیقی و رویدادهای صوتی */
@Composable
fun EsmRamzGame(
    onExitToHub: () -> Unit,
    viewModel: EsmRamzViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // با رفتن به پس‌زمینه، وضعیت بازی برای مقاوم‌سازی در برابر مرگ پروسه ذخیره می‌شود
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.persistSession() }

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                ErSoundEvent.OWN -> sound?.playCorrectWord()
                ErSoundEvent.MISS -> sound?.playWordSkip()
                ErSoundEvent.ASSASSIN -> {
                    sound?.playTimerEnd()
                    sound?.vibrate(400)
                }
                ErSoundEvent.WIN -> sound?.playGameOver()
            }
        }
    }

    LaunchedEffect(state.phase::class) {
        when (state.phase) {
            ErPhase.TeamNames -> sound?.switchMusic(MusicTrack.HUB)
            else -> sound?.stopBackgroundMusic()
        }
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        if (state.phase == ErPhase.TeamNames) {
            GameHelpButton(gameId = "esm_ramz", modifier = Modifier.align(Alignment.TopStart))
        }
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                ErPhase.TeamNames -> {
                    BackHandler { onExitToHub() }
                    ErTeamNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updateTeamName,
                        onConfirm = viewModel::confirmTeamNames,
                    )
                }

                is ErPhase.KeyReveal -> {
                    BackHandler { viewModel.navigateBack() }
                    ErKeyRevealScreen(
                        state = state,
                        shown = phase.shown,
                        onShow = viewModel::showKey,
                        onStart = viewModel::startPlay,
                    )
                }

                ErPhase.Play -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    ErPlayScreen(
                        state = state,
                        onTapTile = viewModel::tapTile,
                        onEndTurn = viewModel::endTurn,
                    )
                }

                is ErPhase.GameOver -> {
                    BackHandler { pendingExit = { viewModel.leaveGame(); onExitToHub() } }
                    ErGameOverScreen(
                        state = state,
                        winner = phase.winner,
                        byAssassin = phase.byAssassin,
                        onPlayAgain = viewModel::playAgain,
                        onExit = { viewModel.leaveGame(); onExitToHub() },
                    )
                }
            }
        }
    }
}

// ================= رنگ خانه‌ها =================

@Composable
private fun tileColor(role: ErRole): Color = when (role) {
    ErRole.TEAM_A -> kiExtras.teamColors.teamColorFor(0)
    ErRole.TEAM_B -> kiExtras.teamColors.teamColorFor(1)
    ErRole.NEUTRAL -> Color(0xFFCBBFA8)
    ErRole.ASSASSIN -> Color(0xFF3A3A46)
}

// ================= صفحه‌ها =================

@Composable
private fun ErTeamNamesScreen(
    state: ErUiState,
    onNameChanged: (Int, String) -> Unit,
    onConfirm: () -> Unit,
) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "🗝️", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "اسم‌رمز", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "هر تیم یک سرگروه انتخاب کنه! سرگروه با یک کلمه‌ی رمز و یک عدد، تیمش رو به کلمه‌های خودی می‌رسونه",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))

        repeat(2) { i ->
            BlobTextField(
                value = state.teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = if (i == 0) "تیم قرمز" else "تیم آبی",
                color = teamColors.teamColorFor(i),
                badge = (i + 1).toPersianDigits(),
                tilt = if (i % 2 == 0) -1.2f else 1.2f,
                phase = i * 1.6f,
                modifier = Modifier
                    .padding(vertical = 7.dp)
                    .offset(x = if (i % 2 == 0) (-6).dp else 6.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "تخته رو بچین! 🎲", onClick = onConfirm)
        }
    }
}

@Composable
private fun ErKeyRevealScreen(
    state: ErUiState,
    shown: Boolean,
    onShow: () -> Unit,
    onStart: () -> Unit,
) {
    val extras = kiExtras
    val starterColor = kiExtras.teamColors.teamColorFor(state.startingTeam)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!shown) {
            BobbingEmoji(emoji = "🤫", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "نقشه‌ی مخفی!", rotation = -2f, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "فقط دو سرگروه بیان و با هم نقشه رو ببینن —\nبقیه روتون رو برگردونید! 🙈",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            KButton(text = "ما سرگروهیم — نقشه رو نشون بده 👀", onClick = onShow)
        } else {
            Text(
                text = "شروع با ${state.teamDisplayName(state.startingTeam)} (۹ کلمه)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = starterColor,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ---- نقشه‌ی رنگی ۵×۵ ----
            GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                Column(modifier = Modifier.padding(12.dp)) {
                    state.board.chunked(5).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { tile ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(3.dp)
                                        .height(52.dp)
                                        .background(tileColor(tile.role), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tile.word,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⬛ کلمه‌ی سیاه = باخت فوری — حواس‌تون بهش باشه!",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = extras.danger,
            )
            Spacer(modifier = Modifier.height(18.dp))
            KButton(text = "حفظ شدیم — قایم کن و شروع!", style = KButtonStyle.Danger, onClick = onStart)
        }
    }
}

@Composable
private fun ErPlayScreen(
    state: ErUiState,
    onTapTile: (Int) -> Unit,
    onEndTurn: () -> Unit,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val currentColor = kiExtras.teamColors.teamColorFor(state.currentTeam)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // ---- وضعیت دو تیم ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { team ->
                val color = kiExtras.teamColors.teamColorFor(team)
                val active = team == state.currentTeam
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .then(if (active) Modifier.breathing(intensity = 0.04f, periodMs = 1400) else Modifier)
                        .background(
                            Brush.verticalGradient(listOf(lerp(color, Color.White, 0.15f), color)),
                            RoundedCornerShape(50)
                        )
                        .border(
                            2.dp,
                            if (active) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.3f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = state.teamDisplayName(team),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.remainingOf(team).toPersianDigits(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "نوبت ${state.teamDisplayName(state.currentTeam)} — سرگروه رمز بده!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = currentColor,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (state.selectedIndex != null) "دوباره لمس کن تا قطعی بشه — یا یه خانه‌ی دیگه انتخاب کن"
            else "لمس اول: انتخاب — لمس دوم: قطعی",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // ---- تخته‌ی ۵×۵ ----
        state.board.chunked(5).forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { colIndex, tile ->
                    val index = rowIndex * 5 + colIndex
                    val selected = state.selectedIndex == index
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(3.dp)
                            .height(58.dp)
                            .then(if (selected) Modifier.breathing(intensity = 0.05f, periodMs = 900) else Modifier)
                            .background(
                                if (tile.revealed) Brush.verticalGradient(
                                    listOf(lerp(tileColor(tile.role), Color.White, 0.12f), tileColor(tile.role))
                                )
                                else Brush.verticalGradient(listOf(extras.glassStrong, extras.glassStrong)),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                if (selected) 2.5.dp else 1.5.dp,
                                when {
                                    selected -> currentColor
                                    tile.revealed -> Color.White.copy(alpha = 0.4f)
                                    else -> extras.glassBorder
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(interactionSource = interaction, indication = null) {
                                sound?.playButtonClick()
                                onTapTile(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (tile.revealed && tile.role == ErRole.ASSASSIN) {
                            Text(text = "☠️", fontSize = 20.sp)
                        } else {
                            Text(
                                text = tile.word,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tile.revealed) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp)
        ) {
            KButton(text = "پایان نوبت ⏭", style = KButtonStyle.Glass, onClick = onEndTurn)
        }
    }
}

@Composable
private fun ErGameOverScreen(
    state: ErUiState,
    winner: Int,
    byAssassin: Boolean,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val winnerColor = kiExtras.teamColors.teamColorFor(winner)

    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (byAssassin) "☠️" else "🏆", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${state.teamDisplayName(winner)} برد!",
                style = MaterialTheme.typography.displayMedium,
                color = winnerColor,
                textAlign = TextAlign.Center,
            )
            if (byAssassin) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.teamDisplayName(1 - winner)} به کلمه‌ی سیاه خورد!",
                    style = MaterialTheme.typography.titleMedium,
                    color = kiExtras.danger,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "نتیجه‌ی دست‌ها: ${state.matchWins[0].toPersianDigits()} — ${state.matchWins[1].toPersianDigits()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(14.dp))

            // ---- تخته‌ی رو شده ----
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    state.board.chunked(5).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { tile ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .height(44.dp)
                                        .background(tileColor(tile.role), RoundedCornerShape(9.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (tile.role == ErRole.ASSASSIN) "☠️" else tile.word,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            ShareWinButton(
                gameId = "esm_ramz",
                gameTitle = "اسم‌رمز",
                gameEmoji = "🗝️",
                winnerText = state.teamDisplayName(winner),
                scoreLines = listOf(
                    state.teamDisplayName(0) to state.matchWins[0].toPersianDigits(),
                    state.teamDisplayName(1) to state.matchWins[1].toPersianDigits(),
                ),
                winnerNames = listOf(state.teamDisplayName(winner)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "دست بعد — تخته‌ی تازه!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExit, style = KButtonStyle.Glass)
        }
    }
}
