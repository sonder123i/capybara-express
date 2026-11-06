package dev.showcode.ui.theme

import kotlin.math.PI
import kotlin.math.sin

/**
 * 启动动画的时间轴，逐条对齐只读参考工程
 * `capybara-course-helper/app/src/main/java/com/tyust/course/ui/theme/StartupChoreography.kt`。
 *
 * 参考工程 0–850ms 是六张卡片展开/错峰收拢 + 闪电描线；A 版把定稿图标拆成 7 个语义层
 * （见 `.verify/launch_layers/storyboard.md`），用同一套包络驱动：0–180ms 分层放大，
 * 180–650ms 按 `index * 12ms` 错峰归位，650–850ms 速度线描回 + 定位针轻弹，
 * 850–1100ms 落定与内容入场。所有包络在 650ms 精确归零，850ms 起只剩整层缩放与淡出。
 */
object StartupChoreography {
    const val DurationMillis = 1100L
    const val RevealStartMillis = 850f
    const val RevealMillis = 250f
    const val NavStartProgress = 0.15f

    /** 0–180ms 展开。 */
    const val ExpandMillis = 180f

    /** 最迟 650ms 全部归位。 */
    const val CollapseMillis = 650f

    /** 每层收拢起点再往后错 `index * 12ms`。 */
    const val StaggerMillis = 12f

    /** 车前速度线 0–150ms 淡出。 */
    const val SpeedOutMillis = 150f

    /** 650–850ms 速度线描回，同时定位针轻弹。 */
    const val SpeedInStartMillis = 650f
    const val SpeedInMillis = 200f

    /** 插画画布的有效边长（dp）。图集 tile 就是这块 76dp 画布，不含 108dp 的透明留白。 */
    const val ArtSizeDp = 76f

    /** 图集：4 列 × 2 行，tile 600px，四周 4px 边缘复制垫片（防止采样串到相邻 tile）。 */
    const val AtlasColumns = 4
    const val AtlasTilePx = 600
    const val AtlasPadPx = 4
    const val AtlasCellPx = AtlasTilePx + AtlasPadPx * 2

    /**
     * 单层运动参数。顺序即绘制顺序（底 → 顶）；`index` 同时是图集 tile 序与错峰序。
     *
     * pivot 归一化到 76dp 插画画布，来自各层包围盒中心（storyboard.md 第 5 节）。
     */
    data class LayerMotion(
        val name: String,
        val index: Int,
        val dxDp: Float,
        val dyDp: Float,
        val maxScale: Float,
        val pivotX: Float,
        val pivotY: Float,
        val speedLine: Boolean = false,
        val bounce: Boolean = false,
    )

    val Layers = listOf(
        LayerMotion("bg", 0, 0f, 0f, 1.000f, 0.5000f, 0.5000f),
        LayerMotion("map", 1, 0f, 1.5f, 1.000f, 0.6208f, 0.3756f),
        LayerMotion("bed", 2, 0f, -1f, 1.004f, 0.4844f, 0.7624f),
        LayerMotion("speed", 3, 5f, 0f, 1.000f, 0.3935f, 0.4067f, speedLine = true),
        LayerMotion("truck", 4, 4f, -1f, 1.025f, 0.8309f, 0.3561f),
        LayerMotion("capybara", 5, 0f, 0f, 1.000f, 0.4434f, 0.5530f),
        LayerMotion("pin", 6, 0f, 0f, 1.100f, 0.6328f, 0.1228f, bounce = true),
    )

    private fun smooth(value: Float): Float {
        val p = value.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    /** 850ms 之后页面内容的入场进度，0→1。 */
    fun content(milliseconds: Float): Float =
        smooth((milliseconds - RevealStartMillis) / RevealMillis)

    /** 图标落定：在内容入场的同一 250ms 内轻微放大再回到 1。 */
    fun settleScale(milliseconds: Float): Float {
        val p = ((milliseconds - RevealStartMillis) / RevealMillis).coerceIn(0f, 1f)
        return 1f + 0.045f * sin(p * PI.toFloat()) * (1f - p)
    }

    /** 底栏比页面晚 15% 入场；参考工程在 MainActivity 里就是这样换算的。 */
    fun navEntry(progress: Float): Float =
        ((progress - NavStartProgress) / (1f - NavStartProgress)).coerceIn(0f, 1f)

    /**
     * 单层位移/缩放包络：1 = 完全展开态，0 = 已归位。
     *
     * 0–180ms 所有层同步 0→1；180ms 后按 `index * 12ms` 先后开始 1→0，
     * 但每层的结束点都被夹在 650ms，所以层号越大，曲线越陡。
     */
    fun spread(milliseconds: Float, index: Int): Float {
        if (milliseconds <= ExpandMillis) return smooth(milliseconds / ExpandMillis)
        val start = ExpandMillis + StaggerMillis * index
        val span = CollapseMillis - start
        return 1f - smooth((milliseconds - start) / span)
    }

    /** 车前速度线的不透明度：0–150ms 淡出，650ms 前为 0，650–850ms 描回。 */
    fun speedAlpha(milliseconds: Float): Float = when {
        milliseconds <= SpeedOutMillis -> 1f - smooth(milliseconds / SpeedOutMillis)
        milliseconds < SpeedInStartMillis -> 0f
        else -> smooth((milliseconds - SpeedInStartMillis) / SpeedInMillis)
    }

    /**
     * 定位针在描线段（650–850ms）的额外缩放，峰值 +0.04。
     * 850ms 起精确为 0，不会和 settle 叠加成第二个形状。
     */
    fun pinBounce(milliseconds: Float): Float {
        val p = ((milliseconds - SpeedInStartMillis) / SpeedInMillis).coerceIn(0f, 1f)
        // 端点 p=1 时 float sin(π) 会落到 -1e-7，夹到 0 保证 850ms 几何精确归位。
        return (0.04f * sin(smooth(p) * PI.toFloat())).coerceAtLeast(0f)
    }

    /** 某层当前的缩放；t=0 与 t≥650ms 都是精确的 1f。 */
    fun layerScale(milliseconds: Float, layer: LayerMotion): Float {
        val scale = 1f + (layer.maxScale - 1f) * spread(milliseconds, layer.index)
        return if (layer.bounce) scale + pinBounce(milliseconds) else scale
    }

    /** 某层当前的横向位移（dp）；t=0 与 t≥650ms 都是精确的 0f。 */
    fun layerOffsetXDp(milliseconds: Float, layer: LayerMotion): Float =
        layer.dxDp * spread(milliseconds, layer.index)

    /** 某层当前的纵向位移（dp）；t=0 与 t≥650ms 都是精确的 0f。 */
    fun layerOffsetYDp(milliseconds: Float, layer: LayerMotion): Float =
        layer.dyDp * spread(milliseconds, layer.index)
}
