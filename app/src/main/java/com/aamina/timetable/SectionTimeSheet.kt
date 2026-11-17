@file:OptIn(ExperimentalMaterial3Api::class)

package com.aamina.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 逐节时间表。
 *
 * 学校作息千差万别，靠「第 1 节 + 每节时长 + 课间 + 午休」推算总有偏差。
 * 这里把每一节都摊开，点哪节改哪节，改完立刻生效（首页、确认页、写入日历全用它）。
 */
@Composable
fun SectionTimeSheet(
    times: SectionTimes,
    onDismiss: () -> Unit,
    onChange: (SectionTimes) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var editing by remember { mutableIntStateOf(-1) }
    var uniform by remember { mutableStateOf(Store.uniformLength(ctx)) }
    val am = times.amCount.coerceAtLeast(1)
    val pm = times.pmCount.coerceAtLeast(0)
    val total = times.totalSections

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("每节时间", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "点任意一节单独设置开始时间，下课时间自动按「每节 ${times.durationMin} 分钟」算。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每节课时长相同", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (uniform) "改开始时间即可，下课时间自动算" else "可以单独设置每节的起止时间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = uniform,
                        onCheckedChange = {
                            uniform = it
                            Store.setUniformLength(ctx, it)
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChange(times.copy(slots = emptyList())) },
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("按规则重排") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${times.fmt(times.startOf(1))} 开始 · 共 $total 节",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()

            // 列表高度跟着屏幕走：小屏手机上绝不会把最后几节顶出屏幕
            val maxList = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.5f).dp
            LazyColumn(Modifier.heightIn(max = maxList)) {
                for (n in 1..total) {
                    if (n == 1 || (n == am + 1) || (n == am + pm + 1)) {
                        val label = when {
                            n == 1 -> "上午"
                            n == am + 1 -> "下午"
                            else -> "晚上"
                        }
                        item(key = "h$n") {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                    }
                    item(key = "s$n") {
                        SlotRow(
                            section = n,
                            start = times.startOf(n),
                            end = times.endOf(n),
                            custom = times.isCustom(n),
                            fmt = times::fmt,
                            onClick = { editing = n - 1 }
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    if (editing >= 0) {
        // key() 保证切换节次时输入框重置成那一节的时间
        key(editing, uniform) {
            val startMin = times.startOf(editing + 1)
            val endMin = times.endOf(editing + 1)
            val st = rememberTimePickerState(
                initialHour = startMin / 60,
                initialMinute = startMin % 60,
                is24Hour = true
            )
            val et = rememberTimePickerState(
                initialHour = endMin / 60,
                initialMinute = endMin % 60,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { editing = -1 },
                icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                title = { Text("第 ${editing + 1} 节") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "开始",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TimeInput(state = st)
                        if (!uniform) {
                            Text(
                                "结束",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TimeInput(state = et)
                        } else {
                            Text(
                                "下课 ${times.fmt(st.hour * 60 + st.minute + times.durationMin)}" +
                                    "（时长 ${times.durationMin} 分钟）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val s = st.hour * 60 + st.minute
                        val e = if (uniform) s + times.durationMin else et.hour * 60 + et.minute
                        onChange(times.withSlot(editing, s, if (e > s) e else s + 1))
                        editing = -1
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { editing = -1 }) { Text("取消") }
                }
            )
        }
    }

}

@Composable
private fun SlotRow(
    section: Int,
    start: Int,
    end: Int,
    custom: Boolean,
    fmt: (Int) -> String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(64.dp)) {
            Text(
                "第 $section 节",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${fmt(start)} – ${fmt(end)}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (custom) FontWeight.SemiBold else FontWeight.Normal,
            color = if (custom) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
        if (custom) {
            Spacer(Modifier.size(6.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            ) {
                Text(
                    "自定义",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "修改",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
