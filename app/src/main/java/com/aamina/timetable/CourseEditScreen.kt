@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aamina.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 编辑课程（单门课的全屏编辑页）。
 *
 * 从首页「全部课程」里点任意一门进来，改完点右上角 ✓ 存回本地。
 * 版式照参考图：三个填空行 + 一行「上课时间」+ 周次网格 + 色板 + 删除。
 * 上课时间点开是双列滚轮（星期 + 起止节次），比在卡片里塞四个加减按钮快。
 */
@Composable
fun CourseEditScreen(
    index: Int,
    onBack: () -> Unit,
    snackbar: androidx.compose.material3.SnackbarHostState
) {
    val ctx = LocalContext.current
    val totalWeeks = remember { Store.totalWeeks(ctx) }
    val times = remember { Store.loadTimes(ctx) }
    // 只在进入时读一次；后续编辑都作用在本地副本上，取消就丢掉
    val original = remember(index) { Store.loadCourses(ctx).getOrNull(index) }
    if (original == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var course by remember(index) { mutableStateOf(original) }
    var showTimeSheet by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun save() {
        val list = Store.loadCourses(ctx)
        if (index in list.indices) list[index] = course
        Store.saveCourses(ctx, list)
        onBack()
        scope.launch { snackbar.showSnackbar("已保存") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { PageTitle("EDIT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = ::save) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            FieldCard("课程名", course.name, "必填") { course = course.copy(name = it) }
            Spacer(Modifier.height(10.dp))
            FieldCard("教室", course.room, "非必填") { course = course.copy(room = it) }
            Spacer(Modifier.height(10.dp))
            FieldCard("老师", course.teacher, "非必填") { course = course.copy(teacher = it) }

            Spacer(Modifier.height(22.dp))
            // 上课时间：一行摘要，点开滚轮
            Row(
                Modifier
                    .fillMaxWidth()
                    .bounceClick { showTimeSheet = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("上课时间", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "${dayName(course.day)} · 第 ${course.startSection}" +
                        (if (course.endSection != course.startSection) "–${course.endSection}" else "") + " 节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp)
                )
            }
            Text(
                "${times.fmt(times.startOf(course.startSection))} – " +
                    times.fmt(times.endOf(course.endSection)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上课周数", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                listOf("单周" to 1, "双周" to 2, "全选" to 0).forEach { (label, mode) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(50)
                            )
                            .bounceClick {
                                val weeks = when (mode) {
                                    0 -> (1..totalWeeks).toList()
                                    1 -> (1..totalWeeks).filter { it % 2 == 1 }
                                    else -> (1..totalWeeks).filter { it % 2 == 0 }
                                }
                                course = course.copy(weeks = encodeWeeks(weeks))
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            WeekGrid(
                totalWeeks = totalWeeks,
                selected = course.weekList().toSet(),
                onToggle = { w ->
                    val cur = course.weekList().toMutableSet()
                    if (!cur.add(w)) cur.remove(w)
                    course = course.copy(weeks = encodeWeeks(cur))
                }
            )
            if (course.weekList().isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "一周都没选，这门课不会写进日历",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("调课 / 停课", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    "只改某一周",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            val moves = course.moveList()
            val skips = course.skipList()
            if (moves.isEmpty() && skips.isEmpty()) {
                Text(
                    "整学期都按上面的时间上。某一周临时停课、或换到别的时间，点下面添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                skips.forEach { w ->
                    AdjustRow(
                        title = "第 $w 周",
                        detail = "停课",
                        warn = true
                    ) { course = course.withSkip(w, false) }
                }
                moves.sortedBy { it.week }.forEach { m ->
                    AdjustRow(
                        title = "第 ${m.week} 周",
                        detail = "${dayName(m.day)} · 第 ${m.start}" +
                            (if (m.end != m.start) "–${m.end}" else "") + " 节"
                    ) { course = course.withoutMove(m.week) }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAdjust = true },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("添加一周的调整", style = MaterialTheme.typography.labelLarge) }

            Spacer(Modifier.height(22.dp))
            Text("颜色", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            ColorPicker(selected = course.color) { course = course.copy(color = it) }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("删除课程") }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这门课？") },
            text = { Text("只会从本地课表里删掉，不会动已经写进系统日历的日程。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val list = Store.loadCourses(ctx)
                    list.removeAll { it === original || it == original }
                    Store.saveCourses(ctx, list)
                    onBack()
                    scope.launch { snackbar.showSnackbar("已删除这门课") }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    if (showTimeSheet) {
        TimeSlotSheet(
            day = course.day,
            start = course.startSection,
            end = course.endSection,
            times = times,
            onDismiss = { showTimeSheet = false },
            onConfirm = { d, s, e ->
                course = course.copy(day = d, startSection = s, endSection = e)
                showTimeSheet = false
            }
        )
    }

    if (showAdjust) {
        MoveSheet(
            totalWeeks = totalWeeks,
            times = times,
            defaultDay = course.day,
            defaultStart = course.startSection,
            defaultEnd = course.endSection,
            onDismiss = { showAdjust = false },
            onSkip = { w ->
                course = course.withSkip(w, true)
                showAdjust = false
            },
            onMove = { m ->
                course = course.withMove(m)
                showAdjust = false
            }
        )
    }
}

/** 「第 5 周 · 停课」这种一行，右侧 × 表示撤销这条调整。 */
@Composable
private fun AdjustRow(
    title: String,
    detail: String,
    warn: Boolean = false,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = if (warn) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close, contentDescription = "撤销",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp)
                )
            }
        }
    }
}

/**
 * 加一周的调整：先点周，再选「停课」还是「换时间」。
 *
 * 换时间时才展开星期 + 节次，免得一进来就是一大堆格子。
 */
@Composable
private fun MoveSheet(
    totalWeeks: Int,
    times: SectionTimes,
    defaultDay: Int,
    defaultStart: Int,
    defaultEnd: Int,
    onDismiss: () -> Unit,
    onSkip: (Int) -> Unit,
    onMove: (LessonMove) -> Unit
) {
    val weeks = totalWeeks.coerceIn(1, 30)
    val total = times.totalSections.coerceIn(1, 24)
    var w by remember { mutableIntStateOf(1) }
    var skip by remember { mutableStateOf(true) }
    var d by remember { mutableIntStateOf(defaultDay.coerceIn(1, 7)) }
    var s by remember { mutableIntStateOf(defaultStart.coerceIn(1, total)) }
    var e by remember { mutableIntStateOf(defaultEnd.coerceIn(defaultStart.coerceIn(1, total), total)) }

    fun pick(i: Int) {
        if (i <= s) {
            s = i
            if (e < s) e = s
        } else e = i
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Text(
                "调整某一周",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            Text("哪一周", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..weeks).forEach { i ->
                    FilterChip(
                        selected = w == i,
                        onClick = { w = i },
                        label = { Text("$i", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("怎么调整", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = skip,
                    onClick = { skip = true },
                    label = { Text("这一周停课", style = MaterialTheme.typography.labelMedium) }
                )
                FilterChip(
                    selected = !skip,
                    onClick = { skip = false },
                    label = { Text("换个时间上", style = MaterialTheme.typography.labelMedium) }
                )
            }

            if (!skip) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { i ->
                        DayCell(
                            label = dayName(i).removePrefix("周"),
                            on = i == d,
                            modifier = Modifier.weight(1f)
                        ) { d = i }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("节次", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (e == s) "第 $s 节" else "第 $s – $e 节",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                val cols = 6
                val rows = (total + cols - 1) / cols
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until cols) {
                                val idx = r * cols + c + 1
                                if (idx <= total) {
                                    SectionCell(
                                        index = idx,
                                        inRange = idx in s..e,
                                        isEdge = idx == s || idx == e,
                                        modifier = Modifier.weight(1f)
                                    ) { pick(idx) }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "第 $w 周改成 ${dayName(d)} ${times.fmt(times.startOf(s))}–${times.fmt(times.endOf(e))}，" +
                        "原来那节不再写进日历。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (skip) onSkip(w) else onMove(LessonMove(week = w, day = d, start = s, end = e))
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50)
                ) { Text("确定", style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 一行「标签 + 可直接输入的值」，视觉上是一个圆角填充块。 */
@Composable
private fun FieldCard(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/**
 * 选择上课时间：上面一排星期，下面两列滚轮选起止节。
 *
 * 滚轮是自己拿 ScrollState 做的（不依赖 snapFlingBehavior 的版本差异）：
 * 停下时把位置吸附到整格，并把当前格回调出去。
 */
@Composable
private fun TimeSlotSheet(
    day: Int,
    start: Int,
    end: Int,
    times: SectionTimes,
    onDismiss: () -> Unit,
    onConfirm: (day: Int, start: Int, end: Int) -> Unit
) {
    val total = times.totalSections.coerceIn(1, 24)
    var d by remember { mutableIntStateOf(day.coerceIn(1, 7)) }
    var s by remember { mutableIntStateOf(start.coerceIn(1, total)) }
    var e by remember { mutableIntStateOf(end.coerceIn(start.coerceIn(1, total), total)) }

    // 点起点之前 = 改起点；点起点之后 = 改终点。一下一个准，不用拖。
    fun pick(i: Int) {
        if (i <= s) {
            s = i
            if (e < s) e = s
        } else {
            e = i
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "上课时间",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "先点起始节，再点结束节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))
            // 星期：7 个等宽方块，单字，一行放得下，不会折行挤成两排
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { i ->
                    DayCell(
                        label = dayName(i).removePrefix("周"),
                        on = i == d,
                        modifier = Modifier.weight(1f)
                    ) { d = i }
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("节次", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text(
                    if (e == s) "第 $s 节" else "第 $s – $e 节",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))

            // 6 列网格：所有节次一屏看全，点一下就选，没有任何拖拽，
            // 也就不会和底部弹窗自己的下滑手势抢（那正是原来"调不动"的原因）
            val cols = 6
            val rows = (total + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until cols) {
                            val idx = r * cols + c + 1
                            if (idx <= total) {
                                SectionCell(
                                    index = idx,
                                    inRange = idx in s..e,
                                    isEdge = idx == s || idx == e,
                                    modifier = Modifier.weight(1f)
                                ) { pick(idx) }
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 落点摘要：选完立刻能看到具体钟点，不用再回上一页核对
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${dayName(d)} · 第 $s" + (if (e != s) "–$e" else "") + " 节",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${times.fmt(times.startOf(s))} — ${times.fmt(times.endOf(e))}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
                        )
                    }
                    Icon(
                        Icons.Outlined.Schedule, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.width(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { onConfirm(d, s, e) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50)
                ) { Text("确定", style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DayCell(
    label: String,
    on: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (on) 1f else 0.94f,
        animationSpec = Motion.Switch,
        label = "dayScale"
    )
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .height(46.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bounceClick(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (on) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCell(
    index: Int,
    inRange: Boolean,
    isEdge: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isEdge) 1f else 0.96f,
        animationSpec = Motion.Switch,
        label = "secScale"
    )
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = when {
            isEdge -> MaterialTheme.colorScheme.primary
            inRange -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .bounceClick(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "$index",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (inRange) FontWeight.Bold else FontWeight.Normal
                ),
                color = when {
                    isEdge -> MaterialTheme.colorScheme.onPrimary
                    inRange -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
