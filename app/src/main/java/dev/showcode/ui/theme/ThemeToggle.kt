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
    val progress by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = MotionTokens.LiquidSettle,
        label = "theme-toggle",
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
            drawTrack(progress)
            drawThumb(progress)
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
private val SkyShadow = Color(0xFF2E4A66)
private val SkyMid = Color(0xFF5A93C1)
private val SkyLight = Color(0xFF9BC9E4)
private val Cloud = Color(0xFFFFFFFF)
private val NightDeep = Color(0xFF3F4250)
private val NightSoft = Color(0xFF565A6B)
private val Star = Color(0xFFFFFFFF)
private val SunFace = Color(0xFFF1C428)
private val MoonFace = Color(0xFFC3C9D3)
private val CraterInk = Color(0xFF959DB0)

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
        /* 白天与黑夜两层交叉淡入：不是滑过去，是「同一块天空换了时间」。 */
        drawRect(
            brush = Brush.verticalGradient(
                0f to SkyShadow,
                0.45f to SkyMid,
                0.78f to SkyLight,
                1f to Color.White,
            ),
            alpha = 1f - progress,
        )
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF23252E),
                0.5f to NightDeep,
                1f to NightSoft,
            ),
            alpha = progress,
        )
        if (progress < 1f) drawClouds(1f - progress)
        if (progress > 0f) drawStars(progress)
        /* 槽顶压暗一档：这是「凹」最直接的一笔。 */
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
                startY = well.top,
                endY = well.top + well.height * 0.40f,
            ),
        )
    }

}

/**
 * 云：一整个连续的团，不是三个分离的圆——同色圆叠在一起，重叠处自然合成一片。
 * 越靠右越大越低，给左边的太阳让出位置。
 */
private fun DrawScope.drawClouds(alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val h = size.height
    val w = size.width
    fun puff(fx: Float, fy: Float, fr: Float, al: Float) {
        drawCircle(Cloud.copy(alpha = a * al), h * fr, Offset(w * fx, h * fy))
    }
    puff(0.44f, 1.02f, 0.30f, 0.95f)
    puff(0.60f, 0.92f, 0.34f, 1.00f)
    puff(0.78f, 1.00f, 0.32f, 1.00f)
    puff(0.94f, 0.88f, 0.28f, 0.95f)
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

private fun DrawScope.drawThumb(progress: Float) {
    val r = ThumbSize.toPx() / 2f
    val left = WellInset.toPx() + ThumbPad.toPx() + ThumbTravel.toPx() * progress
    val c = Offset(left + r, size.height / 2f)

    /* 落影：球压在槽里的投影，偏下一点。 */
    drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.98f, Offset(c.x, c.y + r * 0.14f))

    /* 球体近乎纯色：参考图里太阳是一整块 #F1C428，只有最外缘一圈略暗。
       不用径向高光——那会变成塑料球。 */
    drawCircle(lerpColor(SunFace, MoonFace, progress), r, c)

    if (progress < 1f) drawSunFace(c, r, 1f - progress)
    if (progress > 0f) drawMoonFace(c, r, progress)
}

/**
 * 太阳：参考图里就是一颗**纯平**的黄球——没有光芒、没有高光、没有内圈。
 *
 * 这一版什么都不画。之前加过一圈 9% 的白当高光，在深色槽上渲染成一道可见的环，
 * 反而像贴了个塑料盖；参考图的干净正是来自「什么都不加」。
 */
private fun DrawScope.drawSunFace(center: Offset, r: Float, alpha: Float) = Unit

/** 月亮：三个坑，位置与大小照参考图（左中最大、右上中等、右下最小）。 */
private fun DrawScope.drawMoonFace(center: Offset, r: Float, alpha: Float) {
    val a = alpha.coerceIn(0f, 1f)
    val craters = listOf(
        Triple(-0.16f, 0.16f, 0.30f),
        Triple(0.30f, -0.34f, 0.20f),
        Triple(0.34f, 0.40f, 0.13f),
    )
    craters.forEach { (dx, dy, rr) ->
        drawCircle(
            color = CraterInk.copy(alpha = a * 0.72f),
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
