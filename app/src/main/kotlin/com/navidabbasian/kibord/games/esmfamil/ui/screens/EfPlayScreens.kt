package com.navidabbasian.kibord.games.esmfamil.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.ConfettiOverlay
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.ShareWinButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.rememberMorphingBlobShape
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.esmfamil.model.EfAnswer
import com.navidabbasian.kibord.games.esmfamil.model.JUDGE_SCORES
import com.navidabbasian.kibord.games.esmfamil.model.PERSIAN_LETTERS
import com.navidabbasian.kibord.games.esmfamil.model.sameName
import com.navidabbasian.kibord.games.esmfamil.viewmodel.EsmFamilUiState

/** ردیف امتیاز کل بازیکن‌ها — بالای صفحه‌های میان‌بازی */
@Composable
private fun EfScoreStrip(state: EsmFamilUiState) {
    val snapshot = state.snapshot
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        snapshot.players.forEach { p ->
            val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer { alpha = if (p.connected) 1f else 0.4f }
            ) {
                Text(
                    text = p.totalScore.toPersianDigits(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            Brush.radialGradient(listOf(lerp(color, Color.White, 0.2f), color)),
                            blobShape(seed = p.name.hashCode())
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    text = p.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

/** انتخاب حرف: کاشی‌های تایپوگرافیک الفبا برای نوبت‌دار، انتظار برای بقیه */
@Composable
fun EfLetterScreen(
    state: EsmFamilUiState,
    onPickLetter: (String) -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        EfScoreStrip(state)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "راند ${snapshot.roundIndex.toPersianDigits()} از ${snapshot.settings.totalRounds.toPersianDigits()}",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier
                .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (state.myTurnToPick) {
            StickerTitle(text = "یه حرف انتخاب کن!", rotation = -2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(count = PERSIAN_LETTERS.size, key = { PERSIAN_LETTERS[it] }) { i ->
                    val letter = PERSIAN_LETTERS[i]
                    val used = letter in snapshot.usedLetters
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .then(
                                if (!used) Modifier.breathing(intensity = 0.03f, periodMs = 2400, phase = i * 0.4f)
                                else Modifier
                            )
                            .graphicsLayer { rotationZ = if (i % 2 == 0) -2f else 2f }
                            .background(
                                if (used) Brush.verticalGradient(listOf(extras.glass, extras.glass))
                                else Brush.radialGradient(listOf(lerp(accent, Color.White, 0.2f), accent)),
                                blobShape(seed = i)
                            )
                            .border(
                                1.5.dp,
                                if (used) extras.glassBorder else Color.White.copy(alpha = 0.5f),
                                blobShape(seed = i)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !used,
                            ) {
                                sound?.playButtonClick()
                                onPickLetter(letter)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = if (used) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            else Color.White,
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(40.dp))
            BobbingEmoji(emoji = "🤔", fontSize = 56.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "${snapshot.pickerName} داره حرف این راند رو انتخاب می‌کنه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "آماده باش! تا حرف اومد، تایمر می‌ره",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** اعلام حرف راند: شمارش معکوس ۵ ثانیه‌ای پیش از باز شدن فرم */
@Composable
fun EfCountdownScreen(state: EsmFamilUiState) {
    val accent = LocalGameAccent.current
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot

    LaunchedEffect(snapshot.secondsLeft) {
        sound?.playTimerWarning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StickerTitle(text = "آماده شید!", rotation = -2f)
        Spacer(modifier = Modifier.height(24.dp))

        // ---- کاشی بزرگ حرف راند ----
        Box(
            modifier = Modifier
                .size(160.dp)
                .breathing(intensity = 0.05f, periodMs = 1200)
                .graphicsLayer { rotationZ = -4f }
                .background(
                    Brush.radialGradient(listOf(lerp(accent, Color.White, 0.2f), accent)),
                    rememberMorphingBlobShape()
                )
                .border(4.dp, Color.White.copy(alpha = 0.6f), rememberMorphingBlobShape()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = snapshot.currentLetter,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "همه‌چیز با حرف «${snapshot.currentLetter}»!",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(30.dp))

        // ---- شماره‌ی معکوس با پرش فنری ----
        PhaseTransition(key = snapshot.secondsLeft) {
            Text(
                text = snapshot.secondsLeft.toPersianDigits(),
                fontSize = 96.sp,
                fontWeight = FontWeight.Black,
                color = accent,
            )
        }
    }
}

/** فرم راند: حرف بزرگ + تایمر + یک فیلد بلابی برای هر موضوع + استپ */
@Composable
fun EfPlayScreen(
    state: EsmFamilUiState,
    onAnswerChanged: (topic: String, text: String) -> Unit,
    onStop: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val snapshot = state.snapshot
    val seconds = snapshot.secondsLeft
    val urgent = seconds <= 10

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val progress by animateFloatAsState(
        targetValue = if (snapshot.settings.roundSeconds == 0) 0f
        else seconds.toFloat() / snapshot.settings.roundSeconds,
        animationSpec = tween(300),
        label = "ef_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ---- کاشی حرف راند ----
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .graphicsLayer { rotationZ = -4f }
                    .background(
                        Brush.radialGradient(listOf(lerp(accent, Color.White, 0.18f), accent)),
                        rememberMorphingBlobShape()
                    )
                    .border(2.5.dp, Color.White.copy(alpha = 0.55f), rememberMorphingBlobShape()),
                contentAlignment = Alignment.Center
            ) {
                Text(text = snapshot.currentLetter, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
            Spacer(modifier = Modifier.width(18.dp))
            // ---- تایمر ----
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .then(if (urgent) Modifier.breathing(intensity = 0.06f, periodMs = 600) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    drawArc(
                        color = extras.glassStrong,
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (urgent) extras.danger else extras.gold,
                        startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = seconds.toPersianDigits(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (urgent) extras.danger else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "هر چی می‌دونی با «${snapshot.currentLetter}» بنویس!",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))

        // ---- فرم موضوعات: اینترِ کیبورد به خانه‌ی بعدی می‌پرد ----
        val topics = snapshot.settings.topics
        val focusManager = LocalFocusManager.current
        val focusRequesters = remember(topics) { topics.map { FocusRequester() } }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            topics.forEachIndexed { i, topic ->
                val last = i == topics.lastIndex
                BlobTextField(
                    value = state.myAnswers[topic].orEmpty(),
                    onValueChange = { onAnswerChanged(topic, it) },
                    placeholder = topic,
                    badge = topic.take(1),
                    tilt = if (i % 2 == 0) -0.7f else 0.7f,
                    phase = i * 1.2f,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (last) ImeAction.Done else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { runCatching { focusRequesters[i + 1].requestFocus() } },
                        onDone = { focusManager.clearFocus() },
                    ),
                    focusRequester = focusRequesters[i],
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp)
        ) {
            KButton(
                text = if (state.allMyFieldsFilled) "استپ! ✋" else "همه رو بنویس تا استپ فعال شه",
                style = KButtonStyle.Danger,
                enabled = state.allMyFieldsFilled,
                onClick = onStop,
            )
        }
    }
}

/** فاز اعتراض: ۳۰ ثانیه بررسی جمعی جواب‌ها — «اعتراضی ندارم» همه، زودتر می‌بندد */
@Composable
fun EfReviewScreen(
    state: EsmFamilUiState,
    onVote: (topic: String, owner: String, reject: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val snapshot = state.snapshot
    val doneCount = snapshot.reviewDone.size
    val connectedCount = snapshot.players.count { it.connected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        StickerTitle(text = "اعتراض — حرف «${snapshot.currentLetter}»", rotation = 2f, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (snapshot.stopperName.isBlank()) "وقت تموم شد ⏰"
            else "${snapshot.stopperName} استپ زد ✋",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (snapshot.answers.isEmpty()) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.breathing(intensity = 0.06f, periodMs = 1400)) {
                Text(text = "📨", fontSize = 44.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "در حال جمع‌آوری جواب‌های همه…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // بی‌عجله اعتراض کنید — راند وقتی بسته می‌شود که همه نظرشان را ثبت کنند
            val roundTotals = snapshot.answers
                .groupBy { it.player }
                .mapValues { entry -> entry.value.sumOf { it.score } }
            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(count = snapshot.settings.topics.size, key = { snapshot.settings.topics[it] }) { t ->
                    val topic = snapshot.settings.topics[t]
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        tilt = if (t % 2 == 0) -0.8f else 0.8f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "🗂 $topic",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            snapshot.answers.filter { it.topic == topic }.forEach { answer ->
                                EfAnswerRow(
                                    answer = answer,
                                    state = state,
                                    onVote = { reject -> onVote(topic, answer.player, reject) },
                                )
                            }
                        }
                    }
                }

                // ---- کارت امتیاز: جمع همین راند برای هر بازیکن ----
                item(key = "round_score_card") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        strong = true,
                        tilt = if (snapshot.settings.topics.size % 2 == 0) -0.8f else 0.8f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "💯 امتیاز",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = extras.gold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            snapshot.players.forEach { p ->
                                val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
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
                                        color = color,
                                    )
                                    Text(
                                        text = "+${(roundTotals[p.name] ?: 0).toPersianDigits()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = extras.success,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.iAmDoneReviewing) {
                KButton(
                    text = "منتظر بقیه… (${doneCount.toPersianDigits()} از ${connectedCount.toPersianDigits()})",
                    enabled = false,
                    onClick = {},
                )
            } else {
                val myRejects = snapshot.answers.count { a ->
                    a.rejectVotes.any { sameName(it, state.myName) }
                }
                KButton(
                    text = "اعتراضی ندارم ✋",
                    enabled = snapshot.answers.isNotEmpty(),
                    onClick = onDone,
                )
                Spacer(modifier = Modifier.height(8.dp))
                KButton(
                    text = if (myRejects > 0) "ثبت اعتراض 🚫 (${myRejects.toPersianDigits()} مورد)"
                    else "ثبت اعتراض 🚫",
                    style = KButtonStyle.Danger,
                    enabled = snapshot.answers.isNotEmpty() && myRejects > 0,
                    onClick = onDone,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "به کلمه‌ی مشکوک 🚫 بزن و «ثبت اعتراض» رو بزن؛ همه که نظر دادن راند بسته می‌شه",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** فاز داوری: میزبان اعتراض‌ها را می‌بیند و برای هر جواب حکم امتیازی می‌دهد */
@Composable
fun EfJudgeScreen(
    state: EsmFamilUiState,
    onSetScore: (topic: String, owner: String, score: Int) -> Unit,
    onProceed: () -> Unit,
) {
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot
    // فقط موضوع‌هایی که جوابِ اعتراض‌خورده دارند نمایش داده می‌شوند
    val objectedTopics = snapshot.settings.topics.filter { topic ->
        snapshot.answers.any { it.topic == topic && it.rejectVotes.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        BobbingEmoji(emoji = "⚖️", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(6.dp))
        StickerTitle(text = "داوری میزبان", rotation = -2f, fontSize = 24.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (state.isHost) "برای هر جواب اعتراض‌خورده حکم بده: ۰، ۵، ۱۰ یا ۲۰"
            else "${snapshot.hostName} داره اعتراض‌ها رو بررسی می‌کنه…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(count = objectedTopics.size, key = { objectedTopics[it] }) { t ->
                val topic = objectedTopics[t]
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    strong = true,
                    borderColor = extras.warning.copy(alpha = 0.6f),
                    tilt = if (t % 2 == 0) -0.8f else 0.8f
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            text = "🗂 $topic",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        snapshot.answers
                            .filter { it.topic == topic && it.rejectVotes.isNotEmpty() }
                            .forEach { answer ->
                                val owner = snapshot.player(answer.player)
                                val color = kiExtras.teamColors.teamColorFor(owner?.colorIndex ?: 0)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = answer.player,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = color,
                                            )
                                            Text(
                                                text = answer.text.ifBlank { "—" },
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    textDecoration = if (answer.rejected) TextDecoration.LineThrough
                                                    else TextDecoration.None
                                                ),
                                                color = if (answer.rejected)
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                        Text(
                                            text = "🚫 ${answer.rejectVotes.size.toPersianDigits()}",
                                            fontSize = 13.sp,
                                            color = extras.danger,
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = answer.score.toPersianDigits(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier
                                                .background(
                                                    if (answer.rejected) extras.danger else extras.success,
                                                    RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (state.isHost) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        // ---- حکم میزبان: هر چهار حالت امتیاز ----
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "حکم:",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            JUDGE_SCORES.forEach { option ->
                                                val optionColor = when (option) {
                                                    0 -> extras.danger
                                                    5 -> extras.warning
                                                    10 -> extras.success
                                                    else -> extras.gold
                                                }
                                                val selected = answer.score == option
                                                Text(
                                                    text = option.toPersianDigits(),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (selected) Color.White
                                                    else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .background(
                                                            if (selected) optionColor else extras.glass,
                                                            RoundedCornerShape(50)
                                                        )
                                                        .border(
                                                            1.5.dp,
                                                            if (selected) Color.White.copy(alpha = 0.5f)
                                                            else optionColor.copy(alpha = 0.6f),
                                                            RoundedCornerShape(50)
                                                        )
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                        ) {
                                                            sound?.playButtonClick()
                                                            onSetScore(topic, answer.player, option)
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp)
        ) {
            if (state.isHost) {
                KButton(text = "تایید و ادامه", onClick = onProceed)
            } else {
                Text(
                    text = "منتظر حکم میزبان…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EfAnswerRow(
    answer: EfAnswer,
    state: EsmFamilUiState,
    onVote: (reject: Boolean) -> Unit,
) {
    val extras = kiExtras
    val sound = LocalSoundManager.current
    val snapshot = state.snapshot
    val owner = snapshot.player(answer.player)
    val color = kiExtras.teamColors.teamColorFor(owner?.colorIndex ?: 0)
    val iVotedReject = answer.rejectVotes.any { sameName(it, state.myName) }
    val mine = sameName(answer.player, state.myName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = answer.player,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.width(74.dp),
        )
        Text(
            text = answer.text.ifBlank { "—" },
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (answer.rejected) TextDecoration.LineThrough else TextDecoration.None
            ),
            color = if (answer.rejected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        // ---- امتیاز ----
        val scoreColor = when {
            answer.score >= 20 -> extras.gold
            answer.score >= 10 -> extras.success
            answer.score >= 5 -> extras.warning
            else -> extras.danger
        }
        Text(
            text = answer.score.toPersianDigits(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier
                .background(scoreColor, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        // ---- رای رد ----
        if (!mine && answer.text.isNotBlank()) {
            Text(
                text = if (answer.rejectVotes.isEmpty()) "🚫"
                else "🚫 ${answer.rejectVotes.size.toPersianDigits()}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (iVotedReject) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        if (iVotedReject) extras.danger else extras.glass,
                        RoundedCornerShape(50)
                    )
                    .border(1.dp, extras.glassBorder, RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        sound?.playButtonClick()
                        onVote(!iVotedReject)
                    }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            )
        } else if (answer.rejectVotes.isNotEmpty()) {
            Text(
                text = "🚫 ${answer.rejectVotes.size.toPersianDigits()}",
                fontSize = 13.sp,
                color = extras.danger,
            )
        }
    }
}

/** نتیجه‌ی راند: امتیاز این راند + جمع کل */
@Composable
fun EfRoundResultScreen(
    state: EsmFamilUiState,
    onProceed: () -> Unit,
) {
    val extras = kiExtras
    val snapshot = state.snapshot
    val isLast = snapshot.roundIndex >= snapshot.settings.totalRounds || snapshot.remainingLetters.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BobbingEmoji(emoji = "📊", fontSize = 50.sp)
        Spacer(modifier = Modifier.height(8.dp))
        StickerTitle(
            text = "نتیجه‌ی راند ${snapshot.roundIndex.toPersianDigits()}",
            rotation = -2f,
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        GlassCard(modifier = Modifier.fillMaxWidth(), strong = true) {
            Column(modifier = Modifier.padding(18.dp)) {
                // راند آخر: مجموع‌ها مخفی و ترتیب هم لو نمی‌دهد — هیجان تا لحظه‌ی «کی برد؟»
                val rows = if (isLast) snapshot.players
                else snapshot.players.sortedByDescending { it.totalScore }
                rows.forEach { p ->
                    val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
                    val roundScore = snapshot.roundScores[p.name] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = p.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "+${roundScore.toPersianDigits()}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = extras.success,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            if (isLast) {
                                Box(modifier = Modifier.breathing(intensity = 0.06f, periodMs = 1400, phase = p.colorIndex * 1.1f)) {
                                    Text(
                                        text = "؟",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Black,
                                        color = extras.gold,
                                    )
                                }
                            } else {
                                Text(
                                    text = p.totalScore.toPersianDigits(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLast) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "مجموع امتیازها مخفیه… 🤫 بزن ببین کی برد!",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = extras.gold,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        if (state.isHost) {
            KButton(
                text = if (isLast) "کی برد؟ 🏆" else "راند بعد!",
                onClick = onProceed,
                modifier = Modifier.navigationBarsPadding(),
            )
        } else {
            Text(
                text = "منتظر میزبان برای ادامه…",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** اعلام برنده با بارش کاغذرنگی */
@Composable
fun EfWinnerScreen(
    state: EsmFamilUiState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val snapshot = state.snapshot
    val max = snapshot.players.maxOfOrNull { it.totalScore } ?: 0
    val winners = snapshot.players.filter { it.totalScore == max }

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
                text = winners.joinToString(" و ") { it.name },
                style = MaterialTheme.typography.displayMedium,
                color = kiExtras.teamColors.teamColorFor(winners.firstOrNull()?.colorIndex ?: 0),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    snapshot.players.sortedByDescending { it.totalScore }.forEachIndexed { rank, p ->
                        val color = kiExtras.teamColors.teamColorFor(p.colorIndex)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (p.totalScore == max) "🥇" else (rank + 1).toPersianDigits(),
                                    fontSize = 18.sp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = p.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                )
                            }
                            Text(
                                text = p.totalScore.toPersianDigits(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            ShareWinButton(
                gameId = "esm_famil",
                gameTitle = "اسم فامیل",
                gameEmoji = "✍️",
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
