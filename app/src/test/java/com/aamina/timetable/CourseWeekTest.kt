package com.aamina.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 周次解析与日历重复规则的分段逻辑。写错会导致课程多出/漏掉若干周的日程。 */
class CourseWeekTest {

    @Test
    fun `单双周展开成具体周次`() {
        val odd = Course(startWeek = 1, endWeek = 9, weekMode = 1)
        assertEquals(listOf(1, 3, 5, 7, 9), odd.weekList())

        val even = Course(startWeek = 2, endWeek = 10, weekMode = 2)
        assertEquals(listOf(2, 4, 6, 8, 10), even.weekList())
    }

    @Test
    fun `自定义 weeks 优先于起止周`() {
        val c = Course(startWeek = 1, endWeek = 16, weeks = "2,4-6")
        assertEquals(listOf(2, 4, 5, 6), c.weekList())
        assertTrue(c.isActiveIn(5))
        assertFalse(c.isActiveIn(7))
    }

    @Test
    fun `连续周次合并成一条重复规则`() {
        val runs = weekRuns((1..16).toList())
        assertEquals(1, runs.size)
        assertEquals(Triple(1, 16, 1), runs[0])
    }

    @Test
    fun `单周压缩成 INTERVAL 2`() {
        val runs = weekRuns(listOf(1, 3, 5, 7, 9))
        assertEquals(1, runs.size)
        assertEquals(Triple(1, 5, 2), runs[0])
    }

    @Test
    fun `零散周次拆成多段`() {
        val runs = weekRuns(listOf(1, 2, 3, 7, 8, 12))
        assertEquals(3, runs.size)
        assertEquals(Triple(1, 3, 1), runs[0])
        assertEquals(Triple(7, 2, 1), runs[1])
        assertEquals(Triple(12, 1, 1), runs[2])
    }

    @Test
    fun `编码后再解析能还原`() {
        val weeks = listOf(1, 2, 3, 6, 7, 10)
        val encoded = encodeWeeks(weeks)
        assertEquals("1-3,6-7,10", encoded)
        val back = Course(weeks = encoded).weekList()
        assertEquals(weeks, back)
    }
}
