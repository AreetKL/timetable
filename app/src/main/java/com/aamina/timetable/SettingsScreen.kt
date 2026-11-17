@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.aamina.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val appVersion = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: ""
    }
    val scope = rememberCoroutineScope()

    var times by remember { mutableStateOf(Store.loadTimes(ctx)) }
    var reminder by remember { mutableStateOf(Store.reminderEnabled(ctx)) }
    var dynamicColor by remember { mutableStateOf(Store.dynamicColor(ctx)) }
    var monday by remember { mutableStateOf(Store.firstMonday(ctx)) }
    var aiCfg by remember { mutableStateOf(Store.aiConfig(ctx)) }
    var customBase by remember { mutableStateOf(Store.aiConfig(ctx).baseUrl) }
    var customModel by remember { mutableStateOf(Store.aiConfig(ctx).model) }
    var testing by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showSectionSheet by remember { mutableStateOf(false) }
    var showSlotSheet by remember { mutableStateOf(false) }
    var totalWeeks by remember { mutableIntStateOf(Store.totalWeeks(ctx)) }
    var weekOverride by remember { mutableIntStateOf(Store.weekOverride(ctx)) }
    var showWeekend by remember { mutableStateOf(Store.showWeekend(ctx)) }
    var showOther by remember { mutableStateOf(Store.showOtherWeeks(ctx)) }
    var monFirst by remember { mutableStateOf(Store.mondayFirst(ctx)) }
    var editNumber by remember { mutableStateOf<String?>(null) } // "week" / "total" / "firstDay"
    // 权限申请完成后的待执行动作："deleteAll" = 统计并确认删除全部
    var pendingAction by remember { mutableStateOf("") }
    // 非空 = 显示"删除全部"确认弹窗，值为待删除数量
    var deleteAllCount by remember { mutableStateOf<Int?>(null) }
    // 改规则参数会把逐节手动时间清空，只提示一次，别每拖一下就弹
    var ruleNoticeShown by remember { mutableStateOf(false) }

    fun saveTimes(t: SectionTimes) {
        times = t
        Store.saveTimes(ctx, t)
    }

    /** 改「时长/课间/午休/晚休」这类规则参数：一律重排，手动逐节时间作废（提示一次）。 */
    fun applyRule(t: SectionTimes) {
        val hadSlots = times.slots.isNotEmpty()
        saveTimes(t.copy(slots = emptyList()))
        if (hadSlots && !ruleNoticeShown) {
            ruleNoticeShown = true
            scope.launch {
                snackbar.showSnackbar("已按新规则重排全部节次，之前逐节手动改的时间已清空")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { PageTitle("SETTINGS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("课表设置", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            ListItem(
                headlineContent = { Text("学期第一天") },
                supportingContent = {
                    Text(
                        monday?.toString()?.plus("（周一）")
                            ?: "未设置——同步日程前必须先设置"
                    )
                },
                modifier = Modifier.bounceClick { showDatePicker = true }
            )

            ListItem(
                headlineContent = { Text("当前周数") },
                supportingContent = {
                    Text(
                        if (weekOverride > 0) "手动指定为第 $weekOverride 周"
                        else "自动计算（按学期第一天推算）"
                    )
                },
                trailingContent = {
                    Text(
                        autoWeekLabel(ctx),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.bounceClick { editNumber = "week" }
            )

            ListItem(
                headlineContent = { Text("本学期总周数") },
                supportingContent = { Text("决定编辑课程时周次网格的数量") },
                trailingContent = {
                    Text(
                        "$totalWeeks 周",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.bounceClick { editNumber = "total" }
            )

            ListItem(
                headlineContent = { Text("显示周末") },
                supportingContent = { Text("关闭后周六周日不显示") },
                trailingContent = {
                    Switch(checked = showWeekend, onCheckedChange = {
                        showWeekend = it
                        Store.setShowWeekend(ctx, it)
                    })
                }
            )

            ListItem(
                headlineContent = { Text("显示非本周课程") },
                supportingContent = { Text("关闭后只显示当前周要上的课") },
                trailingContent = {
                    Switch(checked = showOther, onCheckedChange = {
                        showOther = it
                        Store.setShowOtherWeeks(ctx, it)
                    })
                }
            )

            ListItem(
                headlineContent = { Text("一周起始日") },
                supportingContent = { Text("课表从周几开始排") },
                trailingContent = {
                    Text(
                        if (monFirst) "周一" else "周日",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.bounceClick { editNumber = "firstDay" }
            )

            Text("节次时间", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            ListItem(
                headlineContent = { Text("第 1 节开始时间") },
                supportingContent = { Text("改这里会按规则重排全部节次") },
                trailingContent = {
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(times.fmt(times.startOf(1)))
                    }
                }
            )

            // ★ 逐节时间：作息不规律时，一节一节直接钉死，比调参数准
            ListItem(
                headlineContent = { Text("逐节时间") },
                supportingContent = {
                    Text(
                        if (times.slots.isEmpty()) "推算的不准？点进去一节一节改，改完立刻生效"
                        else "已有 ${times.slots.size} 节手动设定"
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.bounceClick { showSlotSheet = true }
            )

            // ★ 每天节数：上午 / 下午 / 晚上 分开设置
            ListItem(
                headlineContent = { Text("每天节数") },
                supportingContent = {
                    Text(
                        "上午 ${times.amCount} 节 · 下午 ${times.pmCount} 节" +
                            if (times.eveCount > 0) " · 晚上 ${times.eveCount} 节" else " · 无晚课"
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.bounceClick { showSectionSheet = true }
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 预览一律按"纯规则"算，别被手动逐节时间带偏
                val base = times.copy(slots = emptyList())
                val amN = base.amCount.coerceAtLeast(1)
                val pmFirst = amN + 1
                val eveFirst = amN + base.pmCount + 1
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    MinuteRow(
                        label = "每节时长",
                        value = times.durationMin,
                        range = 30..120,
                        hint = "第 1 节 ${base.fmt(base.startOf(1))} – ${base.fmt(base.startOf(1) + base.durationMin)}"
                    ) { applyRule(times.copy(durationMin = it)) }

                    MinuteRow(
                        label = "课间休息",
                        value = times.breakMin,
                        range = 0..45,
                        hint = "第 2 节 ${base.fmt(base.startOf(2))} 开始"
                    ) { applyRule(times.copy(breakMin = it)) }

                    if (times.pmCount > 0) {
                        MinuteRow(
                            label = "午休",
                            value = times.noonBreakMin,
                            range = 0..240,
                            hint = "下午第 1 节（第 $pmFirst 节）${base.fmt(base.startOf(pmFirst))} 开始"
                        ) { applyRule(times.copy(noonBreakMin = it)) }
                    }
                    if (times.eveCount > 0) {
                        MinuteRow(
                            label = "晚休",
                            value = times.eveBreakMin,
                            range = 0..240,
                            hint = "晚上第 1 节（第 $eveFirst 节）${base.fmt(base.startOf(eveFirst))} 开始"
                        ) { applyRule(times.copy(eveBreakMin = it)) }
                    }

                    Text(
                        if (times.slots.isEmpty())
                            "全部节次按这套规则自动排出；某几节对不上，去「逐节时间」单独改。"
                        else
                            "当前有 ${times.slots.size} 节是手动设的；改上面任一参数会按新规则整体重排并清空手动设置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            BouncyIn(120) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val amEnd = times.amCount
                        val pmEnd = times.amCount + times.pmCount
                        TimeBlock("上午", 1, amEnd, times)
                        if (times.pmCount > 0) {
                            TimeBlock("下午", amEnd + 1, pmEnd, times, divider = true)
                        }
                        if (times.eveCount > 0) {
                            TimeBlock("晚上", pmEnd + 1, pmEnd + times.eveCount, times, divider = true)
                        }
                    }
                }
            }

            Text("日程", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            ListItem(
                headlineContent = { Text("上课前 10 分钟提醒") },
                supportingContent = { Text("写入日历时自动附加") },
                trailingContent = {
                    Switch(checked = reminder, onCheckedChange = {
                        reminder = it
                        Store.setReminderEnabled(ctx, it)
                    })
                }
            )

            ListItem(
                headlineContent = { Text("跟随壁纸取色") },
                supportingContent = {
                    Text(
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                            "Material You：整个 App 的配色跟着桌面壁纸变"
                        else "需要 Android 12 及以上，当前系统用内置配色"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                        onCheckedChange = {
                            dynamicColor = it
                            ThemeMode.dynamic.value = it
                            Store.setDynamicColor(ctx, it)
                        }
                    )
                }
            )

            Text("在线识别（可选）", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("服务商", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Providers.ALL.forEach { p ->
                            FilterChip(
                                selected = aiCfg.providerId == p.id,
                                onClick = {
                                    aiCfg = aiCfg.copy(providerId = p.id, model = "", baseUrl = "")
                                    customBase = ""
                                    customModel = ""
                                    Store.saveAi(ctx, aiCfg)
                                },
                                label = { Text(p.label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = aiCfg.apiKey,
                        onValueChange = {
                            aiCfg = aiCfg.copy(apiKey = it.trim())
                            Store.saveAi(ctx, aiCfg)
                        },
                        label = { Text("${aiCfg.provider.label} API Key") },
                        placeholder = { Text("不填则只用离线识别") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (aiCfg.providerId == "custom") {
                        OutlinedTextField(
                            value = customBase,
                            onValueChange = {
                                customBase = it.trim()
                                aiCfg = aiCfg.copy(baseUrl = customBase)
                                Store.saveAi(ctx, aiCfg)
                            },
                            label = { Text("接口地址（完整 chat/completions）") },
                            placeholder = { Text("https://xxx/v1/chat/completions") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customModel,
                            onValueChange = {
                                customModel = it.trim()
                                aiCfg = aiCfg.copy(model = customModel)
                                Store.saveAi(ctx, aiCfg)
                            },
                            label = { Text("模型名") },
                            placeholder = { Text("如 qwen-vl-max") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("识别模型", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            aiCfg.provider.models.forEach { (id, label) ->
                                FilterChip(
                                    selected = aiCfg.realModel == id,
                                    onClick = {
                                        aiCfg = aiCfg.copy(model = id)
                                        Store.saveAi(ctx, aiCfg)
                                    },
                                    label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !testing && aiCfg.apiKey.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    testing = true
                                    val msg = withContext(Dispatchers.IO) {
                                        OnlineOcr.verifyKey(aiCfg)
                                    }
                                    testing = false
                                    snackbar.showSnackbar(
                                        msg ?: "✓ ${aiCfg.provider.label} 可用，在线识别已开启"
                                    )
                                }
                            }
                        ) { Text(if (testing) "测试中…" else "测试 Key") }
                        TextButton(
                            enabled = !testing && aiCfg.apiKey.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    testing = true
                                    val hit = withContext(Dispatchers.IO) {
                                        OnlineOcr.detectProvider(aiCfg.apiKey)
                                    }
                                    testing = false
                                    if (hit == null) {
                                        snackbar.showSnackbar("这个 Key 试遍所有服务商都不通，检查一下是否复制完整")
                                    } else {
                                        aiCfg = aiCfg.copy(providerId = hit.first.id, model = hit.second)
                                        Store.saveAi(ctx, aiCfg)
                                        snackbar.showSnackbar("✓ 认出来了：${hit.first.label}")
                                    }
                                }
                            }
                        ) { Text("自动识别") }
                    }
                    Text(
                        aiCfg.provider.hint + "\n\n" +
                            "填好后：离线识别不理想时自动改用这个大模型看图，准确率更高" +
                            "（需联网，课表图片会上传到该服务商）。换 Key 换服务商都不用改代码，任何 OpenAI 兼容接口都能用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("日历同步", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            val courseCount = remember { Store.loadCourses(ctx).size }
            var resyncing by remember { mutableStateOf(false) }
            var confirmResync by remember { mutableStateOf<Int?>(null) }

            // 真正干活的部分：不碰权限、不碰 UI 状态，直接按当前课表重写一遍
            fun performResync() {
                val m = monday
                if (m == null) {
                    showDatePicker = true
                    scope.launch { snackbar.showSnackbar("先选一下学期第一周的周一") }
                    return
                }
                resyncing = true
                scope.launch {
                    val target = withContext(Dispatchers.IO) { CalendarSync.pickTargetCalendar(ctx) }
                    if (target <= 0) {
                        resyncing = false
                        snackbar.showSnackbar("没有找到可写入的日历账户")
                        return@launch
                    }
                    Store.setCalendarId(ctx, target)
                    val r = withContext(Dispatchers.IO) {
                        runCatching {
                            CalendarSync.resyncAll(
                                ctx, target, m,
                                Store.loadCourses(ctx), Store.loadTimes(ctx),
                                Store.reminderEnabled(ctx)
                            )
                        }.getOrNull()
                    }
                    resyncing = false
                    when {
                        r == null -> snackbar.showSnackbar("写入失败，检查日历权限或账户")
                        r.inserted == 0 -> snackbar.showSnackbar("课表是空的，先添加课程")
                        else -> snackbar.showSnackbar(
                            buildString {
                                append("✓ 已写入 ${r.inserted} 个日程")
                                if (r.deleted > 0) append("（先清掉旧的 ${r.deleted} 条）")
                                if (r.failedCourses > 0) append("，${r.failedCourses} 门课失败")
                            }
                        )
                    }
                }
            }

            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val ok = grants.values.all { it }
                val action = pendingAction
                pendingAction = ""
                if (!ok) {
                    scope.launch { snackbar.showSnackbar("需要日历权限才能读写日程") }
                } else if (action == "resync") {
                    performResync()
                } else if (action == "deleteAll") {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) {
                            runCatching { CalendarSync.findCreatedEventIds(ctx).size }
                                .getOrDefault(0)
                        }
                        if (n <= 0) snackbar.showSnackbar("没有找到本应用写入的日程")
                        else deleteAllCount = n
                    }
                }
            }

            // 入口：先看日历里还剩多少自己的日程，有就先问一句要不要清理
            fun startResync() {
                if (!hasCalendarPermission(ctx)) {
                    pendingAction = "resync"
                    permLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                    )
                    return
                }
                scope.launch {
                    val existing = withContext(Dispatchers.IO) {
                        runCatching { CalendarSync.findCreatedEventIds(ctx).size }.getOrDefault(0)
                    }
                    if (existing > 0) confirmResync = existing else performResync()
                }
            }

            Button(
                onClick = ::startResync,
                enabled = !resyncing && courseCount > 0,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (resyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("正在写入…")
                } else {
                    Icon(Icons.Outlined.EventRepeat, contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(
                        if (courseCount > 0) "重新写入日程（$courseCount 门课）"
                        else "重新写入日程"
                    )
                }
            }

            // 写入前确认：日历里还剩自己的日程时，先说明白「清掉旧的再写新的」
            confirmResync?.let { count ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmResync = null },
                    icon = { Icon(Icons.Outlined.EventRepeat, contentDescription = null) },
                    title = { Text("先清理再写入？") },
                    text = {
                        Text(
                            "系统日历里还有 $count 条本 App 写入的日程。\n\n" +
                                "重新写入会先删掉这些旧日程，再按当前课表整份写一遍，" +
                                "避免同一节课出现两份。你自己建的其他日程不受影响。"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmResync = null
                            performResync()
                        }) { Text("清理并重写") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmResync = null }) { Text("取消") }
                    }
                )
            }

            Text(
                "系统日历里的课表被手动删掉、或换了日历账户时，点上面这个按钮，\n" +
                    "就会按当前课表整份重写一遍——不会重复，也不会漏掉调课和停课。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    if (hasCalendarPermission(ctx)) {
                        // 先统计数量，让用户看到要删多少再确认
                        scope.launch {
                            val n = withContext(Dispatchers.IO) {
                                runCatching { CalendarSync.findCreatedEventIds(ctx).size }
                                    .getOrDefault(0)
                            }
                            if (n <= 0) snackbar.showSnackbar("没有找到本应用写入的日程")
                            else deleteAllCount = n
                        }
                    } else {
                        pendingAction = "deleteAll"
                        permLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text("一键删除本 App 写入的全部日程")
            }

            Text("备份", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp))

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val txt = Backup.export(ctx)
                            ctx.contentResolver.openOutputStream(uri)?.use {
                                it.write(txt.toByteArray())
                            }
                            true
                        }.getOrDefault(false)
                    }
                    snackbar.showSnackbar(
                        if (ok) "✓ 备份已保存，换包名或换手机时用它恢复" else "导出失败"
                    )
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) scope.launch {
                    val n = withContext(Dispatchers.IO) {
                        runCatching {
                            val txt = ctx.contentResolver.openInputStream(uri)
                                ?.bufferedReader()?.use { r -> r.readText() }
                                ?: return@runCatching -1
                            if (!Backup.looksValid(txt)) -1 else Backup.import(ctx, txt)
                        }.getOrDefault(-1)
                    }
                    if (n < 0) {
                        snackbar.showSnackbar("这不是课表备份文件")
                    } else {
                        // 立刻把本页状态换成恢复后的值，不用退出再进
                        times = Store.loadTimes(ctx)
                        monday = Store.firstMonday(ctx)
                        totalWeeks = Store.totalWeeks(ctx)
                        weekOverride = Store.weekOverride(ctx)
                        showWeekend = Store.showWeekend(ctx)
                        showOther = Store.showOtherWeeks(ctx)
                        reminder = Store.reminderEnabled(ctx)
                        snackbar.showSnackbar("✓ 已恢复 $n 项设置，返回首页就能看到课表")
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("课表备份.json") },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("导出备份") }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("从备份恢复") }
            }

            Text(
                "备份是一个 json，里面是课表加全部设置（含 AI Key）。\n" +
                    "换包名、换手机、重装系统之前先导一份，装好再恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                if (aiCfg.apiKey.isBlank()) "识别与解析完全离线进行，图片不会上传。"
                else "混合模式：先离线识别，不理想时自动在线识别（图片会上传）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 版本号直接显示在页脚：装没装上、装的是哪一版，一眼能看到
            Spacer(Modifier.height(20.dp))
            Text(
                "课表日历  v$appVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // 「一键删除全部」二次确认：先展示数量，避免误删
    deleteAllCount?.let { count ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteAllCount = null },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("删除全部导入的日程？") },
            text = {
                Text(
                    "将删除本 App 写入的 $count 个日程，包括多次导入产生的重复项。\n\n" +
                        "删除后不可恢复；你在系统日历里手动创建的其他日程不受影响。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteAllCount = null
                    scope.launch {
                        val n = withContext(Dispatchers.IO) {
                            runCatching { CalendarSync.deleteAllCreated(ctx) }.getOrDefault(0)
                        }
                        snackbar.showSnackbar(
                            if (n > 0) "已删除 $n 个日程" else "没有找到可删除的日程"
                        )
                    }
                }) { Text("全部删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAllCount = null }) { Text("取消") }
            }
        )
    }

    if (showSectionSheet) {
        SectionCountSheet(
            times = times,
            onDismiss = { showSectionSheet = false },
            onConfirm = { updated ->
                // 节数变了，旧的逐节时间对不上号 → 清空，回到规则推算
                saveTimes(updated.copy(slots = emptyList()))
                showSectionSheet = false
                scope.launch {
                    snackbar.showSnackbar("已更新：每天 ${updated.totalSections} 节")
                }
            }
        )
    }

    if (showTimePicker) {
        val st = rememberTimePickerState(
            initialHour = times.startOf(1) / 60,
            initialMinute = times.startOf(1) % 60,
            is24Hour = true
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("第 1 节开始时间") },
            confirmButton = {
                TextButton(onClick = {
                    saveTimes(
                        times.copy(
                            firstStartMinutes = st.hour * 60 + st.minute,
                            slots = emptyList()
                        )
                    )
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("取消") } },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeInput(state = st)
                    Text(
                        "确定后，其余节次按「每节时长 + 课间 + 午休 / 晚休」重新排一遍。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    if (showSlotSheet) {
        SectionTimeSheet(
            times = times,
            onDismiss = { showSlotSheet = false },
            onChange = { saveTimes(it) }
        )
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
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

    // 数字类设置：当前周数 / 总周数 / 一周起始日
    editNumber?.let { which ->
        when (which) {
            "firstDay" -> androidx.compose.material3.AlertDialog(
                onDismissRequest = { editNumber = null },
                title = { Text("一周起始日") },
                text = { Text("影响课表里每天的排列顺序，也影响“当前周”的起算。") },
                confirmButton = {
                    TextButton(onClick = {
                        monFirst = true; Store.setMondayFirst(ctx, true); editNumber = null
                    }) { Text("周一") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        monFirst = false; Store.setMondayFirst(ctx, false); editNumber = null
                    }) { Text("周日") }
                }
            )
            else -> {
                val isWeek = which == "week"
                val min = if (isWeek) 1 else 1
                val max = if (isWeek) 30 else 30
                val current = if (isWeek) (if (weekOverride > 0) weekOverride else autoWeekValue(ctx)) else totalWeeks
                NumberDialog(
                    title = if (isWeek) "当前周数" else "本学期总周数",
                    value = current,
                    range = min..max,
                    extraLabel = if (isWeek) "自动计算" else null,
                    onDismiss = { editNumber = null },
                    onExtra = if (isWeek) {
                        {
                            weekOverride = -1
                            Store.setWeekOverride(ctx, -1)
                            editNumber = null
                        }
                    } else null,
                    onPick = { v ->
                        if (isWeek) {
                            weekOverride = v
                            Store.setWeekOverride(ctx, v)
                        } else {
                            totalWeeks = v
                            Store.setTotalWeeks(ctx, v)
                        }
                        editNumber = null
                    }
                )
            }
        }
    }
}

/**
 * 时间轴预览的一段（上午 / 下午 / 晚上）。
 * 分段展示而不是一路排到底，用户一眼能看出"第 5 节是下午第一节"。
 */
@Composable
private fun TimeBlock(
    label: String,
    startSection: Int,
    endSection: Int,
    times: SectionTimes,
    divider: Boolean = false
) {
    if (endSection < startSection) return
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 8.dp),
                color = content.copy(alpha = 0.18f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = content)
            Text(
                if (startSection == endSection) "第 $startSection 节"
                else "第 $startSection–$endSection 节",
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.6f)
            )
        }
        for (n in startSection..endSection) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "第 $n 节",
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.85f)
                )
                Text(
                    "${times.fmt(times.startOf(n))} – ${times.fmt(times.endOf(n))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = content
                )
            }
        }
    }

}

/**
 * 一行「分钟」设置：−/+ 精调 + 吸附到 5 分钟刻度的滑块。
 *
 * 之前直接用 Slider(steps = n) 是错的：手指停在哪就是哪，会存下 139 / 129 这种值，
 * 于是下午第一节课被推到 14:29。这里统一按 5 分钟吸附，循环里永远不会出现怪数字。
 */
@Composable
private fun MinuteRow(
    label: String,
    value: Int,
    range: IntRange,
    hint: String,
    onChange: (Int) -> Unit
) {
    val step = 5
    fun snapped(raw: Float): Int {
        val k = ((raw - range.first) / step).roundToInt()
        return (range.first + k * step).coerceIn(range.first, range.last)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { onChange((value - step).coerceAtLeast(range.first)) },
                modifier = Modifier.size(34.dp),
                enabled = value > range.first
            ) { Icon(Icons.Filled.Remove, contentDescription = "减少", modifier = Modifier.size(17.dp)) }
            Text(
                "$value 分",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp)
            )
            FilledTonalIconButton(
                onClick = { onChange((value + step).coerceAtMost(range.last)) },
                modifier = Modifier.size(34.dp),
                enabled = value < range.last
            ) { Icon(Icons.Filled.Add, contentDescription = "增加", modifier = Modifier.size(17.dp)) }
        }
        MinuteSlider(value = value, range = range, step = step, onChange = onChange)
    }
}

/**
 * 自绘滑块。
 *
 * M3 新版把滑块拇指改成了 4dp 的竖条，在深色主题里看着像轨道裂了一道缝，很怪。
 * 这里画回圆形拇指：轨道圆头、拇指按下放大、位置用弹簧跟随，拖起来是"滑"过去而不是"跳"过去。
 * 取值照旧按 [step] 吸附，不会出现 139 这种数。
 */
@Composable
private fun MinuteSlider(
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit
) {
    val span = (range.last - range.first).coerceAtLeast(1)
    val target = ((value - range.first).toFloat() / span).coerceIn(0f, 1f)
    val pos by animateFloatAsState(target, Motion.Switch, label = "sliderPos")
    val scope = rememberCoroutineScope()
    val thumb = remember { Animatable(1f) }
    val density = LocalDensity.current
    val padPx = with(density) { 12.dp.toPx() }
    var trackW by remember { mutableStateOf(0f) }
    val cs = MaterialTheme.colorScheme

    fun pick(x: Float) {
        val usable = (trackW - padPx * 2).coerceAtLeast(1f)
        val f = ((x - padPx) / usable).coerceIn(0f, 1f)
        val k = ((f * span) / step).roundToInt()
        val v = (range.first + k * step).coerceIn(range.first, range.last)
        if (v != value) onChange(v)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { trackW = it.width.toFloat() }
            .pointerInput(range, step, trackW) {
                detectTapGestures { off -> pick(off.x) }
            }
            .pointerInput(range, step, trackW) {
                detectHorizontalDragGestures(
                    onDragStart = { scope.launch { thumb.animateTo(1.22f, Motion.Bouncy) } },
                    onDragEnd = { scope.launch { thumb.animateTo(1f, Motion.Bouncy) } },
                    onDragCancel = { scope.launch { thumb.animateTo(1f, Motion.Bouncy) } }
                ) { change, _ -> pick(change.position.x) }
            }
    ) {
        val cy = size.height / 2f
        val trackH = 9.dp.toPx()
        val usable = (size.width - padPx * 2).coerceAtLeast(1f)
        val cx = padPx + usable * pos
        val r = 9.dp.toPx() * thumb.value
        val radius = CornerRadius(trackH / 2, trackH / 2)

        drawRoundRect(
            color = cs.surfaceVariant,
            topLeft = Offset(padPx, cy - trackH / 2),
            size = Size(usable, trackH),
            cornerRadius = radius
        )
        drawRoundRect(
            color = cs.primary,
            topLeft = Offset(padPx, cy - trackH / 2),
            size = Size((cx - padPx).coerceAtLeast(trackH / 2), trackH),
            cornerRadius = radius
        )
        // 拇指：外圈留白 + 实心圆 + 中心小点，像个小旋钮
        drawCircle(cs.surface, radius = r + 2.5.dp.toPx(), center = Offset(cx, cy))
        drawCircle(cs.primary, radius = r, center = Offset(cx, cy))
        drawCircle(cs.onPrimary, radius = r * 0.34f, center = Offset(cx, cy))
    }
}

/** 首页/设置里显示的“当前周”文字。 */private fun autoWeekValue(ctx: android.content.Context): Int {
    val mon = Store.firstMonday(ctx) ?: return 1
    val days = java.time.LocalDate.now().toEpochDay() - mon.toEpochDay()
    return (days / 7).toInt() + 1
}

private fun autoWeekLabel(ctx: android.content.Context): String {
    val ov = Store.weekOverride(ctx)
    val w = if (ov > 0) ov else autoWeekValue(ctx)
    return if (ov > 0) "第 $w 周" else "第 $w 周（自动）"
}

@Composable
private fun NumberDialog(
    title: String,
    value: Int,
    range: IntRange,
    extraLabel: String?,
    onDismiss: () -> Unit,
    onExtra: (() -> Unit)?,
    onPick: (Int) -> Unit
) {
    var v by remember { mutableIntStateOf(value.coerceIn(range.first, range.last)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { if (v > range.first) v-- }) {
                    Icon(Icons.Filled.Remove, contentDescription = "减")
                }
                Text(
                    "$v",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )
                FilledTonalIconButton(onClick = { if (v < range.last) v++ }) {
                    Icon(Icons.Filled.Add, contentDescription = "加")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(v) }) { Text("确定") } },
        dismissButton = {
            if (onExtra != null) TextButton(onClick = onExtra) { Text(extraLabel ?: "自动") }
            else TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
