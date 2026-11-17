package com.aamina.timetable

import androidx.compose.ui.graphics.Color

/**
 * 课程色板。
 *
 * 选色标准：深色 / 浅色主题下都能看清，饱和度不刺眼，相邻色相区分明显。
 * 课程自己存的是 1–12 的索引（0 = 按课名自动分配），这样换主题也不会失配。
 */
object Palette {
    val colors = listOf(
        Color(0xFF6C9BF0), // 蓝
        Color(0xFF5FC08A), // 绿
        Color(0xFFF0A055), // 橙
        Color(0xFFE8736F), // 红
        Color(0xFFB98BE8), // 紫
        Color(0xFF5CC4E0), // 青
        Color(0xFFE6C15C), // 黄
        Color(0xFF9A93F5), // 蓝紫
        Color(0xFF4FC7B8), // 青绿
        Color(0xFFEE8FB8), // 粉
        Color(0xFF93A7B8), // 灰蓝
        Color(0xFFC09A76)  // 棕
    )

    val names = listOf(
        "蓝", "绿", "橙", "红", "紫", "青", "黄", "靛", "青绿", "粉", "灰", "棕"
    )

    /** 取某门课的颜色：手动选过就用选的，否则按课名散列稳定分配。 */
    fun of(course: Course): Color {
        val i = course.color
        if (i in 1..colors.size) return colors[i - 1]
        val h = course.name.hashCode()
        return colors[(h and Int.MAX_VALUE) % colors.size]
    }

    fun ofIndex(index: Int): Color =
        if (index in 1..colors.size) colors[index - 1] else colors[0]
}
