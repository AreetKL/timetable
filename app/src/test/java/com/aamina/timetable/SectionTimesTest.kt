package com.aamina.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节次时间表：先按规则推算，再验证「逐节自定义」能覆盖且不影响别的节。
 * 这两个行为直接决定写入日历的时间对不对，改之前先让它们绿。
 */
class SectionTimesTest {

    private val base = SectionTimes(
        firstStartMinutes = 8 * 60 + 10, // 08:10
        durationMin = 45,
        breakMin = 10,
        amCount = 4,
        pmCount = 4,
        eveCount = 3,
        noonBreakMin = 90,
        eveBreakMin = 60
    )

    @Test
    fun `默认规则推算出分段作息`() {
        // 上午：08:10 / 09:05 / 10:00 / 10:55
        assertEquals(490, base.startOf(1))
        assertEquals(545, base.startOf(2))
        assertEquals(600, base.startOf(3))
        assertEquals(655, base.startOf(4))
        // 上午最后一节 11:40 下课，午休 90 分钟 → 下午 13:10
        assertEquals(700, base.endOf(4))
        assertEquals(790, base.startOf(5))
        // 下午第 4 节 16:40 下课，晚休 60 分钟 → 晚上 17:40
        assertEquals(1000, base.endOf(8))
        assertEquals(1060, base.startOf(9))
        assertEquals("08:10", base.fmt(base.startOf(1)))
    }

    @Test
    fun `连堂课跨节取最后一节的下课时间`() {
        // 第 1-2 节连堂：08:10 → 第 2 节 09:50 下课
        assertEquals(590, base.endOf(2))
        assertEquals("09:50", base.fmt(base.endOf(2)))
    }

    @Test
    fun `单独改一节不影响其他节`() {
        val fixed = base.withSlot(index = 2, start = 9 * 60 + 50, end = 10 * 60 + 35)
        assertEquals(590, fixed.startOf(3))
        assertEquals(635, fixed.endOf(3))
        // 第 1、2、4 节维持原样
        assertEquals(490, fixed.startOf(1))
        assertEquals(545, fixed.startOf(2))
        assertEquals(655, fixed.startOf(4))
        assertTrue(fixed.isCustom(3))
        assertTrue(!fixed.isCustom(4))
    }

    @Test
    fun `materialize 把整表固化成显式时间`() {
        val m = base.materialize()
        assertEquals(base.totalSections, m.slots.size)
        for (n in 1..base.totalSections) {
            assertEquals(base.startOf(n), m.startOf(n))
            assertEquals(base.endOf(n), m.endOf(n))
        }
    }

    @Test
    fun `改节数后旧的逐节时间被丢弃`() {
        // 设置页在节数变化时会 slots = emptyList()，这里模拟一次
        val resized = base.withSlot(1, 8 * 60, 8 * 60 + 45).copy(amCount = 3, slots = emptyList())
        assertEquals(3 + 4 + 3, resized.totalSections)
        assertTrue(resized.slots.isEmpty())
        assertEquals(490, resized.startOf(1))
    }

    @Test
    fun `凌晨一点这种越界输入被夹住`() {
        val weird = base.withSlot(0, 25 * 60 + 30, 26 * 60)
        assertTrue(weird.startOf(1) <= 24 * 60)
        assertTrue(weird.endOf(1) >= weird.startOf(1))
    }

    @Test
    fun `139分钟这种手指停在半格的值会被吸附到5分钟刻度`() {
        // v2.4 滑块没吸附，用户停在哪就存哪 → 下午第一节课被推到 14:29
        val dirty = base.copy(noonBreakMin = 139, eveBreakMin = 129, breakMin = 17)
        val clean = dirty.sanitized()
        assertEquals(140, clean.noonBreakMin)
        assertEquals(130, clean.eveBreakMin)
        assertEquals(15, clean.breakMin)
        // 吸附后下午 / 晚上的起点必须是整 5 分钟的时刻
        val s5 = clean.startOf(5)
        assertEquals(0, s5 % 5)
        assertEquals("14:15", clean.fmt(s5))
    }

    @Test
    fun `离谱的时长被夹到合理区间`() {
        val bad = base.copy(durationMin = 999, breakMin = -5, amCount = 0)
        val clean = bad.sanitized()
        assertEquals(120, clean.durationMin)
        assertEquals(0, clean.breakMin)
        assertEquals(1, clean.amCount)
        assertTrue(clean.totalSections >= 1)
    }
}
