@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    androidx.compose.ui.text.ExperimentalTextApi::class
)

package com.aamina.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---------------- 主题 ----------------

/**
 * 字体：完全跟随系统（[FontFamily.Default]），不打包任何字体文件。
 *
 * 小米 / 红米上就是小米兰亭（MiSans），OPPO 是 OPPO Sans，华为是 HarmonyOS Sans——
 * 用户系统里是什么字，App 就是什么字，中英文笔形也天然一致。
 *
 * 之前"太细"不是字体选错，是正文全用了 400。这里正文提到 Medium(500)，标题 700，
 * 在系统字体自身的字重范围内加粗，不引入任何外部字形。
 */
private val AppFont = FontFamily.Default

/** 中文比拉丁字"高"，行高留足 1.5 倍左右才不挤；字距收紧到 0 更利落。 */
private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp
    )
)

// ---------------- 导航 ----------------

sealed interface Screen {
    data object Home : Screen
    data object Confirm : Screen
    data object Settings : Screen
    data object Edit : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeMode.dynamic.value = Store.dynamicColor(this)
        setContent { TimetableApp() }
    }
}

/** 主题模式用一个全局可观察状态，设置页一拨开关整个 App 立刻换色。 */
object ThemeMode {
    val dynamic = mutableStateOf(true)
}

@Composable
fun TimetableApp() {
    val ctx = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val dyn = ThemeMode.dynamic.value
    // 跟随壁纸取色：Android 12+ 才有，低版本自动退回内置配色
    val scheme = remember(dark, dyn) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dyn) {
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx)
            else androidx.compose.material3.dynamicLightColorScheme(ctx)
        } else {
            if (dark) androidx.compose.material3.darkColorScheme()
            else androidx.compose.material3.lightColorScheme()
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Root()
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val draft = remember { mutableStateListOf<Course>() }
    var hasDraft by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    // 在线识别进度：-1 = 不确定（转圈），0..100 = 百分比
    var progress by remember { mutableIntStateOf(-1) }
    var progressText by remember { mutableStateOf("") }
    var homeVersion by remember { mutableIntStateOf(0) }
    var editIndex by remember { mutableIntStateOf(-1) }
    var showFailDialog by remember { mutableStateOf(false) }
    var failReason by remember { mutableStateOf<String?>(null) }
    var diag by remember { mutableStateOf("") }
    var lastPickWasCamera by remember { mutableStateOf(false) }
    // 预测性返回手势进度（0~1），驱动当前页面缩小淡出
    var backProgress by remember { mutableFloatStateOf(0f) }

    fun manualAdd() {
        draft.clear()
        draft.add(Course(name = "", startSection = 1, endSection = 2))
        hasDraft = true
        screen = Screen.Confirm
    }

    fun onImage(uri: Uri) {
        busy = "正在读取图片…"
        scope.launch {
            // 关键一步：立刻把图片拷进本地缓存。微信/网盘等来源的 Uri 只能读一次，
            // 不先落盘的话，离线+在线两轮解析读到第二次就失败（"图片读取失败"）。
            val file = withContext(Dispatchers.IO) {
                runCatching { TimetableParser.importToCache(ctx, uri) }.getOrNull()
            }
            if (file == null) {
                busy = null
                diag = "图片没能保存到本地，无法读取。"
                failReason = "所选图片已失效或来源不允许读取（常见于微信/网盘直接转发的图）。" +
                    "请先把图片保存到手机相册，再从相册里重新选择。"
                showFailDialog = true
                return@launch
            }
            val local = Uri.fromFile(file)
            val imgInfo = withContext(Dispatchers.IO) {
                val head = runCatching {
                    java.io.FileInputStream(file).use { it.readNBytes(32) }
                }.getOrDefault(ByteArray(0))
                "${Img.magicOf(head)} · ${file.length() / 1024}KB · ${file.name}"
            }
            busy = "正在识别课程表…"
            var parsed = withContext(Dispatchers.IO) {
                runCatching { TimetableParser.parse(ctx, local) }.getOrElse { emptyList() }
            }
            val offTrace = TimetableParser.lastChannelTrace.ifBlank { "没跑起来（图片解码失败）" }

            // 离线识别不理想且用户填了免费的智谱 Key → 自动在线识别兜底
            var onlineError: String? = null
            var onlineCount = -1
            val key = Store.apiKey(ctx).trim()
            if (parsed.size < 3 && key.isNotEmpty()) {
                busy = "离线识别不理想，正在在线识别…"
                progress = 0
                progressText = "准备连接"
                try {
                    val cfg = Store.aiConfig(ctx)
                    val online = withContext(Dispatchers.IO) {
                        OnlineOcr.parse(ctx, local, cfg, Store.loadTimes(ctx)) { p, txt ->
                            progress = p
                            progressText = txt
                        }
                    }
                    onlineCount = online.size
                    if (online.isNotEmpty()) {
                        parsed = if (online.size >= 3) {
                            // 在线更准：以在线为准，离线补它漏掉的格子
                            val merged = LinkedHashMap<String, Course>()
                            online.forEach { merged["${it.day}|${it.startSection}|${it.name}"] = it }
                            parsed.forEach {
                                merged.putIfAbsent("${it.day}|${it.startSection}|${it.name}", it)
                            }
                            merged.values.sortedWith(compareBy({ it.day }, { it.startSection }))
                        } else {
                            online
                        }
                    }
                } catch (e: OnlineException) {
                    onlineError = e.message
                }
            }
            busy = null
            progress = -1

            diag = buildString {
                append("图片：").append(imgInfo).append('\n')
                append("离线 OCR：").append(offTrace).append('\n')
                if (key.isEmpty()) {
                    append("在线：未开启（设置里没填 Key）")
                } else if (onlineError != null) {
                    append("在线：").append(onlineError)
                } else {
                    append("在线：").append(onlineCount).append(" 条")
                }
                if (OnlineOcr.lastNote.isNotBlank()) {
                    append("\n\n").append(OnlineOcr.lastNote.trim())
                }
                if (OnlineOcr.lastRaw.isNotBlank() && parsed.isEmpty()) {
                    append("\n\n— 模型原始返回（前 700 字）—\n")
                    append(OnlineOcr.lastRaw.take(700))
                }
                if (parsed.isNotEmpty()) append("\n\n最终：").append(parsed.size).append(" 门课")
            }

            if (parsed.isEmpty()) {
                failReason = onlineError
                showFailDialog = true
            } else {
                draft.clear()
                draft.addAll(parsed)
                hasDraft = true
                screen = Screen.Confirm
                scope.launch {
                    snackbar.showSnackbar("识别到 ${parsed.size} 门课，请逐条核对后再写入")
                }
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        lastPickWasCamera = false
        uri?.let(::onImage)
    }
    val cameraUri = remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri.value?.let(::onImage)
    }

    fun shoot() {
        val dir = File(ctx.cacheDir, "shots").apply { mkdirs() }
        val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        cameraUri.value = uri
        lastPickWasCamera = true
        val camIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        ctx.packageManager.queryIntentActivities(camIntent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
            ctx.grantUriPermission(
                it.activityInfo.packageName, uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        takePicture.launch(uri)
    }

    fun retryLastPick() {
        if (lastPickWasCamera) shoot()
        else pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // 预测性返回：非首页时拦截系统返回，跟随手势做缩放淡出，松手即返回首页
    if (screen != Screen.Home) {
        PredictiveBackHandler { progress: Flow<androidx.activity.BackEventCompat> ->
            try {
                progress.collect { backProgress = it.progress }
                backProgress = 0f
                screen = Screen.Home
            } catch (e: CancellationException) {
                backProgress = 0f
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = backProgress
                scaleX = 1f - 0.12f * p
                scaleY = 1f - 0.12f * p
                translationX = size.width * 0.06f * p
                alpha = 1f - 0.35f * p
            }
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val deeper = targetState != Screen.Home
                    val dir = if (deeper) 1 else -1
                    (
                        slideInHorizontally(
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.84f, stiffness = 360f
                            ),
                            initialOffsetX = { it / 6 * dir }
                        ) + fadeIn(tween(200)) + scaleIn(
                            initialScale = 0.965f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.8f, stiffness = 420f
                            )
                        )
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = tween(220),
                            targetOffsetX = { -it / 10 * dir }
                        ) + fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.985f,
                            animationSpec = tween(220)
                        )
                        )
                },
                label = "screenSwitch"
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(
                        version = homeVersion,
                        onOpenSettings = { screen = Screen.Settings },
                        onCoursesChanged = { homeVersion++ },
                        onManualAdd = ::manualAdd,
                        snackbar = snackbar,
                        onEditCourse = { idx ->
                            editIndex = idx
                            screen = Screen.Edit
                        },
                        onShoot = ::shoot,
                        onPick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    Screen.Confirm -> if (hasDraft) {
                        ConfirmScreen(
                            courses = draft,
                            snackbar = snackbar,
                            onBack = { screen = Screen.Home },
                            onSynced = { ids ->
                                Store.saveCourses(ctx, Store.loadCourses(ctx) + draft.toList())
                                Store.setLastEventIds(ctx, ids)
                                Store.addCreatedIds(ctx, ids)
                                hasDraft = false
                                homeVersion++
                                screen = Screen.Home
                                scope.launch { snackbar.showSnackbar("已写入 ${ids.size} 个日程到日历") }
                            }
                        )
                    }
                    Screen.Settings -> SettingsScreen(
                        // 返回时必须让首页重读一遍设置：节次时间/节数改了要立刻反映到列表
                        onBack = { screen = Screen.Home; homeVersion++ },
                        snackbar = snackbar
                    )
                    Screen.Edit -> CourseEditScreen(
                        index = editIndex,
                        onBack = { screen = Screen.Home; homeVersion++ },
                        snackbar = snackbar
                    )
                }
            }
        }

        // 返回手势进行中的暗角
        if (backProgress > 0.01f) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f * backProgress))
            )
        }

        // 识别失败：给用户留出路，并把现场摊开（哪一步失败、模型原话是什么）
        if (showFailDialog) {
            val clip = LocalClipboardManager.current
            val headline = when {
                failReason != null -> failReason!!
                Store.apiKey(ctx).trim().isEmpty() ->
                    "还没开启在线识别。去设置里填一个免费的智谱 Key（AI 直接看图，准确率高很多），也可以直接手动添加。"
                else ->
                    "在线模型看完了这张图，但没返回能识别的课程。下面的诊断可以帮我定位问题。"
            }
            AlertDialog(
                onDismissRequest = { showFailDialog = false },
                icon = {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text("没认出课程") },
                text = {
                    Column {
                        Text(headline, style = MaterialTheme.typography.bodyMedium)
                        if (diag.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp)
                            ) {
                                Column(
                                    Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        diag,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = {
                                    clip.setText(AnnotatedString(diag))
                                    scope.launch { snackbar.showSnackbar("诊断信息已复制") }
                                }
                            ) { Text("复制诊断信息") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFailDialog = false; retryLastPick() }) {
                        Text("换张图再试")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFailDialog = false; manualAdd() }) {
                        Text("手动添加")
                    }
                }
            )
        }

        // 全屏识别加载层：官方 indeterminate 指示器 + 弹出/收回动画
        AnimatedVisibility(
            visible = busy != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                BouncyIn(fromScale = 0.85f, modifier = Modifier) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MorphLoader(size = 38.dp)
                            if (progress >= 0) {
                                val pct by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = progress.toFloat(),
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.9f, stiffness = 220f
                                    ),
                                    label = "pct"
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "${pct.toInt()}%",
                                    style = MaterialTheme.typography.displaySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-1).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .width(196.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    progressText.ifBlank { "AI 正在看图" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(Modifier.height(18.dp))
                                androidx.compose.animation.AnimatedContent(
                                    targetState = busy ?: "",
                                    transitionSpec = {
                                        (fadeIn(tween(220)) + slideInVertically { it / 3 })
                                            .togetherWith(fadeOut(tween(140)))
                                    },
                                    label = "busy"
                                ) { txt ->
                                    Text(
                                        txt,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ---------------- 首页 ----------------

/**
 * 页面标题：呼吸的小圆点 + 拉开字距的英文大写。
 *
 * 全 App 统一用它，比默认的黑色粗体标题克制得多——标题是"栏目名"，不是口号。
 */
@Composable
fun PageTitle(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "titleDot")
    val dot by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = dot))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.5.sp
            )
        )
    }
}

private fun nowMinutes(): Int {
    val t = java.time.LocalTime.now()
    return t.hour * 60 + t.minute
}

/** 段索引：0 上午 / 1 下午 / 2 晚上。 */
private fun segOf(c: Course, times: SectionTimes): Int = when {
    times.isAm(c.startSection) -> 0
    times.isPm(c.startSection) -> 1
    else -> 2
}

/** 0 未开始 / 1 进行中 / 2 已结束 */
private fun stateOf(c: Course, times: SectionTimes, now: Int): Int {
    val s = times.startOf(c.startSection)
    val e = times.endOf(c.endSection)
    return when {
        now >= e -> 2
        now >= s -> 1
        else -> 0
    }
}

@Composable
fun HomeScreen(
    version: Int,
    onOpenSettings: () -> Unit,
    onCoursesChanged: () -> Unit,
    onManualAdd: () -> Unit,
    snackbar: SnackbarHostState,
    onEditCourse: (Int) -> Unit,
    onShoot: () -> Unit,
    onPick: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Course?>(null) }

    val today = remember(version) { LocalDate.now() }
    val mon = remember(version) { Store.firstMonday(ctx) }
    val all = remember(version) { Store.loadCourses(ctx) }
    val times = remember(version) { Store.loadTimes(ctx) }
    val override = remember(version) { Store.weekOverride(ctx) }
    val showWeekend = remember(version) { Store.showWeekend(ctx) }
    val showOther = remember(version) { Store.showOtherWeeks(ctx) }

    val autoWeek = mon?.let { ((today.toEpochDay() - it.toEpochDay()) / 7).toInt() + 1 }
    val week = if (override > 0) override else autoWeek

    val courses = all.filter { c ->
        (showWeekend || c.day <= 5) &&
            (showOther || week == null || week <= 0 || c.isActiveIn(week))
    }
    val todayDow = today.dayOfWeek.value
    // 今日课程：停课的周自动不显示；别的天的课如果本周调到了今天，也要出现
    val todayCourses = buildList {
        courses.filter { it.day == todayDow && (week == null || week <= 0 || it.activeIn(week)) }
            .forEach { add(it) }
        if (week != null && week > 0) {
            courses.filter { it.day != todayDow }.forEach { c ->
                c.moveIn(week)?.takeIf { it.day == todayDow }?.let { m ->
                    add(c.copy(startSection = m.start, endSection = m.end))
                }
            }
        }
    }.sortedBy { it.startSection }

    // 每 20 秒重算一次"现在"，进行中/已结束的状态会自己跳

    val nowMin by androidx.compose.runtime.produceState(nowMinutes()) {
        while (true) {
            value = nowMinutes()
            kotlinx.coroutines.delay(20_000L)
        }
    }

    // —— 日历里还有没有这份课表 ——
    // 用户在系统日历里把课表事件删光之后，本地只剩一堆失效的 eventId，
    // 既不会自动补写也没有入口手动补。这里检测「课表非空 + 日历里 0 条」，
    // 首页就挂一条提示，点一下按当前课表整份写回去。
    var notInCalendar by remember(version) { mutableStateOf(false) }
    LaunchedEffect(version) {
        if (all.isEmpty()) {
            notInCalendar = false
            return@LaunchedEffect
        }
        notInCalendar = withContext(Dispatchers.IO) {
            runCatching {
                hasCalendarPermission(ctx) && CalendarSync.findCreatedEventIds(ctx).isEmpty()
            }.getOrDefault(false)
        }
    }

    var pendingSync by remember { mutableStateOf(false) }
    val syncPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { g ->
        if (g.values.all { it }) pendingSync = true
        else scope.launch { snackbar.showSnackbar("需要日历权限才能写入日历") }
    }

    fun writeToCalendar() {
        if (!hasCalendarPermission(ctx)) {
            syncPerm.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
            return
        }
        val m = Store.firstMonday(ctx)
        if (m == null) {
            scope.launch { snackbar.showSnackbar("先去设置里选「学期第一周」的周一") }
            return
        }
        scope.launch {
            val target = withContext(Dispatchers.IO) { CalendarSync.pickTargetCalendar(ctx) }
            if (target <= 0) {
                snackbar.showSnackbar("没有找到可写入的日历账户")
                return@launch
            }
            Store.setCalendarId(ctx, target)
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    CalendarSync.resyncAll(ctx, target, m, all, times, Store.reminderEnabled(ctx))
                }.getOrNull()
            }
            if (r == null || r.inserted == 0) {
                snackbar.showSnackbar("写入失败，检查日历权限或账户")
            } else {
                notInCalendar = false
                snackbar.showSnackbar("✓ 已写入 ${r.inserted} 个日程到日历")
            }
        }
    }

    // 授权回来自动接着写，不用再点第二次
    LaunchedEffect(pendingSync) {
        if (pendingSync) {
            pendingSync = false
            writeToCalendar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { PageTitle("TIMETABLE") },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            snackbar.showSnackbar("正在生成课表长图…")
                            val f = runCatching {
                                withContext(Dispatchers.IO) {
                                    ShareCard.render(
                                        ctx, courses, times, week,
                                        Store.totalWeeks(ctx), showWeekend
                                    )
                                }
                            }.getOrNull()
                            if (f == null) {
                                snackbar.showSnackbar("生成失败，先添加几门课再试")
                            } else {
                                ShareCard.share(ctx, f)
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "分享课表")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            val fabInteraction = remember { MutableInteractionSource() }
            val fabPressed by fabInteraction.collectIsPressedAsState()
            // 打开选择面板时 + 号转成 ×，弹一下，状态一眼可见
            val fabRotate by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showSheet) 135f else 0f,
                animationSpec = Motion.Switch,
                label = "fabRotate"
            )
            FloatingActionButton(
                onClick = { showSheet = !showSheet },
                interactionSource = fabInteraction,
                modifier = Modifier.pressScale(fabPressed)
            ) {
                Icon(
                    Icons.Default.Add, contentDescription = "添加课表",
                    modifier = Modifier.graphicsLayer { rotationZ = fabRotate }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // 底部留出 FAB 的高度：最后一张卡不会再被悬浮按钮压住半张
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {  HomeHeader(today, week, todayCourses.size, Store.totalWeeks(ctx))  }

            if (notInCalendar) {
                item(key = "nosync") {
                    Card(
                        onClick = { writeToCalendar() },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.EventBusy, contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "课表不在系统日历里",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "点一下按当前课表写回 ${all.size} 门课",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            val upNext = nextUp(todayCourses, times, nowMin)
            if (upNext != null) {
                item(key = "nextup") {  NextUpCard(upNext, times)  }
            }

            item {  PoemCard()  }

            if (todayCourses.isEmpty()) {
                item {  EmptyTodayCard(hasCourses = courses.isNotEmpty())  }
            } else {
                listOf("上午课程" to 0, "下午课程" to 1, "晚上课程" to 2).forEach { (label, seg) ->
                    val list = todayCourses.filter { segOf(it, times) == seg }
                    if (list.isNotEmpty()) {
                        item(key = "seg$seg") {
                                                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 10.dp, start = 4.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(50)
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        items(list.size, key = { i -> "t$seg-${list[i].startSection}-${list[i].name}" }) { i ->
                            val c = list[i]
                                                        TodayCourseRow(c, times, stateOf(c, times, nowMin)) { detail = c }
                        }
                    }
                }
            }

            item {
                                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp, start = 4.dp)
                ) {
                    Text(
                        "全部课程（${courses.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (courses.isEmpty()) {
                item {
                                        Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(24.dp)) {
                            Icon(
                                Icons.Outlined.CalendarMonth, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("导入第一张课表", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "点右下角 +，拍下或选择教务系统里的课表截图，\n识别后确认无误即可写入系统日历。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val days = if (showWeekend) (1..7).toList() else (1..5).toList()
                days.forEach { d ->
                    val list = courses.filter { it.day == d }.sortedBy { it.startSection }
                    if (list.isNotEmpty()) {
                        item(key = "h$d") {
                                                        Text(
                                dayName(d),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                            )
                        }
                        items(list.size, key = { i -> "${d}_${list[i].startSection}_${list[i].name}" }) { i ->
                            val c = list[i]
                                                        CourseRow(
                                c, times,
                                onRemove = {
                                    Store.saveCourses(ctx, all.filter { it !== c })
                                    onCoursesChanged()
                                },
                                onClick = { onEditCourse(all.indexOfFirst { it === c }) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        // 三个选项从 FAB 上弹出来，再点一次缩回去
        FabBurst(
            open = showSheet,
            onDismiss = { showSheet = false },
            actions = listOf(
                FabAction("拍照识别", Icons.Outlined.PhotoCamera) { showSheet = false; onShoot() },
                FabAction("从相册选择", Icons.Outlined.PhotoLibrary) { showSheet = false; onPick() },
                FabAction("手动添加", Icons.Outlined.EditNote) { showSheet = false; onManualAdd() }
            )
        )
        }
    }

    detail?.let { c ->
        CourseDetailSheet(
            c, times,
            onDismiss = { detail = null },
            onEdit = {
                detail = null
                onEditCourse(all.indexOfFirst { it === c })
            }
        )
    }

}

private data class FabAction(val label: String, val icon: ImageVector, val run: () -> Unit)

/**
 * 点 + 之后从按钮位置"蹦"出来的三个选项。
 *
 * 用欠阻尼弹簧从右下角（transformOrigin 压在 FAB 那一角）弹出：先缩放到 1.25 再回落，
 * 同时轻微右移，看着像从按钮里甩出来；再点一次 + 就按相反方向缩回去。
 * 点空白处也能收起，遮罩只压住列表，FAB 和标题栏仍然清楚。
 */
@Composable
private fun FabBurst(open: Boolean, actions: List<FabAction>, onDismiss: () -> Unit) {
    var rendered by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            rendered = true
        } else {
            kotlinx.coroutines.delay(320)
            rendered = false
        }
    }
    if (!rendered) return

    val scrim by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (open) 180 else 240),
        label = "scrim"
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            // 倒序排：离 FAB 最近的"拍照识别"排在最下面
            actions.reversed().forEach { action ->
                val idx = actions.indexOf(action)
                val p = remember { Animatable(0f) }
                LaunchedEffect(open) {
                    if (open) {
                        kotlinx.coroutines.delay(idx * 45L)
                        p.animateTo(1f, Motion.Bouncy)
                    } else {
                        p.animateTo(0f, Motion.Soft)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = p.value
                            val sc = 0.55f + 0.45f * p.value
                            scaleX = sc
                            scaleY = sc
                            translationX = (1f - p.value) * 44f
                            translationY = (1f - p.value) * 26f
                            transformOrigin = TransformOrigin(1f, 1f)
                        }
                        .bounceClick(enabled = p.value > 0.6f) { action.run() }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            action.icon, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            action.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            // 让出 FAB 自己的位置（56dp 按钮 + 12dp 间隙），第一个选项正好压在 + 上面
            Spacer(Modifier.height(68.dp))
        }
    }
}

/** 首页顶部要提示的那节课：正在上的，或者紧接着的。 */
private data class UpNext(
    val course: Course,
    val running: Boolean,
    val minutes: Int,
    val progress: Float
)

private fun nextUp(list: List<Course>, times: SectionTimes, now: Int): UpNext? {
    list.firstOrNull {
        now >= times.startOf(it.startSection) && now < times.endOf(it.endSection)
    }?.let { c ->
        val s = times.startOf(c.startSection)
        val e = times.endOf(c.endSection)
        return UpNext(c, true, e - now, ((now - s).toFloat() / (e - s).coerceAtLeast(1)))
    }
    list.filter { times.startOf(it.startSection) > now }
        .minByOrNull { times.startOf(it.startSection) }
        ?.let { return UpNext(it, false, times.startOf(it.startSection) - now, 0f) }
    return null
}

/** 剩余时间说人话：90 → "1 小时 30 分"。 */
private fun fmtLeft(m: Int): String =
    if (m >= 60) "${m / 60} 小时 ${m % 60} 分" else "$m 分钟"

/**
 * 「下一节 / 正在上」贴片：一个数字吃满视觉，其余信息靠边站。
 *
 * 进行中时数字每 20 秒自己往下掉，下面还有一条走到头的进度线——
 * 抬眼一看就知道还有多久，不用心算。
 */
@Composable
private fun NextUpCard(up: UpNext, times: SectionTimes) {
    val c = up.course
    val accent = Palette.of(c)
    val transition = rememberInfiniteTransition(label = "upNext")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    // 换课/换状态时整块淡入上浮一点，切换有"接力"的感觉
    AnimatedContent(
        targetState = "${c.name}|${c.day}|${c.startSection}|${up.running}",
        transitionSpec = {
            (fadeIn(tween(260)) + slideInVertically { it / 4 })
                .togetherWith(fadeOut(tween(150)))
        },
        label = "nextUp"
    ) { _ ->
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (up.running) pulse else 1f))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (up.running) "正在上课" else "下一节",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${times.fmt(times.startOf(c.startSection))} – ${times.fmt(times.endOf(c.endSection))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOfNotNull(
                                c.room.takeIf { it.isNotBlank() },
                                c.teacher.takeIf { it.isNotBlank() }
                            ).joinToString(" · ").ifBlank { "地点未填" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${up.minutes}",
                            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-1.5).sp),
                            color = accent
                        )
                        Text(
                            if (up.running) "分钟后下课" else "分钟后开始",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (up.running) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent.copy(alpha = 0.22f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(up.progress.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(accent)
                        )
                    }
                } else if (up.minutes >= 60) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "也就是 ${fmtLeft(up.minutes)}后",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(today: LocalDate, week: Int?, todayCount: Int, totalWeeks: Int) {
    val total = totalWeeks.coerceAtLeast(1)
    val done = ((week ?: 0) - 1).coerceIn(0, total)
    val target = when {
        week == null || week < 1 -> 0f
        else -> (done + 1).toFloat() / total
    }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = Motion.Switch,
        label = "termProgress"
    )
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Text(
                "${today.format(DateTimeFormatter.ofPattern("M月d日"))} · ${dayName(today.dayOfWeek.value)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (week != null && week >= 1) "第 $week 周" else "还没设置学期",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (week != null && week >= 1) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "/ 共 $total 周",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 学期进度条：宽度随周次弹着长出来
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                        RoundedCornerShape(50)
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            RoundedCornerShape(50)
                        )
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    week == null || week < 1 -> "去设置里选学期第一天，日程才会落到正确的日期"
                    todayCount == 0 -> "今天没课，好好休息"
                    todayCount == 1 -> "今天只有 1 节课，打起精神"
                    else -> "今天 $todayCount 节课，打起精神！"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * 首页「今日一句」：随机古诗文。
 *
 * 点一下换一句，内容整体淡入上浮，卡片本身有按压回弹——轻，但有反馈。
 * 排版上刻意留白：句子 18sp/行高 32sp，出处小一档退到次要色，读起来像书页而不是通知。
 */
@Composable
private fun PoemCard() {
    var poem by remember { mutableStateOf(randomPoem()) }
    val scheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().bounceClick { poem = randomPoem(poem) }
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(5.dp).background(scheme.tertiary, RoundedCornerShape(50))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "今日一句",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "换一句",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = poem,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically { it / 4 } + scaleIn(initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 6 })
                },
                label = "poem"
            ) { p ->
                Column {
                    Text(
                        p.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 18.sp,
                            lineHeight = 32.sp,
                            letterSpacing = 0.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = scheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "—— ${p.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTodayCard(hasCourses: Boolean) {    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Schedule, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("今天没有课", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (hasCourses) "安心休息，或者去下面翻翻这一周的安排"
                    else "还没有课程，点右下角 + 导入课表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 今日课程行：左边竖排时间，中间色条 + 课程信息，右边状态。
 * 进行中的课会轻微"呼吸"（1 → 1.015 循环），一眼能看到现在上的是哪节。
 */
@Composable
private fun TodayCourseRow(c: Course, times: SectionTimes, state: Int, onClick: () -> Unit) {
    val color = Palette.of(c)
    val ended = state == 2
    val live = state == 1

    val breathe by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = if (live) 1.012f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (live) color.copy(alpha = 0.13f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = breathe
                scaleY = breathe
                alpha = if (ended) 0.55f else 1f
            }
            .bounceClick { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    times.fmt(times.startOf(c.startSection)),
                    style = MaterialTheme.typography.titleSmall,
                    color = color
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    times.fmt(times.endOf(c.endSection)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .width(4.dp)
                    .height(42.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name.ifBlank { "（未命名课程）" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append("第 ${c.startSection}")
                        if (c.endSection != c.startSection) append("–${c.endSection}")
                        append(" 节")
                        if (c.room.isNotBlank()) append(" · ${c.room}")
                        if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (live) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(color, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "进行中",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            } else if (ended) {
                Text(
                    "已结束",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun hasCalendarPermission(ctx: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun CourseRow(
    c: Course,
    times: SectionTimes,
    onRemove: (() -> Unit)?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.bounceClick(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .background(Palette.of(c), RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.name.ifBlank { "（未命名课程）" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    buildString {
                        append("第 ${c.startSection}")
                        if (c.endSection != c.startSection) append("–${c.endSection}")
                        append(" 节")
                        if (c.room.isNotBlank()) append(" · ${c.room}")
                        append(" · ${weekLabel(c)}")
                        if (c.teacher.isNotBlank()) append(" · ${c.teacher}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    times.fmt(times.startOf(c.startSection)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    times.fmt(times.endOf(c.endSection)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.DeleteOutline, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

fun dayName(d: Int): String = "一二三四五六日".let { if (d in 1..7) "周${it[d - 1]}" else "" }

fun weekLabel(c: Course): String = when (c.weekMode) {
    1 -> "${c.startWeek}-${c.endWeek}周(单)"
    2 -> "${c.startWeek}-${c.endWeek}周(双)"
    else -> "${c.startWeek}-${c.endWeek}周"
}

/** 点今日课程弹出的详情。淡入 + 从下往上弹，和底部弹窗的手势自然衔接。 */
@Composable
private fun CourseDetailSheet(
    c: Course,
    times: SectionTimes,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(Palette.of(c), RoundedCornerShape(50))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    c.name.ifBlank { "（未命名课程）" },
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(Modifier.height(20.dp))
            DetailLine("时间", "${times.fmt(times.startOf(c.startSection))} – ${times.fmt(times.endOf(c.endSection))}")
            DetailLine(
                "节次",
                "第 ${c.startSection}" + (if (c.endSection != c.startSection) "–${c.endSection}" else "") + " 节"
            )
            DetailLine("星期", dayName(c.day))
            DetailLine("周次", weekLabel(c))
            if (c.room.isNotBlank()) DetailLine("教室", c.room)
            if (c.teacher.isNotBlank()) DetailLine("教师", c.teacher)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onEdit) { Text("编辑") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
