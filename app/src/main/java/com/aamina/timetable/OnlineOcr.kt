package com.aamina.timetable

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** 在线识别失败时抛出，message 是可以直接展示给用户的原因。 */
class OnlineException(message: String) : Exception(message)

/**
 * 在线识别（可选）：智谱免费多模态模型直接看课表图，输出结构化课程 JSON。
 *
 * 这一层现在有三重保险，因为"模型明明看懂了、App 却一条都没解析出来"是最难查的失败：
 *  1. 模型链：首选模型没解析出东西，自动换更强的 [FALLBACK_MODEL] 再试一次；
 *  2. JSON 提取：不再只认"第一个 [ 到最后一个 ]"，改为括号配平扫出所有数组片段
 *     （兼容 {"周一":[...],"周二":[...]} 这种按天分组、以及分多段输出）；
 *  3. 文本兜底：JSON 全废时，按 markdown 表格 / "周一 第1-2节 高数" 行模式硬解析。
 *
 * 失败时 [lastRaw] / [lastNote] 会留下现场，界面可以直接展示给用户排查。
 */
object OnlineOcr {

    private const val ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions"

    /** 可选模型：默认轻量稳定；Thinking 带推理更准；4.6V 为旗舰（限时免费）。 */
    val MODELS = listOf(
        "glm-4v-flash" to "GLM-4V-Flash（轻快）",
        "glm-4.1v-thinking-flash" to "GLM-4.1V-Thinking（更准）",
        "glm-4.6v" to "GLM-4.6V（旗舰·限免）"
    )
    const val DEFAULT_MODEL = "glm-4v-flash"

    /** 首选模型解析不出课程时自动补跑的模型。 */
    private const val FALLBACK_MODEL = "glm-4.1v-thinking-flash"

    /** 最近一次模型原始返回（诊断用）。 */
    @Volatile
    var lastRaw: String = ""
        private set

    /** 最近一次用的模型名。 */
    @Volatile
    var lastModel: String = ""
        private set

    /** 最近一次调用的过程摘要：每个模型返回多少字、解析出几条。 */
    @Volatile
    var lastNote: String = ""
        private set

    /**
     * 主提示词：要求模型**输出一张固定表头的 Markdown 表格**。
     *
     * 为什么不是 JSON：多模态模型看图时最自然的行为就是"重画一遍表格"，
     * 让它直接输出表格，格式出错率远低于手写 JSON 的花括号/引号；
     * App 这边只按列名取字段，一行一门课，天然不会把整块课程合并成一条。
     */
    private val TABLE_PROMPT = """
        你是课表识别助手。看这张课表图，把它原样还原成一张 Markdown 表格。
        只输出表格本身，不要解释、不要代码块、不要 JSON。

        表头必须是这一行，5 列，一个不多一个不少：
        | 星期 | 节次 | 课程 | 教师 | 教室 |

        规则：
        - 一格一门课就是一行；同一门课出现在不同天、或同一天不同节次，各占一行，绝不合并。
        - 星期写「周一」到「周日」。
        - 节次写该课程块在表格最左侧占据的节次数字区间，如 1-2、3、5-6。跨两行写 1-2，只占一行就写同一个数字，如 7。
        - 课程写完整名称；单元格里被换行拆断的字要拼回完整单词（"Windo"+"ws" → Windows，"公共艺"+"术" → 公共艺术）。
        - 教师、教室按图上内容填，没有就留空。
        - 晚自习、早自习、班会也要输出，课程名照写。
        - 图里哪怕只有一门课，也要按表头输出一行；完全不是课表才输出空。
    """.trimIndent()

    /** 备用提示词：老路线，让模型吐 JSON。表格解析为 0 时换模型再试一次用它。 */
    private val JSON_PROMPT = """
        你是课表识别助手。先理清这张课表的表格结构：最上面一行是星期表头（周一…周日，可能带日期），最左侧一列是节次号（1、2、3…，可能带时间）。
        然后逐格读取内容，把每一门课输出为 JSON 数组，只输出 JSON，不要任何其他文字：
        [{"name":"课程名","day":1,"rowStart":1,"rowEnd":2,"startTime":"08:10","endTime":"09:50","room":"7 401","teacher":""}]
        规则：
        - day：1=周一 … 7=周日，按它所在的表头列判断。
        - rowStart/rowEnd：★最重要★ 直接读该课程块在表格最左侧占据的节次号（数字！），跨两行就是 1 和 2，只占一行就是两个相同的数字。不要自己推算、不要输出文字。
        - startTime/endTime：该块在图中标注的时间，格式 "08:10"；图上没有就填 ""。
        - room：教室（如 7 401、10 503、7#401），没有则 ""；teacher：教师，没有则 ""。
        - ★ 必须完整：逐个格子读取，每个有内容的格子都要输出，包括只有一门课的列（周六/周日）、以及最下面的晚自习。宁可多输出也不要漏。
        - ★ 同一门课出现在不同天或不同节次（例如周一 3-4 节和 5-6 节都有 Python），每个时段单独输出一条，绝不合并。
        - 晚自习、早自习、班会也输出，name 就写"晚自习"。
        - 单元格内断行、断词的文字要拼完整（"Windo"+"ws" → Windows，"公共艺"+"术" → 公共艺术）。
        - 注意不要把相邻单元格或相邻列的内容混进来。
        - 只要图里有课表格子、哪怕只有一门课，也必须按格式输出数组；只有完全不是课表时才输出 []。
    """.trimIndent()

    /** 用户可能把 "Bearer xxx" 整段粘进来，统一清洗。 */
    private fun cleanKey(raw: String): String =
        raw.trim().removePrefix("Bearer").trim().trim('"', '\'').trim()

    suspend fun parse(
        context: Context,
        uri: Uri,
        cfg: AiConfig,
        times: SectionTimes? = null,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): List<Course> {
        val key = cleanKey(cfg.apiKey)
        if (key.isEmpty()) throw OnlineException("未填写 API Key，请到设置里填写")
        if (cfg.endpoint.isBlank()) throw OnlineException("没填服务地址，请在设置里选择服务或填写 baseUrl")
        lastRaw = ""
        lastModel = ""
        lastNote = ""
        // 路线一（优先）：PNG/JPEG/WebP 原图直传，完全不经过解码，
        // 彻底绕开"GIF/HEIC 等格式解不出 Bitmap"的问题
        onProgress(6, "读取图片")
        val raw = withContext(Dispatchers.IO) { rawImage(context, uri) }
        if (raw == null) onProgress(14, "处理图片")
        // 路线二：解码 → 压缩成 JPEG 再传
        val b64 = raw?.second ?: withContext(Dispatchers.IO) { encodeImage(context, uri) }
        if (b64 == null) {
            val why = withContext(Dispatchers.IO) { describeFile(context, uri) }
            throw OnlineException("图片无法读取（$why），请换一张图试试")
        }
        val mime = raw?.first ?: "image/jpeg"
        onProgress(24, "上传图片")
        return withContext(Dispatchers.IO) {
            try {
                runChain(b64, key, cfg, times, mime, onProgress)
            } catch (e: OnlineException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw OnlineException("连接超时，请检查网络后重试")
            } catch (e: IOException) {
                throw OnlineException("网络连接失败：${e.message?.take(60) ?: "请检查网络"}")
            } catch (e: Exception) {
                throw OnlineException("未知错误：${e.message?.take(60) ?: e.javaClass.simpleName}")
            }
        }
    }

    /** 依次尝试「首选模型 + 表格提示词」「兜底模型 + JSON 提示词」，谁先解析出课程就用谁。 */
    private fun runChain(
        imageB64: String,
        apiKey: String,
        cfg: AiConfig,
        times: SectionTimes?,
        mime: String,
        onProgress: (Int, String) -> Unit
    ): List<Course> {
        val provider = cfg.provider
        val primary = cfg.realModel
        val backup = provider.models.map { it.first }.firstOrNull { it != primary } ?: primary
        // (模型, 提示词, 这一次的玩法名)
        val chain = ArrayList<Triple<String, String, String>>(2)
        chain.add(Triple(primary, TABLE_PROMPT, "表格"))
        chain.add(Triple(backup, JSON_PROMPT, "JSON"))
        val log = StringBuilder()
        var fatal: OnlineException? = null
        for ((idx, item) in chain.withIndex()) {
            val (m, prompt, mode) = item
            try {
                val content = request(imageB64, apiKey, m, times, mime, prompt, cfg.endpoint, onProgress)
                lastRaw = content
                lastModel = m
                if (idx == 0) onProgress(90, "解析结果") else onProgress(94, "换个模型再试")
                val courses = parseCourses(content, times)
                log.append("第${idx + 1}次 $m·$mode：返回 ${content.length} 字 → 解析出 ${courses.size} 条\n")
                lastNote = log.toString()
                if (courses.isNotEmpty()) {
                    onProgress(100, "完成")
                    return courses
                }
            } catch (e: OnlineException) {
                log.append("第${idx + 1}次 $m·$mode：${e.message}\n")
                lastNote = log.toString()
                fatal = e
                val msg = e.message ?: ""
                // Key 无效 / 额度用尽，换模型也一样，没必要再等一轮
                if (msg.contains("Key 无效") || msg.contains("额度") || msg.contains("太频繁")) break
            }
        }
        throw fatal ?: OnlineException("模型看完了，但没有输出能识别的课程")
    }

    /**
     * 原图直传：文件头魔数确认是 PNG/JPEG/WebP 且 ≤3.5MB 时，
     * 直接 base64 原始字节，不做任何解码。返回 (mime, base64) 或 null。
     */
    private fun rawImage(context: Context, uri: Uri): Pair<String, String>? = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > 3_500_000) return null
        val mime = when (Img.magicOf(bytes)) {
            "PNG" -> "image/png"
            "JPEG" -> "image/jpeg"
            "WebP" -> "image/webp"
            else -> return null
        }
        mime to Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    /** 解码彻底失败时，告诉用户文件到底多大、是什么格式。 */
    private fun describeFile(context: Context, uri: Uri): String = try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) "无法打开文件"
        else "${Img.magicOf(bytes)}，${bytes.size / 1024}KB"
    } catch (e: Exception) {
        "无法打开文件"
    }

    /** 校验 Key 是否可用。返回 null = 有效；否则返回错误原因。 */
    fun verifyKey(cfg: AiConfig): String? {
        return try {
            val body = JSONObject().apply {
                put("model", cfg.realModel)
                put("messages", JSONArray().put(
                    JSONObject().put("role", "user").put("content", "请只回复两个字母：OK")))
            }
            post(body, cleanKey(cfg.apiKey), cfg.endpoint, withImage = false)
            null
        } catch (e: OnlineException) {
            e.message
        } catch (e: SocketTimeoutException) {
            "连接超时，请检查网络"
        } catch (e: IOException) {
            "网络连接失败：${e.message?.take(60) ?: ""}"
        } catch (e: Exception) {
            "未知错误：${e.message?.take(60) ?: e.javaClass.simpleName}"
        }
    }

    /**
     * 自动识别：按 Key 长相先猜，猜不到就挨个试（每次只发一句"OK"，很快）。
     * 返回能通的服务商，全都不通返回 null。
     */
    fun detectProvider(apiKey: String): Pair<AiProvider, String>? {
        val key = cleanKey(apiKey)
        if (key.isEmpty()) return null
        Providers.guessByKey(key)?.let { guess ->
            val err = verifyKey(AiConfig(key, guess.id))
            if (err == null) return guess to (guess.models.firstOrNull()?.first ?: "")
        }
        for (p in Providers.ALL) {
            if (p.id == "custom") continue
            val model = p.models.firstOrNull()?.first ?: continue
            val err = verifyKey(AiConfig(key, p.id, model = model))
            if (err == null) return p to model
        }
        return null
    }

    private fun encodeImage(context: Context, uri: Uri): String? {
        // 全格式解码（ImageDecoder 优先），压到最长边 ≤1600：小字课表 1280 会糊
        val bmp = Img.decode(context, uri, 1600) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 发起请求并返回模型回复文本；失败抛 OnlineException（原因可直接展示）。 */
    private fun post(body: JSONObject, apiKey: String, endpoint: String, withImage: Boolean = true): String {
        if (endpoint.isBlank()) throw OnlineException("服务地址为空，请在设置里填写")
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = if (withImage) 90000 else 15000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401 || code == 403) {
                throw OnlineException("API Key 无效或已过期，请到设置里重新检查")
            }
            if (code == 429) {
                throw OnlineException("调用太频繁或额度受限，请稍等再试")
            }
            if (code !in 200..299) {
                // 把服务端给的原始原因带出来，别再只给个 HTTP 码
                val detail = runCatching {
                    JSONObject(text).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrNull().orEmpty()
                throw OnlineException(
                    "HTTPS $code" + if (detail.isNotBlank()) "：${detail.take(80)}" else "，请稍后再试"
                )
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw OnlineException("在线服务返回格式异常：${text.take(60)}")
            }
            json.optJSONObject("error")?.let { err ->
                throw OnlineException(
                    err.optString("message").ifBlank { "在线服务返回错误" }.take(80)
                )
            }
            val choices = json.optJSONArray("choices")
                ?: throw OnlineException("在线服务返回缺少内容：${text.take(60)}")
            val content = choices.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
            if (content.isBlank()) throw OnlineException("在线服务返回内容为空，请重试")
            return content
        } finally {
            conn.disconnect()
        }
    }

    /** 发起一次识别请求，返回模型回复的原始文本（解析交给 [parseCourses]）。 */
    private fun request(
        imageB64: String,
        apiKey: String,
        model: String,
        times: SectionTimes?,
        mime: String = "image/jpeg",
        prompt: String = TABLE_PROMPT,
        endpoint: String = "",
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): String {
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.1)
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", JSONArray()
                    .put(JSONObject().put("type", "text").put("text", prompt))
                    .put(
                        JSONObject().put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:$mime;base64,$imageB64"))
                    )
                )
            }))
        }
        val ep = endpoint.ifBlank { ENDPOINT }
        // 请求期间爬一条假进度：真实进度拿不到，但用户能看到"它在动"，
        // 卡在同一个百分比超过十几秒基本就是网络或模型挂了
        var p = java.util.concurrent.atomic.AtomicInteger(30)
        onProgress(30, "AI 正在看图")
        val ticker = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val next = p.incrementAndGet()
                if (next < 86) onProgress(next, stageHint(next))
            }
        }.apply { isDaemon = true; start() }
        try {
            return post(body, apiKey, ep, withImage = true)
        } finally {
            ticker.interrupt()
        }
    }

    /** 进度百分比 → 一句人话，避免只有数字太干。 */
    private fun stageHint(p: Int): String = when {
        p < 45 -> "上传图片"
        p < 60 -> "AI 正在看图"
        p < 72 -> "正在认格子"
        p < 82 -> "还在读，课表有点密"
        else -> "快好了"
    }

    // ---------------- 解析 ----------------

    /** 一段被扫出来的 JSON 数组，[dayHint] 是从它前面的键名推断出的星期。 */
    private data class Chunk(val text: String, val dayHint: Int?)

    /**
     * 从模型回复中提取课程。三级兜底：
     *  1. 扫出所有 JSON 数组片段（括号配平 + 字符串感知），逐个解析后合并；
     *  2. JSON 一条都没有 → 按 markdown 表格解析；
     *  3. 还是不行 → 按 "周一 第1-2节 高数" 行模式解析。
     *
     * [times] 仅在模型完全没给节次时兜底反推，且限制 1~12 节，不覆盖模型结果。
     */
    fun parseCourses(content: String, times: SectionTimes? = null): List<Course> {
        if (content.isBlank()) return emptyList()

        // ① 首选：一行一门课的记录型表格（| 星期 | 节次 | 课程 | 教师 | 教室 |）
        val byTable = parseRecordTable(content)
        if (byTable.isNotEmpty()) return byTable

        // ② JSON（老路线 / 兜底模型）
        val merged = LinkedHashMap<String, Course>()
        for (chunk in extractChunks(content)) {
            val arr = try {
                JSONArray(chunk.text)
            } catch (e: Exception) {
                continue
            }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val c = courseOf(o, times, chunk.dayHint) ?: continue
                merged.putIfAbsent("${c.day}|${c.startSection}|${c.endSection}|${c.name}", c)
            }
        }
        if (merged.isNotEmpty()) {
            return merged.values.sortedWith(compareBy({ it.day }, { it.startSection }, { it.endSection }))
        }
        // ③ 网格式表格 / 行文本兜底
        return parseTextTable(content, times)
    }

    /**
     * 解析「记录型」Markdown 表格：一行一门课。
     *
     * ```
     * | 星期 | 节次 | 课程 | 教师 | 教室 |
     * |------|------|------|------|------|
     * | 周一 | 1-2  | 数学 | 张三 | 7 401 |
     * ```
     *
     * 列顺序不写死——按表头文字定位（星期/节次/课程/教师/教室），
     * 模型把列换了顺序、多一列"备注"都不影响。没有表头时按常见顺序兜底。
     */
    private fun parseRecordTable(content: String): List<Course> {
        val rows = content.lines().map { it.trim() }.filter { it.startsWith("|") }
        if (rows.isEmpty()) return emptyList()
        var col = mapOf<String, Int>()
        val out = LinkedHashMap<String, Course>()

        for (line in rows) {
            val cells = line.trim().trim('|').split('|').map { it.trim() }
            if (cells.isEmpty()) continue
            if (cells.all { it.isEmpty() || Regex("^:?-{2,}:?$").matches(it) }) continue

            // 表头：出现"星期 / 节次 / 课程"这种标题词
            val looksHeader = cells.any { it == "星期" || it == "节次" || it == "课程" || it == "周次" }
            if (col.isEmpty() && looksHeader) {
                val m = HashMap<String, Int>()
                cells.forEachIndexed { i, raw ->
                    val c = raw.replace("*", "").trim()
                    val key = when {
                        c.contains("节次") || c.contains("节数") || c == "节" -> "section"
                        c.contains("星期") || c.contains("周几") || c == "周" -> "day"
                        c.contains("课程") || c.contains("科目") || c.contains("名称") -> "name"
                        c.contains("教师") || c.contains("老师") || c.contains("任课") -> "teacher"
                        c.contains("教室") || c.contains("地点") || c.contains("场地") -> "room"
                        else -> ""
                    }
                    if (key.isNotEmpty() && m[key] == null) m[key] = i
                }
                if (m.containsKey("day") && m.containsKey("name")) {
                    col = m
                    continue
                }
            }
            // 没有表头就当固定顺序：星期 | 节次 | 课程 | 教师 | 教室
            if (col.isEmpty()) {
                col = mapOf("day" to 0, "section" to 1, "name" to 2, "teacher" to 3, "room" to 4)
            }

            fun cell(k: String): String {
                val i = col[k] ?: return ""
                return cells.getOrNull(i)?.replace("*", "")?.trim().orEmpty()
            }

            val day = dayOfText(cell("day")) ?: continue
            val rawName = cell("name")
            if (rawName.isBlank()) continue
            val (nm, tc, rm) = splitCell(rawName)
            val name = nm.ifBlank { rawName }
            val sec = parseSectionCell(cell("section"))
            // 节次缺失时先给 1，交给后面的时间反推？表格模式不反推，直接取第一个数字
            val s1 = sec?.first ?: 1
            val s2 = sec?.second ?: s1
            val teacher = cell("teacher").ifBlank { tc }
            val room = cell("room").ifBlank { rm }
            val c = Course(
                name = name.take(24), teacher = teacher.take(12), room = room.take(24),
                day = day.coerceIn(1, 7), startSection = s1, endSection = maxOf(s1, s2)
            )
            out.putIfAbsent("${c.day}|${c.startSection}|${c.endSection}|${c.name}", c)
        }
        if (out.isEmpty()) return emptyList()
        return out.values.sortedWith(compareBy({ it.day }, { it.startSection }, { it.endSection }))
    }

    /**
     * 括号配平扫出所有 `[...]` 片段（跳过字符串字面量里的括号），
     * 并剔掉被外层数组包住的嵌套片段——兼容：
     *   [ {...}, {...} ]                     普通数组
     *   {"courses": [...] }                  包一层对象
     *   {"周一":[...],"周二":[...]}           按天分组（dayHint 从键名取）
     *   ```json ... ```                      代码块包裹
     */
    private fun extractChunks(s: String): List<Chunk> {
        val stack = ArrayDeque<Int>()
        val found = ArrayList<Chunk>()
        var inStr = false
        var esc = false
        var quote = ' '
        for (i in s.indices) {
            val c = s[i]
            if (inStr) {
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == quote) inStr = false
                continue
            }
            when (c) {
                '"', '\'' -> { inStr = true; quote = c }
                '[' -> stack.addLast(i)
                ']' -> if (stack.isNotEmpty()) {
                    val p = stack.removeLast()
                    val sub = s.substring(p, i + 1)
                    // 至少要有一个对象，纯数字数组（如 "1,2,3"）没意义
                    if (sub.contains('{')) found.add(Chunk(sub, dayHintBefore(s, p)))
                }
            }
        }
        // 去掉被更长片段包含的（嵌套重复）
        val byLen = found.distinct().sortedByDescending { it.text.length }
        val kept = ArrayList<Chunk>()
        for (ch in byLen) if (kept.none { it.text.contains(ch.text) }) kept.add(ch)
        return kept
    }

    /** 数组前面的键名若是 "周一"/"Monday"/"1"，就当作该数组的默认星期。 */
    private fun dayHintBefore(s: String, bracketPos: Int): Int? {
        val from = maxOf(0, bracketPos - 48)
        val window = s.substring(from, bracketPos)
        val key = Regex("[\"']([^\"']{1,12})[\"']\\s*[:：]\\s*$")
            .find(window)?.groupValues?.get(1) ?: return null
        return dayOfText(key)
    }

    /** 把一条 JSON 对象转成 Course；关键信息缺失返回 null。 */
    private fun courseOf(o: JSONObject, times: SectionTimes?, dayHint: Int?): Course? {
        val name = optName(o) ?: return null
        val day = optDayOrNull(o) ?: dayHint ?: 1

        // 最高优先级：rowStart/rowEnd —— 模型直接读表格最左侧的节次号，
        // 比让它"推算"可靠得多（连堂课 1-2 节、晚自习第 7 节都靠这个）
        var s1: Int? = null
        var s2: Int? = null
        for (k in listOf("rowStart", "row_start", "rowFrom", "startRow", "起始节次")) {
            val r = optSectionPair(o, k)
            if (r != null) { s1 = r.first; s2 = r.second; break }
        }
        if (s2 == null) {
            for (k in listOf("rowEnd", "row_end", "rowTo", "endRow", "结束节次")) {
                optSectionPair(o, k)?.let { s2 = it.first; break }
            }
        }
        // 其次：startSection 系列
        if (s1 == null) {
            for (k in listOf("startSection", "start_section", "start", "sectionStart", "from", "节次")) {
                val r = optSectionPair(o, k)
                if (r != null) { s1 = r.first; s2 = r.second; break }
            }
        }
        if (s2 == null) {
            for (k in listOf("endSection", "end_section", "end", "sectionEnd", "to")) {
                optSectionPair(o, k)?.let { s2 = it.first; break }
            }
        }
        // "section": "3-4" / "第3~4节" / "3,4" 这类合并字段兜底
        if (s1 == null || s2 == null) {
            val sec = listOf("section", "sections", "jieci", "节次")
                .firstNotNullOfOrNull { k ->
                    o.optString(k).takeIf { it.isNotBlank() && it != "null" }
                }
            if (sec != null) {
                val nums = Regex("\\d{1,2}").findAll(sec)
                    .map { it.value.toIntOrNull() }
                    .filterNotNull().filter { it in 1..20 }.toList()
                if (nums.isNotEmpty() && s1 == null) s1 = nums.first()
                if (nums.size >= 2 && s2 == null) s2 = nums.last()
            }
        }
        // 最后兜底：整条 JSON 文本里挖第一个 "N-M" 节次样式
        if (s1 == null) {
            Regex("第?\\s*(\\d{1,2})\\s*[-~—]\\s*(\\d{1,2})\\s*节?").find(o.toString())?.let { m ->
                val a = m.groupValues[1].toIntOrNull()
                val b = m.groupValues[2].toIntOrNull()
                if (a != null && a in 1..20) s1 = a
                if (b != null && b in 1..20) s2 = b
            }
        }
        var startV = (s1 ?: 1).coerceIn(1, 20)
        var endV = maxOf(startV, (s2 ?: startV).coerceIn(1, 20))
        // 时间反推只做兜底：完全没有节次信息时才用；且结果限 1~12 节，
        // 绝不覆盖模型给出的节次（否则用户节次表与学校不一致时会把 7 改成 13）
        if (s1 == null && times != null) {
            val t1 = sectionFromTime(times, o.optString("startTime"), isStart = true)
            val t2 = sectionFromTime(times, o.optString("endTime"), isStart = false)
            if (t1 != null) startV = t1.coerceIn(1, 12)
            endV = if (t2 != null) maxOf(startV, t2.coerceIn(1, 12)) else startV
        }
        return Course(
            name = name.take(24),
            teacher = optStr(o, "teacher", "教师", "老师").take(12),
            room = optStr(o, "room", "classroom", "location", "教室", "地点", "上课地点").take(24),
            day = day.coerceIn(1, 7),
            startSection = startV,
            endSection = endV
        )
    }

    private fun optStr(o: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { k ->
            o.optString(k).takeIf { it.isNotBlank() && it != "null" }
        } ?: ""

    private fun optName(o: JSONObject): String? {
        val n = optStr(
            o, "name", "course", "courseName", "course_name", "subject", "title",
            "课程", "课程名", "课程名称", "科目", "课名"
        ).trim()
        return n.ifEmpty { null }
    }

    /** 返回 (起始节次, 结束节次或 null)。值是 "3-4节" 这类字符串时两个都给。 */
    private fun optSectionPair(o: JSONObject, key: String): Pair<Int, Int?>? {
        val v = o.opt(key) ?: return null
        return when (v) {
            is Number -> if (v.toInt() in 1..20) v.toInt() to null else null
            is String -> {
                if (v.isBlank() || v == "null") return null
                val nums = Regex("\\d{1,2}").findAll(v)
                    .map { it.value.toIntOrNull() }.filterNotNull().filter { it in 1..20 }.toList()
                when {
                    nums.isEmpty() -> null
                    nums.size == 1 -> nums[0] to null
                    else -> nums[0] to nums[1]
                }
            }
            else -> null
        }
    }

    /** 星期解析；拿不到返回 null（交给 dayHint / 默认值），不再无脑当周一。 */
    private fun optDayOrNull(o: JSONObject): Int? {
        for (k in listOf("day", "weekday", "weekDay", "dayOfWeek", "星期", "周", "week")) {
            val v = o.opt(k) ?: continue
            when (v) {
                is Number -> if (v.toInt() in 1..7) return v.toInt()
                is String -> dayOfText(v)?.let { return it }
            }
        }
        return null
    }

    private fun cnDay(ch: String): Int? = when (ch) {
        "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4
        "五" -> 5; "六" -> 6; "日", "天" -> 7; else -> null
    }

    /** "3" / "周一" / "星期一" / "一" / "Monday" / "Mon" 都能认。 */
    private fun dayOfText(raw: String): Int? {
        val s = raw.trim().lowercase()
        if (s.isEmpty() || s == "null") return null
        s.toIntOrNull()?.let { return if (it in 1..7) it else null }
        Regex("(?:周|星期)([一二三四五六日天])").find(s)?.let { return cnDay(it.groupValues[1]) }
        val en = mapOf(
            "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4,
            "fri" to 5, "sat" to 6, "sun" to 7
        )
        en.forEach { (abbr, d) -> if (s.startsWith(abbr)) return d }
        if (s.length == 1) return cnDay(s)
        return null
    }

    // ---------------- 文本兜底 ----------------

    /**
     * 模型没吐 JSON（或 JSON 坏了）时的最后一道网：
     *  - markdown 表格：`| 节次 | 周一 | 周二 |` … `| 1-2 | 高数 | |`
     *  - 行模式：`周一 第1-2节 高等数学 张三 7-401`
     */
    private fun parseTextTable(content: String, times: SectionTimes?): List<Course> {
        val lines = content.lines().map { it.trim() }
        val out = LinkedHashMap<String, Course>()
        val dayCol = LinkedHashMap<Int, Int>()   // 列号 → 星期
        var headerSeen = false

        fun put(c: Course) = out.putIfAbsent("${c.day}|${c.startSection}|${c.endSection}|${c.name}", c)

        for (ln in lines) {
            if (ln.isEmpty()) continue

            if (ln.startsWith("|")) {
                val cells = ln.trim().trim('|').split('|').map { it.trim() }
                if (!headerSeen) {
                    val hasDay = cells.any { Regex("(周|星期)[一二三四五六日天]").containsMatchIn(it) }
                    if (hasDay) {
                        cells.forEachIndexed { i, c ->
                            Regex("(周|星期)([一二三四五六日天])").find(c)?.let { m ->
                                cnDay(m.groupValues[2])?.let { d -> dayCol[i] = d }
                            }
                        }
                        headerSeen = dayCol.isNotEmpty()
                    }
                    continue
                }
                // 分隔行 |---|---|
                if (cells.all { it.isEmpty() || Regex("^:?-{2,}:?$").containsMatchIn(it) }) continue
                val sec = parseSectionCell(cells.firstOrNull().orEmpty())
                for ((i, d) in dayCol) {
                    val cell = cells.getOrNull(i)?.trim().orEmpty()
                    if (cell.isEmpty() || cell == "-" || cell == "/" || cell == "—") continue
                    val (nm, tc, rm) = splitCell(cell)
                    if (nm.isBlank()) continue
                    val s1 = sec?.first ?: 1
                    val s2 = sec?.second ?: s1
                    put(
                        Course(
                            name = nm.take(24), teacher = tc.take(12), room = rm.take(24),
                            day = d, startSection = s1, endSection = maxOf(s1, s2)
                        )
                    )
                }
                continue
            }

            // 行模式：周一 第1-2节 高等数学 张三 7-401
            val m = Regex(
                "(?:周|星期)([一二三四五六日天])\\D{0,6}?(\\d{1,2})\\s*[-~—至到]\\s*(\\d{1,2})\\s*节\\s*(.+)"
            ).find(ln) ?: continue
            val d = cnDay(m.groupValues[1]) ?: continue
            val s1 = m.groupValues[2].toIntOrNull()?.coerceIn(1, 20) ?: continue
            val s2 = m.groupValues[3].toIntOrNull()?.coerceIn(1, 20) ?: s1
            val rest = m.groupValues[4]
            val (nm, tc, rm) = splitCell(rest)
            if (nm.isBlank()) continue
            put(
                Course(
                    name = nm.take(24), teacher = tc.take(12), room = rm.take(24),
                    day = d, startSection = s1, endSection = maxOf(s1, s2)
                )
            )
        }
        if (out.isNotEmpty()) {
            return out.values.sortedWith(compareBy({ it.day }, { it.startSection }, { it.endSection }))
        }
        // 时间反推：模型给了时间却没给节次时，用节次表换算
        if (times != null) {
            Regex("(\\d{1,2}[:：]\\d{2})\\D{1,6}(\\d{1,2}[:：]\\d{2})\\s*[,，]?\\s*(.+)")
                .findAll(content).forEach { mm ->
                    val t1 = sectionFromTime(times, mm.groupValues[1], true) ?: return@forEach
                    val name = splitCell(mm.groupValues[3]).first
                    if (name.isNotBlank()) {
                        put(
                            Course(
                                name = name.take(24), day = 1,
                                startSection = t1.coerceIn(1, 12),
                                endSection = (sectionFromTime(times, mm.groupValues[2], false)
                                    ?: t1).coerceIn(1, 12)
                            )
                        )
                    }
                }
        }
        return out.values.sortedWith(compareBy({ it.day }, { it.startSection }))
    }

    /** "1-2" / "第1~2节" / "1、2" / "1" → (1,2) / (1,1)；认不出返回 null。 */
    private fun parseSectionCell(raw: String): Pair<Int, Int>? {
        val v = raw.replace("★", "").trim()
        if (v.isEmpty()) return null
        val nums = Regex("\\d{1,2}").findAll(v)
            .map { it.value.toIntOrNull() }.filterNotNull().filter { it in 1..20 }.toList()
        if (nums.isEmpty()) return null
        return nums.first() to (nums.getOrNull(1) ?: nums.first())
    }

    /**
     * 把一格文字拆成 (课程名, 教师, 教室)。单元格常见形态：
     * "高等数学<br>张三<br>7-401"、"高等数学 张三 7-401"、"高数@张三@7#401"。
     */
    private fun splitCell(raw: String): Triple<String, String, String> {
        var parts = raw
            .replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            .split(Regex("[\\n/|、，,；;]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        // "高等数学 张三 7-401" 这种一行空格分隔的，再按空格拆一层
        if (parts.size == 1 && parts[0].contains(' ')) {
            parts = parts[0].split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        }
        if (parts.isEmpty()) return Triple("", "", "")

        val roomRe = Regex("^[@＃+]?\\s*\\d{1,2}\\s*[-#号楼]?\\s*\\d{2,4}$")
        val teacherRe = Regex("^(?:[\\u4e00-\\u9fff]{2,4}|[A-Z][a-z]+(?:\\s[A-Z][a-z]+)?)$")

        var name = ""
        var teacher = ""
        var room = ""
        for ((idx, p) in parts.withIndex()) {
            val clean = p.replace(Regex("^[@＃+]"), "").trim()
            when {
                // 教室先认（"7-401" 不会被误当课名）
                room.isEmpty() && roomRe.matches(clean) -> room = clean
                // 第一段永远是课程名：中文课名也常是 2~4 字（"高等数学"），
                // 不能让它掉进下面的教师规则里被顶掉
                idx == 0 -> name = p
                teacher.isEmpty() && teacherRe.matches(clean) -> teacher = clean
                name.isEmpty() -> name = p
                else -> name += " $p"
            }
        }
        // 只有一格时，尝试把末尾的教室/教师抠出来
        if (room.isEmpty() && teacher.isEmpty() && name.length > 4) {
            Regex("(.+?)\\s+(\\d{1,2}[-#号]?\\d{2,4})$").find(name)?.let {
                name = it.groupValues[1].trim()
                room = it.groupValues[2].trim()
            }
        }
        return Triple(name.trim(), teacher.trim(), room.trim())
    }

    /** "08:10" → 最接近的节次号（±20 分钟内才采纳）。 */
    private fun sectionFromTime(times: SectionTimes, hhmm: String, isStart: Boolean): Int? {
        val m = Regex("(\\d{1,2})[:：](\\d{2})").find(hhmm.trim()) ?: return null
        val mins = (m.groupValues[1].toIntOrNull() ?: return null) * 60 +
            (m.groupValues[2].toIntOrNull() ?: return null)
        var best = 1
        var bestDiff = Int.MAX_VALUE
        for (s in 1..20) {
            val t = if (isStart) times.startOf(s) else times.endOf(s)
            val d = Math.abs(t - mins)
            if (d < bestDiff) { bestDiff = d; best = s }
        }
        return if (bestDiff <= 20) best else null
    }
}
