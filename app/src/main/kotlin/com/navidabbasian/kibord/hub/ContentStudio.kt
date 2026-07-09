package com.navidabbasian.kibord.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.content.ContentBank
import com.navidabbasian.kibord.core.content.CustomContentStore
import com.navidabbasian.kibord.core.ui.components.BlobTextField
import com.navidabbasian.kibord.core.ui.components.KButton
import com.navidabbasian.kibord.core.ui.components.KButtonStyle
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.games.dor.model.DorCategory
import com.navidabbasian.kibord.games.dor.model.DorWord
import com.navidabbasian.kibord.games.gandegoo.model.GgCategory
import com.navidabbasian.kibord.games.gandegoo.model.GgQuestion
import com.navidabbasian.kibord.games.pantomime.model.PCategory
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * استودیوی محتوا در تنظیمات: به‌روزرسانی دستی بانک از گیت‌هاب،
 * ساخت کتگوری سفارشی برای دور/گنده‌گو/پانتومیم و ارسال پیشنهاد به مخزن.
 */

private enum class ContentDialog { DOR, GANDEGOO, PANTOMIME }

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun ContentStudioSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sound = LocalSoundManager.current
    var updating by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ContentDialog?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KButton(
            text = if (updating) "⏳ در حال به‌روزرسانی…" else "🔄 به‌روزرسانی بانک کلمات و سوالات",
            enabled = !updating,
            accent = VioletPrimary,
            onClick = {
                sound?.playButtonClick()
                updating = true
                scope.launch {
                    val ok = ContentBank.refreshNow(context)
                    updating = false
                    Toast.makeText(
                        context,
                        if (ok) "بانک محتوا تازه شد ✅ از بازی بعدی فعال است"
                        else "به‌روزرسانی نشد — اینترنت را بررسی کنید",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
        KButton(
            text = "💣 کتگوری جدید برای دور",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            onClick = { sound?.playButtonClick(); dialog = ContentDialog.DOR },
        )
        KButton(
            text = "😏 کتگوری جدید برای گنده‌گو",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            onClick = { sound?.playButtonClick(); dialog = ContentDialog.GANDEGOO },
        )
        KButton(
            text = "🎭 کتگوری جدید برای پانتومیم",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            onClick = { sound?.playButtonClick(); dialog = ContentDialog.PANTOMIME },
        )
        KButton(
            text = "📤 ارسال محتوای من به گیت‌هاب",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            onClick = { sound?.playButtonClick(); sendCustomContentToGitHub(context) },
        )
    }

    when (dialog) {
        ContentDialog.DOR -> DorCategoryDialog(onDismiss = { dialog = null })
        ContentDialog.GANDEGOO -> GgCategoryDialog(onDismiss = { dialog = null })
        ContentDialog.PANTOMIME -> PantoCategoryDialog(onDismiss = { dialog = null })
        null -> Unit
    }
}

// ---- دیالوگ‌های ساخت کتگوری ----

@Composable
private fun DorCategoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var wordsText by remember { mutableStateOf("") }
    val words = parseItems(wordsText)
    val valid = name.isNotBlank() && words.size >= 30

    ContentDialogFrame(title = "کتگوری دور", onDismiss = onDismiss) {
        BlobTextField(value = name, onValueChange = { name = it }, placeholder = "نام کتگوری", color = VioletPrimary, badge = "🏷️")
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(value = emoji, onValueChange = { emoji = it }, placeholder = "ایموجی (اختیاری)", color = VioletPrimary, badge = "😀")
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(
            value = wordsText,
            onValueChange = { wordsText = it },
            placeholder = "کلمه‌ها — هر کدام در یک خط یا با ویرگول جدا (حداقل ۳۰ تا)",
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "تعداد کلمات: ${words.size} از حداقل ۳۰",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else kiExtras.danger,
        )
        Spacer(modifier = Modifier.height(12.dp))
        DialogButtons(saveEnabled = valid, onDismiss = onDismiss) {
            val id = customId(name)
            appendCustomCategory(context, CustomContentStore.DOR,
                DorCategory(id = id, name = name.trim(), emoji = emoji.trim().ifBlank { "✨" },
                    words = words.map { DorWord(it, id) })
            ) { list -> json.encodeToString(list) }
            onDismiss()
        }
    }
}

@Composable
private fun GgCategoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var q20 by remember { mutableStateOf("") }
    var q40 by remember { mutableStateOf("") }
    var q60 by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && q20.isNotBlank() && q40.isNotBlank() && q60.isNotBlank()

    ContentDialogFrame(title = "کتگوری گنده‌گو", onDismiss = onDismiss) {
        BlobTextField(value = name, onValueChange = { name = it }, placeholder = "نام کتگوری", color = VioletPrimary, badge = "🏷️")
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(value = emoji, onValueChange = { emoji = it }, placeholder = "ایموجی (اختیاری)", color = VioletPrimary, badge = "😀")
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = q20, onValueChange = { q20 = it }, placeholder = "سوال ساده (۲۰ امتیازی) — مثلاً: میوه‌ها نام ببرید", minHeight = 64.dp)
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = q40, onValueChange = { q40 = it }, placeholder = "سوال متوسط (۴۰ امتیازی)", minHeight = 64.dp)
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = q60, onValueChange = { q60 = it }, placeholder = "سوال سخت (۶۰ امتیازی)", minHeight = 64.dp)
        Spacer(modifier = Modifier.height(12.dp))
        DialogButtons(saveEnabled = valid, onDismiss = onDismiss) {
            appendCustomCategory(context, CustomContentStore.GANDEGOO,
                GgCategory(id = customId(name), name = name.trim(), emoji = emoji.trim().ifBlank { "✨" },
                    questions = listOf(
                        GgQuestion(20, q20.trim()),
                        GgQuestion(40, q40.trim()),
                        GgQuestion(60, q60.trim()),
                    ))
            ) { list -> json.encodeToString(list) }
            onDismiss()
        }
    }
}

@Composable
private fun PantoCategoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var w2 by remember { mutableStateOf("") }
    var w4 by remember { mutableStateOf("") }
    var w6 by remember { mutableStateOf("") }
    var golden by remember { mutableStateOf("") }
    val words2 = parseItems(w2)
    val words4 = parseItems(w4)
    val words6 = parseItems(w6)
    val valid = name.isNotBlank() && words2.size >= 3 && words4.size >= 3 && words6.size >= 3

    ContentDialogFrame(title = "کتگوری پانتومیم", onDismiss = onDismiss) {
        BlobTextField(value = name, onValueChange = { name = it }, placeholder = "نام کتگوری", color = VioletPrimary, badge = "🏷️")
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(value = emoji, onValueChange = { emoji = it }, placeholder = "ایموجی (اختیاری)", color = VioletPrimary, badge = "😀")
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = w2, onValueChange = { w2 = it }, placeholder = "کلمات ساده (۲ امتیازی) — حداقل ۳ تا، با ویرگول یا خط جدید", minHeight = 72.dp)
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = w4, onValueChange = { w4 = it }, placeholder = "کلمات متوسط (۴ امتیازی) — حداقل ۳ تا", minHeight = 72.dp)
        Spacer(modifier = Modifier.height(8.dp))
        GlassTextArea(value = w6, onValueChange = { w6 = it }, placeholder = "کلمات سخت (۶ امتیازی) — حداقل ۳ تا", minHeight = 72.dp)
        Spacer(modifier = Modifier.height(8.dp))
        BlobTextField(value = golden, onValueChange = { golden = it }, placeholder = "موضوع طلایی (اختیاری)", color = VioletPrimary, badge = "⭐")
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "شمارش: ساده ${words2.size} — متوسط ${words4.size} — سخت ${words6.size} (هر کدام حداقل ۳)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else kiExtras.danger,
        )
        Spacer(modifier = Modifier.height(12.dp))
        DialogButtons(saveEnabled = valid, onDismiss = onDismiss) {
            appendCustomCategory(context, CustomContentStore.PANTOMIME,
                PCategory(id = customId(name), name = name.trim(), emoji = emoji.trim().ifBlank { "✨" },
                    words2 = words2, words4 = words4, words6 = words6, golden = golden.trim())
            ) { list -> json.encodeToString(list) }
            onDismiss()
        }
    }
}

// ---- اجزای مشترک دیالوگ ----

@Composable
private fun ContentDialogFrame(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val extras = kiExtras
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .border(2.dp, extras.glassBorderStrong, RoundedCornerShape(28.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                StickerTitle(text = title, accent = VioletPrimary, rotation = -1.5f)
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun DialogButtons(saveEnabled: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KButton(
            text = "ذخیره",
            enabled = saveEnabled,
            accent = VioletPrimary,
            modifier = Modifier.weight(1f),
            onClick = {
                onSave()
                Toast.makeText(context, "ذخیره شد ✅ از بازی بعدی در دسترس است", Toast.LENGTH_LONG).show()
            },
        )
        KButton(
            text = "انصراف",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            modifier = Modifier.weight(1f),
            onClick = onDismiss,
        )
    }
}

/** فیلد متنی چندخطی شیشه‌ای برای ورود فهرست کلمات/سوال */
@Composable
private fun GlassTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minHeight: androidx.compose.ui.unit.Dp = 130.dp,
) {
    val extras = kiExtras
    val shape = RoundedCornerShape(20.dp)
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(extras.glassStrong, shape)
            .border(1.5.dp, VioletPrimary.copy(alpha = 0.45f), shape)
            .padding(12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(VioletPrimary),
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
            )
        }
    }
}

// ---- منطق ذخیره و ارسال ----

/** جداکردن آیتم‌ها با خط جدید، ویرگول فارسی/لاتین یا نقطه‌ویرگول */
private fun parseItems(text: String): List<String> =
    text.split('\n', '،', ',', '؛').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private fun customId(name: String): String = "custom-${abs(name.trim().hashCode())}"

/** افزودن/جایگزینی کتگوری سفارشی در JSON ذخیره‌شده‌ی همان بازی */
private inline fun <reified T> appendCustomCategory(
    context: Context,
    storeKey: String,
    item: T,
    encode: (List<T>) -> String,
) {
    val store = CustomContentStore(context)
    val existing = try {
        val text = store.getJson(storeKey)
        if (text.isBlank()) emptyList()
        else json.decodeFromString<List<T>>(text)
    } catch (_: Exception) {
        emptyList()
    }
    // کتگوری هم‌نام قبلی جایگزین می‌شود تا تکراری جمع نشود
    val itemJson = json.encodeToString(item)
    val updated = existing.filter { json.encodeToString(it) != itemJson } + item
    store.setJson(storeKey, encode(updated))
}

/** باز کردن صفحه‌ی ثبت issue گیت‌هاب با محتوای سفارشی کاربر؛ اگر طولانی بود، اشتراک‌گذاری متنی */
private fun sendCustomContentToGitHub(context: Context) {
    val store = CustomContentStore(context)
    val sections = buildString {
        listOf(
            Triple("دور", CustomContentStore.DOR, "words.json"),
            Triple("گنده‌گو", CustomContentStore.GANDEGOO, "gandegoo.json"),
            Triple("پانتومیم", CustomContentStore.PANTOMIME, "pantomime.json"),
        ).forEach { (label, key, file) ->
            val text = store.getJson(key)
            if (text.isNotBlank() && text != "[]") {
                append("### $label — $file\n\n```json\n$text\n```\n\n")
            }
        }
    }
    if (sections.isBlank()) {
        Toast.makeText(context, "هنوز محتوایی نساخته‌اید — اول یک کتگوری اضافه کنید", Toast.LENGTH_LONG).show()
        return
    }
    val body = "محتوای پیشنهادی از داخل اپ «کی برد؟» — لطفاً پس از بازبینی به assets اضافه شود.\n\n$sections"
    val url = "https://github.com/navidAbbasian/kibord/issues/new" +
        "?title=" + Uri.encode("پیشنهاد محتوا از اپ") +
        "&body=" + Uri.encode(body)
    try {
        if (url.length <= 7500) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } else {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(share, "ارسال محتوا"))
        }
    } catch (_: Exception) {
        Toast.makeText(context, "مرورگری برای باز کردن گیت‌هاب پیدا نشد", Toast.LENGTH_LONG).show()
    }
}
