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
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.PhaseTransition
import com.navidabbasian.kibord.core.ui.components.breathing
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
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                if (extras.isDark) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
                } else {
                    Color.White.copy(alpha = 0.94f)
                }
            )
            .background(
                Brush.verticalGradient(
                    listOf(extras.glassBorder.copy(alpha = 0.08f), Color.Transparent)
                )
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(
            icon = Icons.Default.Settings,
            label = "تنظیمات",
            selected = currentTab == HubTab.SETTINGS,
            onClick = { onTabSelected(HubTab.SETTINGS) }
        )
        NavHomeItem(
            selected = currentTab == HubTab.HOME,
            onClick = { onTabSelected(HubTab.HOME) }
        )
        NavItem(
            icon = Icons.Default.MenuBook,
            label = "آموزش",
            selected = currentTab == HubTab.HOW_TO_PLAY,
            onClick = { onTabSelected(HubTab.HOW_TO_PLAY) }
        )
    }
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val sound = LocalSoundManager.current
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bounce by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tab_bounce"
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
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { scaleX = bounce; scaleY = bounce }
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
    }
}

@Composable
private fun RowScope.NavHomeItem(selected: Boolean, onClick: () -> Unit) {
    val sound = LocalSoundManager.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        // هاله‌ی ضربان‌دار دور دکمه‌ی خانه
        if (selected) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .breathing(intensity = 0.14f, periodMs = 1800)
                    .background(VioletPrimary.copy(alpha = 0.35f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    if (selected) {
                        Brush.linearGradient(listOf(VioletPrimary, VioletDeep))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                kiExtras.glassStrong,
                                kiExtras.glassStrong
                            )
                        )
                    },
                    CircleShape
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { sound?.playButtonClick(); onClick() }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "خانه",
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
