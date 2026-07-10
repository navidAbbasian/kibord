package com.navidabbasian.kibord.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.ui.theme.kiExtras

/**
 * دیالوگ تایید خروج از بازی با دکمه‌ی برگشت سیستم —
 * در همه‌ی بازی‌ها یکسان: ماندن انتخاب امن و پررنگ است.
 */
@Composable
fun ExitConfirmDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val extras = kiExtras
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .border(2.dp, extras.glassBorderStrong, RoundedCornerShape(28.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BobbingEmoji(emoji = "🚪", fontSize = 44.sp)
            Spacer(modifier = Modifier.height(10.dp))
            StickerTitle(text = "مطمئنی می‌خوای بری؟", rotation = -2f, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "اگه بری، این بازی از دست می‌ره!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            KButton(text = "نه، بمونم!", onClick = onDismiss)
            Spacer(modifier = Modifier.height(10.dp))
            KButton(text = "آره، برم", style = KButtonStyle.Danger, onClick = onConfirm)
        }
    }
}
