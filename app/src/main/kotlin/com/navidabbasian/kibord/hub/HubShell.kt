package com.navidabbasian.kibord.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.rememberMorphingBlobShape
import com.navidabbasian.kibord.core.ui.theme.VioletDeep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras

enum class HubTab { SETTINGS, HOME, HOW_TO_PLAY }

/** پوسته‌ی هاب: سه تب + ناوبری پایین شیشه‌ای شناور */
@Composable
fun HubShell(onOpenGame: (String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(HubTab.HOME) }

    KiBackground {
        PhaseTransition(key = tab) {
            when (tab) {
                HubTab.HOME -> HubScreen(onOpenGame = onOpenGame)
                HubTab.HOW_TO_PLAY -> HowToPlayScreen()
                HubTab.SETTINGS -> SettingsScreen()
            }
        }
        KiBordBottomNav(
            currentTab = tab,
            onTabSelected = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
fun KiBordBottomNav(
    currentTab: HubTab,
    onTabSelected: (HubTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = kiExtras
    val barShape = rememberMorphingBlobShape(phase = 0.7f, periodMs = 7200)

    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(76.dp)
            .background(
                if (extras.isDark) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f)
                } else {
                    Color.White.copy(alpha = 0.95f)
                },
                barShape
            )
            .border(2.dp, extras.glassBorderStrong, barShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavPebbleItem(
            icon = Icons.Default.Settings,
            label = "تنظیمات",
            selected = currentTab == HubTab.SETTINGS,
            pebbleSize = 44.dp,
            iconSize = 21.dp,
            phase = 1.1f,
            onClick = { onTabSelected(HubTab.SETTINGS) }
        )
        // خانه ۱۵٪ بزرگ‌تر از دو تب کناری
        NavPebbleItem(
            icon = Icons.Default.Home,
            label = "خانه",
            selected = currentTab == HubTab.HOME,
            pebbleSize = 51.dp,
            iconSize = 25.dp,
            phase = 2.4f,
            onClick = { onTabSelected(HubTab.HOME) }
        )
        NavPebbleItem(
            icon = Icons.Default.MenuBook,
            label = "آموزش",
            selected = currentTab == HubTab.HOW_TO_PLAY,
            pebbleSize = 44.dp,
            iconSize = 21.dp,
            phase = 3.7f,
            onClick = { onTabSelected(HubTab.HOW_TO_PLAY) }
        )
    }
}

/** آیتم ناوبری: آیکن داخل سنگریزه‌ی مورف‌شونده — بنفش وقتی فعال، شیشه‌ای وقتی غیرفعال */
@Composable
private fun RowScope.NavPebbleItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    pebbleSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    phase: Float,
    onClick: () -> Unit
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val pebble = rememberMorphingBlobShape(phase = phase, periodMs = 4800)
    val bounce by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pebble_bounce"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { sound?.playButtonClick(); onClick() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(pebbleSize)
                .then(
                    if (selected) Modifier.breathing(intensity = 0.025f, periodMs = 2000, phase = phase)
                    else Modifier
                )
                .graphicsLayer {
                    scaleX = bounce
                    scaleY = bounce
                    rotationZ = if (selected) -4f else 0f
                }
                .background(
                    if (selected) {
                        Brush.radialGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.lerp(VioletPrimary, Color.White, 0.2f),
                                VioletPrimary,
                                VioletDeep
                            ),
                            center = androidx.compose.ui.geometry.Offset(0.32f, 0.25f),
                            radius = 150f
                        )
                    } else {
                        Brush.radialGradient(listOf(extras.glassStrong, extras.glassStrong))
                    },
                    pebble
                )
                .border(
                    2.dp,
                    if (selected) Color.White.copy(alpha = 0.55f) else extras.glassBorder,
                    pebble
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
