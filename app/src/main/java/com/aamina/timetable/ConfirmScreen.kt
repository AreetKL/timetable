@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aamina.timetable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ConfirmScreen(
    courses: SnapshotStateList<Course>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSynced: (List<Long>) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val times = remember { Store.loadTimes(ctx) }
    val totalWeeks = remember { Store.totalWeeks(ctx) }

    var expanded by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    // 同一天、节次有交叠、且周次有交集 → 真冲突（单双周的课正好错开，不算）
    val conflicts: Set<Int> = remember(courses.toList()) {
        val out = mutableSetOf<Int>()
        for (i in courses.indices) {
            for (j in i + 1 until courses.size) {
                val a = courses[i]
                val b = courses[j]
                val overlapSection = a.startSection <= b.endSection && b.startSection <= a.endSection
                if (a.day == b.day && overlapSection &&
                    a.weekList().toSet().intersect(b.weekList().toSet()).isNotEmpty()
                ) {
                    out.add(i)
                    out.add(j)
                }
            }
        }
        out
    }

    var calendars by remember { mutableStateOf(listOf<Triple<Long, String, String>>()) }
    var calId by remember { mutableStateOf(Store.calendarId(ctx)) }
    var monday by remember { mutableStateOf(Store.firstMonday(ctx)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var permsGranted by remember { mutableStateOf(hasCalendarPermission(ctx)) }

    fun reloadCalendars() {
        val cals = CalendarSync.listCalendars(ctx)
        calendars = cals
        if (cals.isNotEmpty() && cals.none { it.first == calId }) {
            calId = cals.first().first
            Store.setCalendarId(ctx, calId)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permsGranted = grants.values.all { it }
        if (permsGranted) reloadCalendars()
        else scope.launch { snackbar.showSnackbar("需要日历权限才能写入日程") }
    }

    LaunchedEffect(Unit) {
        if (permsGranted) reloadCalendars()
        else permLauncher.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        )
    }

    fun sync() {
        if (!permsGranted) {
            permLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
            return
        }
        val target = if (calId > 0 && calendars.any { it.first == calId }) calId
        else calendars.firstOrNull()?.first ?: -1L
        if (target <= 0) {
            scope.launch { snackbar.showSnackbar("没有找到可写入的日历账户") }
            return
        }
        calId = target
        Store.setCalendarId(ctx, target)
        val m = monday
        if (m == null) {
            showDatePicker = true
            scope.launch { snackbar.showSnackbar("先选择学期第一周的周一") }
            return
        }
        busy = true
        scope.launch {
            val ids = withContext(Dispatchers.IO) {
                courses.flatMap { c ->
                    runCatching {
                        CalendarSync.insertCourse(ctx, target, m, c, times, Store.reminderEnabled(ctx))
                    }.getOrDefault(emptyList())
                }
            }
            busy = false
            if (ids.isEmpty()) snackbar.showSnackbar("写入失败，请检查日历账户")
            else onSynced(ids)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { PageTitle("REVIEW · ${courses.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text("目标日历") },
                        supportingContent = {
                            Text(
                                calendars.firstOrNull { it.first == calId }?.let {
                                    "${it.second}（${it.third}）"
                                } ?: if (permsGranted) "未选择" else "未授权日历权限"
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Outlined.Event, contentDescription = null)
                        },
                        trailingContent = {
                            if (calendars.size > 1) {
                                Row {
                                    calendars.forEach { cal ->
                                        TextButton(onClick = {
                                            calId = cal.first
                                            Store.setCalendarId(ctx, cal.first)
                                        }) {
                                            Text(cal.second, style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("学期第一周（周一）") },
                        supportingContent = { Text(monday?.toString() ?: "未设置") },
                        leadingContent = {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        },
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = ::sync,
                        enabled = !busy && courses.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("写入 ${courses.size} 门课到系统日历")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (conflicts.isNotEmpty()) {
                item(key = "conflict") {
                    BouncyIn(40) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.width(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "有 ${conflicts.size} 门课时间重叠，标红的那几门建议先改掉",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
            items(courses.size, key = { i -> courses[i].hashCode() + i }) { i ->
                BouncyIn(delayMillis = 60 + i * 45) {
                    Column {
                        if (conflicts.contains(i)) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 6.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.ErrorOutline, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "与另一门课时间重叠",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        CourseEditorCard(
                            index = i,
                            course = courses[i],
                            times = times,
                            totalWeeks = totalWeeks,
                            expanded = expanded == i,
                            onToggle = { expanded = if (expanded == i) -1 else i },
                            onChange = { courses[i] = it },
                            onDelete = { courses.removeAt(i); if (courses.isEmpty()) onBack() }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = monday?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        monday = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        Store.setFirstMonday(ctx, monday!!)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(state = state)
        }
    }

    if (busy) {
        Surface(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize()
        ) {}
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(horizontal = 32.dp, vertical = 28.dp)) {
                    androidx.compose.material3.CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text("正在写入日历…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CourseEditorCard(
    index: Int,
    course: Course,
    times: SectionTimes,
    totalWeeks: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (Course) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.clickable { onToggle() }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        course.name.ifBlank { "（未命名课程）" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("${dayName(course.day)} · 第 ${course.startSection}")
                            if (course.endSection != course.startSection) append("–${course.endSection}")
                            append(" 节 · ${weekLabel(course)}")
                            if (course.teacher.isNotBlank()) append(" · ${course.teacher}")
                            if (course.room.isNotBlank()) append(" · ${course.room}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(270), expandFrom = Alignment.Top) +
                fadeIn(tween(220)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(120))
        ) {
            Column {
                HorizontalDivider()
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                OutlinedTextField(
                    value = course.name,
                    onValueChange = { onChange(course.copy(name = it)) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = course.teacher,
                        onValueChange = { onChange(course.copy(teacher = it)) },
                        label = { Text("教师") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = course.room,
                        onValueChange = { onChange(course.copy(room = it)) },
                        label = { Text("教室") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("上课日", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..7).forEach { d ->
                        FilterChip(
                            selected = course.day == d,
                            onClick = { onChange(course.copy(day = d)) },
                            label = { Text("周${dayName(d)}".removePrefix("周")) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperNum(
                        label = "开始节", value = course.startSection, min = 1, max = 20,
                        modifier = Modifier.weight(1f),
                        onChange = { onChange(course.copy(startSection = it, endSection = maxOf(it, course.endSection))) }
                    )
                    StepperNum(
                        label = "结束节", value = course.endSection, min = 1, max = 20,
                        modifier = Modifier.weight(1f),
                        onChange = { onChange(course.copy(endSection = maxOf(it, course.startSection))) }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上课周数", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${course.weekList().size} 周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("单周" to 1, "双周" to 2, "全选" to 0).forEach { (label, mode) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val weeks = when (mode) {
                                    0 -> (1..totalWeeks).toList()
                                    1 -> (1..totalWeeks).filter { it % 2 == 1 }
                                    else -> (1..totalWeeks).filter { it % 2 == 0 }
                                }
                                onChange(course.copy(weeks = encodeWeeks(weeks)))
                            },
                            label = { Text(label) }
                        )
                    }
                }
                WeekGrid(
                    totalWeeks = totalWeeks,
                    selected = course.weekList().toSet(),
                    onToggle = { w ->
                        val cur = course.weekList().toMutableSet()
                        if (!cur.add(w)) cur.remove(w)
                        onChange(course.copy(weeks = encodeWeeks(cur)))
                    }
                )

                Text("颜色", style = MaterialTheme.typography.labelLarge)
                ColorPicker(
                    selected = course.color,
                    onPick = { onChange(course.copy(color = it)) }
                )
                Text(
                    "时间：${times.fmt(times.startOf(course.startSection))} – ${times.fmt(times.endOf(course.endSection))}（可在设置中调整节次时间）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDelete) {
                    Text("删除这门课", color = MaterialTheme.colorScheme.error)
                }
                }
            }
        }
    }
}

@Composable
fun StepperNum(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$value",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        FilledIconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = "减")
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = { if (value < max) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "加")
        }
    }
}

/**
 * 周次网格：1–总周数，点一下选中/取消。
 * 比"起始周 + 结束周 + 单双周"直观得多，零散周次也能表达。
 */
@Composable
fun WeekGrid(totalWeeks: Int, selected: Set<Int>, onToggle: (Int) -> Unit) {
    val weeks = (1..totalWeeks.coerceAtLeast(1)).toList()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 6
    ) {
        weeks.forEach { w ->
            val on = selected.contains(w)
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .bounceClick { onToggle(w) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$w",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 12 色圆点。选中项加一圈描边 + 中心白点。 */
@Composable
fun ColorPicker(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 6
    ) {
        (1..Palette.colors.size).forEach { i ->
            val color = Palette.colors[i - 1]
            val on = selected == i
            Box(
                Modifier
                    .size(40.dp)
                    .background(color, RoundedCornerShape(50))
                    .then(
                        if (on) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(50))
                        else Modifier.border(2.dp, Color.Transparent, RoundedCornerShape(50))
                    )
                    .bounceClick { onPick(i) },
                contentAlignment = Alignment.Center
            ) {
                if (on) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(Color.White, RoundedCornerShape(50))
                    )
                }
            }
        }
    }
}
