package com.navidabbasian.kibord.hub

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.ui.components.ComingSoonBadge
import com.navidabbasian.kibord.core.ui.components.GlassCard

private data class GameGuide(
    val gameId: String,
    val steps: List<Pair<String, String>>,
    val tips: List<String>,
)

private val guides = listOf(
    GameGuide(
        gameId = "kalamz",
        steps = listOf(
            "تیم‌بندی" to "بازیکنان به تیم‌های ۲ نفره تقسیم می‌شن و هر تیم یک اسم انتخاب می‌کنه.",
            "نوشتن کلمات" to "هر بازیکن مخفیانه چند کلمه وارد می‌کنه؛ همه به بانک مشترک بازی اضافه می‌شن.",
            "راند اول: توضیح بده!" to "کلمه رو با حرف زدن توضیح بده — فقط حق نداری خودِ کلمه رو بگی!",
            "راند دوم: یک کلمه!" to "همون کلمات برمی‌گردن، اما این بار فقط با یک کلمه باید برسونی.",
            "راند سوم: پانتومیم!" to "بدون هیچ حرفی؛ فقط با ادا و اشاره.",
            "کی برد؟" to "هر کلمه‌ی درست یک امتیاز. بعد از سه راند، بیشترین امتیاز برنده‌ست!",
        ),
        tips = listOf(
            "بعد از هر نوبت می‌تونی کلمه‌های اشتباه ثبت‌شده رو حذف کنی.",
            "تایمر وسط نوبت قابل توقفه.",
            "اگه بانک کلمات تموم بشه، راند همون‌جا تموم می‌شه.",
        ),
    ),
    GameGuide(
        gameId = "gandegoo",
        steps = listOf(
            "تیم‌بندی" to "۲ یا ۳ تیمِ دونفره بسازید و برای تیم‌ها اسم انتخاب کنید.",
            "انتخاب از جدول" to "تیم‌ها نوبتی یک کتگوری و یک سوال ۲۰، ۴۰ یا ۶۰ امتیازی انتخاب می‌کنن.",
            "گنده‌گویی!" to "سوال لیستیه — مثلاً «قهرمان‌های جام جهانی». هر تیم بلند ادعا می‌کنه چند مورد می‌تونه بگه؛ بزرگ‌ترین ادعا بازی می‌کنه. نتیجه‌ی مزایده رو در اپ ثبت کنید.",
            "۳۰ ثانیه شمارش" to "تیم بازی‌کننده موردها رو می‌گه و با هر مورد یک بار دکمه‌ی بزرگ رو می‌زنه.",
            "امتیازدهی" to "به ادعا برسی: امتیاز کامل. نصف یا بیشترش رو بگی: امتیاز به حریف(ها) می‌رسه. کمتر از نصف: خودت منفی می‌خوری و بقیه مثبت می‌گیرن!",
            "کی برد؟" to "بعد از تمام‌شدن جدول، تیمی که بیشترین امتیاز رو داره برنده‌ست.",
        ),
        tips = listOf(
            "در ۲ تیمه نصف امتیاز و در ۳ تیمه یک‌چهارم امتیاز جابه‌جا می‌شه.",
            "اگه وسط شمارش به عدد ادعات برسی، همون لحظه برنده‌ی دست می‌شی.",
            "اشتباه شمردی؟ با دکمه‌ی کم‌کردن اصلاحش کن.",
        ),
    ),
    GameGuide(
        gameId = "pantomime_classic",
        steps = listOf(
            "دو گروه بشید" to "دو تیم بسازید و تعداد راندها رو انتخاب کنید. در هر راند هر تیم یک اجرا داره.",
            "انتخاب کلمه" to "تیم اجراکننده یک کتگوری و رده‌ی امتیازی (۲/۴/۶) انتخاب می‌کنه. کلمه فقط به اجراکننده نشون داده می‌شه!",
            "زمان اجرا" to "۲ امتیازی: ۱ دقیقه، ۴ امتیازی: ۹۰ ثانیه، ۶ امتیازی: ۲ دقیقه.",
            "پاداش سرعت" to "هر ۳۰ ثانیه‌ی کامل که از زمانت اضافه بمونه، ۱ امتیاز پاداش می‌گیری.",
            "موضوع طلایی 🏆" to "هر تیم یک بار در کل بازی می‌تونه ریسک طلایی کنه: ۳ دقیقه زمان، حدس بزنن ۳۰ امتیاز — حدس نزنن حذف فوری و باخت کل بازی!",
            "کی برد؟" to "بعد از راند آخر، تیمی که بیشترین امتیاز رو داره برنده‌ست.",
        ),
        tips = listOf(
            "اجراکننده حق نداره حرف بزنه، لب بزنه یا به چیزی اشاره‌ی مستقیم کنه.",
            "کلمه در حین اجرا پایین صفحه‌ست تا داورها بتونن درستی حدس رو تأیید کنن.",
            "ریسک طلایی رو برای وقتی نگه دار که عقبی!",
        ),
    ),
    GameGuide(
        gameId = "pantomime_rival",
        steps = listOf(
            "تیم‌بندی" to "۲ یا ۳ تیم بسازید.",
            "جدول امتیازها" to "۶ کتگوری با خانه‌های ۲، ۴ و ۶ امتیازی. تیم‌ها نوبتی خانه انتخاب و اجرا می‌کنن.",
            "اجرا" to "کلمه فقط به اجراکننده نشون داده می‌شه؛ هم‌تیمی‌ها باید در زمان مقرر حدس بزنن.",
            "زمان و پاداش" to "زمان‌ها مثل پانتومیم کلاسیکه و هر ۳۰ ثانیه‌ی اضافه ۱ امتیاز پاداش داره.",
            "کی برد؟" to "وقتی همه‌ی خانه‌های جدول بازی شد، بیشترین امتیاز برنده‌ست.",
        ),
        tips = listOf(
            "حدس نزدن فقط نوبت رو می‌سوزونه — امتیازی جابه‌جا نمی‌شه.",
            "خانه‌های ۶ امتیازی سخت‌ترن ولی زمان بیشتری هم دارن.",
        ),
    ),
    GameGuide(
        gameId = "dor",
        steps = listOf(
            "دور بچینید!" to "بازیکن‌ها دور تا دور می‌شینن؛ هم‌تیمی‌ها روبه‌روی هم. تیم‌های ۲ نفره خودکار ساخته می‌شن.",
            "دسته‌بندی و حالت" to "دسته‌های کلمات و حالت بازی (سریع یا حرفه‌ای) رو انتخاب کنید.",
            "کلمه رو برسون" to "کلمه رو برای بقیه توضیح بده؛ هر کی حدس زد، گوشی رو می‌گیره و نوبت به نفر بعدی دور می‌رسه.",
            "بمب در حال تیک‌تاکه!" to "اگه بمب موقع نوبتِ تو منفجر بشه، از زمان کل تیمت کم می‌شه!",
            "حذف تیم‌ها" to "زمانِ هر تیم که تموم بشه اون تیم حذف می‌شه.",
            "کی برد؟" to "آخرین تیمی که زنده می‌مونه برنده‌ست!",
        ),
        tips = listOf(
            "ده ثانیه‌ی آخرِ بمب، آهنگ تنش پخش می‌شه — دستپاچه نشو!",
            "دکمه‌ی رد کردن کلمه محدودیت زمانی داره.",
            "می‌تونی کلمات دلخواه خودتو از بخش افزودن کلمه به بازی اضافه کنی.",
        ),
    ),
)

/** تب آموزش هاب — راهنمای هر بازی به‌صورت بخش بازشونده */
@Composable
fun HowToPlayScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "آموزش بازی‌ها",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "هر بازی چطوری بازی می‌شه؟",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(count = gameCatalog.size, key = { gameCatalog[it].id }) { index ->
            val game = gameCatalog[index]
            GuideCard(game = game, guide = guides.find { it.gameId == game.id })
        }
    }
}

@Composable
private fun GuideCard(game: GameInfo, guide: GameGuide?) {
    var expanded by rememberSaveable(game.id) { mutableStateOf(false) }
    val available = guide != null

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        onClick = if (available) ({ expanded = !expanded }) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(game.accent.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = game.emoji, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (available) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ComingSoonBadge()
            }
        }

        if (expanded && guide != null) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                guide.steps.forEachIndexed { i, (title, desc) ->
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(game.accent.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${i + 1}".map { persianDigit(it) }.joinToString(""),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            game.accent.copy(alpha = 0.12f),
                            androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        text = "💡 نکته‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    guide.tips.forEach { tip ->
                        Text(
                            text = "• $tip",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun persianDigit(c: Char): Char =
    if (c in '0'..'9') ('۰' + (c - '0')) else c
