package com.navidabbasian.kibord.hub

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.navidabbasian.kibord.core.audio.LocalSoundManager
import com.navidabbasian.kibord.core.ui.components.BobbingEmoji
import com.navidabbasian.kibord.core.ui.components.ComingSoonBadge
import com.navidabbasian.kibord.core.ui.components.StickerTitle
import com.navidabbasian.kibord.core.ui.components.TicketCard
import com.navidabbasian.kibord.core.ui.components.blobShape
import com.navidabbasian.kibord.core.ui.components.breathing
import com.navidabbasian.kibord.core.ui.components.shineSweep
import com.navidabbasian.kibord.core.ui.theme.VioletPrimary
import com.navidabbasian.kibord.core.ui.theme.kiExtras
import com.navidabbasian.kibord.core.util.toPersianDigits

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
            "شک داشتید جواب درسته؟ «ویدیو چک» بازی و تایمر رو متوقف می‌کنه تا با خیال راحت داوری کنید.",
            "بعد از پایان شمارش، ۱۰ ثانیه فرصت بازبینی دارید تا عدد نهایی رو با دکمه‌های کم/زیاد اصلاح کنید — امتیاز با همون عدد حساب می‌شه.",
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
    GameGuide(
        gameId = "esm_famil",
        steps = listOf(
            "وصل شید!" to "همه روی یک وای‌فای یا هات‌اسپات باشید. هر کی با گوشی خودش بازی می‌کنه؛ اسمت رو بنویس تا شناسه‌ت بشه.",
            "میزبان و مهمان" to "یک نفر «میزبان شو» رو می‌زنه و بازی می‌سازه؛ بقیه از «بپیوند» بازی رو پیدا می‌کنن و وصل می‌شن.",
            "تنظیمات میزبان" to "میزبان مدت هر راند، تعداد راندها و موضوعات (اسم، فامیل، شهر، …) رو انتخاب می‌کنه.",
            "حرف بیار!" to "هر راند یک نفر نوبتی یک حرف الفبا انتخاب می‌کنه؛ همه باید برای هر موضوع کلمه‌ای با اون حرف بنویسن.",
            "استپ!" to "هر کی همه‌ی خونه‌هاش رو پر کنه می‌تونه استپ بزنه — راند برای همه همون لحظه بسته می‌شه.",
            "امتیازشماری" to "کلمه‌ی یکتا ۱۰، کلمه‌ی مشترک ۵ و تنها جواب‌دهنده‌ی یک موضوع ۲۰ امتیاز. به کلمه‌ی قلابی رای «رد» بدید!",
            "کی برد؟" to "بعد از راند آخر، مجموع امتیازها حساب می‌شه و بیشترین امتیاز برنده‌ست.",
        ),
        tips = listOf(
            "اینترنت لازم نیست — هات‌اسپات یکی از گوشی‌ها کافیه.",
            "اگه بازی توی لیست پیدا نشد، آدرسِ لابی میزبان رو دستی وارد کن.",
            "اگه وسط بازی قطع شدی، با همون اسم دوباره بپیوند تا سر جات برگردی.",
        ),
    ),
)

/** تب آموزش هاب — دفترچه‌ی فانتزی: سربرگ‌های گرادیانی کج و گام‌های سنگریزه‌ای */
@Composable
fun HowToPlayScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BobbingEmoji(emoji = "📖", fontSize = 52.sp)
                Spacer(modifier = Modifier.height(8.dp))
                StickerTitle(text = "آموزش بازی‌ها", accent = VioletPrimary, rotation = 2f)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "هر بازی چطوری بازی می‌شه؟ 🤔",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(count = gameCatalog.size, key = { gameCatalog[it].id }) { index ->
            val game = gameCatalog[index]
            GuideCard(game = game, guide = guides.find { it.gameId == game.id }, index = index)
        }
    }
}

@Composable
private fun GuideCard(game: GameInfo, guide: GameGuide?, index: Int) {
    val sound = LocalSoundManager.current
    var expanded by rememberSaveable(game.id) { mutableStateOf(false) }
    val available = guide != null
    val extras = kiExtras
    val tilt = if (index % 2 == 0) -1.2f else 1.2f
    val headerShape = blobShape(seed = index * 3 + 1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = if (index % 2 == 0) (-4).dp else 4.dp)
            .graphicsLayer { rotationZ = tilt }
            .animateContentSize()
    ) {
        // ---- سربرگ گرادیانی سنگریزه‌ای ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!expanded) Modifier.breathing(intensity = 0.012f, periodMs = 3200 + index * 400, phase = index * 1.3f) else Modifier)
                .background(
                    Brush.linearGradient(listOf(lerp(game.accent, Color.White, 0.08f), game.accent, game.accentDark)),
                    headerShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.38f), headerShape)
                .shineSweep(periodMs = 4200 + index * 400, phase = index * 0.5f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = available
                ) {
                    sound?.playButtonClick()
                    expanded = !expanded
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .graphicsLayer { rotationZ = -tilt * 4f }
                    .background(Color.White.copy(alpha = 0.25f), blobShape(seed = index * 7 + 3)),
                contentAlignment = Alignment.Center
            ) {
                BobbingEmoji(emoji = game.emoji, fontSize = 26.sp, phase = index * 1.4f)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (available) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White
                )
            } else {
                ComingSoonBadge()
            }
        }

        // ---- بدنه‌ی راهنما ----
        if (expanded && guide != null) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(extras.glassStrong, blobShape(seed = index * 5 + 2))
                    .border(1.dp, extras.glassBorder, blobShape(seed = index * 5 + 2))
                    .padding(16.dp)
            ) {
                guide.steps.forEachIndexed { i, (title, desc) ->
                    Row(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .offset(x = if (i % 2 == 0) 0.dp else 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .graphicsLayer { rotationZ = if (i % 2 == 0) -6f else 6f }
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(lerp(game.accent, Color.White, 0.2f), game.accent),
                                        center = Offset(0.3f, 0.25f),
                                        radius = 120f
                                    ),
                                    blobShape(seed = index * 11 + i)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (i + 1).toPersianDigits(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
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
                Spacer(modifier = Modifier.height(10.dp))
                TicketCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = game.accent,
                    tilt = if (index % 2 == 0) -1f else 1f
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BobbingEmoji(emoji = "💡", fontSize = 18.sp, phase = index * 2f)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "نکته‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        guide.tips.forEach { tip ->
                            Text(
                                text = "✦ $tip",
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
}
