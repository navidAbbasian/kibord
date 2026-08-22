package com.navidabbasian.kibord.hub

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navidabbasian.kibord.core.cloud.AccountMode
import com.navidabbasian.kibord.core.cloud.AccountViewModel
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.KiBackground
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

/**
 * حساب بازیکن: ثبت‌نام و ورود، یا نمایش پروفایل اگر وارد شده باشد.
 * بازی‌های آفلاین هیچ نیازی به این صفحه ندارند — این‌جا فقط برای کسانی است
 * که می‌خواهند آمارشان آنلاین ثبت شود.
 */
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val viewModel: AccountViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val extras = kiExtras

    BackHandler { onBack() }

    KiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            when {
                !viewModel.cloudAvailable -> CloudOffNotice()
                state.profile != null -> ProfileCard(
                    username = state.profile!!.username,
                    busy = state.busy,
                    note = state.syncNote,
                    onSync = viewModel::syncNow,
                    onSignOut = viewModel::signOut,
                )
                else -> AuthForm(state = state, viewModel = viewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))
            KButton(text = "بازگشت", style = KButtonStyle.Glass, onClick = onBack)
            Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun CloudOffNotice() {
    BobbingEmoji(emoji = "🔌", fontSize = 58.sp)
    Spacer(modifier = Modifier.height(12.dp))
    StickerTitle(text = "بخش آنلاین فعال نیست", accent = VioletPrimary, rotation = -2f, fontSize = 22.sp)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "این نسخه بدون تنظیمات آنلاین ساخته شده. همه‌ی بازی‌ها آفلاین کار می‌کنن.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp,
    )
}

/** پروفایل کاربر وارد‌شده */
@Composable
private fun ProfileCard(
    username: String,
    busy: Boolean,
    note: String?,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
) {
    val extras = kiExtras

    BobbingEmoji(emoji = "🎖️", fontSize = 58.sp)
    Spacer(modifier = Modifier.height(10.dp))
    StickerTitle(text = "خوش اومدی!", accent = VioletPrimary, rotation = -2f, fontSize = 24.sp)
    Spacer(modifier = Modifier.height(16.dp))

    TicketCard(modifier = Modifier.fillMaxWidth(), accent = VioletPrimary, tilt = -1.2f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "یوزرنیم",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "@$username",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (note != null) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = extras.success,
            textAlign = TextAlign.Center,
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    KButton(
        text = if (busy) "یه لحظه…" else "همگام‌سازی آمار 🔄",
        enabled = !busy,
        onClick = onSync,
    )
    Spacer(modifier = Modifier.height(10.dp))
    KButton(text = "خروج از حساب", style = KButtonStyle.Danger, enabled = !busy, onClick = onSignOut)
}

/** فرم ثبت‌نام و ورود */
@Composable
private fun AuthForm(
    state: com.navidabbasian.kibord.core.cloud.AccountUiState,
    viewModel: AccountViewModel,
) {
    val extras = kiExtras
    val registering = state.mode == AccountMode.REGISTER
    var showPassword by remember { mutableStateOf(false) }

    BobbingEmoji(emoji = if (registering) "🚀" else "👋", fontSize = 54.sp)
    Spacer(modifier = Modifier.height(10.dp))
    StickerTitle(
        text = if (registering) "حساب بساز" else "خوش برگشتی",
        accent = VioletPrimary,
        rotation = -2f,
        fontSize = 24.sp,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (registering) {
            "با حساب، آمار و بردهات آنلاین ذخیره می‌شن و می‌تونی با بقیه رقابت کنی. بدون حساب هم همه‌ی بازی‌ها کار می‌کنن."
        } else {
            "با همون یوزرنیم و پسوردت وارد شو"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 24.sp,
    )
    Spacer(modifier = Modifier.height(20.dp))

    BlobTextField(
        value = state.username,
        onValueChange = viewModel::setUsername,
        placeholder = "یوزرنیم (انگلیسی و عدد)",
        badge = "@",
        tilt = -1f,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    Spacer(modifier = Modifier.height(10.dp))
    BlobTextField(
        value = state.password,
        onValueChange = viewModel::setPassword,
        placeholder = "پسورد",
        badge = "🔒",
        tilt = 1f,
        phase = 1.4f,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (showPassword) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
    )
    Text(
        text = if (showPassword) "پنهان کردن پسورد" else "نمایش پسورد",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 6.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showPassword = !showPassword }
            .padding(4.dp),
    )

    if (registering) {
        Spacer(modifier = Modifier.height(10.dp))
        BlobTextField(
            value = state.email,
            onValueChange = viewModel::setEmail,
            placeholder = "ایمیل (اختیاری — برای بازیابی پسورد)",
            badge = "✉️",
            tilt = -0.8f,
            phase = 2.8f,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "اگه ایمیل ندی، در صورت فراموشی پسورد راهی برای برگردوندن حساب نیست",
            style = MaterialTheme.typography.labelMedium,
            color = extras.warning,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }

    state.error?.let { message ->
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(extras.danger.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))
    KButton(
        text = when {
            state.busy -> "یه لحظه…"
            registering -> "ثبت‌نام"
            else -> "ورود"
        },
        enabled = !state.busy && state.username.isNotBlank() && state.password.isNotBlank(),
        onClick = viewModel::submit,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = if (registering) "قبلاً حساب ساختی؟ وارد شو" else "حساب نداری؟ بساز",
        style = MaterialTheme.typography.labelLarge,
        color = VioletPrimary,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                viewModel.setMode(if (registering) AccountMode.SIGN_IN else AccountMode.REGISTER)
            }
            .padding(6.dp),
    )
}
