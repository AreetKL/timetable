@file:OptIn(ExperimentalMaterial3Api::class)

package com.aamina.timetable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 「每天节数」底部弹窗。
 *
 * 原来这里是三列滚轮，两个治不好的毛病：三列取值范围不同（上午从 1 起、下午从 0 起），
 * 滚动位置一错位，高亮条里的数字和真正保存的数字就对不上；内容又比屏幕高，
 * 弹窗一拖就顶出屏幕，确定按钮被切掉。
 * 现在改成**点按加减**：所见即所得，高度固定，怎么点都不会跑到屏幕外。
 */
@Composable
fun SectionCountSheet(
    times: SectionTimes,
    onDismiss: () -> Unit,
    onConfirm: (SectionTimes) -> Unit
) {
    var am by remember { mutableIntStateOf(times.amCount.coerceAtLeast(1)) }
    var pm by remember { mutableIntStateOf(times.pmCount.coerceAtLeast(0)) }
    var eve by remember { mutableIntStateOf(times.eveCount.coerceAtLeast(0)) }
    val total = am + pm + eve

    // skipPartiallyExpanded：打开就是完整高度，不再只露半个头
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "每天节数",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "上午、下午、晚上分别几节，整天一共 $total 节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // 一天的形状：按节数比例画一根彩条，改数字它就弹一下
            DayBar(am = am, pm = pm, eve = eve)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LegendDot("上午", am, MaterialTheme.colorScheme.primary)
                LegendDot("下午", pm, MaterialTheme.colorScheme.tertiary)
                LegendDot("晚上", eve, MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(16.dp))

            CountRow(
                label = "上午",
                hint = if (am <= 0) "没有课" else "第 1–$am 节",
                value = am,
                range = 1..8,
                onChange = { am = it }
            )
            CountRow(
                label = "下午",
                hint = if (pm <= 0) "没有课" else "第 ${am + 1}–${am + pm} 节",
                value = pm,
                range = 0..8,
                onChange = { pm = it }
            )
            CountRow(
                label = "晚上",
                hint = if (eve <= 0) "没有晚课" else "第 ${am + pm + 1}–$total 节",
                value = eve,
                range = 0..6,
                onChange = { eve = it }
            )

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "改了节数会按规则重新排时间，之前逐节手调过的会清空",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50)
                ) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Button(
                    onClick = { onConfirm(times.copy(amCount = am, pmCount = pm, eveCount = eve)) },
                    modifier = Modifier.weight(1.5f).height(50.dp),
                    shape = RoundedCornerShape(50)
                ) { Text("确定", style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** 一根按比例分段的彩条，长度用弹簧长出来。 */
@Composable
private fun DayBar(am: Int, pm: Int, eve: Int) {
    val amW by animateFloatAsState(am.toFloat(), Motion.Bouncy, label = "amW")
    val pmW by animateFloatAsState(pm.toFloat(), Motion.Bouncy, label = "pmW")
    val eveW by animateFloatAsState(eve.toFloat(), Motion.Bouncy, label = "eveW")
    val sum = (amW + pmW + eveW).coerceAtLeast(0.001f)
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val c3 = MaterialTheme.colorScheme.secondary

    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
        val gap = 6.dp.toPx()
        val h = size.height
        val usable = (size.width - gap * 2).coerceAtLeast(1f)
        var x = 0f
        fun seg(w: Float, color: Color) {
            if (w <= 0.6f) return
            drawRoundRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(w, h),
                cornerRadius = CornerRadius(h / 2, h / 2)
            )
            x += w + gap
        }
        seg(usable * amW / sum, c1)
        seg(usable * pmW / sum, c2)
        seg(usable * eveW / sum, c3)
    }
}

@Composable
private fun LegendDot(label: String, n: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Spacer(Modifier.width(5.dp))
        Text(
            "$label $n",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一行：标签 + 减 + 数字 + 加。
 * 数字变化时先缩到 0.72 再弹回 1，按下去"啵"一下。
 */
@Composable
private fun CountRow(
    label: String,
    hint: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StepButton(Icons.Filled.Remove, value > range.first, "$label 减一节") {
            onChange((value - 1).coerceIn(range))
        }
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { PopNumber(value) }
        StepButton(Icons.Filled.Add, value < range.last, "$label 加一节") {
            onChange((value + 1).coerceIn(range))
        }
    }
}

@Composable
private fun PopNumber(value: Int) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(value) {
        scale.snapTo(0.72f)
        // 欠阻尼：冲到 1.1 再回落，数字像被"弹"出来
        scale.animateTo(1f, Motion.Bouncy)
    }
    Text(
        "$value",
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = if (value == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.primary,
        modifier = Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    )
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    desc: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(18.dp))
    }
}
