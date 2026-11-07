package dev.showcode.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 屏幕余量。移植自教务 `ui/system/ScreenMetrics.kt`（阈值与那条硬约束一起搬）。
 *
 * 两个系数都是 **0 = 宽松，1 = 最紧**，专门用来 lerp 写死的几何。
 *
 * **为什么需要**：本仓库那套尺寸是照 20:9（411×914dp）手调的常量。换到 16:9（360×640dp）
 * 横向少 51dp、纵向少 274dp，可用高从 862dp 掉到 568dp——顶栏加底栏能吃掉近一半屏幕。
 * 我自己的模拟器就是 1080×1920（16:9），所以之前那些"实机验证"其实是在**短屏**上做的，
 * 而他手机是 20:9。
 *
 * **阈值为什么这么选**：20:9 上两个系数都恰好为 0，于是所有 `tall()/wide()` 都返回原来那个常量，
 * 20:9 的观感一个 dp 都不变。调阈值前先想清楚它会不会把 20:9 拖离 0。
 *
 * **为什么是连续系数而不是尺寸档**：分档会在阈值附近出现"两台差 5dp 的设备排版完全不同"的跳变，
 * 而 18:9 这种中间比例只能被硬塞进某一档。
 */
private const val SqueezeCeilDp = 720f
private const val SqueezeRangeDp = 160f
private const val NarrowCeilDp = 380f
private const val NarrowRangeDp = 40f

data class ScreenMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    /** 可用高 = 窗口高 − 状态栏 − 导航栏。布局真正能用的就是这一段。 */
    val usableHeightDp: Dp,
    /** 纵向紧凑度。可用高 862（20:9）→ 0，568（16:9 三键导航）→ 0.95。 */
    val squeeze: Float,
    /** 横向紧凑度。宽 411 → 0，360 → 0.5。 */
    val narrow: Float,
    /**
     * 用户字号缩放（系统设置里的「字体大小」）。1 = 标准，最大 2.0。
     *
     * 为什么需要：上面的两个系数只管**屏幕尺寸**，管不了**字号**。
     * 把系统字号调到 1.5×/2× 之后，底栏标签要更高的行高，而轨道高度还是 56dp——
     * 标签被裁掉一截、文字从底栏里溢出去压住页面内容。
     * 那不是「不好看」，是这一档用户根本读不全。
     */
    val fontScale: Float,
) {
    /** 按纵向紧凑度插值。[loose] 是 20:9 的尺寸，短屏收到 [tight]。 */
    fun tall(loose: Dp, tight: Dp): Dp = lerpDp(loose, tight, squeeze)

    /** 按横向紧凑度插值。 */
    fun wide(loose: Dp, tight: Dp): Dp = lerpDp(loose, tight, narrow)

    /**
     * 含字号的纵向尺寸：先按屏幕插值，再按字号放大。
     *
     * 只放大**超出 1.0 的那部分**，而且打折到 [textGrowth]——
     * 字阶放大 1.5× 时容器不需要也长 1.5×（内边距不该跟着涨），
     * 0.6 是让 2× 字号下标签仍有两行余量的最低值。
     */
    fun tallWithText(loose: Dp, tight: Dp, textGrowth: Float = 0.6f): Dp {
        val base = lerpDp(loose, tight, squeeze)
        val extra = (fontScale - 1f).coerceAtLeast(0f) * textGrowth
        return base * (1f + extra)
    }
}

private fun lerpDp(loose: Dp, tight: Dp, t: Float): Dp = loose + (tight - loose) * t

@Composable
fun rememberScreenMetrics(): ScreenMetrics {
    val density = LocalDensity.current
    // 用 View 的像素尺寸而不是 LocalConfiguration：后者在不同版本上是否已扣系统栏并不一致，
    // 扣重了短屏会白白多压一档。View 的 height 就是窗口高度。
    val view = LocalView.current
    val windowWidthDp = view.width / density.density
    val windowHeightDp = view.height / density.density
    val barsDp = (
        WindowInsets.statusBars.getTop(density) +
            WindowInsets.navigationBars.getBottom(density)
        ) / density.density
    val usableDp = windowHeightDp - barsDp
    /* 系统字号。`LocalDensity.fontScale` 会跟着「字体大小」设置变，
       而 `density` 本身不会——所以必须单独读它。 */
    val fontScale = density.fontScale
    return remember(windowWidthDp, usableDp, fontScale) {
        ScreenMetrics(
            widthDp = windowWidthDp.dp,
            heightDp = windowHeightDp.dp,
            usableHeightDp = usableDp.dp,
            squeeze = ((SqueezeCeilDp - usableDp) / SqueezeRangeDp).coerceIn(0f, 1f),
            narrow = ((NarrowCeilDp - windowWidthDp) / NarrowRangeDp).coerceIn(0f, 1f),
            fontScale = fontScale,
        )
    }
}