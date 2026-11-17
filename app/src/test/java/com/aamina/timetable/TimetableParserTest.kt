package com.aamina.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模拟一张深色主题课表截图（周一~周日 7 列，节次 1~7，含午休/晚休分隔行，
 * 课程名断行、教室 @7 401 / +7#401 等真实教务截图特征）的 ML Kit 输出。
 */
class TimetableParserTest {

    private fun ln(text: String, cx: Float, top: Float, w: Float = 60f, h: Float = 20f) =
        TimetableParser.LineIn(text, cx - w / 2, top, cx + w / 2, top + h)

    // 列中心
    private val X = mapOf(
        "sec" to 30f, "mon" to 70f, "tue" to 127f, "wed" to 184f,
        "thu" to 241f, "fri" to 298f, "sat" to 355f, "sun" to 412f
    )

    private fun buildLines(): List<TimetableParser.LineIn> {
        val ls = mutableListOf<TimetableParser.LineIn>()
        ls.add(ln("第11周", 244f, 75f))
        // 表头
        ls.add(ln("周一", X["mon"]!!, 145f, 40f))
        ls.add(ln("11/09", X["mon"]!!, 168f, 44f, 16f))
        ls.add(ln("周二", X["tue"]!!, 145f, 40f))
        ls.add(ln("周三", X["wed"]!!, 145f, 40f))
        ls.add(ln("周四", X["thu"]!!, 145f, 40f))
        ls.add(ln("周五", X["fri"]!!, 145f, 40f))
        ls.add(ln("周六", X["sat"]!!, 145f, 40f))
        ls.add(ln("周日", X["sun"]!!, 145f, 40f))
        // 左列节次 + 时间
        val marks = listOf(1 to 200f, 2 to 262f, 3 to 322f, 4 to 382f, 5 to 485f, 6 to 545f, 7 to 668f)
        marks.forEach { (no, y) ->
            ls.add(ln("$no", X["sec"]!!, y, 12f, 20f))
            ls.add(ln("%02d:10".format(8 + no), X["sec"]!!, y + 20f, 40f, 15f))
            ls.add(ln("%02d:55".format(8 + no), X["sec"]!!, y + 38f, 40f, 15f))
        }
        // 分隔行（横跨，位于周四列中心附近）
        ls.add(ln("午休", 244f, 435f, 60f, 18f))
        ls.add(ln("晚休", 244f, 612f, 60f, 18f))
        // 周一：数学(1-2)、Python(5-6)、晚自习(7)
        ls.add(ln("数学", X["mon"]!!, 195f))
        ls.add(ln("@7", X["mon"]!!, 217f, 30f))
        ls.add(ln("401", X["mon"]!!, 239f, 30f))
        ls.add(ln("Pytho", X["mon"]!!, 480f))
        ls.add(ln("n", X["mon"]!!, 502f, 20f))
        ls.add(ln("@10", X["mon"]!!, 524f, 34f))
        ls.add(ln("503", X["mon"]!!, 546f, 30f))
        ls.add(ln("晚自习", X["mon"]!!, 663f))
        ls.add(ln("@7", X["mon"]!!, 685f, 30f))
        ls.add(ln("401", X["mon"]!!, 707f, 30f))
        // 周二：语文、体育、哲学与人生、晚自习
        ls.add(ln("语文", X["tue"]!!, 195f))
        ls.add(ln("@7", X["tue"]!!, 217f, 30f))
        ls.add(ln("401", X["tue"]!!, 239f, 30f))
        ls.add(ln("体育", X["tue"]!!, 317f))
        ls.add(ln("@7", X["tue"]!!, 339f, 30f))
        ls.add(ln("401", X["tue"]!!, 361f, 30f))
        ls.add(ln("哲学与", X["tue"]!!, 480f))
        ls.add(ln("人生", X["tue"]!!, 502f, 44f))
        ls.add(ln("@7", X["tue"]!!, 524f, 30f))
        ls.add(ln("401", X["tue"]!!, 546f, 30f))
        ls.add(ln("晚自习", X["tue"]!!, 663f))
        ls.add(ln("@7", X["tue"]!!, 685f, 30f))
        ls.add(ln("401", X["tue"]!!, 707f, 30f))
        // 周三：Windows(3-4)、网络设备(5-6)、晚自习
        ls.add(ln("Windo", X["wed"]!!, 317f))
        ls.add(ln("ws", X["wed"]!!, 339f, 36f))
        ls.add(ln("@10", X["wed"]!!, 361f, 34f))
        ls.add(ln("404", X["wed"]!!, 383f, 30f))
        ls.add(ln("网络设", X["wed"]!!, 480f))
        ls.add(ln("备", X["wed"]!!, 502f, 20f))
        ls.add(ln("@7", X["wed"]!!, 524f, 30f))
        ls.add(ln("401", X["wed"]!!, 546f, 30f))
        ls.add(ln("晚自习", X["wed"]!!, 663f))
        ls.add(ln("@7", X["wed"]!!, 685f, 30f))
        ls.add(ln("401", X["wed"]!!, 707f, 30f))
        // 周四：英语(1-2)、网络设备(3-4)、公共艺术+7#401(5-6)、晚自习
        ls.add(ln("英语", X["thu"]!!, 195f))
        ls.add(ln("@7", X["thu"]!!, 217f, 30f))
        ls.add(ln("401", X["thu"]!!, 239f, 30f))
        ls.add(ln("网络设", X["thu"]!!, 317f))
        ls.add(ln("备", X["thu"]!!, 339f, 20f))
        ls.add(ln("@10", X["thu"]!!, 361f, 34f))
        ls.add(ln("406", X["thu"]!!, 383f, 30f))
        ls.add(ln("公共艺", X["thu"]!!, 480f))
        ls.add(ln("术", X["thu"]!!, 502f, 20f))
        ls.add(ln("+7#4", X["thu"]!!, 524f, 40f))
        ls.add(ln("01", X["thu"]!!, 546f, 24f))
        ls.add(ln("晚自习", X["thu"]!!, 663f))
        ls.add(ln("@7", X["thu"]!!, 685f, 30f))
        ls.add(ln("401", X["thu"]!!, 707f, 30f))
        // 周五：Python(1-2)、网络设备(5-6)
        ls.add(ln("Pytho", X["fri"]!!, 195f))
        ls.add(ln("n", X["fri"]!!, 217f, 20f))
        ls.add(ln("@10", X["fri"]!!, 239f, 34f))
        ls.add(ln("406", X["fri"]!!, 261f, 30f))
        ls.add(ln("网络设", X["fri"]!!, 480f))
        ls.add(ln("备", X["fri"]!!, 502f, 20f))
        ls.add(ln("@10", X["fri"]!!, 524f, 34f))
        ls.add(ln("406", X["fri"]!!, 546f, 30f))
        // 周日：晚自习
        ls.add(ln("晚自习", X["sun"]!!, 663f))
        ls.add(ln("@7", X["sun"]!!, 685f, 30f))
        ls.add(ln("401", X["sun"]!!, 707f, 30f))
        return ls
    }

    private fun find(courses: List<Course>, day: Int, s1: Int, name: String): Course? =
        courses.firstOrNull { it.day == day && it.startSection == s1 && it.name == name }

    @Test
    fun `dark grid timetable with fragmented names parses correctly`() {
        val courses = TimetableParser.parseLines(buildLines())
        assertTrue("至少应识别出 10 门课，实际 ${courses.size}", courses.size >= 10)

        // 断行名称合并
        assertEquals(1, find(courses, 1, 1, "数学")!!.startSection)
        assertEquals(2, find(courses, 1, 1, "数学")!!.endSection)
        assertEquals("Python", find(courses, 1, 5, "Python")!!.name)
        assertEquals("Windows", find(courses, 3, 3, "Windows")!!.name)
        assertEquals("哲学与人生", find(courses, 2, 5, "哲学与人生")!!.name)
        assertEquals("公共艺术", find(courses, 4, 5, "公共艺术")!!.name)
        assertEquals("网络设备", find(courses, 3, 5, "网络设备")!!.name)
        assertEquals("网络设备", find(courses, 5, 5, "网络设备")!!.name)

        // 教室合并：@7+401 → "7 401"；+7#4+01 → "7#401"
        assertEquals("7 401", find(courses, 1, 1, "数学")!!.room)
        assertEquals("10 503", find(courses, 1, 5, "Python")!!.room)
        assertEquals("10 404", find(courses, 3, 3, "Windows")!!.room)
        assertEquals("7#401", find(courses, 4, 5, "公共艺术")!!.room)

        // 连堂判断
        assertEquals(2, find(courses, 5, 1, "Python")!!.endSection)
        assertEquals(6, find(courses, 2, 5, "哲学与人生")!!.endSection)
        // 单节
        assertEquals(7, find(courses, 1, 7, "晚自习")!!.endSection)
        assertEquals(7, find(courses, 7, 7, "晚自习")!!.startSection)
        // 分隔行不是课程
        assertTrue(courses.none { it.name.contains("休") })
        // 周六列没有课
        assertTrue(courses.none { it.day == 6 })
    }

    @Test
    fun `compact timetable without column gap still splits blocks`() {
        // 紧凑版式：块间只有一行高的空隙
        val ls = buildLines().mapNotNull {
            when {
                it.t in 195f..260f && it.cx == X["mon"]!! -> null // 去掉周一数学，避免干扰
                else -> it
            }
        }.toMutableList()
        ls.add(ln("高数", X["mon"]!!, 195f))
        ls.add(ln("@7", X["mon"]!!, 214f, 30f, 16f))
        ls.add(ln("401", X["mon"]!!, 232f, 30f, 16f))
        val courses = TimetableParser.parseLines(ls)
        val math = find(courses, 1, 1, "高数")
        assertTrue("高数应被识别", math != null)
        assertEquals(2, math!!.endSection)
    }

    @Test
    fun `merge combines unique courses and keeps most complete per slot`() {
        val a = listOf(
            Course(name = "数学", day = 1, startSection = 1, endSection = 2),
            Course(name = "英语", day = 2, startSection = 3, endSection = 4, room = "A101")
        )
        val b = listOf(
            // 数学信息更全（带教师教室），同一时段应取 b 版本
            Course(name = "数学", day = 1, startSection = 1, endSection = 2, room = "7 401", teacher = "张三"),
            // 英语缺教室的版本，不应覆盖 a 里的
            Course(name = "英语", day = 2, startSection = 3, endSection = 4),
            // b 独有的课，应保留
            Course(name = "体育", day = 3, startSection = 5, endSection = 6)
        )
        val merged = TimetableParser.mergeCourses(listOf(a, b))
        assertEquals(3, merged.size)
        assertEquals("7 401", merged.first { it.name == "数学" }.room)
        assertEquals("张三", merged.first { it.name == "数学" }.teacher)
        assertEquals("A101", merged.first { it.name == "英语" }.room)
        assertTrue(merged.any { it.name == "体育" })
    }

    @Test
    fun `online parser keeps repeated courses with messy section formats`() {
        // 模拟 GLM 输出的各种不规矩节次写法，重点：同一天多节重复课不能丢、节次不能全变 1
        val json = """
            [
              {"name":"Python","day":1,"startSection":"3-4节","room":"7 401"},
              {"name":"Python","day":1,"section":"第5-6节","room":"10 503"},
              {"name":"晚自习","weekday":"周一","startSection":7,"endSection":8},
              {"name":"晚自习","weekday":"周一","start":9,"end":10},
              {"name":"数学","day":"星期二","sectionStart":"1","sectionEnd":"2"},
              {"name":"英语","day":4,"startSection":1,"endSection":2,"extra":"第3-4节无关字段"}
            ]
        """.trimIndent()
        val courses = OnlineOcr.parseCourses(json)
        assertEquals(6, courses.size)
        val py1 = courses.first { it.name == "Python" && it.room == "7 401" }
        assertEquals(3, py1.startSection)
        assertEquals(4, py1.endSection)
        val py2 = courses.first { it.name == "Python" && it.room == "10 503" }
        assertEquals(5, py2.startSection)
        assertEquals(6, py2.endSection)
        assertEquals(10, courses.first { it.name == "晚自习" && it.startSection == 9 }.endSection)
        assertEquals(2, courses.first { it.name == "数学" }.day)
        assertEquals(2, courses.first { it.name == "数学" }.endSection)
        // extra 里的 "第3-4节" 不能覆盖明确的 startSection/endSection
        val en = courses.first { it.name == "英语" }
        assertEquals(1, en.startSection)
        assertEquals(2, en.endSection)
    }

    @Test
    fun `rowStart wins and time inference never overwrites model sections`() {
        // 用户真实课表版式：左侧行号 1/3/5/7，块跨两行；晚自习是第 7 节 19:00-20:30。
        // 用户节次时间表若为默认值（8:00 起），19:00 会被时间反推算成第 13 节——
        // 这里必须保持模型读到的 7，不能被改。
        val json = """
            [
              {"name":"数学","day":1,"rowStart":1,"rowEnd":2,"startTime":"08:10","endTime":"09:50","room":"7 401"},
              {"name":"晚自习","day":1,"rowStart":7,"rowEnd":7,"startTime":"19:00","endTime":"20:30","room":"7 401"}
            ]
        """.trimIndent()
        val times = SectionTimes()   // 默认：第1节 08:00，每节 45 分钟，课间 10 分钟
        val courses = OnlineOcr.parseCourses(json, times)
        val math = courses.first { it.name == "数学" }
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        val evening = courses.first { it.name == "晚自习" }
        assertEquals(7, evening.startSection)
        assertEquals(7, evening.endSection)
    }

    @Test
    fun `time inference fills sections only when model gives none`() {
        val json = """[{"name":"数学","day":1,"startTime":"08:00","endTime":"09:40"}]"""
        val courses = OnlineOcr.parseCourses(json, SectionTimes())
        val math = courses.first()
        assertEquals(1, math.startSection)
        // 09:40 落在第 2 节 (08:55-09:40) 结束时间上
        assertEquals(2, math.endSection)
    }
}
