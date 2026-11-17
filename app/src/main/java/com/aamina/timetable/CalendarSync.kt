package com.aamina.timetable

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 一门课。day: 1=周一 … 7=周日。weekMode: 0=每周 1=单周 2=双周。 */
data class Course(
    var name: String = "",
    var teacher: String = "",
    var room: String = "",
    var day: Int = 1,
    var startSection: Int = 1,
    var endSection: Int = 2,
    var startWeek: Int = 1,
    var endWeek: Int = 16,
    var weekMode: Int = 0,
    /** 色板索引 0–11；0 表示按课名自动分配。 */
    var color: Int = 0,
    /** 精细周次，如 "1,2,3,5-8"；空 = 用 startWeek..endWeek + weekMode 推算。 */
    var weeks: String = "",
    /** 停课周次，如 "5,6"：这几周不上课，也不写日历。 */
    var skips: String = "",
    /** 调课记录，JSON：[{"w":5,"d":3,"s":5,"e":6}] = 第 5 周改到周三 5–6 节。 */
    var moves: String = ""
) {
    /** 实际生效的周次集合。 */
    fun weekList(): List<Int> {
        if (weeks.isNotBlank()) {
            val set = sortedSetOf<Int>()
            weeks.split(",").forEach { seg ->
                val t = seg.trim()
                if (t.isEmpty()) return@forEach
                val r = t.split("-")
                if (r.size == 2) {
                    val a = r[0].trim().toIntOrNull()
                    val b = r[1].trim().toIntOrNull()
                    if (a != null && b != null) for (w in minOf(a, b)..maxOf(a, b)) set.add(w)
                } else {
                    t.toIntOrNull()?.let { set.add(it) }
                }
            }
            if (set.isNotEmpty()) return set.toList()
        }
        val hi = maxOf(startWeek, endWeek)
        return (startWeek..hi).filter {
            when (weekMode) {
                1 -> it % 2 == 1
                2 -> it % 2 == 0
                else -> true
            }
        }
    }

    fun isActiveIn(week: Int): Boolean = weekList().contains(week)

    /** 停课周次集合。 */
    fun skipList(): List<Int> = parseWeekSpec(skips).sorted()

    /** 本周的调课记录，没有则 null。 */
    fun moveIn(week: Int): LessonMove? = moveList().firstOrNull { it.week == week }

    fun moveList(): List<LessonMove> {
        if (moves.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(moves)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val w = o.optInt("w", 0)
                if (w < 1) null else LessonMove(
                    week = w,
                    day = o.optInt("d", day).coerceIn(1, 7),
                    start = o.optInt("s", startSection).coerceAtLeast(1),
                    end = o.optInt("e", endSection).coerceAtLeast(1)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun withMove(m: LessonMove): Course {
        val list = moveList().filterNot { it.week == m.week } + m
        val arr = JSONArray()
        list.sortedBy { it.week }.forEach {
            arr.put(
                JSONObject().put("w", it.week).put("d", it.day)
                    .put("s", it.start).put("e", it.end)
            )
        }
        return copy(moves = arr.toString())
    }

    fun withoutMove(week: Int): Course {
        val arr = JSONArray()
        moveList().filterNot { it.week == week }.sortedBy { it.week }.forEach {
            arr.put(
                JSONObject().put("w", it.week).put("d", it.day)
                    .put("s", it.start).put("e", it.end)
            )
        }
        return copy(moves = arr.toString())
    }

    fun withSkip(week: Int, skip: Boolean): Course {
        val set = skipList().toMutableSet()
        if (skip) set.add(week) else set.remove(week)
        return copy(skips = encodeWeeks(set))
    }

    /** 这一周这门课到底上不上（停课不算）。 */
    fun activeIn(week: Int): Boolean = isActiveIn(week) && week !in skipList()
}

/** 一节课在某一周的调课安排。 */
data class LessonMove(
    val week: Int,
    val day: Int,
    val start: Int,
    val end: Int
)

/** 解析 "1,2,3,5-8" 这种周次串。 */
fun parseWeekSpec(spec: String): List<Int> {
    if (spec.isBlank()) return emptyList()
    val set = sortedSetOf<Int>()
    spec.split(",").forEach { seg ->
        val t = seg.trim()
        if (t.isEmpty()) return@forEach
        val r = t.split("-")
        if (r.size == 2) {
            val a = r[0].trim().toIntOrNull()
            val b = r[1].trim().toIntOrNull()
            if (a != null && b != null) for (w in minOf(a, b)..maxOf(a, b)) set.add(w)
        } else {
            t.toIntOrNull()?.let { set.add(it) }
        }
    }
    return set.toList()
}

/** 按等差切分周次：返回 (起始周, 周数, 步长)。单周 → step=2，连周 → step=1。 */
fun weekRuns(weeks: List<Int>): List<Triple<Int, Int, Int>> {
    if (weeks.isEmpty()) return emptyList()
    val sorted = weeks.distinct().sorted()
    val out = mutableListOf<Triple<Int, Int, Int>>()
    var start = sorted[0]
    var prev = sorted[0]
    var step = 0
    var count = 1
    for (w in sorted.drop(1)) {
        val d = w - prev
        if (step == 0) {
            step = d; prev = w; count = 2
        } else if (d == step) {
            prev = w; count++
        } else {
            out.add(Triple(start, count, step))
            start = w; prev = w; step = 0; count = 1
        }
    }
    out.add(Triple(start, count, if (step == 0) 1 else step))
    return out
}

/** 把周次集合编码成 "1,2,3,5-8" 这种紧凑字符串。 */
fun encodeWeeks(weeks: Collection<Int>): String =
    weekSegments(weeks.toList()).joinToString(",") {
        if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}"
    }

/** 把周次集合压成连续区间（"1,2,3,6,7" → [1..3, 6..7]），写日历时一段一个重复规则。 */
fun weekSegments(weeks: List<Int>): List<IntRange> {
    if (weeks.isEmpty()) return emptyList()
    val sorted = weeks.sorted()
    val out = mutableListOf<IntRange>()
    var from = sorted[0]
    var prev = sorted[0]
    for (w in sorted.drop(1)) {
        if (w == prev + 1) {
            prev = w
        } else {
            out.add(from..prev)
            from = w
            prev = w
        }
    }
    out.add(from..prev)
    return out
}

/**
 * 节次 → 时间的换算规则。
 *
 * 一天分成上午 / 下午 / 晚上三段，三段各几节可以单独设置（设置页「每天节数」）。
 * 段内按「每节时长 + 课间休息」线性推进，段与段之间插午休 / 晚休——
 * 这样第 5 节不会紧贴着第 4 节排下去，而是从下午第一节课开始。
 */
data class SectionTimes(
    val firstStartMinutes: Int = 8 * 60,
    val durationMin: Int = 45,
    val breakMin: Int = 10,
    val amCount: Int = 4,
    val pmCount: Int = 4,
    val eveCount: Int = 3,
    val noonBreakMin: Int = 90,
    val eveBreakMin: Int = 60,
    /**
     * 逐节自定义时间表（start, end），下标 0 = 第 1 节。
     *
     * 各校作息差别极大（有的第 1 节 08:00，有的 08:30；午休 60 分钟 or 150 分钟），
     * 靠"起点 + 时长 + 课间"一路推算永远对不上。所以允许用户把每一节单独钉死，
     * 一旦钉过就以 [slots] 为准，没钉过的节次继续走公式。
     */
    val slots: List<Pair<Int, Int>> = emptyList()
) {
    // 三段节数归一化：上午至少留 1 节（否则"下午第一节"的起点算不出来）
    private val am: Int get() = amCount.coerceAtLeast(1)
    private val pm: Int get() = pmCount.coerceAtLeast(0)
    private val eve: Int get() = eveCount.coerceAtLeast(0)

    val totalSections: Int get() = (am + pm + eve).coerceIn(1, 24)

    private val step: Int get() = durationMin + breakMin

    /** 上午最后一节的下课时刻 */
    private val amEnd: Int get() = firstStartMinutes + (am - 1) * step + durationMin
    private val pmStart: Int get() = amEnd + noonBreakMin
    private val pmEnd: Int get() = pmStart + (pm - 1) * step + durationMin
    private val eveStart: Int get() = pmEnd + eveBreakMin

    /** 纯公式推算（忽略自定义）。 */
    private fun computedStart(section: Int): Int = when {
        section <= am -> firstStartMinutes + (section - 1) * step
        section <= am + pm -> pmStart + (section - am - 1) * step
        else -> eveStart + (section - am - pm - 1) * step
    }

    fun startOf(section: Int): Int =
        slots.getOrNull(section - 1)?.first ?: computedStart(section)

    fun endOf(section: Int): Int =
        slots.getOrNull(section - 1)?.second ?: (computedStart(section) + durationMin)

    /** 是否被手动改过（固化后值仍等于公式的不算）。 */
    fun isCustom(section: Int): Boolean {
        val slot = slots.getOrNull(section - 1) ?: return false
        return slot.first != computedStart(section) || slot.second != computedStart(section) + durationMin
    }

    /** 把当前推算结果固化成显式时间表，方便用户逐节微调。 */
    fun materialize(): SectionTimes {
        if (slots.size == totalSections) return this
        val list = (1..totalSections).map { computedStart(it) to computedStart(it) + durationMin }
        return copy(slots = list)
    }

    /** 单独改某一节（下标从 0 开始）；未固化的节次先按公式补齐再改。 */
    fun withSlot(index: Int, start: Int, end: Int): SectionTimes {
        val base = materialize()
        if (index !in base.slots.indices) return this
        val list = base.slots.toMutableList()
        val s = start.coerceIn(0, 24 * 60 - 1)
        val e = end.coerceIn(s + 1, 25 * 60)
        list[index] = s to e
        return copy(slots = list)
    }

    /** 把第 [section] 节整体后移 [delta] 分钟（连带它之后所有节次，保持间隔）。 */
    fun shiftFrom(section: Int, delta: Int): SectionTimes {
        if (delta == 0) return this
        val base = materialize()
        val list = base.slots.mapIndexed { i, (s, e) ->
            if (i + 1 >= section) (s + delta).coerceAtLeast(0) to (e + delta).coerceAtLeast(1) else s to e
        }
        return copy(slots = list)
    }

    fun isAm(section: Int): Boolean = section in 1..am
    fun isPm(section: Int): Boolean = section > am && section <= am + pm
    fun isEve(section: Int): Boolean = section > am + pm

    /** 第 [section] 节是不是当天最后一节。 */
    fun isLast(section: Int): Boolean = section >= totalSections

    fun fmt(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /**
     * 归一化：把历史遗留的「手指停在半格」值吸附到 5 分钟刻度，并夹到合理区间。
     *
     * v2.4 的滑块没有吸附，拖动时会存下 139 / 129 这种值，
     * 于是下午第一节课被推到了 14:29。读出来先过一遍这里，旧数据也能自愈。
     */
    fun sanitized(): SectionTimes {
        fun Int.snap(lo: Int, hi: Int): Int = ((this + 2) / 5 * 5).coerceIn(lo, hi)
        return copy(
            firstStartMinutes = firstStartMinutes.coerceIn(0, 23 * 60 + 55),
            durationMin = durationMin.snap(30, 120),
            breakMin = breakMin.snap(0, 45),
            noonBreakMin = noonBreakMin.snap(0, 240),
            eveBreakMin = eveBreakMin.snap(0, 240),
            amCount = amCount.coerceIn(1, 12),
            pmCount = pmCount.coerceIn(0, 12),
            eveCount = eveCount.coerceIn(0, 12)
        )
    }
}

/** 全部本地持久化（SharedPreferences + JSON，完全离线）。 */
object Store {
    private const val FILE = "timetable"

    private fun sp(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun loadCourses(context: Context): MutableList<Course> {
        val raw = sp(context).getString("courses", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<Course>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Course(
                        name = o.optString("n"), teacher = o.optString("t"), room = o.optString("r"),
                        day = o.optInt("d", 1), startSection = o.optInt("s1", 1), endSection = o.optInt("s2", 2),
                        startWeek = o.optInt("w1", 1), endWeek = o.optInt("w2", 16), weekMode = o.optInt("wm", 0),
                        color = o.optInt("c", 0), weeks = o.optString("wk"),
                        skips = o.optString("sk"), moves = o.optString("mv")
                    )
                )
            }
            out
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveCourses(context: Context, courses: List<Course>) {
        val arr = JSONArray()
        courses.forEach { c ->
            arr.put(
                JSONObject()
                    .put("n", c.name).put("t", c.teacher).put("r", c.room)
                    .put("d", c.day).put("s1", c.startSection).put("s2", c.endSection)
                    .put("w1", c.startWeek).put("w2", c.endWeek).put("wm", c.weekMode)
                    .put("c", c.color).put("wk", c.weeks)
                    .put("sk", c.skips).put("mv", c.moves)
            )
        }
        sp(context).edit().putString("courses", arr.toString()).apply()
    }

    fun loadTimes(context: Context): SectionTimes {
        val p = sp(context)
        return SectionTimes(
            firstStartMinutes = p.getInt("t0", 8 * 60),
            durationMin = p.getInt("td", 45),
            breakMin = p.getInt("tb", 10),
            amCount = p.getInt("tam", 4),
            pmCount = p.getInt("tpm", 4),
            eveCount = p.getInt("teve", 3),
            noonBreakMin = p.getInt("tnoon", 90),
            eveBreakMin = p.getInt("teveb", 60),
            slots = decodeSlots(p.getString("tslots", "") ?: "")
        ).sanitized()
    }

    fun saveTimes(context: Context, t: SectionTimes) {
        val s = t.sanitized()
        sp(context).edit()
            .putInt("t0", s.firstStartMinutes).putInt("td", s.durationMin).putInt("tb", s.breakMin)
            .putInt("tam", s.amCount).putInt("tpm", s.pmCount).putInt("teve", s.eveCount)
            .putInt("tnoon", s.noonBreakMin).putInt("teveb", s.eveBreakMin)
            .putString("tslots", encodeSlots(s.slots))
            .apply()
    }

    /** "480-525,535-620" ⇄ List<Pair<Int,Int>> */
    private fun encodeSlots(slots: List<Pair<Int, Int>>): String =
        slots.joinToString(",") { "${it.first}-${it.second}" }

    private fun decodeSlots(raw: String): List<Pair<Int, Int>> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { seg ->
            val a = seg.split("-")
            if (a.size != 2) return@mapNotNull null
            val s = a[0].trim().toIntOrNull() ?: return@mapNotNull null
            val e = a[1].trim().toIntOrNull() ?: return@mapNotNull null
            if (s <= 0 || e <= s) null else s to e
        }
    }

    // ---- 课表显示设置 ----

    /** 本学期总周数（决定编辑页周次网格的数量）。 */
    fun totalWeeks(context: Context): Int = sp(context).getInt("totalWeeks", 20)
    fun setTotalWeeks(context: Context, n: Int) {
        sp(context).edit().putInt("totalWeeks", n.coerceIn(1, 30)).apply()
    }

    /** 当前周手动覆盖；-1 = 按学期第一天自动计算。 */
    fun weekOverride(context: Context): Int = sp(context).getInt("curWeek", -1)
    fun setWeekOverride(context: Context, w: Int) {
        sp(context).edit().putInt("curWeek", w).apply()
    }

    fun showWeekend(context: Context): Boolean = sp(context).getBoolean("showWeekend", true)
    fun setShowWeekend(context: Context, b: Boolean) {
        sp(context).edit().putBoolean("showWeekend", b).apply()
    }

    /** 是否显示非本周课程（关掉后列表只剩当前周要上的课）。 */
    fun showOtherWeeks(context: Context): Boolean = sp(context).getBoolean("showOther", true)
    fun setShowOtherWeeks(context: Context, b: Boolean) {
        sp(context).edit().putBoolean("showOther", b).apply()
    }

    /** 每节课时长是否统一（关掉后逐节可以单独设起止）。 */
    fun uniformLength(context: Context): Boolean = sp(context).getBoolean("uniform", true)
    fun setUniformLength(context: Context, b: Boolean) {
        sp(context).edit().putBoolean("uniform", b).apply()
    }

    /** 一周是否从周一开始（false = 周日）。 */
    fun mondayFirst(context: Context): Boolean = sp(context).getBoolean("monFirst", true)
    fun setMondayFirst(context: Context, b: Boolean) {
        sp(context).edit().putBoolean("monFirst", b).apply()
    }

    fun firstMonday(context: Context): LocalDate? =
        sp(context).getString("mon", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setFirstMonday(context: Context, d: LocalDate) {
        sp(context).edit().putString("mon", d.toString()).apply()
    }

    fun calendarId(context: Context): Long = sp(context).getLong("cal", -1L)

    fun setCalendarId(context: Context, id: Long) {
        sp(context).edit().putLong("cal", id).apply()
    }

    /** 跟随壁纸取色（Material You，Android 12+）。 */
    fun dynamicColor(context: Context): Boolean = sp(context).getBoolean("dyncolor", true)

    fun setDynamicColor(context: Context, v: Boolean) {
        sp(context).edit().putBoolean("dyncolor", v).apply()
    }

    fun reminderEnabled(context: Context): Boolean = sp(context).getBoolean("rem", true)

    fun setReminderEnabled(context: Context, v: Boolean) {
        sp(context).edit().putBoolean("rem", v).apply()
    }

    fun lastEventIds(context: Context): List<Long> =
        sp(context).getString("eids", "")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun setLastEventIds(context: Context, ids: List<Long>) {
        sp(context).edit().putString("eids", ids.joinToString(",")).apply()
    }

    /** 历史累计写入过的所有 eventId（多次导入会累积，用于"删除全部"）。 */
    fun allCreatedIds(context: Context): List<Long> =
        sp(context).getString("eidsAll", "")?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    fun addCreatedIds(context: Context, ids: List<Long>) {
        if (ids.isEmpty()) return
        val merged = (allCreatedIds(context) + ids).distinct().takeLast(3000)
        sp(context).edit().putString("eidsAll", merged.joinToString(",")).apply()
    }

    fun clearCreatedIds(context: Context) {
        sp(context).edit().remove("eidsAll").apply()
    }

    fun apiKey(context: Context): String = sp(context).getString("key", "") ?: ""

    fun setApiKey(context: Context, v: String) {
        sp(context).edit().putString("key", v).apply()
    }

    fun model(context: Context): String =
        sp(context).getString("ocrmodel", OnlineOcr.DEFAULT_MODEL) ?: OnlineOcr.DEFAULT_MODEL

    fun setModel(context: Context, v: String) {
        sp(context).edit().putString("ocrmodel", v).apply()
    }

    /** 在线识别的完整配置；老版本只存了 key + 模型名，这里自动迁移到智谱。 */
    fun aiConfig(context: Context): AiConfig {
        val p = sp(context)
        val prov = p.getString("aiprov", "") ?: ""
        val model = p.getString("aimodel", "") ?: ""
        if (prov.isNotBlank()) {
            return AiConfig(apiKey(context), prov, p.getString("aibase", "") ?: "", model)
        }
        val old = p.getString("ocrmodel", "") ?: ""
        val guess = Providers.ALL.firstOrNull { pr -> pr.models.any { it.first == old } }
            ?: Providers.byId("zhipu")
        return AiConfig(apiKey(context), guess.id, "", old)
    }

    fun saveAi(context: Context, cfg: AiConfig) {
        sp(context).edit()
            .putString("key", cfg.apiKey)
            .putString("aiprov", cfg.providerId)
            .putString("aibase", cfg.baseUrl)
            .putString("aimodel", cfg.model)
            .apply()
    }
}

/** 系统日历读写。 */
object CalendarSync {

    /** 写入日程时统一附加的标记，便于"删除全部"精确识别（旧版本写入的靠 id/特征兜底）。 */
    const val MARKER = "【课表同步】"

    /** 本 App 写入的日程 id：标记匹配 + 历史 id + 旧版本特征（同一目标日历、周重复、特征描述）。 */
    fun findCreatedEventIds(context: Context): List<Long> {
        val ids = LinkedHashSet<Long>()
        // ① 新版本：描述里带标记（跨所有日历查找）
        runCatching {
            context.contentResolver.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID),
                "${Events.DESCRIPTION} LIKE ?",
                arrayOf("%$MARKER%"),
                null
            )?.use { cur ->
                while (cur.moveToNext()) ids.add(cur.getLong(0))
            }
        }
        // ② 旧版本（v1.0~v1.9 无标记）：限定在 App 记录的目标日历内，
        //    周重复 + 描述为"由课表日历导入"或"教师：…"——这是旧版写入的特征
        val cal = Store.calendarId(context)
        if (cal > 0) {
            runCatching {
                context.contentResolver.query(
                    Events.CONTENT_URI,
                    arrayOf(Events._ID),
                    "${Events.CALENDAR_ID} = ? AND ${Events.RRULE} IS NOT NULL " +
                        "AND (${Events.DESCRIPTION} = ? OR ${Events.DESCRIPTION} LIKE ?)",
                    arrayOf(cal.toString(), "由课表日历导入", "教师：%"),
                    null
                )?.use { cur ->
                    while (cur.moveToNext()) ids.add(cur.getLong(0))
                }
            }
        }
        // ③ 本地记过的 id：先回日历里核实是否还存在。
        //    用户可能在系统日历里手动删过，这些 id 早就失效——直接信缓存会让
        //    「撤销同步」以为还有东西可删，也会让「是否已同步」的判断永远为真。
        val cached = (Store.lastEventIds(context) + Store.allCreatedIds(context)).distinct()
        cached.chunked(400).forEach { chunk ->
            runCatching {
                val sel = chunk.joinToString(",") { it.toString() }
                context.contentResolver.query(
                    Events.CONTENT_URI, arrayOf(Events._ID), "${Events._ID} IN ($sel)", null, null
                )?.use { cur ->
                    while (cur.moveToNext()) ids.add(cur.getLong(0))
                }
            }
        }
        return ids.toList()
    }

    /**
     * 挑一个可写入的日历：优先上次选过的，否则第一个可写账户；都没有返回 -1。
     */
    fun pickTargetCalendar(context: Context): Long {
        val cals = listCalendars(context)
        val saved = Store.calendarId(context)
        return cals.firstOrNull { it.first == saved }?.first
            ?: cals.firstOrNull()?.first
            ?: -1L
    }

    /** 重新写入的结果：清掉几条旧的、写进去几条、几门课彻底失败。 */
    data class ResyncResult(val deleted: Int, val inserted: Int, val failedCourses: Int)

    /**
     * 按当前课表整体重写一遍日程。
     *
     * 这是「重新添加」的正解：用户在系统日历里把课表事件删光了以后，本地只剩一堆失效的
     * eventId，App 既不会自动补写、也没有入口手动补——于是课表在日历里就凭空消失了。
     * 这里先删掉日历里还残留的本 App 事件（避免重复），再按最新课表全量插入。
     */
    fun resyncAll(
        context: Context,
        calendarId: Long,
        firstMonday: LocalDate,
        courses: List<Course>,
        times: SectionTimes,
        reminder: Boolean
    ): ResyncResult {
        val deleted = deleteEvents(context, findCreatedEventIds(context))
        Store.setLastEventIds(context, emptyList())
        Store.clearCreatedIds(context)

        val ids = mutableListOf<Long>()
        var failed = 0
        courses.forEach { c ->
            runCatching { insertCourse(context, calendarId, firstMonday, c, times, reminder) }
                .onSuccess { ids.addAll(it) }
                .onFailure { failed++ }
        }
        Store.setLastEventIds(context, ids)
        Store.addCreatedIds(context, ids)
        return ResyncResult(deleted, ids.size, failed)
    }

    /** 一键删除本 App 写入的全部日程，返回删除数量。 */
    fun deleteAllCreated(context: Context): Int {
        val ids = findCreatedEventIds(context)
        val n = deleteEvents(context, ids)
        Store.setLastEventIds(context, emptyList())
        Store.clearCreatedIds(context)
        return n
    }

    /** 可写入的日历账户列表（需要 READ_CALENDAR 权限）。 */
    fun listCalendars(context: Context): List<Triple<Long, String, String>> {
        val out = mutableListOf<Triple<Long, String, String>>()
        runCatching {
            val projection = arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.CALENDAR_ACCESS_LEVEL
            )
            context.contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf("500"),
                null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    out.add(
                        Triple(
                            cur.getLong(0),
                            cur.getString(1) ?: "日历",
                            cur.getString(2) ?: ""
                        )
                    )
                }
            }
        }
        return out
    }

    /**
     * 把一门课写成重复日程。
     *
     * 周次先解析成具体集合，再按「等差」分组：连续的用 FREQ=WEEKLY，隔一周的用 INTERVAL=2，
     * 剩下的零散周各写一条。返回所有 eventId（周次不连续时可能多于一条）。
     */
    fun insertCourse(
        context: Context,
        calendarId: Long,
        firstMonday: LocalDate,
        c: Course,
        times: SectionTimes,
        reminder: Boolean
    ) : List<Long> {
        val moves = c.moveList()
        // 停课的周不写；调课的周单独写一条（不参与常规重复规则）
        val movedWeeks = moves.map { it.week }.toSet()
        val weeks = c.weekList().filter { it !in c.skipList() && it !in movedWeeks }
        val zone = ZoneId.systemDefault()
        val ids = mutableListOf<Long>()
        for ((startWeek, count, step) in weekRuns(weeks)) {
            val firstDate = firstMonday
                .plusDays((c.day - 1).toLong())
                .plusWeeks((startWeek - 1).toLong())
            val begin = toMillis(firstDate, times.startOf(c.startSection), zone)
            val end = toMillis(firstDate, times.endOf(c.endSection), zone)
            val rrule = when {
                count == 1 -> "FREQ=DAILY;COUNT=1"
                step == 1 -> "FREQ=WEEKLY;COUNT=$count"
                else -> "FREQ=WEEKLY;INTERVAL=$step;COUNT=$count"
            }
            insertOne(context, calendarId, c, begin, end, rrule, reminder, zone)?.let { ids.add(it) }
        }
        // 调课：每周一条独立日程
        moves.forEach { m ->
            if (!c.isActiveIn(m.week)) return@forEach
            val date = firstMonday
                .plusDays((m.day - 1).toLong())
                .plusWeeks((m.week - 1).toLong())
            val begin = toMillis(date, times.startOf(m.start), zone)
            val end = toMillis(date, times.endOf(m.end), zone)
            insertOne(context, calendarId, c, begin, end, "FREQ=DAILY;COUNT=1", reminder, zone, "（调课）")
                ?.let { ids.add(it) }
        }
        return ids
    }

    private fun insertOne(
        context: Context,
        calendarId: Long,
        c: Course,
        begin: Long,
        end: Long,
        rrule: String,
        reminder: Boolean,
        zone: ZoneId,
        titleSuffix: String = ""
    ): Long? {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, c.name.ifBlank { "课程" } + titleSuffix)
            val desc = if (c.teacher.isBlank()) "由课表日历导入" else "教师：${c.teacher}"
            put(Events.DESCRIPTION, "$desc\n$MARKER")
            put(Events.EVENT_LOCATION, c.room)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.EVENT_TIMEZONE, zone.id)
            put(Events.RRULE, rrule)
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
            put(Events.CUSTOM_APP_PACKAGE, context.packageName)
        }
        val uri: Uri = context.contentResolver.insert(Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(uri)
        if (reminder) {
            runCatching {
                val rv = ContentValues().apply {
                    put(Reminders.EVENT_ID, eventId)
                    put(Reminders.MINUTES, 10)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(Reminders.CONTENT_URI, rv)
            }
        }
        return eventId
    }

    fun deleteEvents(context: Context, ids: List<Long>): Int {
        var n = 0
        ids.forEach { id ->
            runCatching {
                n += context.contentResolver.delete(
                    ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null
                )
            }
        }
        return n
    }

    private fun toMillis(date: LocalDate, minutesOfDay: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minutesOfDay / 60, minutesOfDay % 60))
            .atZone(zone).toInstant().toEpochMilli()
}
