package com.navidabbasian.kibord.games.gandegoo.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.core.ui.components.ChoiceBubble
import androidx.compose.foundation.layout.offset
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.games.gandegoo.model.GandeGooUiState
import com.navidabbasian.kibord.games.gandegoo.model.GgCategory
import com.navidabbasian.kibord.games.gandegoo.model.GgMode

/** انتخاب تعداد تیم‌ها: ۲ یا ۳ تیم دونفره */
@Composable
fun GgTeamCountScreen(onTeamCountSelected: (Int) -> Unit) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        BobbingEmoji(emoji = "🎭", fontSize = 60.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "چند تیم هستید؟", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "هر تیم دو نفره است",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            listOf(2 to "۴ نفر", 3 to "۶ نفر").forEachIndexed { i, (count, people) ->
                ChoiceBubble(
                    main = count.toPersianDigits(),
                    sub = "تیم — $people",
                    size = 148.dp,
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.5f,
                    modifier = Modifier.offset(y = if (i % 2 == 0) 0.dp else 30.dp),
                    onClick = { onTeamCountSelected(count) }
                )
            }
        }
    }
}

/** ورود نام تیم‌ها */
@Composable
fun GgTeamNamesScreen(
    teamCount: Int,
    teamNames: List<String>,
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
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "نام تیم‌ها",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "چه اسمی روی خودتون می‌ذارید؟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        repeat(teamCount) { i ->
            val color = teamColors.getOrElse(i) { teamColors[0] }
            BlobTextField(
                value = teamNames.getOrElse(i) { "" },
                onValueChange = { onNameChanged(i, it.take(20)) },
                placeholder = "تیم ${(i + 1).toPersianDigits()}",
                color = color,
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
            KButton(text = "ادامه", onClick = onConfirm)
        }
    }
}

/** انتخاب حالت بازی: کامل (سه سطح امتیازی) یا سریع (فقط ۲۰ امتیازی) */
@Composable
fun GgModeScreen(onModeSelected: (GgMode) -> Unit) {
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        BobbingEmoji(emoji = "🎛️", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(10.dp))
        StickerTitle(text = "چطوری بازی کنیم؟", rotation = -2f)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "کامل با هر سه سطح امتیازی، سریع فقط با سوال‌های ۲۰ امتیازی",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally)
        ) {
            listOf(
                Triple(GgMode.FULL, "کامل", "هر دسته ۳ سوال\n۲۰ / ۴۰ / ۶۰ امتیازی"),
                Triple(GgMode.QUICK, "سریع", "هر دسته فقط\nسوال ۲۰ امتیازی"),
            ).forEachIndexed { i, (mode, title, sub) ->
                ChoiceBubble(
                    main = title,
                    sub = sub,
                    emoji = if (mode == GgMode.FULL) "🏆" else "⚡",
                    size = 152.dp,
                    mainFontSize = 26.sp,
                    accent = teamColors[(i * 2 + 1) % teamColors.size],
                    tilt = if (i % 2 == 0) -3f else 3f,
                    phase = i * 1.5f,
                    modifier = Modifier.offset(y = if (i % 2 == 0) 0.dp else 30.dp),
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

/** انتخاب دستی دسته‌های بازی: جستجو در کل بانک و برداشتن هر تعداد دلخواه (حداقل ۲) */
@Composable
fun GgSetupScreen(
    availableCategories: List<GgCategory>,
    chosenIds: Set<String>,
    cellsPerCategory: Int,
    onToggleCategory: (String) -> Unit,
    onStart: () -> Unit,
) {
    val accent = LocalGameAccent.current
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, availableCategories) {
        val q = query.trim()
        if (q.isEmpty()) availableCategories
        else availableCategories.filter { q in it.name }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        BobbingEmoji(emoji = "🧮", fontSize = 38.sp)
        Spacer(modifier = Modifier.height(6.dp))
        StickerTitle(text = "دسته‌ها رو انتخاب کن", rotation = 2f, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (cellsPerCategory == 1) "حالت سریع: هر دسته یک سوال ۲۰ امتیازی داره — هر چندتا خواستی بردار"
            else "هر دسته سه سوال ۲۰، ۴۰ و ۶۰ امتیازی داره — هر چندتا خواستی بردار",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))

        BlobTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "جستجو بین دسته‌ها…",
            badge = "🔍",
            tilt = -0.8f,
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ---- شمارنده‌ی انتخاب ----
        Text(
            text = if (chosenIds.isEmpty()) "هنوز دسته‌ای انتخاب نشده"
            else "${chosenIds.size.toPersianDigits()} دسته — ${(chosenIds.size * cellsPerCategory).toPersianDigits()} خانه‌ی امتیازی",
            style = MaterialTheme.typography.labelLarge,
            color = if (chosenIds.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            modifier = Modifier
                .graphicsLayer { rotationZ = -1f }
                .background(
                    if (chosenIds.isEmpty()) kiExtras.glass else accent.copy(alpha = 0.15f),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 14.dp, vertical = 5.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(count = filtered.size, key = { filtered[it].id }) { i ->
                val category = filtered[i]
                GgCategoryChip(
                    category = category,
                    selected = category.id in chosenIds,
                    index = i,
                    onToggle = { onToggleCategory(category.id) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp)
        ) {
            val enough = chosenIds.size >= GandeGooUiState.MIN_CATEGORIES
            KButton(
                text = if (enough) "بریم بازی!" else "حداقل ۲ دسته انتخاب کن",
                onClick = onStart,
                enabled = enough,
            )
        }
    }
}

/** تراشه‌ی دسته: سنگریزه‌ی شیشه‌ای که با انتخاب به رنگ بازی درمی‌آید */
@Composable
private fun GgCategoryChip(
    category: GgCategory,
    selected: Boolean,
    index: Int,
    onToggle: () -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val extras = kiExtras
    val shape = blobShape(seed = category.id.hashCode())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .graphicsLayer { rotationZ = if (index % 2 == 0) -1.2f else 1.2f }
            .background(
                if (selected) Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.12f), accent))
                else Brush.verticalGradient(listOf(extras.glassStrong, extras.glassStrong)),
                shape
            )
            .border(
                2.dp,
                if (selected) Color.White.copy(alpha = 0.55f) else extras.glassBorder,
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                sound?.playButtonClick()
                onToggle()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = category.emoji, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 2,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(text = "✔", fontSize = 15.sp, color = Color.White)
        }
    }
}
