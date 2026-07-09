package com.navidabbasian.kibord.games.kalamz.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.GlassCard
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.games.kalamz.model.Team
import com.navidabbasian.kibord.core.ui.components.BlobTextField

@Composable
fun TeamSetupScreen(
    teams: List<Team>,
    onPlayerNameChanged: (playerId: Int, name: String) -> Unit,
    onTeamNameChanged: (teamId: Int, name: String) -> Unit,
    onConfirm: () -> Unit
) {
    val allNamesFilled = teams.all {
        it.player1.name.isNotBlank() && it.player2.name.isNotBlank() && it.name.isNotBlank()
    }
    val teamColors = kiExtras.teamColors

    // imePadding روی ستون بیرونی → وقتی کیبورد باز می‌شه فضای کل ستون کوچک می‌شه
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()                       // ← کلید حل مشکل
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(52.dp))

        Text(
            text = "تیم‌بندی",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "اسم تیم‌ها و بازیکن‌ها رو وارد کنید",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ناحیه اسکرول — کارت‌های تیم + دکمه ادامه (داخل اسکرول)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            teams.forEach { team ->
                val teamColor = teamColors.teamColorFor(team.id)

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    borderColor = teamColor.copy(alpha = 0.45f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Team number label
                        Text(
                            text = "تیم ${team.id + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = teamColor,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = team.name,
                            onValueChange = { onTeamNameChanged(team.id, it) },
                            label = "نام تیم",
                            accentColor = teamColor,
                            tilt = -0.8f,
                            phase = team.id * 2.1f
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GlassTextField(
                            value = team.player1.name,
                            onValueChange = { onPlayerNameChanged(team.player1.id, it) },
                            label = "بازیکن ۱",
                            accentColor = teamColor,
                            tilt = 0.8f,
                            phase = team.id * 2.1f + 1f
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        GlassTextField(
                            value = team.player2.name,
                            onValueChange = { onPlayerNameChanged(team.player2.id, it) },
                            label = "بازیکن ۲",
                            accentColor = teamColor,
                            tilt = -0.8f,
                            phase = team.id * 2.1f + 2f
                        )
                    }
                }
            }

            // دکمه داخل اسکرول → همیشه بعد از آخرین کارت قابل اسکرول است
            Spacer(modifier = Modifier.height(4.dp))
            KButton(
                text = "ادامه",
                onClick = onConfirm,
                enabled = allNamesFilled,
                style = KButtonStyle.Primary
            )
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(24.dp)
            )
        }
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    tilt: Float = 0f,
    phase: Float = 0f,
) {
    BlobTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = label,
        color = accentColor,
        tilt = tilt,
        phase = phase,
    )
}
