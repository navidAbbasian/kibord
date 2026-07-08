package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.R
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.games.kalamz.model.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import androidx.compose.foundation.layout.offset
import com.navidabbasian.kibord.core.util.toPersianDigits

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordEntryScreen(
    player: Player,
    wordsPerPlayer: Int,
    currentPlayerIndex: Int,
    totalPlayers: Int,
    onSubmitWords: (playerIndex: Int, words: List<String>) -> Unit
) {
    var words by remember(player.id) { mutableStateOf(List(wordsPerPlayer) { "" }) }
    var showWords by remember(player.id) { mutableStateOf(false) }
    val allWordsFilled = words.all { it.isNotBlank() }

    if (!showWords) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Placeholder image: person shushing
            Image(
                painter = painterResource(R.drawable.img_secret_player),
                contentDescription = "گوشی رو بده",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                "گوشی رو بده به",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                player.name,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = kiExtras.gold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "نفر ${currentPlayerIndex + 1} از $totalPlayers",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(48.dp))
            KButton(
                text = "آماده‌ام",
                onClick = { showWords = true },
                style = KButtonStyle.Primary,
                modifier = Modifier.fillMaxWidth(0.75f)
            )
        }
    } else {
        val listState = rememberLazyListState()
        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        val accentFocus = MaterialTheme.colorScheme.primary

        // imePadding روی LazyColumn → لیست کوتاه می‌شه تا کیبورد روی آیتم‌ها نیفته
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),                  // ← کلید حل مشکل
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = kiExtras.gold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        player.name,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = kiExtras.gold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "$wordsPerPlayer کلمه بنویس!",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            itemsIndexed(words) { index, word ->
                // BringIntoViewRequester → وقتی فیلد فوکوس می‌گیره خودش رو از زیر کیبورد می‌کشه بیرون
                val bringIntoViewRequester = remember { BringIntoViewRequester() }

                BlobTextField(
                    value = word,
                    onValueChange = { newVal ->
                        words = words.toMutableList().also { it[index] = newVal }
                    },
                    placeholder = "کلمه ${(index + 1).toPersianDigits()}",
                    color = accentFocus,
                    badge = (index + 1).toPersianDigits(),
                    tilt = if (index % 2 == 0) -0.9f else 0.9f,
                    phase = index * 1.1f,
                    modifier = Modifier
                        .offset(x = if (index % 2 == 0) (-5).dp else 5.dp)
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                scope.launch {
                                    // کمی صبر می‌کنیم تا کیبورد کامل باز بشه
                                    delay(300)
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                KButton(
                    text = "ثبت کلمات",
                    onClick = { focusManager.clearFocus(); onSubmitWords(currentPlayerIndex, words) },
                    enabled = allWordsFilled,
                    style = KButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
