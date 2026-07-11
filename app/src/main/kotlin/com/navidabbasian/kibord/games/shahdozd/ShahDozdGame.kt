package com.navidabbasian.kibord.games.shahdozd

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
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
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ================= مدل =================

enum class SdRole(val title: String, val emoji: String) {
    SHAH("شاه", "👑"),
    VAZIR("وزیر", "🎩"),
    DOZD("دزد", "🦹"),
    RAIAT("رعیت", "🧑‍🌾"),
}

sealed class SdPhase {
    data object PlayerCount : SdPhase()
    data object PlayerNames : SdPhase()
    /** پخش مخفیانه‌ی نقش‌ها: گوشی دست بازیکن index */
    data class Reveal(val index: Int, val shown: Boolean) : SdPhase()
    /** شاه و وزیر علنی شدند؛ وزیر باید دزد را حدس بزند */
    data object Guess : SdPhase()
    data class RoundResult(val correct: Boolean, val guessed: Int) : SdPhase()
    data object Winner : SdPhase()
}

data class SdUiState(
    val phase: SdPhase = SdPhase.PlayerCount,
    val playerCount: Int = 4,
    val playerNames: List<String> = emptyList(),
    val roles: List<SdRole> = emptyList(),
    val scores: List<Int> = emptyList(),
    val roundIndex: Int = 1,
    val totalRounds: Int = 5,
) {
    fun playerDisplayName(index: Int): String =
        playerNames.getOrNull(index)?.ifBlank { "بازیکن ${(index + 1).toPersianDigits()}" }
            ?: "بازیکن ${(index + 1).toPersianDigits()}"

    val shahIndex: Int get() = roles.indexOf(SdRole.SHAH)
    val vazirIndex: Int get() = roles.indexOf(SdRole.VAZIR)
    val dozdIndex: Int get() = roles.indexOf(SdRole.DOZD)
}

// ================= موتور =================

/**
 * شاه دزد وزیر: نقش‌ها هر راند قرعه می‌خورند؛ وزیر به فرمان شاه دزد را حدس می‌زند.
 * امتیاز: شاه ۱۰۰ — وزیرِ تیزبین ۸۰ — دزدِ لو نرفته ۸۰ — رعیت ۵۰.
 */
class ShahDozdViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SdUiState())
    val uiState: StateFlow<SdUiState> = _uiState.asStateFlow()

    fun setPlayerCount(count: Int) {
        _uiState.update {
            it.copy(
                playerCount = count,
                playerNames = List(count) { "" },
                scores = List(count) { 0 },
                phase = SdPhase.PlayerNames,
            )
        }
    }

    fun updatePlayerName(index: Int, name: String) {
        _uiState.update { s ->
            s.copy(playerNames = s.playerNames.mapIndexed { i, n -> if (i == index) name else n })
        }
    }

    fun confirmNames() = startRound(resetScores = true)

    private fun startRound(resetScores: Boolean = false) {
        val s = _uiState.value
        // نقش‌ها: شاه، وزیر، دزد و بقیه رعیت — قرعه‌ی تازه در هر راند
        val roles = MutableList(s.playerCount) { SdRole.RAIAT }
        val picks = (0 until s.playerCount).shuffled()
        roles[picks[0]] = SdRole.SHAH
        roles[picks[1]] = SdRole.VAZIR
        roles[picks[2]] = SdRole.DOZD
        _uiState.update {
            it.copy(
                roles = roles,
                scores = if (resetScores) List(it.playerCount) { 0 } else it.scores,
                roundIndex = if (resetScores) 1 else it.roundIndex,
                phase = SdPhase.Reveal(0, shown = false),
            )
        }
    }

    fun showRole() {
        val phase = _uiState.value.phase as? SdPhase.Reveal ?: return
        _uiState.update { it.copy(phase = phase.copy(shown = true)) }
    }

    fun hideRoleAndNext() {
        val s = _uiState.value
        val phase = s.phase as? SdPhase.Reveal ?: return
        if (phase.index + 1 >= s.playerCount) {
            _uiState.update { it.copy(phase = SdPhase.Guess) }
        } else {
            _uiState.update { it.copy(phase = SdPhase.Reveal(phase.index + 1, shown = false)) }
        }
    }

    /** وزیر یکی از مظنون‌ها را انتخاب کرد */
    fun guessDozd(index: Int) {
        val s = _uiState.value
        if (s.phase != SdPhase.Guess) return
        if (index == s.shahIndex || index == s.vazirIndex) return
        val correct = index == s.dozdIndex
        val newScores = s.scores.mapIndexed { i, score ->
            score + when {
                i == s.shahIndex -> 100
                i == s.vazirIndex && correct -> 80
                i == s.dozdIndex && !correct -> 80
                s.roles[i] == SdRole.RAIAT -> 50
                else -> 0
            }
        }
        _uiState.update {
            it.copy(scores = newScores, phase = SdPhase.RoundResult(correct, index))
        }
    }

    fun proceedAfterRound() {
        val s = _uiState.value
        if (s.roundIndex >= s.totalRounds) {
            _uiState.update { it.copy(phase = SdPhase.Winner) }
        } else {
            _uiState.update { it.copy(roundIndex = it.roundIndex + 1) }
            startRound()
        }
    }

    fun winners(): List<Int> {
        val scores = _uiState.value.scores
        val max = scores.maxOrNull() ?: return emptyList()
        return scores.withIndex().filter { it.value == max }.map { it.index }
    }

    fun playAgain() {
        startRound(resetScores = true)
    }

    fun navigateBack() {
        _uiState.update { s ->
            when (s.phase) {
                SdPhase.PlayerNames -> s.copy(phase = SdPhase.PlayerCount)
                else -> s
            }
        }
    }
}

// ================= ریشه و صفحه‌ها =================

/** ریشه‌ی شاه دزد وزیر */
@Composable
fun ShahDozdGame(
    onExitToHub: () -> Unit,
    viewModel: ShahDozdViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // خروج با دکمه‌ی برگشت سیستم فقط با تاییدِ کاربر
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    val sound = LocalSoundManager.current

    LaunchedEffect(state.phase) {
        val track = when (state.phase) {
            SdPhase.PlayerCount, SdPhase.PlayerNames -> MusicTrack.HUB
            else -> MusicTrack.KALAMZ_ROUND_1
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
                SdPhase.PlayerCount -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SdPlayerCountScreen(onSelected = viewModel::setPlayerCount)
                }

                SdPhase.PlayerNames -> {
                    BackHandler { viewModel.navigateBack() }
                    SdPlayerNamesScreen(
                        state = state,
                        onNameChanged = viewModel::updatePlayerName,
                        onConfirm = viewModel::confirmNames,
                    )
                }

                is SdPhase.Reveal -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SdRevealScreen(
                        state = state,
                        index = phase.index,
                        shown = phase.shown,
                        onShow = viewModel::showRole,
                        onHide = viewModel::hideRoleAndNext,
                    )
                }

                SdPhase.Guess -> {
                    BackHandler { pendingExit = { onExitToHub() } }
                    SdGuessScreen(state = state, onGuess = viewModel::guessDozd)
                }

                is SdPhase.RoundResult -> {
                    BackHandler { viewModel.proceedAfterRound() }
                    SdRoundResultScreen(
                        state = state,
                        correct = phase.correct,
                        guessed = phase.guessed,
                        onProceed = viewModel::proceedAfterRound,
                    )
                }

                SdPhase.Winner -> {
                    BackHandler { pendingExit = { viewModel.playAgain(); onExitToHub() } }
                    SdWinnerScreen(
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
private fun SdPlayerCountScreen(onSelected: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(26.dp))
        BobbingEmoji(emoji = "👑", fontSize = 54.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "شاه دزد وزیر", rotation = -2f)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "نوستالژی خالص! نقش‌ها قرعه می‌خورن و وزیر باید دزد رو پیدا کنه",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))

        listOf(4, 5, 6, 7, 8).chunked(2).forEachIndexed { rowIndex, row ->
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
private fun SdPlayerNamesScreen(
    state: SdUiState,
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
            KButton(text = "قرعه‌کشی نقش‌ها! 🎲", onClick = onConfirm)
        }
    }
}

@Composable
private fun SdRevealScreen(
    state: SdUiState,
    index: Int,
    shown: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit,
) {
    val extras = kiExtras
    val accent = LocalGameAccent.current
    val role = state.roles.getOrNull(index) ?: SdRole.RAIAT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "راند ${state.roundIndex.toPersianDigits()} از ${state.totalRounds.toPersianDigits()}",
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
            BobbingEmoji(emoji = "🎲", fontSize = 60.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "کسی نبینه! نقش تو این راند اینجاست",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            KButton(text = "نقشم رو نشون بده 👀", onClick = onShow)
        } else {
            TicketCard(
                modifier = Modifier.fillMaxWidth(),
                accent = when (role) {
                    SdRole.SHAH -> extras.gold
                    SdRole.DOZD -> extras.danger
                    else -> accent
                },
                tilt = -1.5f
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = role.emoji, fontSize = 56.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "تو ${role.title} هستی!",
                        style = MaterialTheme.typography.displayMedium,
                        color = when (role) {
                            SdRole.SHAH -> extras.gold
                            SdRole.DOZD -> extras.danger
                            else -> accent
                        },
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (role) {
                            SdRole.SHAH -> "بعد از پخش نقش‌ها، خودتو معرفی کن و به وزیرت فرمان بده!"
                            SdRole.VAZIR -> "وقتی شاه فرمان داد، خودتو معرفی کن و دزد رو حدس بزن!"
                            SdRole.DOZD -> "هیس! عادی رفتار کن که وزیر بو نبره…"
                            SdRole.RAIAT -> "آروم بشین و از مچ‌گیری لذت ببر!"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            KButton(
                text = if (index + 1 >= state.playerCount) "قایم کن — شاه کیه؟ 👑" else "قایم کن و بده نفر بعد",
                style = KButtonStyle.Danger,
                onClick = onHide,
            )
        }
    }
}

@Composable
private fun SdGuessScreen(
    state: SdUiState,
    onGuess: (Int) -> Unit,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val suspects = (0 until state.playerCount).filter { it != state.shahIndex && it != state.vazirIndex }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        BobbingEmoji(emoji = "👑", fontSize = 44.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "شاه: ${state.playerDisplayName(state.shahIndex)}",
            style = MaterialTheme.typography.headlineMedium,
            color = extras.gold,
            fontWeight = FontWeight.Black,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "🎩 وزیر: ${state.playerDisplayName(state.vazirIndex)}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(14.dp))
        StickerTitle(text = "وزیر! دزد کیه؟", rotation = -2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "شاه فرمان داد: «وزیر، دزد رو پیدا کن!»\nوزیر یکی از مظنون‌ها رو انتخاب کنه:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            suspects.forEach { i ->
                val color = kiExtras.teamColors.teamColorFor(i)
                val interaction = remember { MutableInteractionSource() }
                Text(
                    text = "🦹 ${state.playerDisplayName(i)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(color, RoundedCornerShape(22.dp))
                        .clickable(interactionSource = interaction, indication = null) {
                            sound?.playButtonClick()
                            onGuess(i)
                        }
                        .padding(vertical = 14.dp),
                )
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SdRoundResultScreen(
    state: SdUiState,
    correct: Boolean,
    guessed: Int,
    onProceed: () -> Unit,
) {
    val extras = kiExtras

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (correct) "🎉" else "😜", fontSize = 64.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (correct) "وزیر مچ دزد رو گرفت!"
            else "دزدِ زرنگ در رفت!",
            style = MaterialTheme.typography.displayMedium,
            color = if (correct) extras.success else extras.danger,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "وزیر گفت ${state.playerDisplayName(guessed)} — " +
                "دزد واقعی: ${state.playerDisplayName(state.dozdIndex)}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                (0 until state.playerCount)
                    .sortedByDescending { state.scores[it] }
                    .forEach { i ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${state.roles[i].emoji} ${state.playerDisplayName(i)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = kiExtras.teamColors.teamColorFor(i),
                            )
                            Text(
                                text = state.scores[i].toPersianDigits(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        KButton(
            text = if (state.roundIndex >= state.totalRounds) "کی برد؟ 🏆" else "راند بعد!",
            onClick = onProceed,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
private fun SdWinnerScreen(
    state: SdUiState,
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
            Text(text = "🏆", fontSize = 80.sp)
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
                        .sortedByDescending { state.scores[it] }
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
                                    text = state.scores[i].toPersianDigits(),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            KButton(text = "دوباره بازی کنیم!", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", onClick = onExitToHub, style = KButtonStyle.Glass)
        }
    }
}
