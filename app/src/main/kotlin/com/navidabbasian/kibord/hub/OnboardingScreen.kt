package com.navidabbasian.kibord.hub

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.R
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import kotlinx.coroutines.launch

private data class OnboardStep(val emoji: String, val title: String, val body: String)

private val onboardSteps = listOf(
    OnboardStep(
        emoji = "🎉",
        title = "به «کی برد؟» خوش اومدی!",
        body = "خونه‌ی بازی‌های دورهمیِ ایرانی — یه عالمه بازی برای شب‌نشینی‌ها، مهمونی‌ها و دورهمی‌های خانوادگی!\n\nاین نسخه‌ی اولیه‌ی بازیه و منتظر انتقادها و پیشنهادهای شما عزیزان هستیم 💜",
    ),
    OnboardStep(
        emoji = "📵",
        title = "بدون اینترنت، بدون اکانت",
        body = "هیچ ثبت‌نامی لازم نیست و هیچ اطلاعاتی ازت جمع نمی‌کنیم. بازی‌های چندگوشی هم فقط با وای‌فای یا هات‌اسپاتِ خودتون کار می‌کنن.",
    ),
    OnboardStep(
        emoji = "👨‍👩‍👧‍👦",
        title = "دور هم جمع شید و بترکونید!",
        body = "یه بازی انتخاب کن، گوشی رو دست‌به‌دست کن یا با تیم‌چین تیم بشید — بعدش ببینید «کی برد؟»",
    ),
)

/** خوش‌آمدگوییِ بارِ اول — سه گام با ورق‌زدنِ افقی و هویت بصری اپ */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val sound = LocalSoundManager.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { onboardSteps.size })
    val isLast = pager.currentPage == onboardSteps.lastIndex

    KiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val step = onboardSteps[page]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (page == 0) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_kibord),
                            contentDescription = "مسکات کی برد؟",
                            modifier = Modifier.size(160.dp)
                        )
                    } else {
                        BobbingEmoji(emoji = step.emoji, fontSize = 76.sp)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    StickerTitle(text = step.title, accent = VioletPrimary, rotation = -2f, fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = step.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                    )
                }
            }

            // ---- نقطه‌های صفحه ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(onboardSteps.size) { i ->
                    val selected = i == pager.currentPage
                    val width by animateFloatAsState(
                        targetValue = if (selected) 26f else 9f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .height(9.dp)
                            .graphicsLayer { }
                            .size(width = width.dp, height = 9.dp)
                            .background(
                                if (selected) VioletPrimary else kiExtras.glassBorder,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                KButton(
                    text = if (isLast) "بریم بازی! 🎮" else "بعدی",
                    onClick = {
                        sound?.playButtonClick()
                        if (isLast) onDone()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                )
            }
            if (!isLast) {
                Text(
                    text = "رد کردن",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { sound?.playButtonClick(); onDone() }
                        .padding(6.dp),
                )
            } else {
                Spacer(modifier = Modifier.navigationBarsPadding().height(18.dp))
            }
        }
    }
}
