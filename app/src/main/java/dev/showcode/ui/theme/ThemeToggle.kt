package dev.showcode.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 亮暗开关。
 *
 * 造型照 Figma 那版：**嵌进卡片里的凹槽 + 槽里一颗小球**，白天是太阳和云，
 * 夜里是月亮和星。
 *
 * 全部尺寸按参考图量出来的比例换算（以轨道高为 1）：
 *
 * | 元素 | 参考图 | 比例 |
 * |---|---|---|
 * | 轨道宽 | 212px | 2.16 |
 * | 轨道高 | 98px | 1 |
 * | 凹槽（天空）高 | 86px | 0.88 |
 * | 球直径 | 68px | **0.69** |
 * | 球到槽边 | 9px | 0.09 |
 *
 * 关键一条：**球比槽小、完全嵌在槽里**。球一旦溢出轨道，就从「嵌进去的圆钮」
 * 退化成「滑块」——那是第一版做错的地方。
 *
 * 三件事 Compose 没有原生支持，都是手画的：
 * 1. **凹槽**：外圈留空（露出来的就是边框），里面缩进 2.5dp 画天空，上缘压暗；
 * 2. **太阳是纯色**：参考图里整颗球是一块实心 `#F1C428`，没有光芒、没有高光。
 *    加光芒会变成齿轮，加径向高光会变成塑料球——两样都不要；
 * 3. **月亮上的坑**：三个大小不一的暗灰圆，避开球心。
 */
@Composable
fun ThemeToggle(
    dark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val target = if (dark) 1f else 0f
    /* 两条时间线，**故意不同步**：
     *
     * - **天空**（`skyProgress`）**瞬时**跟着页面走。页面主题是瞬间切换的
     *   （`ShowCodePaletteState` 一改全 App 立刻重组），天空只要慢一帧，
     *   就会在刚变白的卡片上留一块深色——那正是「暗切亮时框内闪黑」。
     *   这里曾经给天空 140ms 的 tween，仍然看得见；所以它现在是 0。
     * - **球**（`thumbProgress`）走 280ms 的弹簧，它才是「角色在动」的那一半，
     *   而且球本来就只在槽内移动，不参与背景明暗，慢一点不会有色块冲突。
     */
    val skyProgress = target
    val thumbProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = MotionTokens.LiquidSettle,
        label = "theme-toggle-thumb",
    )
    val interaction = remember { MutableInteractionSource() }
    val label = if (dark) "当前是暗色，点一下换亮色" else "当前是亮色，点一下换暗色"

    Box(
        modifier = modifier
            .width(TrackWidth)
            .height(TrackHeight)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onToggle()
            }
            .semantics {
                contentDescription = label
                role = Role.Switch
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.width(TrackWidth).height(TrackHeight)) {
            drawTrack(skyProgress)
            drawThumb(thumbProgress)
        }
    }
}

/* ── 尺寸：按参考图比例，以轨道高 40dp 为基准 ───────────────────────── */
private val TrackHeight = 44.dp
private val TrackWidth = 95.dp                                     // 44 × 2.16
private val WellInset = 4.dp                                       // 槽到轨道边
/** 球到槽内壁的呼吸位：参考图里球左边距 12px、槽左边距 6px，差 6px ≈ 0.06 倍轨道高。 */
private val ThumbPad = 2.5.dp
private val ThumbSize = 28.dp                                      // 井 36dp × 0.79（参考图 68/86）
private val ThumbTravel = TrackWidth - WellInset * 2 - ThumbPad * 2 - ThumbSize

/* ── 颜色：全部从参考图取样 ─────────────────────────────────────────── */
/* 晴朗的天：从「浅天蓝」到「近白的地平线」，全程不提黑。
   参考图的顶部其实有一道深蓝，但缩到 44dp 高之后那一道会被读成「发霉」，
   所以这里刻意提亮——用户要的是「晴朗」，不是「傍晚」。 */
/* 色值直接取自参考图（`ref-sky-big.png` 逐像素采样）：
   天空中部 #4888BB、下部 #5893C5。参考图的「晴朗」来自**饱和度**，
   不是来自亮度——所以这一版比上一版更深、更蓝，但顶部绝不再压黑。 */
private val SkyTopEdge = Color(0xFF5D91C2)
private val SkyMain = Color(0xFF538CC2)
private val SkyLower = Color(0xFF7FB4DA)
private val SkyHaze = Color(0xFFD8ECF8)
private val Cloud = Color(0xFFFFFFFF)
/* 云的三层：越远越蓝、越小、位置越高。参考图里正是这三层叠出纵深。 */
private val CloudFar = Color(0xFFB6D3EA)   // 参考图取样 #ADCAE6，略提亮以抵消屏幕压缩
private val CloudMid = Color(0xFFD6E8F6)
/* 阳光的暖白：纯白在蓝天上像雾，带一点黄才像光。 */
private val SunGlow = Color(0xFFFFF0C8)
/* 夜空的四段带，色值取自参考图逐列采样（`ref-frame5.png`）。 */
private val NightBand1 = Color(0xFF1C1F2C)
private val NightBand2 = Color(0xFF2D333D)
private val NightBand3 = Color(0xFF3E4250)
private val NightBand4 = Color(0xFF4C505C)
private val Star = Color(0xFFFFFFFF)
private val SunFace = Color(0xFFF1C428)
/* 月亮的灰直接取参考图实测值 #C3C9D1。
   ⚠️ 曾经提到 #D6DBE4，实测渲染成 216——在深色槽里跳成一块「白饼」。
   参考图的月亮其实很暗、很平，只靠三个坑和一道落影立住。 */
private val MoonTop = Color(0xFFC9CFD9)
private val MoonFace = Color(0xFFC3C9D3)
private val MoonLow = Color(0xFFB9C0CC)
private val CraterInk = Color(0xFF949DB0)

/**
 * 槽：外圈留空（露出来的就是边框），里面缩进一点画天空。
 */
private fun DrawScope.drawTrack(progress: Float) {
    val inset = WellInset.toPx()
    val radius = (size.height - inset * 2) / 2f
    val well = RoundRect(
        left = inset,
        top = inset,
        right = size.width - inset,
        bottom = size.height - inset,
        radiusX = radius,
        radiusY = radius,
    )
    val wellPath = Path().apply { addRoundRect(well) }

    /* 凹槽的投影：上缘外侧那道暗边，是「陷进去」最直接的证据。 */
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.10f),
        topLeft = Offset(inset, inset),
        size = Size(well.width, well.height),
        cornerRadius = CornerRadius(radius, radius),
    )

    clipPath(wellPath) {
        /* 晴天永远画满（alpha = 1），夜色只往上叠一层。
         *
         * ⚠️ 这里曾经是「两层都按 progress 交叉淡入」——那会在中点留下 25% 的透明，
         * 卡片底色从井里透出来，就是用户报的「暗色切亮色时框内闪黑」。
         * 天空必须是不透明的地板，夜色是盖在上面的被子，顺序不能反。
         */
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to SkyTopEdge,
                0.34f to SkyMain,
                0.70f to SkyLower,
                0.94f to SkyHaze,
                1.00f to SkyHaze,
            ),
        )
        /* 晴天的三件套，按「远 → 近」叠：光束 → 远云 → 近云。
           参考图里最显眼的就是那几道从太阳斜着射出去的浅色光带——
           它比云更能表达「晴」，所以放在云下面、只压天空。 */
        if (progress < 1f) {
            val a = 1f - progress
            drawSunRays(a)
            drawClouds(a)
        }
        /* 夜色：参考图（`ref-frame5.png`）从左到右是 **四层由深到浅的云影**，
           而不是一块黑底撒几颗星。正是这四层让暗色和白天一样有纵深。
         *
         * ⚠️ 两层都试过，都不行：
         * - 纯 `horizontalGradient` 四段：边界是**竖直线**，读成「画了三条线」；
         * - 过渡区间给到 0.09：四段被渐变糊成一片，完全看不出层次。
         *
         * 参考图的边界其实是**弧**（凸向左侧，像一团团云的后缘），
         * 所以这里用半径很大的圆来切——半径是井高的 1.6 倍，
         * 弧顶位置就是色带的边界，弧线因此极缓，看不出是圆。 */
        if (progress > 0f) {
            drawNightSky(progress)
            drawStars(progress)
        }
        /* 内高光：沿井口内侧一圈极淡的白，让槽看起来是「被凿出来的玻璃」。
           参考图左上那道亮边就是它，只有 0.5dp 宽，但少了它就平。
           ⚠️ **只在亮色画**：暗色下这圈白会变成一道发亮的刮痕，
           贴在深色卡片上非常刺眼（用户报的「白色区域」之一）。 */
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f * (1f - progress)),
            topLeft = Offset(well.left + 0.5f, well.top + 0.5f),
            size = Size(well.width - 1f, well.height - 1f),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1f),
        )
        /* 槽顶压暗一档：这是「凹」最直接的一笔。 */
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.11f), Color.Transparent),
                startY = well.top,
                endY = well.top + well.height * 0.09f,
            ),
        )
    }

}

/**
 * 夜空：一条**平滑**的左深右浅渐变，不带任何可见的边界。
 *
 * 这里试过三版「分层」，全部被读成脏痕，最后放弃：
 *
 * 1. `horizontalGradient` 分四段（边界竖线）→ 像刷了三道竖痕；
 * 2. 三层弧各用不同圆心高度 → 弧线互相交叉，交叉处拼出斑块；
 * 3. 三层同心弧 + 描一道 4.5% 的白当亮边 → 那根 `Stroke` 就是划痕本身。
 *
 * 根因是**尺寸**：参考图轨道宽 212px，能容下三道云影的边界；
 * 这里只有 95dp，任何一条边界都只有一两像素的过渡空间，必然读成线。
 * 所以夜色只保留「右浅」这个方向感——它靠渐变就够，不需要画出来。
 *
 * 星星仍然是星星，纵深交给它们和月亮的明度差去做。
 */
private fun DrawScope.drawNightSky(alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    drawRect(
        brush = Brush.horizontalGradient(
            0.00f to NightBand1,
            0.38f to NightBand2,
            0.72f to NightBand3,
            1.00f to NightBand4,
        ),
        alpha = a,
    )
}

/**
 * 光束：从太阳中心斜着射出去的几道浅色带。
 *
 * 参考图里那几道竖条纹不是装饰——它们是「太阳在左边、光往右上走」的方向感。
 * 做法是几个平行四边形（随高度略微右倾），越靠右越淡、越窄，
 * 全部用白 6%~10% 的极低透明度，叠在天空上只提亮、不变白。
 */
/**
 * 晴天的光：太阳周围一小圈暖光 + 三道**宽而近垂直**的光带。
 *
 * 两次失败换来的结论：
 * - 光晕半径不能大（1.25h 时整块天空被洗成灰绿，云也糊了），这里收到 0.85h、峰值 0.28；
 * - 光带不能窄、不能斜（0.06w × 0.34h 斜切读成栅栏），要**宽**（0.13w）且几乎垂直（0.04h）。
 */
private fun DrawScope.drawSunRays(alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val h = size.height
    val w = size.width
    val sunCx = WellInset.toPx() + ThumbPad.toPx() + ThumbSize.toPx() / 2f
    val sunCy = size.height / 2f

    /* ① 紧贴球缘的一圈白光。
       半径 0.85h 的暖光晕试过，会把左上角洗成灰绿——那不是「晴」，是「雾霾」。
       参考图里太阳边缘其实只有极窄的一圈提亮，所以这里收到 0.58h、峰值 0.16。 */
    val glowR = h * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to SunGlow.copy(alpha = a * 0.34f),
            0.50f to SunGlow.copy(alpha = a * 0.11f),
            1f to Color.Transparent,
            center = Offset(sunCx, sunCy),
            radius = glowR,
        ),
        radius = glowR,
        center = Offset(sunCx, sunCy),
    )

    /* ② 三道宽光带，从太阳右缘往外铺；越远越淡。 */
    val bands = listOf(
        Triple(0.33f, 0.115f, 0.20f),
        Triple(0.48f, 0.095f, 0.16f),
        Triple(0.62f, 0.075f, 0.12f),
    )
    bands.forEach { (fx, fw, fa) ->
        val top = fx * w
        val skew = h * 0.04f
        val path = Path().apply {
            moveTo(top, 0f)
            lineTo(top + fw * w, 0f)
            lineTo(top + fw * w + skew, h)
            lineTo(top + skew, h)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.45f to Color.White.copy(alpha = a * fa),
                1f to Color.Transparent,
                startX = top,
                endX = top + fw * w + skew,
            ),
        )
    }

}

/**
 * 云：两层。
 *
 * - **远云**（`CloudFar`）：更大、更高、偏灰蓝，压在天空里，制造纵深；
 * - **近云**（白）：贴底、更实、边缘更清晰，是参考图里那几团大白。
 *
 * 只有一层会显得「贴纸感」——纵深靠的就是这一前一后两个明度差。
 */
private fun DrawScope.drawClouds(alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val h = size.height
    val w = size.width
    fun puff(color: Color, fx: Float, fy: Float, fr: Float, al: Float) {
        drawCircle(color.copy(alpha = color.alpha * a * al), h * fr, Offset(w * fx, h * fy))
    }
    /* 云按参考图（`ref-sky-big.png`）量出来的轮廓摆，井口归一化成 0~1：
     *
     * | x    | 白云顶 | 淡云顶 |
     * |------|--------|--------|
     * | 0.42 | 0.77   | 0.58   |
     * | 0.62 | 0.83   | 0.49   |
     * | 0.77 | 0.74   | 0.40   |
     * | 0.92 | 0.29   | 0.00   |
     *
     * 关键那条：**右侧是一整片斜着爬上去的云堤**，从左下一直顶到右上角。
     * 只把云铺在底边，右半张就全是空蓝——那才是「单调」的根。
     *
     * 形状上试过三种：
     * - 正圆半径 0.24h：圆之间只剩深谷，读成「扇贝边」；
     * - 横向压扁的椭圆：云顶变成一道软坡，**体积感全丢**（更糟）；
     * - 正圆半径 0.30~0.34h：相邻圆重叠过半，交点只比圆顶低一点点，
     *   云顶既连成一片又保留圆顶的起伏——参考图就是这个。
     */
    /* ① 远云：最蓝，垫在最后，只在右上露出来。 */
    puff(CloudFar, 0.60f, 0.86f, 0.34f, 0.92f)
    puff(CloudFar, 0.82f, 0.62f, 0.35f, 0.92f)
    puff(CloudFar, 1.02f, 0.30f, 0.36f, 0.92f)
    /* ② 中云：淡蓝，把远云和白云隔开一层。
       这一层比白云高半格，露出来的那半圈就是「云后面的云」。 */
    puff(CloudMid, 0.46f, 0.94f, 0.33f, 0.97f)
    puff(CloudMid, 0.68f, 0.80f, 0.34f, 0.97f)
    puff(CloudMid, 0.90f, 0.54f, 0.35f, 0.97f)
    /* ③ 近云：纯白，参考图里那几团最实的大白，右端顺着斜线一路爬高。
       fy 一律压在 1.02~1.06：再低就只剩一条白边，再高就盖掉太阳。 */
    puff(Cloud, 0.30f, 1.06f, 0.31f, 1.00f)
    puff(Cloud, 0.48f, 1.04f, 0.32f, 1.00f)
    puff(Cloud, 0.66f, 1.02f, 0.32f, 1.00f)
    puff(Cloud, 0.84f, 0.86f, 0.32f, 1.00f)
    puff(Cloud, 1.02f, 0.54f, 0.34f, 1.00f)
    /* 太阳脚下压一团：让球「坐」在云上而不是浮着。 */
    puff(Cloud, 0.08f, 1.16f, 0.28f, 0.96f)
}

/** 星：一小撮亮点 + 两颗四角星。位置固定，随机会让每帧画面抖。 */
private fun DrawScope.drawStars(alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val h = size.height
    val w = size.width
    val dots = listOf(
        Triple(0.13f, 0.36f, 0.85f),
        Triple(0.21f, 0.70f, 0.55f),
        Triple(0.31f, 0.48f, 0.40f),
        Triple(0.40f, 0.76f, 0.50f),
        Triple(0.48f, 0.32f, 0.35f),
        Triple(0.09f, 0.60f, 0.30f),
        Triple(0.26f, 0.20f, 0.26f),
        Triple(0.44f, 0.62f, 0.22f),
    )
    dots.forEach { (fx, fy, bright) ->
        drawCircle(Star.copy(alpha = a * bright), h * 0.016f, Offset(w * fx, h * fy))
    }
    fourPointStar(w * 0.19f, h * 0.30f, h * 0.100f, a * 0.95f)
    fourPointStar(w * 0.35f, h * 0.60f, h * 0.065f, a * 0.62f)
}

/** 四角星：两个菱形交叉。比五角星省事，也更接近参考图那种尖角星。 */
private fun DrawScope.fourPointStar(cx: Float, cy: Float, len: Float, alpha: Float) {
    val thin = len * 0.17f
    val path = Path().apply {
        moveTo(cx, cy - len); lineTo(cx + thin, cy); lineTo(cx, cy + len); lineTo(cx - thin, cy); close()
        moveTo(cx - len, cy); lineTo(cx, cy - thin); lineTo(cx + len, cy); lineTo(cx, cy + thin); close()
    }
    drawPath(path, Star.copy(alpha = alpha))
}

/* ── 球：太阳与月亮 ─────────────────────────────────────────────────── */

/**
 * 球：太阳与月亮在同一颗球上「擦除式」交替。
 *
 * 参考图里的过渡不是淡入淡出，是**一道弧从右往左扫过球面**：
 * 扫过的部分是月亮（灰 + 坑），没扫过的还是太阳（黄）。
 * 所以这里画两颗同心的球，用一条半径很大的圆弧当遮罩——
 * 大半径让边界看起来是「弯的」，和参考图一致。
 */
private fun DrawScope.drawThumb(progress: Float) {
    val r = ThumbSize.toPx() / 2f
    val left = WellInset.toPx() + ThumbPad.toPx() + ThumbTravel.toPx() * progress
    val c = Offset(left + r, size.height / 2f)

    /* ⚠️ 这里曾经有一圈「月晕」（半径 2.6r 的灰白径向渐变）。
       它是个错误：参考图里月亮周围显亮，靠的是**夜色本身就右浅**，
       不是一圈光。再叠一层灰白渐变，就变成月亮外挂着一块脏雾。 */

    /* 球**没有投影**。
     *
     * 参考图里太阳和月亮都是直接贴在槽里的实心圆片，边缘干干净净。
     * 这里试过三种投影，全都不成立：
     * - 半径 0.98r、下偏 0.14r：球下缘挂一道灰色月牙（亮色「那个弧」就是它）；
     * - 半径 1.16r、下偏 0.20r：变成围着球的一整圈灰环，更脏；
     * - 暗色下任何投影：月亮自己发光，压投影就是「脏边」。
     *
     * 球和槽的层次已经由**槽本身**承担（上缘压暗 + 内高光），
     * 球只需要是一块干净的实心色。 */

    /* 太阳：整颗纯黄，没有光芒也没有高光（参考图就是一块实心色）。 */
    drawCircle(SunFace, r, c)

    /* 月亮：从右边缘起，用一个大圆当遮罩扫过来。p=0 时遮罩完全在球外，
       p=1 时把整颗球盖住。半径取 1.9r，边界因此是一段缓弧而不是直线。 */
    if (progress > 0f) {
        val wipeR = r * 1.9f
        /* 圆心从「球右缘再往外 1.9r」移到「球心」，正好扫完整颗球。 */
        val wipeCx = c.x + (1f - progress) * (r + wipeR)
        val moonMask = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    center = Offset(wipeCx, c.y),
                    radius = wipeR,
                )
            )
        }
        clipPath(moonMask) {
            /* 多画 1px：遮罩边缘抗锯齿会漏出底下那道黄，缩在 r 上会留一圈金边。 */
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to MoonTop,
                    0.55f to MoonFace,
                    1f to MoonLow,
                    startY = c.y - r,
                    endY = c.y + r,
                ),
                radius = r + 1f,
                center = c,
            )
            drawMoonCraters(c, r)
        }
    }
}

/** 月亮上的三个坑，位置与大小照参考图（左中最大、右上中等、右下最小）。 */
private fun DrawScope.drawMoonCraters(center: Offset, r: Float) {
    val craters = listOf(
        Triple(-0.16f, 0.16f, 0.30f),
        Triple(0.30f, -0.34f, 0.20f),
        Triple(0.34f, 0.40f, 0.13f),
    )
    craters.forEach { (dx, dy, rr) ->
        drawCircle(
            color = CraterInk,
            radius = r * rr,
            center = Offset(center.x + r * dx, center.y + r * dy),
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
