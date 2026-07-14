package com.navidabbasian.kibord.core.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.navidabbasian.kibord.R
import java.io.File

/**
 * کارت بردِ قابل اشتراک: یک تصویر با هویت بصری اپ می‌سازد و
 * پنجره‌ی اشتراک‌گذاری را باز می‌کند تا برنده پزش را توی گروه بدهد.
 */
object WinnerCard {

    private const val W = 1080
    private const val H = 1350

    fun share(
        context: Context,
        gameTitle: String,
        gameEmoji: String,
        winnerText: String,
        scoreLines: List<Pair<String, String>>,
    ) {
        try {
            val bitmap = draw(context, gameTitle, gameEmoji, winnerText, scoreLines)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "kibord_win.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "$winnerText — $gameTitle 🏆 «کی برد؟»")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "پز بده به…"))
        } catch (_: Exception) {
        }
    }

    private fun draw(
        context: Context,
        gameTitle: String,
        gameEmoji: String,
        winnerText: String,
        scoreLines: List<Pair<String, String>>,
    ): Bitmap {
        val black = ResourcesCompat.getFont(context, R.font.vazirmatn_black) ?: Typeface.DEFAULT_BOLD
        val bold = ResourcesCompat.getFont(context, R.font.vazirmatn_bold) ?: Typeface.DEFAULT_BOLD
        val medium = ResourcesCompat.getFont(context, R.font.vazirmatn_medium) ?: Typeface.DEFAULT

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // ---- پس‌زمینه: گرادیان بنفش برند ----
        val bg = Paint().apply {
            shader = LinearGradient(
                0f, 0f, W.toFloat(), H.toFloat(),
                intArrayOf(0xFF8B7FD6.toInt(), 0xFF6C5FC7.toInt(), 0xFF4E4390.toInt()),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)

        // حباب‌های تزئینی
        val bubble = Paint().apply { color = Color.WHITE; alpha = 22 }
        canvas.drawCircle(120f, 180f, 150f, bubble)
        canvas.drawCircle(W - 90f, 320f, 110f, bubble)
        canvas.drawCircle(W - 200f, H - 160f, 170f, bubble)
        canvas.drawCircle(160f, H - 300f, 90f, bubble)

        val center = W / 2f

        // ---- سربرگ ----
        val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 150f; textAlign = Paint.Align.CENTER }
        canvas.drawText("🏆", center, 250f, emojiPaint)

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = black; textSize = 96f; color = Color.WHITE; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("کی برد؟", center, 390f, title)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 52f; color = 0xFFFFE8A3.toInt(); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$gameEmoji $gameTitle", center, 480f, sub)

        // ---- کارت سفید برنده ----
        val cardTop = 560f
        val cardBottom = (cardTop + 220f + scoreLines.size * 86f + 40f).coerceAtMost(H - 180f)
        val card = RectF(70f, cardTop, W - 70f, cardBottom)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 235 }
        canvas.drawRoundRect(card, 48f, 48f, cardPaint)

        val winnerLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = medium; textSize = 42f; color = 0xFF8A8798.toInt(); textAlign = Paint.Align.CENTER
        }
        canvas.drawText("برنده شد:", center, cardTop + 85f, winnerLabel)

        val winner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = black; textSize = 78f; color = 0xFF6C5FC7.toInt(); textAlign = Paint.Align.CENTER
        }
        canvas.drawText(clip(winnerText, 22), center, cardTop + 185f, winner)

        // ---- خط‌های امتیاز ----
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 46f; color = 0xFF3B3947.toInt(); textAlign = Paint.Align.RIGHT
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = black; textSize = 46f; color = 0xFF6C5FC7.toInt(); textAlign = Paint.Align.LEFT
        }
        var y = cardTop + 280f
        scoreLines.take(8).forEach { (name, score) ->
            if (y < cardBottom - 30f) {
                canvas.drawText(clip(name, 20), W - 140f, y, namePaint)
                canvas.drawText(score, 140f, y, scorePaint)
                y += 86f
            }
        }

        // ---- پانوشت ----
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold; textSize = 44f; color = Color.WHITE; alpha = 220; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🎲 «کی برد؟» — بازی‌های دورهمی ایرانی", center, H - 90f, footer)

        return bitmap
    }

    private fun clip(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"
}
