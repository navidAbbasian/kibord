package com.navidabbasian.kibord.games.backgammon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.ExitConfirmDialog
import com.navidabbasian.kibord.core.ui.components.GameHelpButton
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.backgammon.engine.BgMove
import com.navidabbasian.kibord.games.backgammon.engine.BgPhase
import com.navidabbasian.kibord.games.backgammon.engine.BgPlayer
import com.navidabbasian.kibord.games.backgammon.engine.BgState
import com.navidabbasian.kibord.games.backgammon.engine.BgVariant
import com.navidabbasian.kibord.games.backgammon.engine.relToAbs

/** اسم نمایشی هر بازیکن روی این گوشی */
private fun playerName(p: BgPlayer): String =
    if (p == BgPlayer.WHITE) "بازیکن ۱" else "بازیکن ۲"

/** اسم فارسی نتیجه: تکی، مارس، مارس کامل */
private fun resultName(score: Int): String = when (score) {
    3 -> "مارس کامل"
    2 -> "مارس"
    else -> "تکی"
}

/** ریشه‌ی بازی تخته‌نرد — سه روش، دو نفره روی یک گوشی */
@Composable
fun BackgammonGame(
    onExitToHub: () -> Unit,
    viewModel: BackgammonViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val sound = LocalSoundManager.current

    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { event ->
            when (event) {
                BgSoundEvent.DICE -> {
                    sound?.playDorNextTurn()
                    sound?.vibrate(40)
                }
                BgSoundEvent.MOVE -> sound?.playButtonClick()
                BgSoundEvent.HIT -> {
                    sound?.playWordSkip()
                    sound?.vibrate(80)
                }
                BgSoundEvent.BEAR_OFF -> sound?.playCorrectWord()
                BgSoundEvent.SKIP -> sound?.playTimerEnd()
                BgSoundEvent.WIN -> {
                    sound?.playGameOver()
                    sound?.vibrate(200)
                }
            }
        }
    }

    LaunchedEffect(Unit) { sound?.stopBackgroundMusic() }

    KiBackground {
        ExitConfirmDialog(
            visible = pendingExit != null,
            onConfirm = { pendingExit?.invoke(); pendingExit = null },
            onDismiss = { pendingExit = null },
        )
        val game = state.game
        PhaseTransition(key = state.stage to (game?.phase == BgPhase.FINISHED)) {
            when {
                state.stage == BgStage.VariantSelect -> {
                    BackHandler { onExitToHub() }
                    BgVariantSelectScreen(onPick = viewModel::chooseVariant)
                }

                game != null && game.phase == BgPhase.FINISHED -> {
                    BackHandler { viewModel.backToVariants(); onExitToHub() }
                    BgWinnerScreen(
                        game = game,
                        onPlayAgain = viewModel::playAgain,
                        onExitToHub = { viewModel.backToVariants(); onExitToHub() },
                    )
                }

                game != null -> {
                    BackHandler { pendingExit = { viewModel.backToVariants(); onExitToHub() } }
                    BgPlayScreen(state = state, game = game, viewModel = viewModel)
                }
            }
        }
    }
}

/** صفحه‌ی انتخاب روش: کلاسیک، هلندی، هایپرگامون */
@Composable
private fun BgVariantSelectScreen(onPick: (BgVariant) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        GameHelpButton(gameId = "backgammon", modifier = Modifier.align(Alignment.TopStart))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            BobbingEmoji(emoji = "🎲", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = "تخته‌نرد")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "کدوم روش رو بازی می‌کنید؟",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            BgVariantCard(
                emoji = "🏛️",
                title = "تخته‌نرد کلاسیک",
                desc = "همون تخته‌ی همیشگی: چیدمان استاندارد، زدن و بستن و مارس!",
                onClick = { onPick(BgVariant.STANDARD) },
            )
            Spacer(modifier = Modifier.height(14.dp))
            BgVariantCard(
                emoji = "🌷",
                title = "تخته‌نرد هلندی",
                desc = "صفحه خالیه! اول باید هر ۱۵ مهره رو وارد کنی و تا مهره‌ای به خونه‌ت نرسه، حق زدن نداری.",
                onClick = { onPick(BgVariant.DUTCH) },
            )
            Spacer(modifier = Modifier.height(14.dp))
            BgVariantCard(
                emoji = "⚡",
                title = "هایپرگامون",
                desc = "فقط ۳ مهره برای هر نفر — کوتاه، تند و پرهیجان!",
                onClick = { onPick(BgVariant.HYPER) },
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BgVariantCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        strong = true,
        onClick = { sound?.playButtonClick(); onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** صفحه‌ی اصلی بازی: نشان نوبت، صفحه‌ی تخته، تاس‌ها و پیام‌ها */
@Composable
private fun BgPlayScreen(
    state: BgUiState,
    game: BgState,
    viewModel: BackgammonViewModel,
) {
    val teamColors = kiExtras.teamColors
    val whiteColor = teamColors.getOrElse(0) { Color(0xFFF2E9DC) }
    val blackColor = teamColors.getOrElse(1) { Color(0xFF54423A) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // ---- نشان بازیکن‌ها و نوبت ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BgPlayerChip(
                player = BgPlayer.WHITE,
                color = whiteColor,
                game = game,
                isTurn = game.turn == BgPlayer.WHITE && game.phase != BgPhase.OPENING_ROLL,
            )
            BgPlayerChip(
                player = BgPlayer.BLACK,
                color = blackColor,
                game = game,
                isTurn = game.turn == BgPlayer.BLACK && game.phase != BgPhase.OPENING_ROLL,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---- صفحه‌ی تخته ----
        BackgammonBoard(
            state = game,
            sourcesAbs = state.sourcesAbs,
            selectedAbs = state.selectedSource?.let { sel ->
                if (sel == BgMove.ENTRY) null else game.turn?.let { relToAbs(it, sel) }
            },
            destsAbs = state.destsAbs,
            offIsDest = state.offIsDest,
            whiteColor = whiteColor,
            blackColor = blackColor,
            onTapPoint = viewModel::tapPoint,
            onTapEntry = viewModel::tapEntry,
            onTapOff = viewModel::tapOff,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ---- ناحیه‌ی تاس و پیام ----
        when {
            state.skipMessage != null -> {
                GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = state.skipMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        KButton(text = "باشه، نوبت بعدی", onClick = viewModel::confirmSkip)
                    }
                }
            }

            game.phase == BgPhase.OPENING_ROLL -> BgOpeningArea(game, viewModel)

            game.phase == BgPhase.ROLLING -> {
                Text(
                    text = "نوبت ${playerName(game.turn ?: BgPlayer.WHITE)}ه — تاس بریز!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(10.dp))
                KButton(text = "تاس بریز 🎲", onClick = viewModel::rollDice)
            }

            game.phase == BgPhase.MOVING -> {
                BgDiceRow(dice = game.dice, remaining = game.remainingDice)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.entryIsSource) {
                        "باید مهره وارد کنی — یه خونه‌ی سبز رو لمس کن"
                    } else {
                        "یه مهره‌ت رو انتخاب کن، بعد خونه‌ی سبز رو بزن"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** ناحیه‌ی پرتاب شروع: هر بازیکن یک تاس؛ مساوی یعنی تکرار */
@Composable
private fun BgOpeningArea(game: BgState, viewModel: BackgammonViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val w = game.openingDieWhite
        val b = game.openingDieBlack
        if (w != null && b != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BgDieFace(value = w, used = false)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "مقابل", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(10.dp))
                BgDieFace(value = b, used = false)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        val message = when {
            game.openingTie -> "مساوی شد! دوباره تاس بریزید"
            else -> "هر بازیکن یه تاس می‌ندازه — بالاتر شروع می‌کنه"
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(10.dp))
        KButton(text = "تاس بریز 🎲", onClick = viewModel::rollOpening)
    }
}

/** نشان یک بازیکن: رنگ، اسم، مهره‌های بیرون و بار و خارج‌شده */
@Composable
private fun BgPlayerChip(player: BgPlayer, color: Color, game: BgState, isTurn: Boolean) {
    GlassCard(strong = isTurn, cornerRadius = 18.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(color, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = playerName(player) + if (isTurn) " — نوبتشه" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val parts = buildList {
                if (game.offBoard(player) > 0) add("بیرون: ${game.offBoard(player).toPersianDigits()}")
                if (game.bar(player) > 0) add("بار: ${game.bar(player).toPersianDigits()}")
                add("خارج‌شده: ${game.borneOff(player).toPersianDigits()}")
            }
            Text(
                text = parts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** ردیف تاس‌های نوبت: مصرف‌شده‌ها کم‌رنگ می‌شوند؛ جفت چهار مصرف دارد */
@Composable
private fun BgDiceRow(dice: List<Int>, remaining: List<Int>) {
    val faces = if (dice.size == 2 && dice[0] == dice[1]) List(4) { dice[0] } else dice
    val remainingCount = remaining.toMutableList()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        faces.forEach { value ->
            val stillThere = remainingCount.remove(value)
            BgDieFace(value = value, used = !stillThere)
        }
    }
}

/** یک تاس: مربع گرد با رقم فارسی درشت */
@Composable
private fun BgDieFace(value: Int, used: Boolean) {
    val extras = kiExtras
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(
                if (used) extras.glass else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toPersianDigits(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = if (used) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** صفحه‌ی برنده: نتیجه (تکی/مارس/مارس کامل)، پز دادن و بازی دوباره */
@Composable
private fun BgWinnerScreen(
    game: BgState,
    onPlayAgain: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val winner = game.winner ?: return
    Box(modifier = Modifier.fillMaxSize()) {
        ConfettiOverlay()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            BobbingEmoji(emoji = "🏆", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StickerTitle(text = "${playerName(winner)} برد!")
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "نتیجه: ${resultName(game.resultScore)} (${game.resultScore.toPersianDigits()} امتیاز)",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = "backgammon",
                gameTitle = "تخته‌نرد",
                gameEmoji = "🎲",
                winnerText = playerName(winner),
                scoreLines = listOf(BgPlayer.WHITE, BgPlayer.BLACK).map { p ->
                    playerName(p) to "${game.borneOff(p).toPersianDigits()} مهره خارج"
                },
                winnerNames = listOf(playerName(winner)),
            )
            Spacer(modifier = Modifier.height(12.dp))
            KButton(text = "دوباره بازی 🔁", onClick = onPlayAgain)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "بازگشت به خانه", style = KButtonStyle.Glass, onClick = onExitToHub)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
