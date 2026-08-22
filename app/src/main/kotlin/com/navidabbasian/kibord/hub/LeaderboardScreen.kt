package com.navidabbasian.kibord.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.cloud.AccountRepository
import com.navidabbasian.kibord.core.cloud.Cloud
import com.navidabbasian.kibord.core.cloud.CloudResult
import com.navidabbasian.kibord.core.cloud.GameLeaderboardRow
import com.navidabbasian.kibord.core.cloud.StatsSync
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

/** بازی‌هایی که حالت اینترنتی دارند — فقط همین‌ها آمار آنلاین می‌سازند */
val onlineGameIds = listOf("nofoozi", "mafia", "esm_famil", "backgammon")

/**
 * لیدربورد هر بازی: بازیکن‌هایی با بیشترین برد.
 * فقط بازی‌های اینترنتی شمرده می‌شوند؛ اسم‌ها همان یوزرنیم‌های حساب‌اند.
 */
@Composable
fun LeaderboardScreen(onBack: () -> Unit, initialGameId: String? = null) {
    val extras = kiExtras
    val allGames = remember { gameCatalog + moreGamesCatalog }
    val games = remember { onlineGameIds.mapNotNull { id -> allGames.firstOrNull { it.id == id } } }
    var selected by rememberSaveable { mutableStateOf(initialGameId?.takeIf { it in onlineGameIds } ?: onlineGameIds.first()) }
    var rows by remember { mutableStateOf<List<GameLeaderboardRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val myId = remember { AccountRepository.currentUserId() }

    BackHandler { onBack() }

    LaunchedEffect(selected) {
        rows = null
        error = null
        if (!Cloud.isConfigured) {
            error = "بخش آنلاین روی این نسخه فعال نیست"
            return@LaunchedEffect
        }
        when (val r = StatsSync.gameLeaderboard(selected)) {
            is CloudResult.Ok -> rows = r.value
            is CloudResult.Failed -> error = r.message
        }
    }

    KiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            BobbingEmoji(emoji = "🏆", fontSize = 54.sp)
            Spacer(modifier = Modifier.height(8.dp))
            StickerTitle(text = "رتبه‌بندی", accent = VioletPrimary, rotation = -2f, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "فقط بازی‌های اینترنتی شمرده می‌شن — اسم‌ها همون یوزرنیمِ حساب‌هاست",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ---- انتخاب بازی ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                games.forEach { game ->
                    val on = game.id == selected
                    Row(
                        modifier = Modifier
                            .background(
                                if (on) game.accent.copy(alpha = 0.22f) else extras.glassStrong,
                                RoundedCornerShape(16.dp),
                            )
                            .border(
                                1.5.dp,
                                if (on) game.accent else extras.glassBorder,
                                RoundedCornerShape(16.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { selected = game.id }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = game.emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Black else FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- جدول ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(extras.glassStrong, RoundedCornerShape(24.dp))
                    .border(1.5.dp, extras.glassBorder, RoundedCornerShape(24.dp))
                    .padding(14.dp),
            ) {
                when {
                    error != null -> Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.danger,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    rows == null -> Text(
                        text = "یه لحظه…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    rows!!.isEmpty() -> Text(
                        text = "هنوز کسی این بازی رو اینترنتی تموم نکرده — اولین نفر باش! 🚀",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> rows!!.forEachIndexed { i, row ->
                        val mine = row.userId == myId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (mine) VioletPrimary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (row.rank) {
                                    1L -> "🥇"
                                    2L -> "🥈"
                                    3L -> "🥉"
                                    else -> row.rank.toInt().toPersianDigits()
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(36.dp),
                            )
                            Text(
                                text = "\u200E@${row.username}" + if (mine) " (تو)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${row.wins.toPersianDigits()} برد",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Black,
                                color = VioletPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "از ${row.plays.toPersianDigits()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (i < rows!!.lastIndex) Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            KButton(text = "بازگشت", style = KButtonStyle.Glass, onClick = onBack)
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}
