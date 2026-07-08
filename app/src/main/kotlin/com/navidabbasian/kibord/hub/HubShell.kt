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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp)
    ) {
        // ---- بدنه‌ی ارگانیک نوار ----
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(62.dp)
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
            NavItem(
                icon = Icons.Default.Settings,
                label = "تنظیمات",
                selected = currentTab == HubTab.SETTINGS,
                seed = 3,
                onClick = { onTabSelected(HubTab.SETTINGS) }
            )
            // جای خالی زیر سنگ شناور خانه
            Spacer(modifier = Modifier.weight(1f))
            NavItem(
                icon = Icons.Default.MenuBook,
                label = "آموزش",
                selected = currentTab == HubTab.HOW_TO_PLAY,
                seed = 8,
                onClick = { onTabSelected(HubTab.HOW_TO_PLAY) }
            )
        }

        // ---- سنگ شناور خانه: از نوار بیرون زده ----
        HomePebble(
            selected = currentTab == HubTab.HOME,
            onClick = { onTabSelected(HubTab.HOME) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-24).dp)
        )
    }
}

@Composable
private fun RowScope.NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    seed: Int,
    onClick: () -> Unit
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val bounce by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
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
        // آیکن تب فعال داخل سنگریزه‌ی بنفشِ کم‌رنگ می‌نشیند
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer {
                    scaleX = bounce
                    scaleY = bounce
                    rotationZ = if (selected) -4f else 0f
                }
                .background(
                    if (selected) VioletPrimary.copy(alpha = 0.22f) else Color.Transparent,
                    blobShape(seed = seed)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
    }
}

@Composable
private fun HomePebble(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sound = LocalSoundManager.current
    val extras = kiExtras
    val pebble = rememberMorphingBlobShape(phase = 2.4f, periodMs = 4800)
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "home_scale"
    )

    Box(modifier = modifier.size(74.dp), contentAlignment = Alignment.Center) {
        // هاله‌ی ضربان‌دار
        if (selected) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .breathing(intensity = 0.16f, periodMs = 1800)
                    .background(VioletPrimary.copy(alpha = 0.3f), pebble)
            )
        }
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = -3f }
                .background(
                    if (selected) {
                        Brush.radialGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.lerp(VioletPrimary, Color.White, 0.2f),
                                VioletPrimary,
                                VioletDeep
                            ),
                            center = androidx.compose.ui.geometry.Offset(0.32f, 0.25f),
                            radius = 170f
                        )
                    } else {
                        Brush.radialGradient(listOf(extras.glassStrong, extras.glassStrong))
                    },
                    pebble
                )
                .border(
                    2.5.dp,
                    if (selected) Color.White.copy(alpha = 0.55f) else extras.glassBorderStrong,
                    pebble
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
                modifier = Modifier.size(27.dp)
            )
        }
        // جرقه‌ی معلق کنار سنگ فعال
        if (selected) {
            BobbingEmoji(
                emoji = "✨",
                fontSize = 13.sp,
                phase = 1.2f,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}
