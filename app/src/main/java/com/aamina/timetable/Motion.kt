package com.aamina.timetable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 全局动效参数。
 *
 * "Q 弹"的本质是**欠阻尼弹簧**：dampingRatio 小于 1 就会过冲一点再回落。
 * 这里三档：
 *  - [Bouncy] 0.45：最弹，用在入场、确认、节数滚轮这种偶尔发生的事上；
 *  - [Soft]   0.62：按压回弹，频率高，过冲要收着点，不然手会晕；
 *  - [Switch] 0.72：页面/内容切换，接近临界阻尼，稳中带一点余韵。
 */
object Motion {
    val Bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = 480f)
    val Soft = spring<Float>(dampingRatio = 0.62f, stiffness = 720f)
    val Switch = spring<Float>(dampingRatio = 0.74f, stiffness = 360f)

    /** 弹簧的通用 spec（带质量，稍重一点的"肉感"）。 */
    fun <T> springy(damping: Float = 0.5f, stiffness: Float = Spring.StiffnessMediumLow) =
        spring<T>(dampingRatio = damping, stiffness = stiffness)
}

/**
 * 入场动画：从 [fromScale] 弹到 1，同时淡入。
 * [delayMillis] 用于列表错峰，一条一条弹出来比整块砸下来灵动得多。
 */
@Composable
fun BouncyIn(
    delayMillis: Int = 0,
    fromScale: Float = 0.88f,
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        shown = true
    }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else fromScale,
        animationSpec = Motion.Bouncy,
        label = "bouncyScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 260,
            delayMillis = delayMillis.coerceAtMost(140),
            easing = FastOutSlowInEasing
        ),
        label = "bouncyAlpha"
    )
    Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) { content() }
}

/**
 * 带按压缩放的点击：手指按下缩到 0.955，松手弹回。
 * 比 Material 默认的水波纹多一层"按得下去"的手感，且保留原生 ripple。
 */
fun Modifier.bounceClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.955f,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val indication = LocalIndication.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.Soft,
        label = "pressScale"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = indication,
            enabled = enabled,
            onClick = onClick
        )
}

/** 只做缩放反馈、不接管点击（用于已经自带 onClick 的组件，如 FAB、Card）。 */
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.Soft,
        label = "pressScaleOnly"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
