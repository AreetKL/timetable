package com.aamina.timetable

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 把课表画成一张可以直接发群里的长图。
 *
 * 为什么不用截屏：截屏只能截到一屏，且带着状态栏和 FAB；这里用 Canvas 手绘，
 * 尺寸、配色、留白全部可控，出来的就是一张成品海报。
 */
object ShareCard {

    private const val W = 1080
    private const val PAD = 52f
    private const val HEAD_H = 260f
    private const val ROW_H = 120f
    private const val LEFT_W = 96f
    private const val FOOT_H = 110f

    // 米白底 + 墨绿字，和图标同一套色，不像模板生成的东西
    private const val BG = 0xFFFAF7F0.toInt()
    private const val INK = 0xFF1E2A26.toInt()
    private const val MUTED = 0xFF7A8580.toInt()
    private const val LINE = 0xFFE2DED3.toInt()
    private const val ACCENT = 0xFF1F3D34.toInt()
    private const val WARM = 0xFFE0A75C.toInt()

    private val dayEn = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    fun render(
        context: Context,
        courses: List<Course>,
        times: SectionTimes,
        week: Int?,
        totalWeeks: Int,
        showWeekend: Boolean = true
    ): File {
        val dayCount = if (showWeekend) 7 else 5
        val rows = times.totalSections.coerceIn(1, 16)
        val colW = (W - PAD * 2 - LEFT_W) / dayCount
        val gridTop = HEAD_H + 64f
        val bodyH = rows * ROW_H
        val H = (gridTop + bodyH + FOOT_H).toInt()

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(BG)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- 顶部 ----
        p.color = ACCENT
        p.style = Paint.Style.FILL
        c.drawCircle(PAD + 9f, 92f, 9f, p)

        p.color = INK
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 58f
        drawSpaced(c, "TIMETABLE", PAD + 34f, 110f, p, 6f)

        // 右上角周数
        if (week != null && week >= 1) {
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 30f
            p.color = WARM
            val txt = "W$week"
            c.drawText(txt, W - PAD - p.measureText(txt), 96f, p)
            p.typeface = Typeface.DEFAULT
            p.textSize = 22f
            p.color = MUTED
            val sub = "/ $totalWeeks"
            c.drawText(sub, W - PAD - p.measureText(sub), 128f, p)
        }

        // 日期副标题
        p.typeface = Typeface.DEFAULT
        p.textSize = 26f
        p.color = MUTED
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
        c.drawText("$date · 第 ${week ?: "-"} 周 · 共 $totalWeeks 周", PAD, 170f, p)

        // 细分隔线
        p.color = LINE
        p.strokeWidth = 2f
        c.drawLine(PAD, HEAD_H + 20f, W - PAD, HEAD_H + 20f, p)

        // ---- 星期表头 ----
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 24f
        for (d in 0 until dayCount) {
            p.color = if (d >= 5) MUTED else INK
            p.textAlign = Paint.Align.CENTER
            val x = PAD + LEFT_W + colW * d + colW / 2f
            c.drawText(dayEn[d], x, HEAD_H + 56f, p)
        }
        p.textAlign = Paint.Align.LEFT

        // ---- 网格 + 节次 ----
        p.strokeWidth = 1.5f
        for (r in 1 until rows) {
            p.color = LINE
            val y = gridTop + r * ROW_H
            c.drawLine(PAD + LEFT_W, y, W - PAD, y, p)
        }
        // 每天一条竖线
        for (d in 0..dayCount) {
            p.color = LINE
            val x = PAD + LEFT_W + colW * d
            c.drawLine(x, gridTop, x, gridTop + bodyH, p)
        }

        // 节次号
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 26f
        p.color = MUTED
        p.textAlign = Paint.Align.CENTER
        for (r in 1..rows) {
            val y = gridTop + (r - 1) * ROW_H + ROW_H / 2f + 9f
            c.drawText("$r", PAD + LEFT_W / 2f, y, p)
        }
        p.textAlign = Paint.Align.LEFT

        // ---- 课程块 ----
        courses.forEach { course ->
            if (course.day > dayCount) return@forEach
            val s = course.startSection
            val e = course.endSection.coerceAtLeast(s)
            if (s < 1 || s > rows) return@forEach
            val top = gridTop + (s - 1) * ROW_H + 6f
            val bottom = gridTop + e.coerceAtMost(rows) * ROW_H - 6f
            val left = PAD + LEFT_W + colW * (course.day - 1) + 5f
            val right = left + colW - 10f

            val argb = Palette.of(course).toArgb()
            val block = RectF(left, top, right, bottom)

            // 底色：同色系淡填充
            p.style = Paint.Style.FILL
            p.color = withAlpha(argb, 30)
            c.drawRoundRect(block, 16f, 16f, p)

            // 左侧色条
            p.color = withAlpha(argb, 235)
            c.drawRoundRect(
                RectF(left, top + 10f, left + 7f, bottom - 10f), 4f, 4f, p
            )

            // 课名（最多两行）
            p.color = INK
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textSize = 28f
            val textLeft = left + 20f
            val maxW = right - textLeft - 10f
            val nameLines = wrap(course.name, p, maxW, 2)
            var ty = top + 18f + 28f
            nameLines.forEach { line ->
                c.drawText(line, textLeft, ty, p)
                ty += 34f
            }

            // 教室 / 老师
            val sub = listOfNotNull(
                course.room.takeIf { it.isNotBlank() },
                course.teacher.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (sub.isNotBlank() && bottom - ty > 12f) {
                p.typeface = Typeface.DEFAULT
                p.textSize = 20f
                p.color = MUTED
                c.drawText(wrap(sub, p, maxW, 1).firstOrNull() ?: "", textLeft, ty + 6f, p)
            }
        }

        // ---- 底部 ----
        p.typeface = Typeface.DEFAULT
        p.textSize = 22f
        p.color = MUTED
        c.drawText("由 课表日历 生成 · 识别自课表截图", PAD, H - 46f, p)
        p.color = WARM
        p.textSize = 22f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tag = "TIMETABLE"
        c.drawText(tag, W - PAD - p.measureText(tag), H - 46f, p)

        val dir = File(context.cacheDir, "shots").apply { mkdirs() }
        val file = File(dir, "timetable-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    /** 一键分享：系统面板里选微信 / QQ / 相册都行。 */
    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享课表"))
    }

    private fun withAlpha(argb: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF)

    /** 按实际字宽折行，最多 [maxLines] 行，最后一行超了加省略号。 */
    private fun wrap(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        var line = StringBuilder()
        for (ch in text) {
            val test = line.toString() + ch
            if (paint.measureText(test) > maxWidth && line.isNotEmpty()) {
                out.add(line.toString())
                line = StringBuilder().append(ch)
                if (out.size == maxLines) break
            } else {
                line.append(ch)
            }
        }
        if (out.size < maxLines && line.isNotEmpty()) out.add(line.toString())
        if (out.size == maxLines) {
            val last = out.last()
            if (last.length < text.length && paint.measureText(last + "…") <= maxWidth) {
                out[out.size - 1] = last.trimEnd() + "…"
            }
        }
        return out
    }

    /** 逐字绘制实现字距——给标题用，比默认紧排更像排版过的。 */
    private fun drawSpaced(c: Canvas, text: String, x: Float, y: Float, paint: Paint, spacing: Float) {
        var cx = x
        text.forEach { ch ->
            val s = ch.toString()
            c.drawText(s, cx, y, paint)
            cx += paint.measureText(s) + spacing
        }
    }
}
