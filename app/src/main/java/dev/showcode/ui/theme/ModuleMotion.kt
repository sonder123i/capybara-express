package dev.showcode.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 换屏时由导航层持有的入场时间线；列表项**永不**自己拥有或重启入场动画。
 *
 * 教务 `ui/theme/ModuleMotion.kt` 的同源移植。它解决的问题很具体：
 * 每个模块各自 `LaunchedEffect` 播一次入场，会在快速换屏、返回、滚动复用时任性地重播；
 * 而时间线挂在导航层之后，滚动复用只是读到同一个进度值，入场只属于「这一次换屏」。
 */
val LocalModuleEntrance = compositionLocalOf<(() -> Float)?> { null }

object ModuleMotion {
    const val EnterMillis = 380
    const val StaggerMillis = 75
    const val MaxGroups = 4
    const val TimelineMillis = EnterMillis + StaggerMillis * (MaxGroups - 1)
    const val ExitMillis = 170

    /**
     * 时间线 → 某一组模块的相位。组号越大的模块起步越晚，
     * 超过 [MaxGroups] 的组与最后一组同相——长列表的尾部不无限排队。
     */
    fun progress(timeline: Float, group: Int): Float {
        val elapsed = timeline.coerceIn(0f, 1f) * TimelineMillis
        val phase = ((elapsed - group.coerceIn(0, MaxGroups - 1) * StaggerMillis) / EnterMillis)
            .coerceIn(0f, 1f)
        return MotionEasing.FastOutSlowIn.transform(phase)
    }

    fun expand(reduced: Boolean): EnterTransition = if (reduced) EnterTransition.None else
        fadeIn(tween(EnterMillis)) + expandVertically(tween(EnterMillis, easing = FastOutSlowInEasing))

    fun collapse(reduced: Boolean): ExitTransition = if (reduced) ExitTransition.None else
        fadeOut(tween(ExitMillis)) + shrinkVertically(tween(ExitMillis, easing = MotionEasing.Accelerate))
}

/**
 * 模块入场：淡入 + 16dp 上浮，组号决定错峰。
 *
 * 没有导航时间线（LocalModuleEntrance 为 null，比如子屏或预览）时原样返回——
 * 模块不自己造动画，这是「入场只属于换屏」这条契约的另一半。
 *
 * ModulateAlpha 而不是自动 offscreen：自动策略会在 alpha < 1 时建一层 bounds 尺寸的
 * 离屏图层，玻璃阴影会在最后一帧被裁掉；直接调制绘制命令就没有这个问题。
 */
@Composable
fun Modifier.moduleEntrance(group: Int, progress: (() -> Float)? = null): Modifier {
    val timeline = progress ?: LocalModuleEntrance.current ?: return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.ModulateAlpha
        val p = ModuleMotion.progress(timeline(), group)
        alpha = p
        translationY = 16.dp.toPx() * (1f - p)
    }
}
