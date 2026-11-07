package dev.showcode.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import dev.showcode.ui.theme.Accent
import dev.showcode.ui.theme.Ink
import dev.showcode.ui.theme.UrgencyDueSoon
import dev.showcode.ui.theme.UrgencySafe
import dev.showcode.ui.theme.rememberReducedMotion
import kotlin.random.Random

/**
 * 一次性的纸屑。用在「这一趟取完了」这种真正的终点，不用在每一次点击上。
 *
 * 三条约束：
 * - **只播一次**：由 [trigger] 驱动，播完就停，不是常驻循环；
 * - **减少动画时直接不画**：系统开了「移除动画」的人只看到勾和文字；
 * - 颜色只用主题里已有的四种（成功绿、强调蓝、临期橙、墨），不做彩虹。
 */
@Composable
fun ConfettiBurst(
    trigger: Any,
    modifier: Modifier = Modifier,
    count: Int = 30,
) {
    val reduced = rememberReducedMotion()
    if (reduced) return

    /* 颜色**不能** remember：主题一换，纸屑还留着上一套的墨色和绿。
       这几条是 getter，每次重组都读当前色板。 */
    val colors = listOf(UrgencySafe, Accent, UrgencyDueSoon, Ink)
    val specs = remember(trigger) {
        val random = Random(trigger.hashCode().toLong())
        List(count) {
            ConfettiSpec(
                x = 0.18f + random.nextFloat() * 0.64f,
                y = 0.04f + random.nextFloat() * 0.10f,
                vx = (random.nextFloat() - 0.5f) * 0.55f,
                vy = 0.18f + random.nextFloat() * 0.42f,
                size = 0.008f + random.nextFloat() * 0.008f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                delay = random.nextFloat() * 0.18f,
                color = colors[it % colors.size],
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1250, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        specs.forEach { p ->
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val x = (p.x + p.vx * local) * size.width
            val y = (p.y + p.vy * local + 0.58f * local * local) * size.height
            if (y > size.height) return@forEach
            val fade = (1f - ((local - 0.72f) / 0.28f)).coerceIn(0f, 1f)
            val side = p.size * size.width
            rotate(degrees = p.spin * local, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - side / 2f, y - side * 0.3f),
                    size = Size(side, side * 0.6f),
                    cornerRadius = CornerRadius(side * 0.2f),
                )
            }
        }
    }
}

private data class ConfettiSpec(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val spin: Float,
    val delay: Float,
    val color: Color,
)
