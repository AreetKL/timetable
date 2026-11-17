package com.aamina.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 在线结果的解析容错测试。
 *
 * 这些用例全部来自线上翻车现场：模型明明返回了内容，App 却解析出 0 条。
 * 只要有一条挂了，就说明用户又会看到"没认出课程"。
 */
class OnlineOcrTest {

    @Test
    fun `标准数组_带代码块包裹`() {
        val raw = """
            好的，识别结果如下：
            ```json
            [{"name":"高等数学","day":1,"rowStart":1,"rowEnd":2,"room":"7-401","teacher":"张三"},
             {"name":"Python","day":3,"rowStart":3,"rowEnd":4,"room":"10-503"}]
            ```
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(2, cs.size)
        val math = cs.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals("7-401", math.room)
        assertEquals("张三", math.teacher)
        val py = cs.first { it.name == "Python" }
        assertEquals(3, py.day)
        assertEquals(4, py.endSection)
    }

    @Test
    fun `按天分组的对象_星期要从键名推断`() {
        // 模型不按提示词输出数组，而是 {"周一":[...]} —— 老版本会在这里全丢
        val raw = """
            {"周一":[{"name":"高等数学","rowStart":1,"rowEnd":2}],
             "周三":[{"name":"Python","rowStart":3,"rowEnd":4}],
             "周日":[{"name":"晚自习","rowStart":9,"rowEnd":10}]}
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(3, cs.size)
        assertEquals(1, cs.first { it.name == "高等数学" }.day)
        assertEquals(3, cs.first { it.name == "Python" }.day)
        assertEquals(7, cs.first { it.name == "晚自习" }.day)
    }

    @Test
    fun `分两段输出的数组_要合并`() {
        val raw = """
            先看前半张表：
            [{"name":"高等数学","day":1,"rowStart":1,"rowEnd":2}]
            再看后半张表：
            [{"name":"大学英语","day":2,"rowStart":3,"rowEnd":4}]
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(2, cs.size)
        assertTrue(cs.any { it.name == "高等数学" })
        assertTrue(cs.any { it.name == "大学英语" })
    }

    @Test
    fun `markdown表格兜底`() {
        val raw = """
            识别结果：
            | 节次 | 周一 | 周二 |
            |---|---|---|
            | 1-2 | 高等数学<br>张三<br>7-401 | |
            | 3-4 | | 大学英语<br>李四<br>10-503 |
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(2, cs.size)
        val math = cs.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals("7-401", math.room)
        assertEquals("张三", math.teacher)
        val en = cs.first { it.name == "大学英语" }
        assertEquals(2, en.day)
        assertEquals(3, en.startSection)
    }

    @Test
    fun `行模式兜底`() {
        val raw = """
            周一 第1-2节 高等数学 张三 7-401
            周三 第5-6节 大学物理 王五 3-201
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(2, cs.size)
        val math = cs.first { it.name == "高等数学" }
        assertEquals(1, math.day)
        assertEquals(2, math.endSection)
        assertEquals("7-401", math.room)
        assertEquals("张三", math.teacher)
        assertEquals(3, cs.first { it.name == "大学物理" }.day)
    }

    @Test
    fun `空数组和纯文字都不崩`() {
        assertEquals(0, OnlineOcr.parseCourses("[]").size)
        assertEquals(0, OnlineOcr.parseCourses("").size)
        assertEquals(0, OnlineOcr.parseCourses("这不是课表").size)
    }

    @Test
    fun `字段名五花八门也能认`() {
        val raw = """
            [{"课程名称":"数据结构","周":"二","节次":"第3~4节","教室":"A305","教师":"赵六"}]
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(1, cs.size)
        val c = cs[0]
        assertEquals("数据结构", c.name)
        assertEquals(2, c.day)
        assertEquals(3, c.startSection)
        assertEquals(4, c.endSection)
        assertEquals("A305", c.room)
        assertEquals("赵六", c.teacher)
    }

    // ---- 记录型表格（v3.0 主路线：让模型直接输出表格）----

    @Test
    fun `记录表格_一行一门课`() {
        val raw = """
            识别结果如下：
            | 星期 | 节次 | 课程 | 教师 | 教室 |
            |------|------|------|------|------|
            | 周一 | 1-2 | 数学 | 张三 | 7 401 |
            | 周三 | 3 | Windows | | 10 404 |
            | 周日 | 7 | 晚自习 | | 7 401 |
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(3, cs.size)
        val math = cs.first { it.name == "数学" }
        assertEquals(1, math.day)
        assertEquals(1, math.startSection)
        assertEquals(2, math.endSection)
        assertEquals("张三", math.teacher)
        assertEquals("7 401", math.room)

        val win = cs.first { it.name == "Windows" }
        assertEquals(3, win.day)
        assertEquals(3, win.startSection)
        assertEquals(3, win.endSection)
        assertEquals("10 404", win.room)

        assertEquals(7, cs.first { it.name == "晚自习" }.day)
    }

    @Test
    fun `记录表格_列顺序打乱或多一列也不影响`() {
        val raw = """
            | 课程 | 星期 | 教室 | 节次 | 备注 |
            |---|---|---|---|---|
            | 数据结构 | 周二 | A305 | 5-6 | 实验课 |
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(1, cs.size)
        val c = cs[0]
        assertEquals("数据结构", c.name)
        assertEquals(2, c.day)
        assertEquals(5, c.startSection)
        assertEquals(6, c.endSection)
        assertEquals("A305", c.room)
    }

    @Test
    fun `记录表格_没有表头按固定顺序`() {
        val raw = """
            | 周五 | 3-4 | 大学物理 | 王五 | 3-201 |
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(1, cs.size)
        assertEquals("大学物理", cs[0].name)
        assertEquals(5, cs[0].day)
        assertEquals(3, cs[0].startSection)
        assertEquals(4, cs[0].endSection)
        assertEquals("3-201", cs[0].room)
    }

    @Test
    fun `记录表格_同课不同天不合并`() {
        val raw = """
            | 星期 | 节次 | 课程 | 教师 | 教室 |
            |---|---|---|---|---|
            | 周一 | 1-2 | Python | | 7 401 |
            | 周三 | 1-2 | Python | | 7 401 |
            | 周五 | 4 | Python | | 10 406 |
        """.trimIndent()
        val cs = OnlineOcr.parseCourses(raw)
        assertEquals(3, cs.size)
        assertEquals(listOf(1, 3, 5), cs.map { it.day })
    }
}
