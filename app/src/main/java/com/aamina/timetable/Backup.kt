package com.aamina.timetable

import android.content.Context
import org.json.JSONObject

/**
 * 课表备份：把 SharedPreferences 里的全部键值导成一个 JSON。
 *
 * 换包名 / 换手机时，旧包的数据在新包里读不到（沙箱按 uid 隔离），
 * 只能由用户自己导出再导入。所以这里不挑字段，整份配置一起带走：
 * 课程、节次时间、学期第一周、AI Key、显示偏好都在里面。
 */
object Backup {
    private const val FILE = "timetable"
    private const val FORMAT = 1

    fun export(context: Context): String {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val strings = JSONObject()
        val ints = JSONObject()
        val bools = JSONObject()
        val longs = JSONObject()

        sp.all.forEach { (k, v) ->
            when (v) {
                is String -> strings.put(k, v)
                is Int -> ints.put(k, v)
                is Boolean -> bools.put(k, v)
                is Long -> longs.put(k, v)
            }
        }

        return JSONObject().apply {
            put("_v", FORMAT)
            put("_app", context.packageName)
            put("_at", System.currentTimeMillis())
            put("s", strings)
            put("i", ints)
            put("b", bools)
            put("l", longs)
            // 再单放一份课程，万一将来 SP 结构变了也能人工抢救
            put("courses", sp.getString("courses", "[]") ?: "[]")
        }.toString(2)
    }

    /** 粗略判断是不是本 App 的备份文件，避免把随便一个 json 灌进来。 */
    fun looksValid(json: String): Boolean = runCatching {
        val root = JSONObject(json)
        root.has("courses") || (root.optJSONObject("s")?.has("courses") == true)
    }.getOrDefault(false)

    /** 覆盖式恢复，返回写回的键数量。 */
    fun import(context: Context, json: String): Int {
        val root = JSONObject(json)
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val e = sp.edit()
        var n = 0

        root.optJSONObject("s")?.let { o ->
            o.keys().forEach { k -> e.putString(k, o.optString(k)); n++ }
        }
        root.optJSONObject("i")?.let { o ->
            o.keys().forEach { k -> e.putInt(k, o.optInt(k)); n++ }
        }
        root.optJSONObject("b")?.let { o ->
            o.keys().forEach { k -> e.putBoolean(k, o.optBoolean(k)); n++ }
        }
        root.optJSONObject("l")?.let { o ->
            o.keys().forEach { k -> e.putLong(k, o.optLong(k)); n++ }
        }
        // 老备份里可能只有 courses 一个字段
        if (n == 0) root.optString("courses").takeIf { it.isNotBlank() }?.let {
            e.putString("courses", it); n++
        }
        e.apply()
        return n
    }
}
