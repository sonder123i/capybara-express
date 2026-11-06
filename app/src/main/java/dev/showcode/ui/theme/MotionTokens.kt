package dev.showcode.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动效 token。时长与弹簧取自教务 `ui/theme/MotionTokens.kt`（.design-qa/DESIGN.md 的 `motion` 段），
 * 这是「看起来像一家人」之外「摸起来像一家人」的那一维——L0 收口时量过，那时本仓库 grep 到
 * **0 处** animate/spring/tween，是全 App 唯一完全没有动效的系统。
 *
 * 教务那套的完整时长表是 100 / 180(tab) / 180(nav-label) / 200 / 250 / 260(nav-indicator) /
 * 400 / 400(dialog) / 1400(emphasis) ms，外加几个具名弹簧。这里**只声明真的有人消费的那几条**：
 * 留一个没人消费的 token，和「M3 给了 5 个容器 role、代码消费 0 个」是同一种毛病。
 *
 * 缓动曲线只对底栏动效族搬了（那几条是弹簧不是 tween）；换屏的缓动与弹簧在
 * `NavigationMotion.kt` 的 MotionProfile / ModuleMotion 里（2026-10-19 起归那一层管）。
 */
/**
 * 缓动曲线族。教务 `MotionTokens.kt` 的同源移植——之前这里只有弹簧，没有曲线，
 * 所以 tween 全在吃默认缓动；现在每条曲线都「按用途」取，而不是顺手 default。
 */
object MotionEasing {
    /** 常规过渡（Material 3 默认族）。 */
    val Standard = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** 强调过渡：起步更决，收尾更稳。 */
    val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** 元素进入：快出慢入。 */
    val FastOutSlowIn = androidx.compose.animation.core.CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    /** 连续运动。 */
    val Linear = androidx.compose.animation.core.CubicBezierEasing(0.0f, 0.0f, 1.0f, 1.0f)

    /** 元素退出：慢出快入。 */
    val Accelerate = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

/**
 * 时长表，逐值来自教务 `MotionTokens.kt` 的 `MotionDuration`（第 16–46 行）：
 * Fast 120 / Medium 220 / Number 180 / Slow 400 / ExpandCollapse 250 / NavIndicator 260 / NavLabel 180。
 * 只声明真的有人消费的条目，避免留下死配置。
 */
object MotionDuration {
    const val Fast = 120
    const val Medium = 220
    const val Number = 180
    const val Slow = 400
    const val ExpandCollapse = 250
    const val NavIndicator = 260
    const val NavLabel = 180
}

object MotionTokens {

    /* 换屏动效不在这一张表里：主屏之间归 `NavigationMotion.kt` 的 MotionProfile
       （pageSpring 0.74/180 + 8/12dp/0.985），子屏层级归 hierarchySpring（22dp/6dp）。
       这里曾立过 RouteChange/RouteExit/RouteEnterDistance 那六条，2026-10-19 全部删除——
       它们声明了却始终没人消费（上一版只做了淡化），和「M3 给了 5 个容器 role、
       代码消费 0 个」是同一种死配置。 */

    /** 无玻璃回退档（教务 `FallbackNavigationBar`）的选中度渐变：tween(220)。 */
    val FallbackWeight: FiniteAnimationSpec<Float> = tween(durationMillis = 220)

    /* ── 底栏动效核心（教务 `MotionSpring`）──────────────────────────── */

    /** 指示胶囊落位：`navSettle(0.60, 310)`——长行程要明显的果冻过冲。 */
    val NavSettle: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.60f, stiffness = 310f)

    /** 按压缩放回弹：`navRelease(0.55, 290)`。 */
    val NavRelease: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.55f, stiffness = 290f)

    /** 轨道/面板漂移回位：`liquidJellyRebound(0.66, 320)`。 */
    val LiquidJellyRebound: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.66f, stiffness = 320f)

    /** 液态控件落位：`liquidSettle(0.80, 420)`——收起形态的整栏收拢/单胶囊弹出共用。 */
    val LiquidSettle: FiniteAnimationSpec<Float> = spring(dampingRatio = 0.80f, stiffness = 420f)
}
