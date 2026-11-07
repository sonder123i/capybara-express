package dev.showcode.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.MotionDuration
import dev.showcode.ui.theme.MotionEasing
import dev.showcode.ui.theme.MotionTokens
import dev.showcode.ui.theme.ShowCodeShapes
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.rememberReducedMotion

/**
 * 动效工具箱。
 *
 * 三条原则，写在这里免得以后每一处各写一套：
 * 1. **只在有信息时动**：动效要么说「正在发生」（扫描光、进度），要么说「刚刚发生」（盖章、画勾）。
 *    没有信息的地方不加装饰性循环。
 * 2. **弹簧优先**：到位感来自过冲与回弹，线性 tween 只用于「持续」类（扫描、呼吸）。
 * 3. **减少动画时全部退化为静态**：系统开了「移除动画」，这些全部直接返回终态。
 */

/* ── 按压抬升 ─────────────────────────────────────────────────────────── */

/**
 * 卡片按下时抬起 2dp 并轻微放大，松手落回。
 *
 * 和「缩 0.97」不同：抬升 + 放大读起来是「这张卡被拎起来了」，缩放读起来是「按钮被按了」。
 * 列表卡片要的是前者——用户是在「拿起一件东西」。
 */
@Composable
fun Modifier.pressLift(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val reduced = rememberReducedMotion()
    val pressed by interaction.collectIsPressedAsState()
    val lift = if (!enabled || reduced) 0f else if (pressed) 1f else 0f
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = lift,
        animationSpec = MotionTokens.NavRelease,
        label = "press-lift",
    )
    return graphicsLayer {
        translationY = -2.dp.toPx() * animated
        val s = 1f + 0.012f * animated
        scaleX = s
        scaleY = s
        shadowElevation = 6.dp.toPx() * animated
        this.shape = ShowCodeShapes.Md
        clip = false
    }
}

/* ── 画出来的勾 ───────────────────────────────────────────────────────── */

/**
 * 自绘的勾：路径在 [durationMs] 内按 stroke 画出来，先画短边再画长边。
 *
 * 比「整颗勾直接出现」多出来的那 180ms，是「完成了」这个判断被看见的时间。
 */
@Composable
fun DrawingCheck(
    color: Color = UrgencySafe,
    strokeWidthDp: Float = 2.5f,
    durationMs: Int = MotionDuration.Number,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) progress.animateTo(1f, tween(durationMs, easing = MotionEasing.FastOutSlowIn))
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.20f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.76f)
            lineTo(w * 0.80f, h * 0.26f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(
            path = drawn,
            color = color,
            style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/* ── 扫描光 ───────────────────────────────────────────────────────────── */

/**
 * 一道光从列表顶部扫到底部，[durationMs] 内走完。
 *
 * 它替代的是「顶部一个小圈在转」：转圈只说「在忙」，扫光说「正在一页一页看过去」，
 * 而且扫过的区域会短暂提亮——用户能感到进度在往前走。
 *
 * [trigger] 每次变化重新扫一遍；null 时不画。
 */
@Composable
fun ScanSweep(
    trigger: Any?,
    modifier: Modifier = Modifier,
    durationMs: Int = 900,
    tint: Color = Ink,
) {
    val reduced = rememberReducedMotion()
    if (trigger == null || reduced) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    Canvas(modifier = modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        /* 光带高 = 屏高 22%，中心落在 p 处；两端用透明渐变收掉，避免硬边。 */
        val bandH = size.height * 0.22f
        val centerY = size.height * p
        val top = centerY - bandH / 2f
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.5f to tint.copy(alpha = 0.05f),
                1f to Color.Transparent,
                startY = top,
                endY = top + bandH,
            ),
            topLeft = Offset(0f, top),
            size = Size(size.width, bandH),
        )
        /* 前沿一条 1dp 的细线：这是「扫到哪了」的唯一硬边界。 */
        drawLine(
            color = tint.copy(alpha = 0.10f),
            start = Offset(0f, top + bandH),
            end = Offset(size.width, top + bandH),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/* ── 呼吸 ─────────────────────────────────────────────────────────────── */

/** 一段缓慢呼吸的弧，给「正在等一件还不知道多久的事」。比转圈安静。 */
@Composable
fun BreathingRing(
    color: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 1_600,
) {
    val reduced = rememberReducedMotion()
    if (reduced) return
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = MotionEasing.Standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathing-phase",
    )
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val r = size.minDimension / 2f - stroke
        drawCircle(
            color = color.copy(alpha = 0.18f + 0.42f * phase),
            radius = r,
            style = Stroke(width = stroke),
        )
    }
}
