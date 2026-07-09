package com.navidabbasian.kibord.games.kalamz.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.ui.theme.teamColorFor
import com.navidabbasian.kibord.games.kalamz.model.Team

@Composable
fun TeamScoreCard(
    team: Team,
    rank: Int,
    isWinner: Boolean = false,
    modifier: Modifier = Modifier
) {
    val extras = kiExtras
    val teamColor = extras.teamColors.teamColorFor(team.id)
    val borderColor = if (isWinner) teamColor.copy(alpha = 0.6f) else extras.glassBorder
    val bgColor = if (isWinner) teamColor.copy(alpha = 0.2f) else extras.glass

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isWinner) 1.5.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Rank badge
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isWinner) "🏆" else "$rank",
                        fontSize = if (isWinner) 28.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWinner) extras.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = team.name.ifBlank { "تیم ${team.id + 1}" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = teamColor
                    )
                    Text(
                        text = "${team.player1.name} و ${team.player2.name}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${team.totalScore}",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWinner) extras.gold else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "امتیاز",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
