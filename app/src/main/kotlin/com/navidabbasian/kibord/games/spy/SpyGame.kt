package com.navidabbasian.kibord.games.spy

import android.app.Application
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.audio.MusicTrack
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.PlayedContentStore
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
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
import com.navidabbasian.kibord.core.util.formatMillisAsClock
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ================= مدل =================

@Serializable
data class SpyLocation(val name: String, val emoji: String = "📍")

sealed class SpyPhase {
    data object PlayerCount : SpyPhase()
    data object PlayerNames : SpyPhase()
    data object Settings : SpyPhase()
    /** پخش مخفیانه‌ی نقش‌ها: گوشی دست بازیکن index */
    data class Reveal(val index: Int, val shown: Boolean) : SpyPhase()
    data object Discussion : SpyPhase()
    data object Uncover : SpyPhase()
}

data class SpyUiState(
    val phase: SpyPhase = SpyPhase.PlayerCount,
    val playerCount: Int = 4,
    val playerNames: List<String> = emptyList(),
    val discussionMinutes: Int = 5,
    val spyIndex: Int = 0,
    val location: SpyLocation? = null,
    val secondsLeft: Int = 0,
) {
    fun playerDisplayName(index: Int): String =
        playerNames.getOrNull(index)?.ifBlank { "بازیکن ${(index + 1).toPersianDigits()}" }
            ?: "بازیکن ${(index + 1).toPersianDigits()}"
}

// ================= موتور =================

/** جاسوس: همه مکان مشترک را می‌دانند جز جاسوس — با سوال و جواب پیدایش کنید! */
class SpyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SpyUiState())
    val uiState: StateFlow<SpyUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }
    private val playedStore = PlayedContentStore(application)
    private var locations: List<SpyLocation> = emptyList()
    private var tickerJob: Job? = null

    init {
        locations = try {
            json.decodeFromString<List<SpyLocation>>(ContentBank.open(application, "spy.json"))
        } catch (_: Exception) {
            listOf(SpyLocation("رستوران", "🍽️"), SpyLocation("سینما", "🎬"))
        }
    }

    fun setPlayerCount(count: Int) {
        _uiState.update {
            it.copy(
                playerCount = count,
                playerNames = List(count) { "" },
                phase = SpyPhase.PlayerNames,
            )
        }
    }

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(playerNames = s.playerNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmNames() = _uiState.update { it.copy(phase = SpyPhase.Settings) }

    fun setDiscussionMinutes(minutes: Int) = _uiState.update { it.copy(discussionMinutes = minutes) }

    fun startGame() {
        // مکان بازی‌نشده؛ بعد از یک دور کامل، سابقه ریست می‌شود
        val played = playedStore.played(KEY)
        var fresh = locations.filter { it.name !in played }
        if (fresh.isEmpty()) {
            playedStore.clear(KEY)
            fresh = locations
        }
        val location = fresh.random()
        playedStore.markPlayed(KEY, location.name)
        _uiState.update {
            it.copy(
                location = location,
                spyIndex = (0 until it.playerCount).random(),
                phase = SpyPhase.Reveal(0, shown = false),
            )
        }
    }

    fun showRole() {
        val phase = _uiState.value.phase as? SpyPhase.Reveal ?: return
        _uiState.update { it.copy(phase = phase.copy(shown = true)) }
    }

    fun hideRoleAndNext() {
        val s = _uiState.value
        val phase = s.phase as? SpyPhase.Reveal ?: return
        if (phase.index + 1 >= s.playerCount) {
            _uiState.update {
                it.copy(
                    phase = SpyPhase.Discussion,
                    secondsLeft = it.discussionMinutes * 60,
                )
            }
            startTicker()
        } else {
            _uiState.update { it.copy(phase = SpyPhase.Reveal(phase.index + 1, shown = false)) }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val s = _uiState.value
                if (s.phase != SpyPhase.Discussion) break
                val left = s.secondsLeft - 1
                if (left <= 0) {
                    _uiState.update { it.copy(phase = SpyPhase.Uncover, secondsLeft = 0) }
                    break
                }
                _uiState.update { it.copy(secondsLeft = left) }
            }
        }
    }

    /** پایان زودهنگام بحث: جاسوس لو رفت یا جمع تصمیم گرفت */
    fun endDiscussion() {
        tickerJob?.cancel()
        _uiState.update { it.copy(phase = SpyPhase.Uncover) }
    }

    fun playAgain() {
        tickerJob?.cancel()
        val old = _uiState.value
        _uiState.value = SpyUiState(
            playerCount = old.playerCount,
            playerNames = old.playerNames,
            discussionMinutes = old.discussionMinutes,
            phase = SpyPhase.Settings,
        )
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                SpyPhase.PlayerNames -> s.copy(phase = SpyPhase.PlayerCount)
                SpyPhase.Settings -> s.copy(phase = SpyPhase.PlayerNames)
                else -> s
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }

    companion object {
        const val KEY = "spy_locations"
    }
}

// ================= ریشه و صفحه‌ها =================

/** ریشه‌ی جاسوس — پخش مخفی نقش‌ها، بحث زمان‌دار و لو رفتن */
@Composable
fun SpyGame(
    onExitToHub: () -> Unit,
    viewModel: SpyViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            SpyPhase.PlayerCount, SpyPhase.PlayerNames, SpyPhase.Settings -> MusicTrack.HUB
            else -> MusicTrack.DOR
        }
        sound?.switchMusic(track)
    }

    // تیک‌تاک دقایق آخر بحث
    LaunchedEffect(state.secondsLeft) {
        if (state.phase == SpyPhase.Discussion && state.secondsLeft in 1..10) {
            sound?.playTimerWarning()
        }
    }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        PhaseTransition(key = state.phase::class) {
            when (val phase = state.phase) {
                SpyPhase.PlayerCount -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SpyPlayerCountScreen(onSelected = viewModel::setPlayerCount)
                }

                SpyPhase.PlayerNames -> {
                    BackHandler { viewModel.navigateBack() }
                    SpyPlayerNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updatePlayerName,
                        onConfirm = viewModel::confirmNames,
                    )
                }

                SpyPhase.Settings -> {
                    BackHandler { viewModel.navigateBack() }
                    SpySettingsScreen(
                        state = state,
                        onMinutes = viewModel::setDiscussionMinutes,
                        onStart = viewModel::startGame,
                    )
                }

                is SpyPhase.Reveal -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SpyRevealScreen(
                        state = state,
                        index = phase.index,
                        shown = phase.shown,
                        onShow = viewModel::showRole,
                        onHide = viewModel::hideRoleAndNext,
                    )
                }

                SpyPhase.Discussion -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SpyDiscussionScreen(
                        state = state,
                        onEnd = viewModel::endDiscussion,
                    )
                }

                SpyPhase.Uncover -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    SpyUncoverScreen(
                        state = state,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = {
                            viewModel.playAgain()
                            onExitToHub()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpyPlayerCountScreen(onSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "🕵️", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "جاسوس", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "همه می‌دونن کجایید جز جاسوس! با سوال و جواب پیداش کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))

        listOf(3, 4, 5, 6, 7, 8).chunked(2).forEachIndexed { rowIndex, row ->
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
                        size = 108.dp,
                        mainFontSize = 30.sp,
                        accent = kiExtras.teamColors.teamColorFor(i + 1),
                        tilt = if (zig) -3f else 3f,
                        phase = i * 1.3f,
                        modifier = Modifier.offset(y = if (zig) 0.dp else 14.dp),
                        onClick = { onSelected(count) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpyPlayerNamesScreen(
    state: SpyUiState,
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
            KButton(text = "ادامه", onClick = onConfirm)
        }
    }
}

@Composable
private fun SpySettingsScreen(
    state: SpyUiState,
    onMinutes: (Int) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val sound = LocalSoundManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(30.dp))
        BobbingEmoji(emoji = "⏱", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "چند دقیقه بحث؟", rotation = 2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "بعد از دیدن نقش‌ها، این‌قدر وقت دارید جاسوس رو پیدا کنید",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(26.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            listOf(3, 5, 8).forEachIndexed { i, m ->
                ChoiceBubble(
                    main = m.toPersianDigits(),
                    sub = "دقیقه",
                    size = 104.dp,
                    mainFontSize = 30.sp,
                    accent = if (state.discussionMinutes == m) accent
                    else kiExtras.teamColors.teamColorFor(i + 1),
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.2f,
                    modifier = Modifier.offset(y = if (i == 1) 18.dp else 0.dp),
                    onClick = { onMinutes(m) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp)
        ) {
            KButton(text = "پخش نقش‌ها 🤫", onClick = {
                sound?.playButtonClick()
                onStart()
            })
        }
    }
}

@Composable
private fun SpyRevealScreen(
    state: SpyUiState,
    index: Int,
    shown: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val isSpy = index == state.spyIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${(index + 1).toPersianDigits()} از ${state.playerCount.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "گوشی دست ${state.playerDisplayName(index)}",
            style = MaterialTheme.typography.headlineMedium,
            color = kiExtras.teamColors.teamColorFor(index),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))

        if (!shown) {
            BobbingEmoji(emoji = "🫣", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "مطمئن شو کسی صفحه رو نمی‌بینه!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            KButton(text = "نقشم رو نشون بده 👀", onClick = onShow)
        } else {
            TicketCard(
                modifier = Modifier.fillMaxWidth(),
                accent = if (isSpy) extras.danger else accent,
                tilt = -1.5f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSpy) {
                        Text(text = "🕵️", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تو جاسوسی!",
                            style = MaterialTheme.typography.displayMedium,
                            color = extras.danger,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "مکان رو نمی‌دونی — طوری سوال بده و جواب بده که لو نری!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(text = state.location?.emoji ?: "📍", fontSize = 54.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.location?.name ?: "",
                            style = MaterialTheme.typography.displayMedium,
                            color = accent,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "این مکان رو مخفی نگه دار و جاسوس رو پیدا کن!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            KButton(
                text = if (index + 1 >= state.playerCount) "قایم کن — شروع بحث! 🗣" else "قایم کن و بده نفر بعد",
                style = KButtonStyle.Danger,
                onClick = onHide,
            )
        }
    }
}

@Composable
private fun SpyDiscussionScreen(
    state: SpyUiState,
    onEnd: () -> Unit,
) {
    val extras = kiExtras
    val urgent = state.secondsLeft <= 30

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "🗣", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "بحث و بازجویی!", rotation = -2f, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier.then(
                if (urgent) Modifier.breathing(intensity = 0.05f, periodMs = 700) else Modifier
            )
        ) {
            Text(
                text = formatMillisAsClock(state.secondsLeft * 1000L),
                fontSize = 68.sp,
                fontWeight = FontWeight.Black,
                color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "نوبتی از هم سوال بپرسید: «اینجا چی می‌پوشن؟»، «کی میاد اینجا؟»\nجاسوس بلوف می‌زنه، شما مچشو بگیرید!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(30.dp))
        KButton(text = "جاسوس رو پیدا کردیم!", style = KButtonStyle.Danger, onClick = onEnd)
    }
}

@Composable
private fun SpyUncoverScreen(
    state: SpyUiState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current

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
            Text(text = "🕵️", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "جاسوس این بود:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.playerDisplayName(state.spyIndex),
                style = MaterialTheme.typography.displayMedium,
                color = extras.danger,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${state.location?.emoji ?: ""} مکان: ${state.location?.name ?: ""}",
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "درست حدس زده بودید؟ اگه جاسوس لو نرفت، برنده اونه! 😏",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
