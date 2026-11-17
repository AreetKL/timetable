package com.aamina.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 调课 / 停课：只改某一周，不动整学期。 */
class AdjustTest {

    private val base = Course(
        name = "Python", day = 1, startSection = 1, endSection = 2,
        startWeek = 1, endWeek = 16, weeks = "1-16"
    )

    @Test
    fun `停课那一周不算上课`() {
        val c = base.withSkip(5, true)
        assertFalse(c.activeIn(5))
        assertTrue(c.activeIn(4))
        assertTrue(c.activeIn(6))
        // 原始数据不受影响
        assertTrue(base.activeIn(5))
    }

    @Test
    fun `撤销停课能恢复`() {
        val c = base.withSkip(5, true).withSkip(5, false)
        assertTrue(c.activeIn(5))
        assertTrue(c.skipList().isEmpty())
    }

    @Test
    fun `多周停课按逗号存起来`() {
        val c = base.withSkip(3, true).withSkip(4, true)
        assertEquals(listOf(3, 4), c.skipList())
    }

    @Test
    fun `调课能记下来并读回来`() {
        val c = base.withMove(LessonMove(week = 5, day = 3, start = 5, end = 6))
        val m = c.moveIn(5)
        assertEquals(3, m?.day)
        assertEquals(5, m?.start)
        assertEquals(6, m?.end)
        assertNull(c.moveIn(6))
    }

    @Test
    fun `同一周再调一次会覆盖旧的`() {
        val c = base
            .withMove(LessonMove(5, 3, 5, 6))
            .withMove(LessonMove(5, 4, 7, 8))
        assertEquals(1, c.moveList().size)
        assertEquals(4, c.moveIn(5)?.day)
    }

    @Test
    fun `删掉调课记录`() {
        val c = base.withMove(LessonMove(5, 3, 5, 6)).withoutMove(5)
        assertNull(c.moveIn(5))
        assertTrue(c.moveList().isEmpty())
    }

    @Test
    fun `坏掉的调课 JSON 不会崩`() {
        val c = base.copy(moves = "这不是 JSON")
        assertTrue(c.moveList().isEmpty())
    }

    @Test
    fun `停课和调课互不影响`() {
        val c = base
            .withSkip(8, true)
            .withMove(LessonMove(5, 2, 3, 4))
        assertFalse(c.activeIn(8))
        assertEquals(2, c.moveIn(5)?.day)
    }
}

/** 任意 AI Key：靠 Key 长相先猜服务商。 */
class ProvidersTest {

    @Test
    fun `智谱那种带点的 key 能认出来`() {
        val p = Providers.guessByKey("1541cb71094a4697b12bd17f10f785dc.2Hxxxx")
        assertEquals("zhipu", p?.id)
    }

    @Test
    fun `gemini 的 AIza 开头能认出来`() {
        assertEquals("gemini", Providers.guessByKey("AIzaSyD-abcdefghijklmn")?.id)
    }

    @Test
    fun `认不出来的 key 交给逐个试`() {
        assertNull(Providers.guessByKey("sk-abcdef0123456789"))
    }

    @Test
    fun `空配置回落到第一个模型`() {
        val cfg = AiConfig(apiKey = "k", providerId = "dashscope")
        assertEquals("qwen-vl-plus", cfg.realModel)
        assertTrue(cfg.endpoint.contains("dashscope.aliyuncs.com"))
    }

    @Test
    fun `自定义地址优先于预设`() {
        val cfg = AiConfig("k", "custom", "https://my.proxy/v1/chat/completions", "my-vl")
        assertEquals("https://my.proxy/v1/chat/completions", cfg.endpoint)
        assertEquals("my-vl", cfg.realModel)
    }

    @Test
    fun `家家的预设地址都不为空`() {
        Providers.ALL.filter { it.id != "custom" }.forEach {
            assertTrue("${it.id} 没有地址", it.baseUrl.startsWith("https://"))
            assertTrue("${it.id} 没有模型", it.models.isNotEmpty())
        }
    }
}
