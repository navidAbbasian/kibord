package com.navidabbasian.kibord.games.gandegoo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.theme.LocalGameAccent
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.gandegoo.model.GandeGooUiState
import com.navidabbasian.kibord.games.gandegoo.model.GgCell

/** جدول کتگوری‌ها: هر ردیف یک کتگوری با سه خانه‌ی امتیازی */
@Composable
fun GgBoardScreen(
    state: GandeGooUiState,
    onCellSelected: (GgCell) -> Unit,
) {
    val sound = LocalSoundManager.current
    val accent = LocalGameAccent.current
    val teamColors = kiExtras.teamColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // ---- امتیاز تیم‌ها ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            repeat(state.teamCount) { i ->
                val color = teamColors.getOrElse(i) { teamColors[0] }
                val isPicking = i == state.pickingTeam
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            color.copy(alpha = if (isPicking) 1f else 0.55f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = state.teamDisplayName(i),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                    Text(
                        text = state.scores[i].toPersianDigits(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                }
            }
        }

        Text(
            text = "نوبت انتخاب: ${state.teamDisplayName(state.pickingTeam)}",
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(count = state.categories.size, key = { state.categories[it].id }) { catIndex ->
                val category = state.categories[catIndex]
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp,
                    tilt = if (catIndex % 2 == 0) -1f else 1f
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = category.emoji, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            category.questions.forEachIndexed { qIndex, question ->
                                val cell = GgCell(catIndex, qIndex)
                                val used = cell in state.usedCells
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (used) kiExtras.glass else accent.copy(alpha = 0.85f))
                                        .then(
                                            if (used) Modifier
                                            else Modifier.clickable {
                                                sound?.playButtonClick()
                                                onCellSelected(cell)
                                            }
                                        )
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = question.points.toPersianDigits(),
                                        color = if (used) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        } else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        textDecoration = if (used) TextDecoration.LineThrough else null
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
