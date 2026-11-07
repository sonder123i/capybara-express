package dev.showcode.ui.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 共享元素转场的「接线盒」。
 *
 * Compose 的 `sharedElement` 需要两样东西：`SharedTransitionScope`（谁在管这场转场）
 * 和 `AnimatedVisibilityScope`（这个元素此刻是进还是出）。前者由 `SharedTransitionLayout`
 * 给，后者由 `AnimatedContent` 的内容 lambda 给——但它们在**根布局**里，
 * 而要用它的地方在好几层之下的 `HomeScreen` 里。
 *
 * 所以这里用 CompositionLocal 把它们一起传下去。传两个而不是只传 scope，
 * 是因为 `AnimatedVisibilityScope` 必须来自**当前这一次**内容组合，不能缓存。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Stable
class SharedTransitionBridge(
    val scope: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    /** 系统开了「减少动画」时整条链关掉：共享元素本质是位移，正是这类人要关的东西。 */
    val enabled: Boolean,
)

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransition = compositionLocalOf<SharedTransitionBridge?> { null }

/** 商品缩略图的共享 key。用 id 而不是 URL：同一件商品换图也要接着飞。 */
fun parcelThumbKey(parcelId: Long): String = "parcel-thumb-$parcelId"

/**
 * 把一块内容标成共享元素。
 *
 * 没有桥（比如在预览里、或者根布局还没包上 `SharedTransitionLayout`）时**原样返回**，
 * 不抛异常——这条 modifier 是「锦上添花」，不该让任何一屏因为它而画不出来。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedParcelThumb(parcelId: Long): Modifier {
    val bridge = LocalSharedTransition.current ?: return this
    if (!bridge.enabled) return this
    val key = parcelThumbKey(parcelId)
    return with(bridge.scope) {
        this@sharedParcelThumb.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = bridge.visibility,
        )
    }
}
