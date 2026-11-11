package com.heeseung.drivekip.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WatermarkUtil {

    fun addWatermark(
        src: Bitmap,
        projectName: String,
        date: Date = Date()
    ): Bitmap {
        val result = src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
        val dateStr = dateFormat.format(date)
        val text = "[$projectName | $dateStr]"

        val width = result.width.toFloat()
        val height = result.height.toFloat()

        // Responsive font size based on image width
        val textSize = (width * 0.032f).coerceIn(24f, 64f)
        val padding = textSize * 0.6f

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val textWidth = paintText.measureText(text)
        val fontMetrics = paintText.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        val margin = width * 0.04f
        val bgRight = width - margin
        val bgBottom = height - margin
        val bgLeft = bgRight - textWidth - (padding * 2)
        val bgTop = bgBottom - textHeight - (padding * 2)

        val bgRect = RectF(bgLeft, bgTop, bgRight, bgBottom)
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
        }

        // Draw dark translucent rounded background for clarity
        canvas.drawRoundRect(bgRect, padding * 0.5f, padding * 0.5f, paintBg)

        // Draw text
        val textX = bgLeft + padding
        val textY = bgBottom - padding - fontMetrics.descent
        canvas.drawText(text, textX, textY, paintText)

        return result
    }
}
