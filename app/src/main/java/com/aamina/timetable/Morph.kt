package com.aamina.timetable

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Material 3 Expressive 风格的「形变」加载指示器。
 *
 * 圆角在 34% ↔ 50% 之间呼吸（方 → 圆 → 方），同时 180° 旋转、尺寸轻微起伏；
 * 单块形状，没有进度环那种"机械"感，转起来像一颗会变形的软糖。
 */
@Composable
fun MorphLoader(
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val trans = rememberInfiniteTransition(label = "morph")
    val phase by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val wave = sin(phase * 2f * PI.toFloat())
    val cornerPercent = (50f - 16f * (1f - abs(wave))).roundToInt().coerceIn(20, 50)
    val scale = 1f - 0.09f * abs(cos(phase * 2f * PI.toFloat()))

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationZ = phase * 180f
                scaleX = scale
                scaleY = scale
            }
            .background(color, RoundedCornerShape(percent = cornerPercent))
    )
}
