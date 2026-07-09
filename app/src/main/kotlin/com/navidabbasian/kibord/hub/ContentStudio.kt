package com.navidabbasian.kibord.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
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
import com.navidabbasian.kibord.core.util.toPersianDigits
import com.navidabbasian.kibord.games.dor.model.DorCategory
import com.navidabbasian.kibord.games.dor.model.DorWord
import com.navidabbasian.kibord.games.gandegoo.data.GandeGooRepository
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

private enum class ContentDialog { DOR, GANDEGOO, PANTOMIME, MY_CONTENT }

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
            text = "📚 محتوای من — دیدنِ افزوده‌های محلی",
            style = KButtonStyle.Glass,
            accent = VioletPrimary,
            onClick = { sound?.playButtonClick(); dialog = ContentDialog.MY_CONTENT },
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
        ContentDialog.MY_CONTENT -> MyContentDialog(onDismiss = { dialog = null })
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
    var addToExisting by remember { mutableStateOf(false) }

    ContentDialogFrame(title = "محتوای گنده‌گو", onDismiss = onDismiss) {
        // ---- سوییچ: دسته‌ی جدید یا افزودن به دسته‌ی موجود ----
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(false to "دسته‌ی جدید", true to "سوال برای دسته‌ی موجود").forEach { (mode, label) ->
                val selected = addToExisting == mode
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(
                            if (selected) VioletPrimary else kiExtras.glass,
                            RoundedCornerShape(50)
                        )
                        .border(
                            1.dp,
                            if (selected) VioletPrimary else kiExtras.glassBorder,
                            RoundedCornerShape(50)
                        )
                        .clickable { addToExisting = mode }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (addToExisting) GgAddToExistingContent(onDismiss = onDismiss)
        else GgNewCategoryContent(onDismiss = onDismiss)
    }
}

/** ساخت دسته‌ی کاملاً جدید با یک سوال در هر سطح */
@Composable
private fun GgNewCategoryContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    var q20 by remember { mutableStateOf("") }
    var q40 by remember { mutableStateOf("") }
    var q60 by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && q20.isNotBlank() && q40.isNotBlank() && q60.isNotBlank()

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
        val id = customId(name)
        appendGgQuestions(
            context,
            GgCategory(id = id, name = name.trim(), emoji = emoji.trim().ifBlank { "✨" }),
            listOf(
                GgQuestion(20, q20.trim()),
                GgQuestion(40, q40.trim()),
                GgQuestion(60, q60.trim()),
            ),
        )
        onDismiss()
    }
}

/** افزودن سوال‌های تازه به یکی از دسته‌های موجود بانک */
@Composable
private fun GgAddToExistingContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val bankCategories = remember {
        try {
            GandeGooRepository(context).apply { load() }.allCategories
        } catch (_: Exception) {
            emptyList()
        }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GgCategory?>(null) }
    var tier by remember { mutableStateOf(20) }
    var questionsText by remember { mutableStateOf("") }
    // سوال ممکن است ویرگول داشته باشد؛ فقط خط جدید جداکننده است
    val questions = questionsText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val filtered = remember(query, bankCategories) {
        val q = query.trim()
        if (q.isEmpty()) bankCategories else bankCategories.filter { q in it.name }
    }
    val valid = selected != null && questions.isNotEmpty()

    BlobTextField(value = query, onValueChange = { query = it }, placeholder = "جستجوی دسته…", color = VioletPrimary, badge = "🔍")
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(kiExtras.glass, RoundedCornerShape(18.dp))
            .border(1.dp, kiExtras.glassBorder, RoundedCornerShape(18.dp)),
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            items(count = filtered.size, key = { filtered[it].id }) { i ->
                val cat = filtered[i]
                val isSel = selected?.id == cat.id
                Text(
                    text = "${cat.emoji} ${cat.name}",
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) androidx.compose.ui.graphics.Color.White
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(
                            if (isSel) VioletPrimary else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selected = cat }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))

    // ---- سطح امتیازی سوال‌های جدید ----
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(20, 40, 60).forEach { t ->
            val isSel = tier == t
            Text(
                text = "${t.toPersianDigits()} امتیازی",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSel) androidx.compose.ui.graphics.Color.White
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(
                        if (isSel) VioletPrimary else kiExtras.glass,
                        RoundedCornerShape(50)
                    )
                    .clickable { tier = t }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    GlassTextArea(
        value = questionsText,
        onValueChange = { questionsText = it },
        placeholder = "سوال‌های جدید — هر سوال در یک خط",
        minHeight = 96.dp,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = if (selected == null) "یک دسته انتخاب کن"
        else "افزودن ${questions.size.toPersianDigits()} سوال ${tier.toPersianDigits()} امتیازی به «${selected?.name}»",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else kiExtras.danger,
    )
    Spacer(modifier = Modifier.height(12.dp))
    DialogButtons(saveEnabled = valid, onDismiss = onDismiss) {
        selected?.let { cat ->
            appendGgQuestions(context, cat, questions.map { GgQuestion(tier, it) })
        }
        onDismiss()
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

/**
 * افزودن سوال به مدخل سفارشی گنده‌گو بر پایه‌ی شناسه‌ی دسته:
 * اگر مدخلی با همان شناسه باشد سوال‌ها ادغام می‌شوند — این‌طوری هم دسته‌ی
 * جدید ساخته می‌شود و هم به دسته‌های بانک سوال اضافه می‌شود (مخزن هنگام
 * بارگذاری، مدخل هم‌شناسه را داخل دسته‌ی بانک ادغام می‌کند).
 */
private fun appendGgQuestions(context: Context, category: GgCategory, newQuestions: List<GgQuestion>) {
    val store = CustomContentStore(context)
    val existing: List<GgCategory> = try {
        val text = store.getJson(CustomContentStore.GANDEGOO)
        if (text.isBlank()) emptyList() else json.decodeFromString(text)
    } catch (_: Exception) {
        emptyList()
    }
    val prev = existing.firstOrNull { it.id == category.id }
    val entry = GgCategory(
        id = category.id,
        name = category.name,
        emoji = category.emoji,
        questions = (prev?.questions.orEmpty() + newQuestions).distinct(),
    )
    store.setJson(
        CustomContentStore.GANDEGOO,
        json.encodeToString(existing.filter { it.id != category.id } + entry),
    )
}

// ---- نمای محتوای محلی ----

private data class LocalCat(val title: String, val items: List<String>)

/** خواندن همه‌ی محتوای سفارشیِ ذخیره‌شده روی دستگاه، گروه‌بندی‌شده بر اساس بازی */
private fun loadLocalContent(context: Context): List<Pair<String, List<LocalCat>>> {
    val store = CustomContentStore(context)

    fun read(key: String, map: (String) -> List<LocalCat>): List<LocalCat> = try {
        val text = store.getJson(key)
        if (text.isBlank()) emptyList() else map(text)
    } catch (_: Exception) {
        emptyList()
    }

    val dor = read(CustomContentStore.DOR) { text ->
        json.decodeFromString<List<DorCategory>>(text).map { c ->
            LocalCat("${c.emoji} ${c.name}", c.words.map { it.text })
        }
    }
    val gandegoo = read(CustomContentStore.GANDEGOO) { text ->
        json.decodeFromString<List<GgCategory>>(text).map { c ->
            LocalCat("${c.emoji} ${c.name}", c.questions.map { q -> "${q.points.toPersianDigits()} امتیازی — ${q.text}" })
        }
    }
    val pantomime = read(CustomContentStore.PANTOMIME) { text ->
        json.decodeFromString<List<PCategory>>(text).map { c ->
            LocalCat(
                "${c.emoji} ${c.name}",
                buildList {
                    c.words2.forEach { add("۲ — $it") }
                    c.words4.forEach { add("۴ — $it") }
                    c.words6.forEach { add("۶ — $it") }
                    if (c.golden.isNotBlank()) add("⭐ طلایی — ${c.golden}")
                },
            )
        }
    }
    return listOf("💣 دور" to dor, "😏 گنده‌گو" to gandegoo, "🎭 پانتومیم" to pantomime)
}

/** نمایش کتگوری‌ها و آیتم‌های افزوده‌شده به‌صورت محلی — فقط خواندنی */
@Composable
private fun MyContentDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sections = remember { loadLocalContent(context) }
    val isEmpty = sections.all { it.second.isEmpty() }
    var expanded by remember { mutableStateOf<String?>(null) }

    ContentDialogFrame(title = "محتوای من", onDismiss = onDismiss) {
        if (isEmpty) {
            Text(
                text = "هنوز محتوایی نساخته‌اید — از دکمه‌های «کتگوری جدید» شروع کنید.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sections.forEach { (gameTitle, cats) ->
                if (cats.isNotEmpty()) {
                    Text(
                        text = gameTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = VioletPrimary,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    cats.forEach { cat ->
                        val key = "$gameTitle/${cat.title}"
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(kiExtras.glass, RoundedCornerShape(16.dp))
                                .border(1.dp, kiExtras.glassBorder, RoundedCornerShape(16.dp))
                                .clickable { expanded = if (expanded == key) null else key }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = cat.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${cat.items.size.toPersianDigits()} مورد ${if (expanded == key) "▲" else "▼"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (expanded == key) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = cat.items.joinToString("\n"),
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        KButton(text = "بستن", style = KButtonStyle.Glass, accent = VioletPrimary, onClick = onDismiss)
    }
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
