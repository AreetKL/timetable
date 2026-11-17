package com.aamina.timetable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * 离线课表解析器。
 *
 * 路线一（网格重建）：用"星期X"表头确定列边界，用左侧节次列确定行带，
 * 列内按行距聚块，再从块内文本拆出 课程名 / 教师 / 教室。
 * 路线二（兜底）：找不到表头时按"周X 第X-X节 课程 教师 教室"的行模式解析。
 *
 * OCR 层：深色截图先反色再识别（ML Kit 对暗图效果差），识别结果太少时自动换
 * 另一种预处理再跑一遍，取课程数更多的结果。
 */
object TimetableParser {

    /** 最近一次离线识别总共拿到多少行文字、每路通道各认出几门课（诊断用）。 */
    @Volatile
    var lastLineCount: Int = 0
        private set

    @Volatile
    var lastChannelTrace: String = ""
        private set

    /**
     * 把选中的图片立即拷贝进应用缓存。
     * 很多图片来源（微信传输、网盘、部分相册提供者）的 Uri 只允许读取一次，
     * 而识别流程要读两轮（离线 + 在线兜底），重复 openInputStream 会返回 null，
     * 导致"图片读取失败"。先落盘成自己的文件，之后随便读多少次都行。
     * 返回 null = 拷贝失败（来源已失效）。
     */
    fun importToCache(context: Context, uri: Uri): File? = try {
        val dir = File(context.cacheDir, "import").apply { mkdirs() }
        // 只保留最近 3 张，避免缓存堆积
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.delete() }
        val f = File(dir, "img_${System.currentTimeMillis()}")
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { input.copyTo(it, 64 * 1024) }
            true
        } ?: false
        if (copied && f.length() > 0L) f else { f.delete(); null }
    } catch (e: Exception) {
        null
    }

    /** 纯数据行（坐标为像素），与 Android 类解耦，便于 JVM 单元测试。 */
    data class LineIn(val text: String, val l: Float, val t: Float, val r: Float, val b: Float) {
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        val h = b - t
    }

    private val WEEK_RE = Regex("(星期|周)[一二三四五六日天]")
    private val TEACHER_RE = Regex("老师|教师|教授|讲师|副教授|博士|主讲")
    private val SEC_RE = Regex("(?:第)?(\\d+)\\s*[-~—至到]\\s*(\\d+)(?:节)?|(?:第)?(\\d+)\\s*节")

    /** 表格里常见的分隔行（不是课程）。 */
    private val SEPARATOR_RE = Regex("^(午休|晚休|午间|晚间|中午|下午|晚上|休息)$")

    /** 疑似教室的行：@10 / 401 / +7#4 / A101 / 7#401 等。 */
    private val ROOM_LINE_RE = Regex("^[@＃+]?[0-9A-Za-z]{1,3}[#]?\\d{1,4}$|^[0-9A-Za-z]{1,3}[-#]\\d{2,4}$|^\\d{3,}$")

    private val PURE_TIME_RE = Regex("^\\d{1,2}[:：]\\d{2}$")

    /** 表头下的日期行，如 11/09、11-09、11.09。 */
    private val PURE_DATE_RE = Regex("^\\d{1,2}[/\\-.]\\d{1,2}$")

    suspend fun parse(context: Context, uri: Uri): List<Course> {
        lastLineCount = 0
        lastChannelTrace = ""
        val src = decodeScaled(context, uri) ?: run {
            lastChannelTrace = "图片解码失败"
            return emptyList()
        }
        val bmp = upscale(src, 1400)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val dark = meanLuminance(bmp) < 110f
            // 四通道识别：不同预处理下 OCR 命中的行不同，全部跑一遍再合并，
            // 任何一条通道认出来的课都不会丢。前两遍命中足够多时提前收工。
            val channels = ArrayList<Bitmap>(4)
            if (dark) {
                channels.add(invert(bmp))   // 暗图：反色通道效果通常最好，先跑
                channels.add(bmp)
            } else {
                channels.add(bmp)
                channels.add(invert(bmp))
            }
            channels.add(contrast(bmp, 1.55f))
            channels.add(binarize(bmp, dark))

            val results = ArrayList<List<Course>>(4)
            val trace = StringBuilder()
            for (i in channels.indices) {
                val r = runCatching { recognizeAndParse(recognizer, channels[i]) }
                    .getOrDefault(emptyList())
                if (r.isNotEmpty()) results.add(r)
                trace.append("通道${i + 1}：${r.size}门 ")
                // 前两遍已经认出足够多，后面通道不必再跑
                if (i >= 1 && results.maxOfOrNull { it.size } ?: 0 >= 8) break
            }
            lastChannelTrace = trace.toString().trim() + "；共 ${lastLineCount} 行文字"
            return mergeCourses(results)
        } finally {
            recognizer.close()
            if (bmp !== src) bmp.recycle()
            src.recycle()
        }
    }

    /**
     * 合并多个识别通道的课程：同一时段（day+startSection）取信息最全的一条，
     * 其余全部保留并按时间排序。不同通道识别出的不同课程会互相补漏。
     */
    internal fun mergeCourses(all: List<List<Course>>): List<Course> {
        val bySlot = LinkedHashMap<Pair<Int, Int>, Course>()
        for (list in all) for (c in list) {
            val key = c.day to c.startSection
            val cur = bySlot[key]
            if (cur == null || completeness(c) > completeness(cur)) bySlot[key] = c
        }
        return bySlot.values.sortedWith(
            compareBy({ it.day }, { it.startSection }, { it.endSection })
        )
    }

    /** 课程信息完整度：有教室/教师加分，名字长（非碎片）加分。 */
    private fun completeness(c: Course): Int =
        (if (c.room.isNotBlank()) 2 else 0) +
            (if (c.teacher.isNotBlank()) 2 else 0) +
            (if (c.name.length > 2) 1 else 0)

    private suspend fun recognizeAndParse(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bmp: Bitmap
    ): List<Course> {
        val text = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        return parseText(text)
    }

    // ---------- ML Kit ----------

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { if (cont.isActive) cont.cancel() }
    }

    private fun decodeScaled(context: Context, uri: Uri, longest: Int = 2304): Bitmap? =
        Img.decode(context, uri, longest)

    private fun upscale(src: Bitmap, minLongest: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest >= minLongest) return src
        val ratio = minLongest.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun invert(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val matrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** 对比度增强：低对比浅色字 / 浅色背景下的文字经此通道更容易命中。 */
    private fun contrast(src: Bitmap, factor: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val offset = -64f
        val matrix = ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, offset,
                0f, factor, 0f, 0f, offset,
                0f, 0f, factor, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** 二值化通道：灰度 → 以全局均值为阈值拉成纯黑白（统一为深字浅底），对均匀背景截图很有效。 */
    private fun binarize(src: Bitmap, dark: Boolean): Bitmap {
        val out = if (dark) invert(src) else src.copy(src.config!!, false)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        var sum = 0L
        for (p in px) {
            val g = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            sum += g
        }
        val threshold = (sum / px.size).toInt()
        for (i in px.indices) {
            val p = px[i]
            val g = (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
            px[i] = if (g < threshold) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun meanLuminance(bmp: Bitmap): Float {
        val w = 24
        val h = 24
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        small.recycle()
        var sum = 0L
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (r * 299 + g * 587 + b * 114) / 1000
        }
        return sum.toFloat() / px.size
    }

    // ---------- 解析入口 ----------

    fun parseText(result: Text): List<Course> {
        val input = result.textBlocks.flatMap { blk -> blk.lines }.map { line ->
            val r = line.boundingBox ?: android.graphics.Rect(0, 0, 0, 0)
            LineIn(line.text.filter { !it.isWhitespace() },
                r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
        }
        if (input.size > lastLineCount) lastLineCount = input.size
        return parseLines(input)
    }

    /** 纯 Kotlin 解析入口（单元测试直接调用这个）。 */
    fun parseLines(input: List<LineIn>): List<Course> {
        val lines = input.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val grid = parseGrid(lines)
        if (grid.isNotEmpty()) return grid
        return parseByLine(lines)
    }

    // ---------- 网格重建 ----------

    private fun parseGrid(lines: List<LineIn>): List<Course> {
        // 1) 表头行：内容含"周X/星期X"且很短
        val headerLike = lines.filter {
            WEEK_RE.containsMatchIn(it.text) && it.text.length <= 8
        }
        if (headerLike.size < 2) return emptyList()

        val avgH = lines.map { it.h }.average().toFloat().coerceAtLeast(1f)

        // 聚成一行（y 接近的算同一排表头），取数量最多、最靠上的一排
        val sorted = headerLike.sortedBy { it.cy }
        val clusters = mutableListOf<MutableList<LineIn>>(mutableListOf(sorted[0]))
        for (i in 1 until sorted.size) {
            if (abs(sorted[i].cy - sorted[i - 1].cy) < avgH * 1.6f) clusters.last().add(sorted[i])
            else clusters.add(mutableListOf(sorted[i]))
        }
        val header = clusters.filter { it.size >= 2 }.minByOrNull { it.first().cy } ?: return emptyList()
        if (header.size < 2) return emptyList()

        // 2) 列中心 → 星期
        data class Col(val day: Int, val cx: Float)
        val cols = header.sortedBy { it.cx }.mapNotNull { ln ->
            val m = Regex("[一二三四五六日天]").find(ln.text)?.value ?: return@mapNotNull null
            Col(dayOf(m), ln.cx)
        }
        if (cols.size < 2) return emptyList()

        val bounds = mutableListOf<Float>()
        bounds.add(cols[0].cx - (cols[1].cx - cols[0].cx) / 2)
        for (i in 1 until cols.size) bounds.add((cols[i - 1].cx + cols[i].cx) / 2)
        bounds.add(cols.last().cx + (cols.last().cx - cols[cols.size - 2].cx) / 2)

        val headerBottom = header.maxOf { it.b }

        // 3) 左列节次标记（排除 08:10 这类时间）
        val leftBound = bounds.first()
        val secMarks = lines.filter {
            it.cx < leftBound && it.cy > headerBottom - avgH &&
                !it.text.contains(':') && !it.text.contains('：')
        }.mapNotNull { ln ->
            val m = Regex("^第?\\s*(\\d{1,2})\\s*节?$").find(ln.text) ?: return@mapNotNull null
            val no = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            if (no in 1..20) no to ln.cy else null
        }.sortedBy { it.second }

        // 节次行带的分界（相邻节次 y 的中点）
        val bandMids = mutableListOf<Float>()
        for (i in 1 until secMarks.size) bandMids.add((secMarks[i - 1].second + secMarks[i].second) / 2f)

        // 4) 每个星期列内的文本聚块
        val courses = mutableListOf<Course>()
        cols.forEachIndexed { idx, col ->
            val colLines = lines.filter {
                it.cx >= bounds[idx] && it.cx < bounds[idx + 1] && it.cy > headerBottom &&
                    !SEPARATOR_RE.containsMatchIn(it.text) && !PURE_TIME_RE.containsMatchIn(it.text) &&
                    !PURE_DATE_RE.containsMatchIn(it.text)
            }.sortedBy { it.t }
            if (colLines.isEmpty()) return@forEachIndexed

            // 块内行距远小于块间距：阈值取本列行高中位数的一半
            val medianH = colLines.map { it.h }.sorted().let { l ->
                if (l.isEmpty()) avgH else l[l.size / 2]
            }.coerceAtLeast(1f)
            val gapThreshold = medianH * 0.6f

            val blocks = mutableListOf<MutableList<LineIn>>(mutableListOf(colLines[0]))
            for (i in 1 until colLines.size) {
                if (colLines[i].t - colLines[i - 1].b > gapThreshold) blocks.add(mutableListOf(colLines[i]))
                else blocks.last().add(colLines[i])
            }

            val blockRanges = blocks.map { blk ->
                blk.minOf { it.t } to blk.maxOf { it.b }
            }

            var guessNext = 1
            blocks.forEachIndexed { bi, blk ->
                val (top, bottom) = blockRanges[bi]
                val texts = blk.map { it.text }

                // 节次：块覆盖到的节次标记
                val covered = secMarks
                    .filter { it.second >= top - medianH * 0.7f && it.second <= bottom + medianH * 0.9f }
                    .map { it.first }
                var s1: Int
                var s2: Int
                when {
                    covered.size >= 2 -> { s1 = covered.min(); s2 = covered.max() }
                    covered.size == 1 -> {
                        s1 = covered[0]
                        s2 = s1
                        // 块底部越过本节与下一节的分界 → 是连堂课（如 1-2 节）
                        val nextMark = secMarks.firstOrNull { it.first == s1 + 1 }
                        if (nextMark != null) {
                            val mid = (secMarks.first { it.first == s1 }.second + nextMark.second) / 2f
                            if (bottom > mid) s2 = s1 + 1
                        }
                        // 文本只占单节且下一块隔了空档 → 空档是连堂（如 1-2 节只有一行字）
                        if (s2 == s1 && bi + 1 < blocks.size) {
                            val nextCovered = secMarks.filter {
                                it.second >= blockRanges[bi + 1].first - medianH &&
                                    it.second <= blockRanges[bi + 1].second + medianH
                            }.map { it.first }
                            val ns = nextCovered.minOrNull()
                            if (ns != null && ns > s1 + 1) s2 = s1 + 1
                        }
                    }
                    else -> { s1 = guessNext; s2 = guessNext + 1 }
                }
                guessNext = s2 + 1

                val course = splitFields(texts)
                if (course.name.isNotBlank()) {
                    courses.add(
                        Course(
                            name = course.name, teacher = course.teacher, room = course.room,
                            day = col.day, startSection = s1, endSection = s2.coerceAtLeast(s1)
                        )
                    )
                }
            }
        }

        // 按列内同块去重（同列同节同名的重复识别）
        return courses.distinctBy { Triple(it.day, it.startSection, it.name) }
    }

    private class Fields(var name: String, var teacher: String, var room: String)

    /**
     * 从一格内的多行文本拆出课程名 / 教师 / 教室。
     * 重点处理断行碎片："Windo"+"ws" → Windows，"哲学与"+"人生" → 哲学与人生，
     * "@7"+"401" → 7 401，"+7#4"+"01" → 7#401。
     */
    private fun splitFields(texts: List<String>): Fields {
        data class Part(val raw: String, val kind: Int) // 0=名称 1=教室 2=教师 3=噪声

        val parts = texts.map { raw ->
            val t = raw.trim()
            when {
                t.isEmpty() -> Part(t, 3)
                PURE_TIME_RE.containsMatchIn(t) -> Part(t, 3)
                PURE_DATE_RE.containsMatchIn(t) -> Part(t, 3)
                SEPARATOR_RE.containsMatchIn(t) -> Part(t, 3)
                Regex("^([1-9]|1[0-2])$").containsMatchIn(t) -> Part(t, 3)   // 游离的节号（"01" 这类可能是教室碎片，不排除）
                t.contains('@') || t.contains('＃') || t.startsWith('+') -> Part(t, 1)
                ROOM_LINE_RE.containsMatchIn(t) -> Part(t, 1)
                TEACHER_RE.containsMatchIn(t) -> Part(t, 2)
                else -> Part(t, 0)
            }
        }

        val nameParts = parts.filter { it.kind == 0 }
        val roomParts = parts.filter { it.kind == 1 }
        val teacherParts = parts.filter { it.kind == 2 }

        // 名称：第一段 + 合法的断行续接
        val name = StringBuilder()
        if (nameParts.isNotEmpty()) {
            name.append(cleanName(nameParts[0].raw))
            for (i in 1 until nameParts.size) {
                val frag = nameParts[i].raw
                val prev = nameParts[i - 1].raw
                val isLatin = frag.any { it in 'A'..'Z' || it in 'a'..'z' }
                val single = frag.length == 1
                val truncated = prev.length == 3 && frag.length <= 3 &&
                    frag.all { it in '\u4e00'..'\u9fff' }
                if (isLatin || single || truncated) name.append(frag)
                else break // 更可能是教师等其它字段，停止拼接
            }
        }

        // 教师：除已并入名称之外的独立中文短行
        val teacher = teacherParts.firstOrNull()?.raw?.replace(TEACHER_RE, "")?.trim() ?: ""

        // 教室：合并碎片。带 #/+ 的按断 token 直拼，其余用空格连接
        var room = ""
        for (p in roomParts) {
            var frag = p.raw.replace(Regex("^[@＃+]"), "").trim()
            if (frag.isEmpty()) continue
            room = when {
                room.isEmpty() -> frag
                room.contains('#') || p.raw.startsWith('+') -> room + frag
                else -> "$room $frag"
            }
        }
        room = room.replace(Regex("^\\s*"), "").trim()

        return Fields(name.toString().trim(), teacher, room)
    }

    private fun cleanName(s: String): String = s
        .replace(Regex("第?\\d+\\s*[-~—]\\s*\\d+\\s*节?"), "")
        .replace(Regex("^\\d+\\s*"), "")
        .replace(WEEK_RE, "")
        .trim()

    /** 兜底：按"周X 第X-X节 课程 教师 教室"的行模式解析。 */
    private fun parseByLine(lines: List<LineIn>): List<Course> {
        val courses = mutableListOf<Course>()
        var current: Course? = null
        var pending = mutableListOf<String>()
        fun flush() {
            val c = current ?: return
            if (pending.isNotEmpty()) {
                val f = splitFields(pending)
                if (f.name.isNotBlank()) c.name = f.name
                if (f.teacher.isNotBlank()) c.teacher = f.teacher
                if (f.room.isNotBlank()) c.room = f.room
            }
            pending = mutableListOf()
        }
        for (ln in lines.sortedBy { it.t }) {
            val t = ln.text
            if (SEPARATOR_RE.containsMatchIn(t) || PURE_TIME_RE.containsMatchIn(t)) continue
            val week = Regex("[一二三四五六日天]").find(WEEK_RE.find(t)?.value ?: "")?.value
            val sec = SEC_RE.find(t)
            if (week != null && sec != null) {
                flush()
                val s1 = sec.groupValues[1].toIntOrNull() ?: 1
                val s2 = sec.groupValues[2].toIntOrNull() ?: s1
                var name = t
                    .replace(WEEK_RE, "")
                    .replace(Regex("第?\\d+\\s*[-~—至到]?\\s*\\d*\\s*节?"), "")
                    .trim()
                current = Course(
                    name = name.ifBlank { "课程" },
                    day = dayOf(week), startSection = s1, endSection = maxOf(s1, s2)
                )
            } else if (current != null) {
                if (t.length <= 4 && Regex("^第?\\s*\\d{1,2}\\s*节?$").containsMatchIn(t)) continue
                pending.add(t)
            }
        }
        flush()
        return courses.distinctBy { Triple(it.day, it.startSection, it.name) }
    }

    private fun dayOf(cn: String): Int = when (cn) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; else -> 7
    }
}
