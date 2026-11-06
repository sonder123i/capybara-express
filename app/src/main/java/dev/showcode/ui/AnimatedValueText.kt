package dev.showcode.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.showcode.ui.theme.MotionDuration
import dev.showcode.ui.theme.MotionProfile
import dev.showcode.ui.theme.rememberReducedMotion

/**
 * 数值变化时的滚动交接：新值从下方滑入，旧值向上退出，容器尺寸平滑跟随（SizeTransform clip=false）。
 *
 * 用在「会活着的数字」上——件数、计数。tabular 数字保证宽度不晃，这里保证**变化本身**有形：
 * 数字从 3 变 2 时不是凭空一跳，而是旧数字走、新数字来。
 *
 * 教务 `AnimatedValueText` 的同源移植。两条规矩跟着过来：
 * - 只在真实数据变化时播（targetState 变化触发），回到页面不重播；
 * - 减少动画时直接切换，没有滑动。
 *
 * 入场 220ms、退场 120ms：新的慢慢来、旧的赶紧走，两值重叠的那一瞬才有滚动的错觉。
 */
@Composable
fun AnimatedValueText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                /* 教务 AnimatedValueText 第 96–98 行：入场 220ms / 位移 1/4，退场 120ms 淡出。 */
                (fadeIn(tween(MotionDuration.Medium)) +
                    slideInVertically(tween(MotionDuration.Medium)) { it / 4 }) togetherWith
                    (fadeOut(tween(MotionProfile.PressMillis)) +
                        slideOutVertically(tween(MotionDuration.Medium)) { -it / 4 })
            }.using(SizeTransform(clip = false))
        },
        label = "value-change",
    ) { text ->
        Text(text = text, color = color, style = style, maxLines = 1)
    }
}
